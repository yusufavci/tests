package com.example.specification;

/**
 * Comparison operators supported by {@link GenericSpecification}.
 */
public enum SearchOperator {

    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    /** Case-insensitive "contains". */
    LIKE,
    /** Case-insensitive prefix match. */
    STARTS_WITH,
    /** Case-insensitive suffix match. */
    ENDS_WITH,
    IN,
    NOT_IN,
    IS_NULL,
    IS_NOT_NULL,
    /** Inclusive range; expects exactly two entries in {@code values}. */
    BETWEEN
}
