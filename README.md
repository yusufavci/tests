# Generic JPA Specification Builder

A small, dependency-light toolkit for building dynamic queries with Spring Data JPA
`Specification`s. It supports:

- **Nested AND/OR condition trees** of arbitrary depth
- **15 operators**: `EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`,
  `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `LIKE`, `NOT_LIKE`, `STARTS_WITH`, `ENDS_WITH`,
  `IN`, `NOT_IN`, `IS_NULL`, `IS_NOT_NULL`, `BETWEEN`
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

## Usage from a REST endpoint

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
| `LIKE` / `NOT_LIKE` | `value` | Case-insensitive *contains* |
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
