package com.example.specification;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A boolean group of filter conditions. A group combines its direct
 * {@code conditions} and its nested {@code groups} with a single
 * {@link LogicalOperator}, so arbitrarily deep AND/OR trees can be expressed:
 *
 * <pre>{@code
 * status = ACTIVE AND (title LIKE "spring" OR author.name LIKE "spring")
 * }</pre>
 *
 * <p>is a top-level AND group with one condition and one nested OR group.</p>
 */
public class FilterGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LogicalOperator operator = LogicalOperator.AND;
    // Declared as ArrayList (not List) so the fields themselves are
    // serializable types, satisfying strict non-transient-field checkers.
    private ArrayList<FilterCriteria> conditions = new ArrayList<>();
    private ArrayList<FilterGroup> groups = new ArrayList<>();

    public FilterGroup() {
    }

    public FilterGroup(LogicalOperator operator) {
        this.operator = operator;
    }

    public static FilterGroup and() {
        return new FilterGroup(LogicalOperator.AND);
    }

    public static FilterGroup or() {
        return new FilterGroup(LogicalOperator.OR);
    }

    public FilterGroup addCondition(FilterCriteria criteria) {
        this.conditions.add(criteria);
        return this;
    }

    public FilterGroup addGroup(FilterGroup group) {
        this.groups.add(group);
        return this;
    }

    /** True when the group holds no conditions and no nested groups. */
    public boolean isEmpty() {
        return (conditions == null || conditions.isEmpty())
                && (groups == null || groups.isEmpty());
    }

    public LogicalOperator getOperator() {
        return operator;
    }

    public void setOperator(LogicalOperator operator) {
        this.operator = operator != null ? operator : LogicalOperator.AND;
    }

    public List<FilterCriteria> getConditions() {
        return conditions;
    }

    public void setConditions(List<FilterCriteria> conditions) {
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
    }

    public List<FilterGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<FilterGroup> groups) {
        this.groups = groups != null ? new ArrayList<>(groups) : new ArrayList<>();
    }
}
