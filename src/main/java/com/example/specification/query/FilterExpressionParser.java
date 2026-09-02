package com.example.specification.query;

import com.example.specification.FilterCriteria;
import com.example.specification.FilterGroup;
import com.example.specification.SearchOperator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses OData-style filter expressions into a {@link FilterGroup} tree.
 *
 * <p>Grammar (case-insensitive keywords, {@code and} binds tighter than
 * {@code or}, parentheses for grouping):</p>
 *
 * <pre>
 * expr        := andExpr ("or" andExpr)*
 * andExpr     := primaryExpr ("and" primaryExpr)*
 * primaryExpr := "(" expr ")" | comparison
 * comparison  := path op value
 *              | path "in" "(" value ("," value)* ")"
 *              | path "between" value "and" value
 *              | path "is" ["not"] "null" | path "isnot" "null"
 * op          := eq | ne | gt | ge | lt | le | like
 *                (text ops accept a "cs" suffix for exact-case matching)
 * value       := 'string' | number | true | false | bareword
 * </pre>
 *
 * <p>Examples:</p>
 * <pre>
 * name eq 'aaa' and salary isnot null
 * genre eq FICTION or (genre eq SCIENCE and pages gt 600)
 * author.name like 'tolkien' and publishedDate between '1930-01-01' and '1960-12-31'
 * status notin ('CLOSED', 'ARCHIVED')
 * </pre>
 *
 * <p>String literals use single quotes with {@code ''} as the escape for a
 * quote. Bare words (e.g. enum constants) are treated as string values. Null
 * checks use {@code is null} / {@code is not null} / {@code isnot null}.
 * Negation is expressed through the negated operators themselves ({@code ne},
 * {@code notin}, the null checks); there is no {@code not} keyword.</p>
 *
 * <p>Text comparisons are case-insensitive by default. Appending {@code cs}
 * to a text operator ({@code eqcs}, {@code necs}, {@code likecs},
 * {@code incs}, {@code notincs}) switches that condition to exact-case
 * matching.</p>
 */
public final class FilterExpressionParser {

    /** Marker for the {@code null} literal, distinct from an absent value. */
    private static final Object NULL_LITERAL = new Object();

    /** Text operators that accept the exact-case "cs" suffix. */
    private static final java.util.Set<String> TEXT_OPERATORS =
            java.util.Set.of("eq", "ne", "like", "in", "notin");

    private final List<Token> tokens;
    private int pos;

    private FilterExpressionParser(String input) {
        this.tokens = tokenize(input);
    }

    /** Parses the expression; a null or blank input yields an empty (match-all) group. */
    public static FilterGroup parse(String input) {
        if (input == null || input.isBlank()) {
            return new FilterGroup();
        }
        FilterExpressionParser parser = new FilterExpressionParser(input);
        Object node = parser.parseOr();
        parser.expect(TokenType.EOF);
        return parser.toGroup(node);
    }

    // ------------------------------------------------------------------
    // Recursive descent
    // ------------------------------------------------------------------

    private Object parseOr() {
        Object left = parseAnd();
        if (!peekKeyword("or")) {
            return left;
        }
        FilterGroup group = FilterGroup.or();
        addChild(group, left);
        while (peekKeyword("or")) {
            next();
            addChild(group, parseAnd());
        }
        return group;
    }

    private Object parseAnd() {
        Object left = parsePrimary();
        if (!peekKeyword("and")) {
            return left;
        }
        FilterGroup group = FilterGroup.and();
        addChild(group, left);
        while (peekKeyword("and")) {
            next();
            addChild(group, parsePrimary());
        }
        return group;
    }

    private Object parsePrimary() {
        if (peek().type == TokenType.LPAREN) {
            next();
            Object inner = parseOr();
            expect(TokenType.RPAREN);
            return inner;
        }
        return parseComparison();
    }

    private Object parseComparison() {
        String field = expect(TokenType.IDENT).text;
        Token opToken = expect(TokenType.IDENT);
        String op = opToken.text.toLowerCase(Locale.ROOT);

        // A "cs" suffix on a text operator (eqcs, likecs, incs, ...) makes it exact-case.
        boolean caseSensitive = false;
        String base = stripCsSuffix(op);
        if (base != null && TEXT_OPERATORS.contains(base)) {
            caseSensitive = true;
            op = base;
        }

        FilterCriteria criteria = switch (op) {
            case "eq" -> FilterCriteria.of(field, SearchOperator.EQUALS, requireValue(op));
            case "ne" -> FilterCriteria.of(field, SearchOperator.NOT_EQUALS, requireValue(op));
            case "gt" -> FilterCriteria.of(field, SearchOperator.GREATER_THAN, requireValue(op));
            case "ge" -> FilterCriteria.of(field, SearchOperator.GREATER_THAN_OR_EQUAL, requireValue(op));
            case "lt" -> FilterCriteria.of(field, SearchOperator.LESS_THAN, requireValue(op));
            case "le" -> FilterCriteria.of(field, SearchOperator.LESS_THAN_OR_EQUAL, requireValue(op));
            case "like" -> FilterCriteria.of(field, SearchOperator.LIKE, requireValue(op));
            case "in" -> FilterCriteria.of(field, SearchOperator.IN, parseValueList());
            case "notin" -> FilterCriteria.of(field, SearchOperator.NOT_IN, parseValueList());
            case "between" -> parseBetween(field, SearchOperator.BETWEEN);
            case "is" -> parseIs(field);
            case "isnot" -> {
                expectKeyword("null");
                yield FilterCriteria.of(field, SearchOperator.IS_NOT_NULL);
            }
            default -> throw error(opToken, "Unknown operator '" + opToken.text + "'");
        };
        return caseSensitive ? criteria.exactCase() : criteria;
    }

