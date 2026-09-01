package com.example.specification;

import com.fasterxml.jackson.annotation.JsonInclude;

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
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FilterGroup {

    private LogicalOperator operator = LogicalOperator.AND;
    private List<FilterCriteria> conditions = new ArrayList<>();
    private List<FilterGroup> groups = new ArrayList<>();

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
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }

    public List<FilterGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<FilterGroup> groups) {
        this.groups = groups != null ? groups : new ArrayList<>();
    }
}
