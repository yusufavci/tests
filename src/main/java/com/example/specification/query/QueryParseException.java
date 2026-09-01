package com.example.specification.query;

/**
 * Thrown when a filter or order-by expression cannot be parsed. The message is
 * safe to return to API clients (it describes the syntax problem and position,
 * never internal state).
 */
public class QueryParseException extends IllegalArgumentException {

    public QueryParseException(String message) {
        super(message);
    }
}
