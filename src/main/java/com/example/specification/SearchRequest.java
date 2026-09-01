package com.example.specification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete, JSON-friendly search request: a nested filter tree plus paging
 * and ordering. Typical use in a controller:
 *
 * <pre>{@code
 * @PostMapping("/books/search")
 * Page<Book> search(@RequestBody SearchRequest request) {
 *     return repository.findAll(request.<Book>toSpecification(), request.toPageable());
 * }
 * }</pre>
 */
public class SearchRequest {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 500;

    private FilterGroup filter = new FilterGroup();
    private int page = 0;
    private int size = DEFAULT_PAGE_SIZE;
    private List<SortOrder> sort = new ArrayList<>();

    /** The specification for the filter tree (an empty filter matches everything). */
    @JsonIgnore
    public <T> Specification<T> toSpecification() {
        return new GenericSpecification<>(filter);
    }

    /** Paging and ordering as a Spring {@link Pageable}. */
    @JsonIgnore
    public Pageable toPageable() {
        return PageRequest.of(Math.max(page, 0), normalizedSize(), toSort());
    }

    @JsonIgnore
    public Sort toSort() {
        if (sort == null || sort.isEmpty()) {
            return Sort.unsorted();
        }
        return Sort.by(sort.stream().map(SortOrder::toOrder).toList());
    }

    private int normalizedSize() {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public FilterGroup getFilter() {
        return filter;
    }

    public void setFilter(FilterGroup filter) {
        this.filter = filter != null ? filter : new FilterGroup();
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

    public List<SortOrder> getSort() {
        return sort;
    }

    public void setSort(List<SortOrder> sort) {
        this.sort = sort != null ? sort : new ArrayList<>();
    }
}
