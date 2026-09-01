package com.example.specification.query;

import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses OData-style {@code orderBy} expressions into a Spring {@link Sort}:
 * a comma-separated list of {@code field [asc|desc]}, direction defaulting to
 * ascending. Fields support the same dot notation as filters.
 *
 * <p>Example: {@code "salary desc, author.name asc, id"}</p>
 */
public final class SortExpressionParser {

    private SortExpressionParser() {
    }

    /** Parses the expression; a null or blank input yields {@link Sort#unsorted()}. */
    public static Sort parse(String input) {
        if (input == null || input.isBlank()) {
            return Sort.unsorted();
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String segment : input.split(",")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length > 2) {
                throw new QueryParseException(
                        "Invalid order-by segment '" + trimmed + "': expected 'field [asc|desc]'");
            }
            Sort.Direction direction = Sort.Direction.ASC;
            if (parts.length == 2) {
                direction = switch (parts[1].toLowerCase(Locale.ROOT)) {
                    case "asc" -> Sort.Direction.ASC;
                    case "desc" -> Sort.Direction.DESC;
                    default -> throw new QueryParseException(
                            "Invalid sort direction '" + parts[1] + "' in '" + trimmed
                                    + "': expected 'asc' or 'desc'");
                };
            }
            orders.add(new Sort.Order(direction, parts[0]));
        }
        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }
}
