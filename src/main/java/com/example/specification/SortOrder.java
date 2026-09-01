package com.example.specification;

import org.springframework.data.domain.Sort;

/**
 * One sort instruction of a {@link SearchRequest}. {@code field} supports the
 * same dot notation as filters (e.g. "author.name"); {@code direction}
 * defaults to ascending.
 */
public class SortOrder {

    private String field;
    private Sort.Direction direction = Sort.Direction.ASC;

    public SortOrder() {
    }

    public SortOrder(String field, Sort.Direction direction) {
        this.field = field;
        this.direction = direction != null ? direction : Sort.Direction.ASC;
    }

    public static SortOrder asc(String field) {
        return new SortOrder(field, Sort.Direction.ASC);
    }

    public static SortOrder desc(String field) {
        return new SortOrder(field, Sort.Direction.DESC);
    }

    Sort.Order toOrder() {
        return new Sort.Order(direction, field);
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Sort.Direction getDirection() {
        return direction;
    }

    public void setDirection(Sort.Direction direction) {
        this.direction = direction != null ? direction : Sort.Direction.ASC;
    }
}
