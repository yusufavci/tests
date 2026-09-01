package com.example.specification.query;

import com.example.specification.SortOrder;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses OData-style {@code $orderby} expressions into {@link SortOrder}s:
 * a comma-separated list of {@code field [asc|desc]}, direction defaulting to
 * ascending. Fields support the same dot notation as filters.
 *
 * <p>Example: {@code "salary desc, author.name asc, id"}</p>
 */
public final class SortExpressionParser {

    private SortExpressionParser() {
    }

    /** Parses the expression; a null or blank input yields an empty list. */
    public static List<SortOrder> parse(String input) {
        List<SortOrder> orders = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return orders;
        }
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
            orders.add(new SortOrder(parts[0], direction));
        }
        return orders;
    }
}
