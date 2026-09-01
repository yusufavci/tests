package com.example.specification.web;

import com.example.specification.FilterCriteria;
import com.example.specification.FilterGroup;
import com.example.specification.GenericSpecification;
import com.example.specification.query.FilterExpressionParser;
import com.example.specification.query.SortExpressionParser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;

/**
 * Generic query DTO for REST controllers. Binds from plain GET query
 * parameters and carries the OData-style expressions as raw strings:
 *
 * <pre>
 * GET /employees?filter=name eq 'aaa' and salary isnot null
 *              &amp;orderBy=salary desc, name
 *              &amp;page=0&amp;size=20
 * </pre>
 *
 * <p>Usage — the controller stays a one-liner and never touches the parsing or
 * criteria layers:</p>
 *
 * <pre>{@code
 * @GetMapping("/employees")
 * Page<Employee> search(@ModelAttribute QueryRequest query) {
 *     return repository.findAll(query.<Employee>toSpecification(), query.toPageable());
 * }
 * }</pre>
 *
 * <p>When the client-facing field names differ from the entity paths, register
 * per-endpoint mappings with {@link #withFieldMappings(Map)} before building
 * the specification. Mapped names are translated in both {@code filter} and
 * {@code orderBy}; unmapped names are used as-is:</p>
 *
 * <pre>{@code
 * private static final Map<String, String> FIELDS = Map.of("userId", "user.id");
 *
 * @GetMapping("/employees")
 * Page<Employee> search(@ModelAttribute QueryRequest query) {
 *     query.withFieldMappings(FIELDS);
 *     return repository.findAll(query.<Employee>toSpecification(), query.toPageable());
 * }
 * }</pre>
 *
 * <p>{@code filter} parses into the {@code FilterGroup} tree (empty filter
 * matches everything) and {@code orderBy} into a Spring {@code Sort}. Page
 * size falls back to {@link #DEFAULT_PAGE_SIZE} when missing or invalid and is
 * capped at {@link #MAX_PAGE_SIZE}. Syntax and value errors surface as
 * {@link com.example.specification.query.QueryParseException} /
 * {@code IllegalArgumentException}, mapped to HTTP 400 by
 * {@link QueryExceptionHandler}.</p>
 */
public class QueryRequest {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 500;

    private String filter;
    private String orderBy;
    private int page = 0;
    private int size = DEFAULT_PAGE_SIZE;
    private Map<String, String> fieldMappings = Map.of();

    /**
     * Registers client-field-name → entity-path translations (e.g.
     * {@code "userId" -> "user.id"}), applied to both filter and order-by
     * fields. Fields without a mapping are used unchanged.
     */
    public QueryRequest withFieldMappings(Map<String, String> fieldMappings) {
        this.fieldMappings = fieldMappings != null ? fieldMappings : Map.of();
        return this;
    }

    public <T> Specification<T> toSpecification() {
        FilterGroup group = FilterExpressionParser.parse(filter);
        applyFieldMappings(group);
        return new GenericSpecification<>(group);
    }

    public Pageable toPageable() {
        Sort sort = SortExpressionParser.parse(orderBy);
        if (!fieldMappings.isEmpty() && sort.isSorted()) {
            sort = Sort.by(sort.stream()
                    .map(order -> new Sort.Order(order.getDirection(), mapField(order.getProperty())))
                    .toList());
        }
        return PageRequest.of(Math.max(page, 0), normalizedSize(), sort);
    }

    private void applyFieldMappings(FilterGroup group) {
        if (fieldMappings.isEmpty()) {
            return;
        }
        for (FilterCriteria criteria : group.getConditions()) {
            criteria.setField(mapField(criteria.getField()));
        }
        for (FilterGroup nested : group.getGroups()) {
            applyFieldMappings(nested);
        }
    }

    private String mapField(String field) {
        return field == null ? null : fieldMappings.getOrDefault(field, field);
    }

    private int normalizedSize() {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
