package com.example.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A {@link Specification} driven by a {@link FilterGroup} tree, supporting
 * arbitrarily nested AND/OR groups and dot-notation paths across associations
 * (resolved with reused LEFT joins). When a filter walks through a collection
 * association, the query is marked {@code distinct} so joined rows are not
 * duplicated.
 */
public class GenericSpecification<T> implements Specification<T> {

    private final FilterGroup filter;

    public GenericSpecification(FilterGroup filter) {
        this.filter = filter;
    }

    public static <T> GenericSpecification<T> of(FilterGroup filter) {
        return new GenericSpecification<>(filter);
    }

    /** Convenience for a specification with a single condition. */
    public static <T> GenericSpecification<T> of(FilterCriteria criteria) {
        return new GenericSpecification<>(FilterGroup.and().addCondition(criteria));
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (filter == null || filter.isEmpty()) {
            return cb.conjunction();
        }
        boolean[] joinedCollection = {false};
        Predicate predicate = buildGroupPredicate(filter, root, cb, joinedCollection);
        if (joinedCollection[0] && query != null) {
            query.distinct(true);
        }
        return predicate;
    }

    private Predicate buildGroupPredicate(FilterGroup group, Root<T> root, CriteriaBuilder cb,
                                          boolean[] joinedCollection) {
        List<Predicate> predicates = new ArrayList<>();
        for (FilterCriteria criteria : group.getConditions()) {
            predicates.add(buildConditionPredicate(criteria, root, cb, joinedCollection));
        }
        for (FilterGroup nested : group.getGroups()) {
            if (!nested.isEmpty()) {
                predicates.add(buildGroupPredicate(nested, root, cb, joinedCollection));
            }
        }
        if (predicates.isEmpty()) {
            return cb.conjunction();
        }
        Predicate[] array = predicates.toArray(new Predicate[0]);
        return group.getOperator() == LogicalOperator.OR ? cb.or(array) : cb.and(array);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildConditionPredicate(FilterCriteria criteria, Root<T> root,
                                              CriteriaBuilder cb, boolean[] joinedCollection) {
        if (criteria.getField() == null || criteria.getField().isBlank()) {
            throw new IllegalArgumentException("Filter condition is missing a field: " + criteria);
        }
        if (criteria.getOperator() == null) {
            throw new IllegalArgumentException("Filter condition is missing an operator: " + criteria);
        }

        Path<?> path = resolvePath(root, criteria.getField(), joinedCollection);
        Class<?> javaType = path.getJavaType();

        return switch (criteria.getOperator()) {
            case EQUALS -> cb.equal(path, convert(criteria, javaType));
            case NOT_EQUALS -> cb.notEqual(path, convert(criteria, javaType));
            case GREATER_THAN ->
                    cb.greaterThan((Expression<Comparable>) path, (Comparable) convert(criteria, javaType));
            case GREATER_THAN_OR_EQUAL ->
                    cb.greaterThanOrEqualTo((Expression<Comparable>) path, (Comparable) convert(criteria, javaType));
            case LESS_THAN ->
                    cb.lessThan((Expression<Comparable>) path, (Comparable) convert(criteria, javaType));
            case LESS_THAN_OR_EQUAL ->
                    cb.lessThanOrEqualTo((Expression<Comparable>) path, (Comparable) convert(criteria, javaType));
            case LIKE -> like(cb, path, criteria, "%", "%");
            case NOT_LIKE -> cb.not(like(cb, path, criteria, "%", "%"));
            case STARTS_WITH -> like(cb, path, criteria, "", "%");
            case ENDS_WITH -> like(cb, path, criteria, "%", "");
            case IN -> path.in(convertList(criteria, javaType));
            case NOT_IN -> cb.not(path.in(convertList(criteria, javaType)));
            case IS_NULL -> cb.isNull(path);
            case IS_NOT_NULL -> cb.isNotNull(path);
            case BETWEEN -> {
                List<Object> range = convertList(criteria, javaType);
                if (range.size() != 2) {
                    throw new IllegalArgumentException(
                            "BETWEEN expects exactly 2 values for field '" + criteria.getField() + "'");
                }
                yield cb.between((Expression<Comparable>) path,
                        (Comparable) range.get(0), (Comparable) range.get(1));
            }
        };
    }

    private Predicate like(CriteriaBuilder cb, Path<?> path, FilterCriteria criteria,
                           String prefix, String suffix) {
        Object value = requireValue(criteria);
        String pattern = prefix + value.toString().toLowerCase(Locale.ROOT) + suffix;
        return cb.like(cb.lower(path.as(String.class)), pattern);
    }

    private Object convert(FilterCriteria criteria, Class<?> targetType) {
        return ValueConverter.convert(requireValue(criteria), targetType, criteria.getField());
    }

    private List<Object> convertList(FilterCriteria criteria, Class<?> targetType) {
        List<Object> values = criteria.getValues();
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(
                    criteria.getOperator() + " requires non-empty 'values' for field '" + criteria.getField() + "'");
        }
        List<Object> converted = new ArrayList<>(values.size());
        for (Object value : values) {
            converted.add(ValueConverter.convert(value, targetType, criteria.getField()));
        }
        return converted;
    }

    private Object requireValue(FilterCriteria criteria) {
        Object value = criteria.getValue();
        if (value == null) {
            throw new IllegalArgumentException(
                    criteria.getOperator() + " requires a 'value' for field '" + criteria.getField() + "'");
        }
        return value;
    }

    /**
     * Resolves a dot-notation property path. Every segment before the last is
     * treated as an association and resolved with a LEFT join; joins already
     * present on the {@code From} are reused so multiple conditions on the same
     * association share one join.
     */
    private Path<?> resolvePath(Root<T> root, String field, boolean[] joinedCollection) {
        String[] parts = field.split("\\.");
        From<?, ?> from = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Join<?, ?> join = getOrCreateJoin(from, parts[i]);
            if (join.getAttribute().isCollection()) {
                joinedCollection[0] = true;
            }
            from = join;
        }
        return from.get(parts[parts.length - 1]);
    }

    private Join<?, ?> getOrCreateJoin(From<?, ?> from, String attribute) {
        for (Join<?, ?> join : from.getJoins()) {
            if (join.getAttribute().getName().equals(attribute) && join.getJoinType() == JoinType.LEFT) {
                return join;
            }
        }
        return from.join(attribute, JoinType.LEFT);
    }
}
