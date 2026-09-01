# Generic JPA Specification Builder

A small, dependency-light toolkit for building dynamic queries with Spring Data JPA
`Specification`s. It supports:

- **Nested AND/OR condition trees** of arbitrary depth
- **14 operators**: `EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`,
  `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `LIKE`, `STARTS_WITH`, `ENDS_WITH`,
  `IN`, `NOT_IN`, `IS_NULL`, `IS_NOT_NULL`, `BETWEEN`
- **OData-style filter strings** for GET endpoints — `name eq 'aaa' and salary isnot null`
  is parsed into the same filter tree, with `and`/`or`/`not`, parentheses, `in`,
  `between`, and `contains()`/`startswith()`/`endswith()` functions
- **Nested property paths** with dot notation (`author.address.city`) — associations are
  resolved with reused LEFT joins, and queries through collection associations are
  automatically made `distinct`
- **Automatic value conversion** — string/number values from JSON are converted to the
  entity attribute's type (enums, dates, UUIDs, numbers, booleans, `BigDecimal`, …)
- **Paging and ordering** via a JSON-friendly `SearchRequest` (with a page-size cap)

## Requirements

- Java 21+, Spring Boot 3.x (Jakarta Persistence)
- Your repository must extend `JpaSpecificationExecutor<T>`:

```java
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {
}
```

## Architecture / separation of layers

```
GET request params            POST JSON body
        │                            │
   QueryRequest  (web DTO)           │
        │  FilterExpressionParser    │
        │  SortExpressionParser      │
        ▼                            ▼
   SearchRequest  ──── FilterGroup / FilterCriteria / SortOrder  (transport-agnostic model)
        │
        ▼
   GenericSpecification<T>  +  Pageable   (persistence layer)
        │
        ▼
   repository.findAll(spec, pageable)
```

- `com.example.specification` — the core model and `Specification` engine. No web
  dependencies; usable from services, batch jobs, tests.
- `com.example.specification.query` — parsers turning OData-style strings into the core
  model (`FilterExpressionParser`, `SortExpressionParser`, `QueryParseException`).
- `com.example.specification.web` — the controller-facing `QueryRequest` DTO and the
  `QueryExceptionHandler` advice mapping bad queries to HTTP 400 problem details.

## Usage from a GET endpoint (OData-style request params)

Controllers bind the generic `QueryRequest` DTO from plain request parameters and stay
one-liners:

```java
@RestController
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeRepository repository;

    @GetMapping("/search")
    public Page<Employee> search(@ModelAttribute QueryRequest query) {
        return repository.findAll(query.<Employee>toSpecification(), query.toPageable());
    }
}
```

Example requests (values URL-encoded by the client):

```
GET /employees/search?filter=name eq 'aaa' and salary isnot null
GET /employees/search?filter=genre eq FICTION or (genre eq SCIENCE and pages gt 600)
                     &orderBy=pages desc, title asc&page=0&size=20
GET /employees/search?filter=contains(author.name, 'tolkien') and
                     publishedDate between '1930-01-01' and '1960-12-31'