    /** Returns the operator without its "cs" suffix, or null when there is none. */
    private static String stripCsSuffix(String op) {
        return op.length() > 2 && op.endsWith("cs") ? op.substring(0, op.length() - 2) : null;
    }

    private FilterCriteria parseIs(String field) {
        if (peekKeyword("not")) {
            next();
            expectKeyword("null");
            return FilterCriteria.of(field, SearchOperator.IS_NOT_NULL);
        }
        expectKeyword("null");
        return FilterCriteria.of(field, SearchOperator.IS_NULL);
    }

    private FilterCriteria parseBetween(String field, SearchOperator operator) {
        Object low = requireValue("between");
        expectKeyword("and");
        Object high = requireValue("between");
        return FilterCriteria.of(field, operator, List.of(low, high));
    }

    private List<Object> parseValueList() {
        expect(TokenType.LPAREN);
        List<Object> values = new ArrayList<>();
        values.add(requireValue("in"));
        while (peek().type == TokenType.COMMA) {
            next();
            values.add(requireValue("in"));
        }
        expect(TokenType.RPAREN);
        return values;
    }

    private Object requireValue(String op) {
        Object value = parseValue();
        if (value == NULL_LITERAL) {
            throw new QueryParseException("Operator '" + op + "' does not accept null"
                    + " (use 'is null' / 'is not null')");
        }
        return value;
    }

    private Object parseValue() {
        Token token = next();
        return switch (token.type) {
            case STRING -> token.text;
            case NUMBER -> new BigDecimal(token.text);
            case IDENT -> switch (token.text.toLowerCase(Locale.ROOT)) {
                case "null" -> NULL_LITERAL;
                case "true" -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                // Bare words (e.g. enum constants) are treated as strings.
                default -> token.text;
            };
            default -> throw error(token, "Expected a value");
        };
    }

    // ------------------------------------------------------------------
    // Tree assembly
    // ------------------------------------------------------------------

    private FilterGroup toGroup(Object node) {
        if (node instanceof FilterGroup group) {
            return group;
        }
        return FilterGroup.and().addCondition((FilterCriteria) node);
    }

    private void addChild(FilterGroup parent, Object child) {
        if (child instanceof FilterCriteria criteria) {
            parent.addCondition(criteria);
        } else {
            parent.addGroup((FilterGroup) child);
        }
    }

    // ------------------------------------------------------------------
    // Tokenizer
    // ------------------------------------------------------------------

    private enum TokenType { IDENT, STRING, NUMBER, LPAREN, RPAREN, COMMA, EOF }

    private record Token(TokenType type, String text, int position) {
    }

    private static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int length = input.length();
        while (i < length) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "(", i++));
            } else if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")", i++));
            } else if (c == ',') {
                tokens.add(new Token(TokenType.COMMA, ",", i++));
            } else if (c == '\'') {
                int start = i;
                StringBuilder sb = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < length) {
                    char ch = input.charAt(i);
                    if (ch == '\'') {
                        if (i + 1 < length && input.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                        } else {
                            i++;
                            closed = true;
                            break;
                        }
                    } else {
                        sb.append(ch);
                        i++;
                    }
                }
                if (!closed) {
                    throw new QueryParseException("Unterminated string literal at position " + start);
                }
                tokens.add(new Token(TokenType.STRING, sb.toString(), start));
            } else if (Character.isDigit(c) || (c == '-' && i + 1 < length && Character.isDigit(input.charAt(i + 1)))) {
                int start = i;
                i++;
                while (i < length && (Character.isDigit(input.charAt(i)) || input.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, input.substring(start, i), start));
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < length) {
                    char ch = input.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.') {
                        i++;
                    } else {
                        break;
                    }
                }
                tokens.add(new Token(TokenType.IDENT, input.substring(start, i), start));
            } else {
                throw new QueryParseException("Unexpected character '" + c + "' at position " + i);
            }
        }
        tokens.add(new Token(TokenType.EOF, "", length));
        return tokens;
    }

    // ------------------------------------------------------------------
    // Token helpers
    // ------------------------------------------------------------------

    private Token peek() {
        return tokens.get(pos);
    }

    private Token next() {
        Token token = tokens.get(pos);
        if (token.type != TokenType.EOF) {
            pos++;
        }
        return token;
    }

    private boolean peekKeyword(String keyword) {
        Token token = peek();
        return token.type == TokenType.IDENT && token.text.equalsIgnoreCase(keyword);
    }

    private void expectKeyword(String keyword) {
        Token token = next();
        if (token.type != TokenType.IDENT || !token.text.equalsIgnoreCase(keyword)) {
            throw error(token, "Expected '" + keyword + "'");
        }
    }

    private Token expect(TokenType type) {
        Token token = next();
        if (token.type != type) {
            String expected = switch (type) {
                case IDENT -> "an identifier";
                case RPAREN -> "')'";
                case LPAREN -> "'('";
                case COMMA -> "','";
                case EOF -> "end of expression";
                case STRING, NUMBER -> "a value";
            };
            throw error(token, "Expected " + expected);
        }
        return token;
    }

    private QueryParseException error(Token token, String message) {
        String at = token.type == TokenType.EOF
                ? "end of expression"
                : "'" + token.text + "' at position " + token.position;
        return new QueryParseException(message + " but found " + at);
    }
}
