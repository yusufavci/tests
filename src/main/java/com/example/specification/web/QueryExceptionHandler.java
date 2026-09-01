package com.example.specification.web;

import com.example.specification.query.QueryParseException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps search-query failures to HTTP 400 with an RFC 9457 problem body:
 * syntax errors from the expression parsers, and semantic errors (unknown
 * field, unconvertible value, wrong value count) that Spring wraps in
 * {@link InvalidDataAccessApiUsageException} at the repository boundary.
 * Active wherever this package is component-scanned.
 */
@RestControllerAdvice
public class QueryExceptionHandler {

    @ExceptionHandler(QueryParseException.class)
    public ProblemDetail handleParseError(QueryParseException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid query expression");
        return problem;
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ProblemDetail handleInvalidQuery(InvalidDataAccessApiUsageException e) {
        // The outermost IllegalArgumentException carries the client-friendly
        // message (e.g. "Cannot convert value 'X' to Genre for field 'genre'");
        // anything else is not a client error — rethrow.
        Throwable cause = e.getCause();
        while (cause != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        if (cause == null) {
            throw e;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, cause.getMessage());
        problem.setTitle("Invalid query");
        return problem;
    }
}