GET /employees/search?filter=not (status in ('CLOSED', 'ARCHIVED'))
```

### Filter expression grammar

Keywords are case-insensitive; precedence is `not` > `and` > `or`, with parentheses for
grouping:

| Syntax | Meaning |
|---|---|
| `field eq value` / `field ne value` | equals / not equals (`eq null` → is-null) |
| `field gt/ge/lt/le value` | comparisons (aliases `gte`, `lte`, `neq`) |
| `field like 'x'` | case-insensitive contains |
| `contains(field, 'x')`, `startswith(field, 'x')`, `endswith(field, 'x')` | OData string functions |
| `field in ('a', 'b')` / `notin` | membership |
| `field between 1 and 10` | inclusive range |
| `field is null`, `field is not null`, `field isnot null`, `field isnotnull` | null checks |
| `not <expr>` | negation (folded via De Morgan; not applicable to `like`, `startswith`, `endswith`, `between`) |

Values: single-quoted strings (`''` escapes a quote), numbers, `true`/`false`, `null`,
and bare words (handy for enum constants: `genre eq FICTION`). Fields support dot
notation across associations. Sorting uses `orderBy=field [asc|desc], ...`.

Syntax errors and invalid values return `400 Bad Request` with an RFC 9457 problem body
(via `QueryExceptionHandler`, active when the `web` package is component-scanned):

```json
{ "title": "Invalid query expression", "status": 400, "detail": "Expected a value but found end of expression" }
```

## Usage from a POST endpoint (JSON body)

`SearchRequest` deserializes straight from JSON, so a single generic search endpoint is:

```java
@PostMapping("/books/search")
public Page<Book> search(@RequestBody SearchRequest request) {
    return bookRepository.findAll(request.<Book>toSpecification(), request.toPageable());
}
```

Example payload — `country = "USA" AND (title contains "java" OR price between 100 and 200)`,
page 0, 10 per page, ordered by title:

```json
{
  "filter": {
    "operator": "AND",
    "conditions": [
      { "field": "author.country", "operator": "EQUALS", "value": "USA" }
    ],
    "groups": [
      {
        "operator": "OR",
        "conditions": [
          { "field": "title", "operator": "LIKE", "value": "java" },
          { "field": "price", "operator": "BETWEEN", "values": [100, 200] }
        ]
      }
    ]
  },
  "page": 0,
  "size": 10,
  "sort": [
    { "field": "title", "direction": "ASC" }
  ]
}
```

Groups nest recursively: any group can contain both `conditions` and further `groups`,
combined by that group's `operator` (`AND` is the default).

## Usage from code (fluent builder)

```java
Specification<Book> spec = SpecificationBuilder.<Book>and()
        .eq("genre", Genre.SCIENCE)
        .or(g -> g.like("title", "spring")
                  .like("author.name", "spring"))
        .between("publishedDate", "2020-01-01", "2024-12-31")
        .build();

Page<Book> page = bookRepository.findAll(
        spec, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "publishedDate")));
```

The builder can also produce the raw tree (`toFilterGroup()`) to embed in a
`SearchRequest`, and `GenericSpecification` composes with any other specification via
the standard `Specification.and(...)` / `.or(...)` combinators.

## Operator semantics

| Operator | Input | Notes |
|---|---|---|
| `EQUALS` / `NOT_EQUALS` | `value` | Works for enums/dates/UUIDs given as strings |
| `GREATER_THAN` … `LESS_THAN_OR_EQUAL` | `value` | Attribute must be `Comparable` |
| `LIKE` | `value` | Case-insensitive *contains* |
| `STARTS_WITH` / `ENDS_WITH` | `value` | Case-insensitive |
| `IN` / `NOT_IN` | `values` | Non-empty list |
| `IS_NULL` / `IS_NOT_NULL` | — | Also works on associations (`"field": "author"`) |
| `BETWEEN` | `values` | Exactly two entries, inclusive |

Invalid input (unknown field, wrong value count, unconvertible value) fails fast with
`IllegalArgumentException` carrying the offending field name — map it to a 400 in a
`@RestControllerAdvice` if you expose the endpoint publicly.

## Notes for production use

- `SearchRequest` caps `size` at 500 (`SearchRequest.MAX_PAGE_SIZE`) and falls back to 20
  when the size is missing or invalid.
- Field names are resolved against the entity metamodel, so clients can only filter on
  mapped attributes; if you need to restrict filterable fields further (e.g. hide
  `password`-like columns), validate `FilterGroup` field names before building the
  specification.
- Sorting on nested paths (`author.name`) is supported by Spring Data's `Sort` directly.

## Running the tests

```bash
mvn test
```

The test suite (`GenericSpecificationTest`) runs against an in-memory H2 database and
covers nested groups, joins, distinct collection joins, value conversion, paging,
ordering, and JSON deserialization.
