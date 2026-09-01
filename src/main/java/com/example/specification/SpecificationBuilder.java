package com.example.specification;

import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for {@link GenericSpecification}. Builds a {@link FilterGroup}
 * tree in code, with nested groups expressed as lambdas:
 *
 * <pre>{@code
 * Specification<Book> spec = SpecificationBuilder.<Book>and()
 *         .eq("genre", Genre.SCIENCE)
 *         .or(g -> g.like("title", "spring").like("author.name", "spring"))
 *         .build();
 * }</pre>
 */
public class SpecificationBuilder<T> {

    private final FilterGroup group;

    private SpecificationBuilder(LogicalOperator operator) {
        this.group = new FilterGroup(operator);
    }

    /** Starts a builder whose top-level conditions are combined with AND. */
    public static <T> SpecificationBuilder<T> and() {
        return new SpecificationBuilder<>(LogicalOperator.AND);
    }

    /** Starts a builder whose top-level conditions are combined with OR. */
    public static <T> SpecificationBuilder<T> or() {
        return new SpecificationBuilder<>(LogicalOperator.OR);
    }

    public SpecificationBuilder<T> eq(String field, Object value) {
        return add(field, SearchOperator.EQUALS, value);
    }

    public SpecificationBuilder<T> notEq(String field, Object value) {
        return add(field, SearchOperator.NOT_EQUALS, value);
    }

    public SpecificationBuilder<T> gt(String field, Object value) {
        return add(field, SearchOperator.GREATER_THAN, value);
    }

    public SpecificationBuilder<T> gte(String field, Object value) {
        return add(field, SearchOperator.GREATER_THAN_OR_EQUAL, value);
    }

    public SpecificationBuilder<T> lt(String field, Object value) {
        return add(field, SearchOperator.LESS_THAN, value);
    }

    public SpecificationBuilder<T> lte(String field, Object value) {
        return add(field, SearchOperator.LESS_THAN_OR_EQUAL, value);
    }

    public SpecificationBuilder<T> like(String field, String value) {
        return add(field, SearchOperator.LIKE, value);
    }

    public SpecificationBuilder<T> notLike(String field, String value) {
        return add(field, SearchOperator.NOT_LIKE, value);
    }

    public SpecificationBuilder<T> startsWith(String field, String value) {
        return add(field, SearchOperator.STARTS_WITH, value);
    }

    public SpecificationBuilder<T> endsWith(String field, String value) {
        return add(field, SearchOperator.ENDS_WITH, value);
    }

    public SpecificationBuilder<T> in(String field, Object... values) {
        return in(field, Arrays.asList(values));
    }

    public SpecificationBuilder<T> in(String field, List<Object> values) {
        group.addCondition(FilterCriteria.of(field, SearchOperator.IN, values));
        return this;
    }

    public SpecificationBuilder<T> notIn(String field, Object... values) {
        group.addCondition(FilterCriteria.of(field, SearchOperator.NOT_IN, Arrays.asList(values)));
        return this;
    }

    public SpecificationBuilder<T> isNull(String field) {
        group.addCondition(FilterCriteria.of(field, SearchOperator.IS_NULL));
        return this;
    }

    public SpecificationBuilder<T> isNotNull(String field) {
        group.addCondition(FilterCriteria.of(field, SearchOperator.IS_NOT_NULL));
        return this;
    }

    public SpecificationBuilder<T> between(String field, Object from, Object to) {
        group.addCondition(FilterCriteria.of(field, SearchOperator.BETWEEN, Arrays.asList(from, to)));
        return this;
    }

    /** Adds a nested group combined with AND internally. */
    public SpecificationBuilder<T> and(Consumer<SpecificationBuilder<T>> nested) {
        return addGroup(LogicalOperator.AND, nested);
    }

    /** Adds a nested group combined with OR internally. */
    public SpecificationBuilder<T> or(Consumer<SpecificationBuilder<T>> nested) {
        return addGroup(LogicalOperator.OR, nested);
    }

    /** Adds an arbitrary pre-built condition. */
    public SpecificationBuilder<T> condition(FilterCriteria criteria) {
        group.addCondition(criteria);
        return this;
    }

    /** Adds a pre-built group. */
    public SpecificationBuilder<T> group(FilterGroup nested) {
        group.addGroup(nested);
        return this;
    }

    private SpecificationBuilder<T> addGroup(LogicalOperator operator,
                                             Consumer<SpecificationBuilder<T>> nested) {
        SpecificationBuilder<T> builder = new SpecificationBuilder<>(operator);
        nested.accept(builder);
        group.addGroup(builder.group);
        return this;
    }

    private SpecificationBuilder<T> add(String field, SearchOperator operator, Object value) {
        group.addCondition(FilterCriteria.of(field, operator, value));
        return this;
    }

    /** The underlying filter tree, e.g. to serialize it or embed in a {@link SearchRequest}. */
    public FilterGroup toFilterGroup() {
        return group;
    }

    public Specification<T> build() {
        return new GenericSpecification<>(group);
    }
}
