package com.example.specification;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Converts raw filter values (typically strings or numbers coming from JSON)
 * into the Java type of the targeted entity attribute, so that JSON payloads
 * like {@code {"field": "publishedDate", "value": "2024-01-15"}} bind to
 * {@code LocalDate} attributes without the caller pre-converting anything.
 */
final class ValueConverter {

    private ValueConverter() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object convert(Object raw, Class<?> targetType, String field) {
        if (raw == null || targetType == null || targetType.isInstance(raw)) {
            return raw;
        }
        try {
            if (targetType.isEnum()) {
                return Enum.valueOf((Class<Enum>) targetType, raw.toString());
            }
            if (targetType == String.class) {
                return raw.toString();
            }
            if (raw instanceof Number number) {
                return convertNumber(number, targetType, field);
            }
            String text = raw.toString().trim();
            if (targetType == UUID.class) {
                return UUID.fromString(text);
            }
            if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(text);
            }
            if (targetType == LocalDate.class) {
                return LocalDate.parse(text);
            }
            if (targetType == LocalDateTime.class) {
                return LocalDateTime.parse(text);
            }
            if (targetType == LocalTime.class) {
                return LocalTime.parse(text);
            }
            if (targetType == Instant.class) {
                return Instant.parse(text);
            }
            if (targetType == OffsetDateTime.class) {
                return OffsetDateTime.parse(text);
            }
            if (targetType == ZonedDateTime.class) {
                return ZonedDateTime.parse(text);
            }
            if (targetType == Character.class || targetType == char.class) {
                return text.charAt(0);
            }
            return convertNumber(new BigDecimal(text), targetType, field);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Cannot convert value '" + raw + "' to " + targetType.getSimpleName()
                            + " for field '" + field + "'", e);
        }
    }

    private static Object convertNumber(Number number, Class<?> targetType, String field) {
        if (targetType == Integer.class || targetType == int.class) {
            return number.intValue();
        }
        if (targetType == Long.class || targetType == long.class) {
            return number.longValue();
        }
        if (targetType == Double.class || targetType == double.class) {
            return number.doubleValue();
        }
        if (targetType == Float.class || targetType == float.class) {
            return number.floatValue();
        }
        if (targetType == Short.class || targetType == short.class) {
            return number.shortValue();
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return number.byteValue();
        }
        if (targetType == BigDecimal.class) {
            return number instanceof BigDecimal bd ? bd : new BigDecimal(number.toString());
        }
        if (targetType == BigInteger.class) {
            return number instanceof BigInteger bi ? bi : new BigDecimal(number.toString()).toBigInteger();
        }
        throw new IllegalArgumentException(
                "Unsupported target type " + targetType.getSimpleName()
                        + " for numeric value on field '" + field + "'");
    }
}
