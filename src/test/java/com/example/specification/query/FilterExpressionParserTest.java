package com.example.specification.query;

import com.example.specification.FilterCriteria;
import com.example.specification.FilterGroup;
import com.example.specification.LogicalOperator;
import com.example.specification.SearchOperator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterExpressionParserTest {

    @Test
    void parsesSimpleComparison() {
        FilterGroup group = FilterExpressionParser.parse("name eq 'aaa'");

        assertThat(group.getConditions()).containsExactly(
                FilterCriteria.of("name", SearchOperator.EQUALS, "aaa"));
        assertThat(group.getGroups()).isEmpty();
    }

    @Test
    void parsesTheUsersExampleQuery() {
        FilterGroup group = FilterExpressionParser.parse("name eq 'aaa' and salary isnot null");

        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        assertThat(group.getConditions()).containsExactly(
                FilterCriteria.of("name", SearchOperator.EQUALS, "aaa"),
                FilterCriteria.of("salary", SearchOperator.IS_NOT_NULL));
    }

    @Test
    void andBindsTighterThanOr() {
        // a or (b and c)
        FilterGroup group = FilterExpressionParser.parse(
                "genre eq 'HISTORY' or pages gt 100 and pages lt 500");

        assertThat(group.getOperator()).isEqualTo(LogicalOperator.OR);
        assertThat(group.getConditions()).containsExactly(
                FilterCriteria.of("genre", SearchOperator.EQUALS, "HISTORY"));
        assertThat(group.getGroups()).hasSize(1);
        FilterGroup nested = group.getGroups().get(0);
        assertThat(nested.getOperator()).isEqualTo(LogicalOperator.AND);
        assertThat(nested.getConditions()).containsExactly(
                FilterCriteria.of("pages", SearchOperator.GREATER_THAN, new BigDecimal("100")),
                FilterCriteria.of("pages", SearchOperator.LESS_THAN, new BigDecimal("500")));
    }

    @Test
    void parenthesesOverridePrecedence() {
        // (a or b) and c
        FilterGroup group = FilterExpressionParser.parse(
                "(genre eq 'HISTORY' or genre eq 'FICTION') and pages gt 100");

        assertThat(group.getOperator()).isEqualTo(LogicalOperator.AND);
        assertThat(group.getConditions()).containsExactly(
                FilterCriteria.of("pages", SearchOperator.GREATER_THAN, new BigDecimal("100")));
        assertThat(group.getGroups()).hasSize(1);
        assertThat(group.getGroups().get(0).getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    void nullLiteralsAndAliases() {
        assertThat(FilterExpressionParser.parse("author eq null").getConditions())
                .containsExactly(FilterCriteria.of("author", SearchOperator.IS_NULL));
        assertThat(FilterExpressionParser.parse("author ne null").getConditions())
                .containsExactly(FilterCriteria.of("author", SearchOperator.IS_NOT_NULL));
        assertThat(FilterExpressionParser.parse("author is null").getConditions())
                .containsExactly(FilterCriteria.of("author", SearchOperator.IS_NULL));
        assertThat(FilterExpressionParser.parse("author is not null").getConditions())
                .containsExactly(FilterCriteria.of("author", SearchOperator.IS_NOT_NULL));
        assertThat(FilterExpressionParser.parse("author isnotnull").getConditions())
                .containsExactly(FilterCriteria.of("author", SearchOperator.IS_NOT_NULL));
    }

    @Test
    void negatedOperators() {
        assertThat(FilterExpressionParser.parse("genre ne 'FICTION'").getConditions())
                .containsExactly(FilterCriteria.of("genre", SearchOperator.NOT_EQUALS, "FICTION"));
        assertThat(FilterExpressionParser.parse("genre notin ('FICTION', 'HISTORY')").getConditions())
                .containsExactly(FilterCriteria.of("genre", SearchOperator.NOT_IN,
                        java.util.List.of((Object) "FICTION", "HISTORY")));
    }

    @Test
    void textOperatorsAndLists() {
        assertThat(FilterExpressionParser.parse("author.name like 'tolkien'").getConditions())
                .containsExactly(FilterCriteria.of("author.name", SearchOperator.LIKE, "tolkien"));
        assertThat(FilterExpressionParser.parse("title startswith 'The'").getConditions())
                .containsExactly(FilterCriteria.of("title", SearchOperator.STARTS_WITH, "The"));
        assertThat(FilterExpressionParser.parse("genre in ('FICTION', 'HISTORY')").getConditions())
                .containsExactly(FilterCriteria.of("genre", SearchOperator.IN,
                        java.util.List.of((Object) "FICTION", "HISTORY")));
        assertThat(FilterExpressionParser.parse("pages between 100 and 500").getConditions())
                .containsExactly(FilterCriteria.of("pages", SearchOperator.BETWEEN,
                        java.util.List.of((Object) new BigDecimal("100"), new BigDecimal("500"))));
    }

    @Test
    void bareWordsQuotedEscapesAndBooleans() {
        assertThat(FilterExpressionParser.parse("genre eq FICTION").getConditions())
                .containsExactly(FilterCriteria.of("genre", SearchOperator.EQUALS, "FICTION"));
        assertThat(FilterExpressionParser.parse("title eq 'O''Brien'").getConditions())
                .containsExactly(FilterCriteria.of("title", SearchOperator.EQUALS, "O'Brien"));
        assertThat(FilterExpressionParser.parse("active eq true").getConditions())
                .containsExactly(FilterCriteria.of("active", SearchOperator.EQUALS, Boolean.TRUE));
    }

    @Test
    void csSuffixMarksConditionCaseSensitive() {
        FilterCriteria criteria = FilterExpressionParser.parse("title eqcs 'The Hobbit'")
                .getConditions().get(0);
        assertThat(criteria.getOperator()).isEqualTo(SearchOperator.EQUALS);
        assertThat(criteria.getValue()).isEqualTo("The Hobbit");
        assertThat(criteria.isCaseSensitive()).isTrue();

        assertThat(FilterExpressionParser.parse("title likecs 'Hobbit'")
                .getConditions().get(0).isCaseSensitive()).isTrue();
        assertThat(FilterExpressionParser.parse("title startswithcs 'The'")
                .getConditions().get(0).isCaseSensitive()).isTrue();
        assertThat(FilterExpressionParser.parse("genre incs ('FICTION')")
                .getConditions().get(0).isCaseSensitive()).isTrue();

        // Plain spellings stay case-insensitive; cs on non-text ops is unknown.
        assertThat(FilterExpressionParser.parse("title eq 'x'")
                .getConditions().get(0).isCaseSensitive()).isFalse();
        assertThatThrownBy(() -> FilterExpressionParser.parse("pages gtcs 5"))
                .isInstanceOf(QueryParseException.class)
                .hasMessageContaining("Unknown operator 'gtcs'");
    }

    @Test
    void blankInputMatchesEverything() {
        assertThat(FilterExpressionParser.parse(null).isEmpty()).isTrue();
        assertThat(FilterExpressionParser.parse("   ").isEmpty()).isTrue();
    }

    @Test
    void syntaxErrorsCarryPosition() {
        assertThatThrownBy(() -> FilterExpressionParser.parse("name eq"))
                .isInstanceOf(QueryParseException.class)
                .hasMessageContaining("end of expression");
        assertThatThrownBy(() -> FilterExpressionParser.parse("name foo 'x'"))
                .isInstanceOf(QueryParseException.class)
                .hasMessageContaining("Unknown operator 'foo'");
        assertThatThrownBy(() -> FilterExpressionParser.parse("name eq 'unterminated"))
                .isInstanceOf(QueryParseException.class)
                .hasMessageContaining("Unterminated string");
        assertThatThrownBy(() -> FilterExpressionParser.parse("(name eq 'x'"))
                .isInstanceOf(QueryParseException.class)
                .hasMessageContaining("')'");
        assertThatThrownBy(() -> FilterExpressionParser.parse("pages gt null"))
                .isInstanceOf(QueryParseException.class)
                .hasMessageContaining("does not accept null");
    }
}
