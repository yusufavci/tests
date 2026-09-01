package com.example.specification.web;

import com.example.specification.GenericSpecification;
import com.example.specification.query.FilterExpressionParser;
import com.example.specification.query.SortExpressionParser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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

    public <T> Specification<T> toSpecification() {
        return new GenericSpecification<>(FilterExpressionParser.parse(filter));
    }

    public Pageable toPageable() {
        return PageRequest.of(Math.max(page, 0), normalizedSize(), SortExpressionParser.parse(orderBy));
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
