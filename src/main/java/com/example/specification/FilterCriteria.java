package com.example.specification;

import java.io.Serializable;
import java.util.List;

/**
 * A single filter condition, e.g. {@code price GREATER_THAN 10} or
 * {@code author.name LIKE "kent"}.
 *
 * <p>{@code field} may be a nested property path using dot notation
 * ("author.address.city"); each intermediate segment is resolved with a LEFT
 * join. Single-value operators read {@code value}; {@code IN}, {@code NOT_IN}
 * and {@code BETWEEN} read {@code values}; {@code IS_NULL} / {@code IS_NOT_NULL}
 * need neither. Text comparisons are case-insensitive unless
 * {@code caseSensitive} is set (see {@link #exactCase()}).</p>
 */
public record FilterCriteria(String field, SearchOperator operator, Object value,
                             List<Object> values, boolean caseSensitive) implements Serializable {

    public static FilterCriteria of(String field, SearchOperator operator) {
        return new FilterCriteria(field, operator, null, null, false);
    }

    public static FilterCriteria of(String field, SearchOperator operator, Object value) {
        return new FilterCriteria(field, operator, value, null, false);
    }

    public static FilterCriteria of(String field, SearchOperator operator, List<Object> values) {
        return new FilterCriteria(field, operator, null, values, false);
    }

    /** Copy of this condition that matches text with exact case. */
    public FilterCriteria exactCase() {
        return new FilterCriteria(field, operator, value, values, true);
    }

    /** Copy of this condition targeting another field (used by field mappings). */
    public FilterCriteria withField(String newField) {
        return new FilterCriteria(newField, operator, value, values, caseSensitive);
    }
}
