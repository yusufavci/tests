package com.example.specification;

import java.util.List;
import java.util.Objects;

/**
 * A single filter condition, e.g. {@code price GREATER_THAN 10} or
 * {@code author.name LIKE "kent"}.
 *
 * <p>{@code field} may be a nested property path using dot notation
 * ("author.address.city"); each intermediate segment is resolved with a LEFT
 * join. Single-value operators read {@code value}; {@code IN}, {@code NOT_IN}
 * and {@code BETWEEN} read {@code values}; {@code IS_NULL} / {@code IS_NOT_NULL}
 * need neither.</p>
 */
public class FilterCriteria {

    private String field;
    private SearchOperator operator;
    private Object value;
    private List<Object> values;

    public FilterCriteria() {
    }

    public FilterCriteria(String field, SearchOperator operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public FilterCriteria(String field, SearchOperator operator, List<Object> values) {
        this.field = field;
        this.operator = operator;
        this.values = values;
    }

    public static FilterCriteria of(String field, SearchOperator operator) {
        return new FilterCriteria(field, operator, (Object) null);
    }

    public static FilterCriteria of(String field, SearchOperator operator, Object value) {
        return new FilterCriteria(field, operator, value);
    }

    public static FilterCriteria of(String field, SearchOperator operator, List<Object> values) {
        return new FilterCriteria(field, operator, values);
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public SearchOperator getOperator() {
        return operator;
    }

    public void setOperator(SearchOperator operator) {
        this.operator = operator;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }

    @Override
    public String toString() {
        return "FilterCriteria{" + field + " " + operator + " "
                + (values != null ? values : value) + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FilterCriteria that)) {
            return false;
        }
        return Objects.equals(field, that.field)
                && operator == that.operator
                && Objects.equals(value, that.value)
                && Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, operator, value, values);
    }
}
