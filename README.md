# Generic JPA Specification Builder

A small, dependency-light toolkit for building dynamic queries with Spring Data JPA
`Specification`s. It supports:

- **OData-style filter strings** sent as GET query parameters —
  `?filter=name eq 'aaa' and salary isnot null` — with `and`/`or`, parentheses, `in`,
  `between`, and `like`/`startswith`/`endswith` text operators
- **Nested AND/OR condition trees** of arbitrary depth
- **14 operators**: `EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`,
  `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `LIKE`, `STARTS_WITH`, `ENDS_WITH`,
  `IN`, `NOT_IN`, `IS_NULL`, `IS_NOT_NULL`, `BETWEEN`
- **Nested property paths** with dot notation (`author.address.city`) — associations are
  resolved with reused LEFT joins, and queries through collection associations are
  automatically made `distinct`
- **Automatic value conversion** — string/number values from the query string are
  converted to the entity attribute's type (enums, dates, UUIDs, numbers, booleans,
  `BigDecimal`, …)
- **Paging and ordering** via the `page`, `size` and `orderBy` query parameters
  (with a page-size cap)

## Requirements

- Java 21+, Spring Boot 4.x (Jakarta Persistence)
- Your repository must extend `JpaSpecificationExecutor<T>`:

```java
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {
}
```

## Architecture / separation of layers

```
GET query params  (filter, orderBy, page, size)
        │
   QueryRequest  (web DTO)
        │  FilterExpressionParser → FilterGroup / FilterCriteria  (transport-agnostic model)
        │  SortExpressionParser   → Sort
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

## Usage in a controller

Controllers bind the generic `QueryRequest` DTO from plain GET query parameters and stay
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

### Field mappings (client names → entity paths)

When the names the frontend sends differ from the entity model — e.g. the client knows
`userId` but the entity path is `user.id` — register per-endpoint mappings on the DTO.
Mapped names are translated in both `filter` and `orderBy`; any field **not** in the
mapping is searched as-is:

```java
private static final Map<String, String> FIELD_MAPPINGS = Map.of(
        "userId", "user.id",
        "userName", "user.name");

@GetMapping("/search")
public Page<Employee> search(@ModelAttribute QueryRequest query) {
    query.withFieldMappings(FIELD_MAPPINGS);
    return repository.findAll(query.<Employee>toSpecification(), query.toPageable());
}
```

```
GET /employees/search?filter=userId eq 42 and salary gt 50000&orderBy=userName asc
        → WHERE user.id = 42 AND salary > 50000 ORDER BY user.name ASC
```

Example requests (values URL-encoded by the client):

```
GET /employees/search?filter=name eq 'aaa' and salary isnot null
GET /employees/search?filter=genre eq FICTION or (genre eq SCIENCE and pages gt 600)
                     &orderBy=pages desc, title asc&page=0&size=20
GET /employees/search?filter=author.name like 'tolkien' and
                     publishedDate between '1930-01-01' and '1960-12-31'
GET /employees/search?filter=status notin ('CLOSED', 'ARCHIVED')
```

### Filter expression grammar

Keywords are case-insensitive; `and` binds tighter than `or`, with parentheses for
grouping:

| Syntax | Meaning |
|---|---|
| `field eq value` / `field ne value` | equals / not equals — case-insensitive on text attributes (`eq null` → is-null) |
| `field gt/ge/lt/le value` | comparisons (aliases `gte`, `lte`, `neq`) |
| `field like 'x'` | case-insensitive contains |
| `field startswith 'x'` / `field endswith 'x'` | case-insensitive prefix / suffix |
| `field in ('a', 'b')` / `notin` | membership — case-insensitive on text attributes |
| `field between 1 and 10` | inclusive range |
| `field is null`, `field is not null`, `field isnot null`, `field isnotnull` | null checks |

Values: single-quoted strings (`''` escapes a quote), numbers, `true`/`false`, `null`,
and bare words (handy for enum constants: `genre eq FICTION`). Fields support dot
notation across associations. Sorting uses `orderBy=field [asc|desc], ...`.

Text comparisons are **case-insensitive by default**. Append `cs` to a text operator or
function for exact-case matching, per condition — like PostgreSQL's `LIKE` vs `ILIKE`,
mirrored:

```
?filter=username eqcs 'JDoe' and name like 'john'
         └─ exact case              └─ case-insensitive
```

`eqcs`, `necs`, `likecs`, `startswithcs`, `endswithcs`, `incs`, `notincs`.

Syntax errors and invalid values return `400 Bad Request` with an RFC 9457 problem body
(via `QueryExceptionHandler`, active when the `web` package is component-scanned):

```json
{ "title": "Invalid query expression", "status": 400, "detail": "Expected a value but found end of expression" }
```

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

The builder can also expose the raw tree (`toFilterGroup()`), and `GenericSpecification`
composes with any other specification via the standard `Specification.and(...)` /
`.or(...)` combinators. Groups nest recursively: any `FilterGroup` can contain both
conditions and further groups, combined by that group's operator (`AND` is the default).

## Operator semantics

| Operator | Input | Notes |
|---|---|---|
| `EQUALS` / `NOT_EQUALS` | `value` | Works for enums/dates/UUIDs given as strings; case-insensitive on String attributes |
| `GREATER_THAN` … `LESS_THAN_OR_EQUAL` | `value` | Attribute must be `Comparable` |
| `LIKE` | `value` | Case-insensitive *contains* |
| `STARTS_WITH` / `ENDS_WITH` | `value` | Case-insensitive |
| `IN` / `NOT_IN` | `values` | Non-empty list; case-insensitive on String attributes |
| `IS_NULL` / `IS_NOT_NULL` | — | Also works on associations (`author isnot null`) |
| `BETWEEN` | `values` | Exactly two entries, inclusive |

Invalid input (unknown field, wrong value count, unconvertible value) fails fast with
`IllegalArgumentException` carrying the offending field name — map it to a 400 in a
`@RestControllerAdvice` if you expose the endpoint publicly.

## Notes for production use

- `QueryRequest` caps `size` at 500 (`QueryRequest.MAX_PAGE_SIZE`) and falls back to 20
  when the size is missing or invalid.
- Field names are resolved against the entity metamodel, so clients can only filter on
  mapped attributes; if you need to restrict filterable fields further (e.g. hide
  `password`-like columns), validate `FilterGroup` field names before building the
  specification.
- Sorting on nested paths (`author.name`) is supported by Spring Data's `Sort` directly.
- Default text matching (LIKE family, and equality/membership on String attributes) is
  case-insensitive via `LOWER(column)`; on large tables consider a function-based index
  on `LOWER(column)` for the frequently filtered text columns. The `cs` variants compare
  the raw column and can use plain indexes. From code, exact-case conditions are built
  with `FilterCriteria.of(...).caseSensitive()`.

## Running the tests

```bash
mvn test
```

The test suite runs against an in-memory H2 database and covers nested groups, joins,
distinct collection joins, value conversion, paging, ordering, the filter grammar, and
the full GET request flow (including 400 responses) through a MockMvc controller test.
