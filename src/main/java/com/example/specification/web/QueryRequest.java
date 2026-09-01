package com.example.specification.web;

import com.example.specification.SearchRequest;
import com.example.specification.query.FilterExpressionParser;
import com.example.specification.query.SortExpressionParser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Generic query DTO for REST controllers. Binds from plain request parameters
 * and carries the OData-style expressions as raw strings:
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
 * <p>The DTO is a thin adapter: it parses {@code filter} and {@code orderBy}
 * into the structured {@link SearchRequest} (which is also usable directly as
 * a JSON body on POST endpoints) and delegates everything else to it. Syntax
 * errors surface as {@link com.example.specification.query.QueryParseException},
 * mapped to HTTP 400 by {@link QueryExceptionHandler}.</p>
 */
public class QueryRequest {

    private String filter;
    private String orderBy;
    private int page = 0;
    private int size = SearchRequest.DEFAULT_PAGE_SIZE;

    /** Parses both expressions into the structured, transport-agnostic form. */
    public SearchRequest toSearchRequest() {
        SearchRequest request = new SearchRequest();
        request.setFilter(FilterExpressionParser.parse(filter));
        request.setSort(SortExpressionParser.parse(orderBy));
        request.setPage(page);
        request.setSize(size);
        return request;
    }

    public <T> Specification<T> toSpecification() {
        return toSearchRequest().toSpecification();
    }

    public Pageable toPageable() {
        return toSearchRequest().toPageable();
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
