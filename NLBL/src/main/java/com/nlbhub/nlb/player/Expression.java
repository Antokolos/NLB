/**
 * @(#)Expression.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2026 Anton P. Kolosov
 * Authors: Anton P. Kolosov, et al.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * ANTON P. KOLOSOV. ANTON P. KOLOSOV DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the Non-Linear Book software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Anton P. Kolosov at this
 * address: antokolos@gmail.com
 *
 * Copyright (c) 2026 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Вычислитель выражений книги (условия ссылок и объектов, значения модификаций).
 * Синтаксис — как в редакторе: числа, строки в кавычках, переменные, true/false, скобки,
 * арифметика, сравнения, {@code && || !}; случайно попавший Lua-синтаксис ({@code and or not
 * nil .. ~=}) понимается так же, как в NLBNavigator. Семантика операций — как в cparse
 * (NLBNavigator), с которым сверяется плеер:
 * <ul>
 *     <li>арифметика {@code + - * / **} всегда даёт вещественное число, {@code %} — целое;</li>
 *     <li>сравнения чисел — как вещественных, логическое значение — число 0 или 1;</li>
 *     <li>{@code && ||} не сокращаются и дают логическое значение по целой части операндов;</li>
 *     <li>{@code +} со строкой склеивает, число печатается как double в std::ostream (%g);</li>
 *     <li>{@code ==} у значений разных типов (кроме чисел) — ложь.</li>
 * </ul>
 * Неизвестная переменная — false (как в NLBNavigator и старом плеере).
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public final class Expression {
    /**
     * Переменные, видимые выражению
     */
    public interface Scope {
        /**
         * @return значение переменной или null, если такой нет
         */
        Value get(String name);
    }

    private static final Map<String, Integer> BINARY_PRECEDENCE = new HashMap<>();

    static {
        // Приоритеты cparse (как у операторов C++): меньше — связывает сильнее
        BINARY_PRECEDENCE.put("**", 3);
        BINARY_PRECEDENCE.put("*", 5);
        BINARY_PRECEDENCE.put("/", 5);
        BINARY_PRECEDENCE.put("%", 5);
        BINARY_PRECEDENCE.put("+", 6);
        BINARY_PRECEDENCE.put("-", 6);
        BINARY_PRECEDENCE.put("<<", 7);
        BINARY_PRECEDENCE.put(">>", 7);
        BINARY_PRECEDENCE.put("<", 9);
        BINARY_PRECEDENCE.put("<=", 9);
        BINARY_PRECEDENCE.put(">", 9);
        BINARY_PRECEDENCE.put(">=", 9);
        BINARY_PRECEDENCE.put("==", 10);
        BINARY_PRECEDENCE.put("!=", 10);
        BINARY_PRECEDENCE.put("&", 11);
        BINARY_PRECEDENCE.put("^", 12);
        BINARY_PRECEDENCE.put("|", 13);
        BINARY_PRECEDENCE.put("&&", 14);
        BINARY_PRECEDENCE.put("||", 15);
    }

    private static final String[] OPERATORS = {
            "**", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "~=", "..",
            "*", "/", "%", "+", "-", "<", ">", "&", "^", "|", "!", "(", ")"
    };

    private enum TokenType {NUMBER, STRING, IDENT, OP, END}

    private static final class Token {
        private final TokenType m_type;
        private final String m_text;
        private final Value m_value;

        private Token(TokenType type, String text, Value value) {
            m_type = type;
            m_text = text;
            m_value = value;
        }
    }

    private final String m_source;
    private final List<Token> m_tokens;
    private final Scope m_scope;
    private int m_pos;

    private Expression(String source, List<Token> tokens, Scope scope) {
        m_source = source;
        m_tokens = tokens;
        m_scope = scope;
    }

    /**
     * Вычисляет выражение. Пустое выражение — false.
     *
     * @throws PlayerException синтаксическая ошибка или недопустимая операция
     */
    public static Value evaluate(String expression, Scope scope) {
        try {
            List<Token> tokens = tokenize(expression);
            if (tokens.size() == 1) {
                return Value.FALSE;
            }
            Expression parser = new Expression(expression, tokens, scope);
            Value result = parser.parseBinary(Integer.MAX_VALUE);
            if (parser.peek().m_type != TokenType.END) {
                throw new PlayerException("unexpected '" + parser.peek().m_text + "'");
            }
            return result;
        } catch (PlayerException | ArithmeticException e) {
            throw new PlayerException("Cannot evaluate expression '" + expression + "': " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ лексер

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentChar(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static List<Token> tokenize(String s) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        final int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"' || c == '\'') {
                StringBuilder text = new StringBuilder();
                i++;
                while (i < n && s.charAt(i) != c) {
                    char ch = s.charAt(i);
                    if (ch == '\\' && i + 1 < n) {
                        char next = s.charAt(++i);
                        switch (next) {
                            case 'n':
                                text.append('\n');
                                break;
                            case 't':
                                text.append('\t');
                                break;
                            default:
                                text.append(next);
                                break;
                        }
                    } else {
                        text.append(ch);
                    }
                    i++;
                }
                if (i >= n) {
                    throw new PlayerException("Expected quote (" + c + ") at end of string declaration");
                }
                i++;
                tokens.add(new Token(TokenType.STRING, text.toString(), Value.of(text.toString())));
            } else if (isDigit(c)) {
                int start = i;
                Value value;
                if (c == '0' && i + 1 < n && s.charAt(i + 1) == 'x') {
                    i += 2;
                    int digitsStart = i;
                    while (i < n && Character.digit(s.charAt(i), 16) >= 0) i++;
                    value = Value.of(digitsStart == i ? 0 : Long.parseLong(s.substring(digitsStart, i), 16));
                } else if (c == '0' && i + 1 < n && isDigit(s.charAt(i + 1))) {
                    // 01 — восьмеричная запись, как strtoll(..., 8) в cparse
                    i++;
                    int digitsStart = i;
                    while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '7') i++;
                    value = Value.of(digitsStart == i ? 0 : Long.parseLong(s.substring(digitsStart, i), 8));
                } else {
                    while (i < n && isDigit(s.charAt(i))) i++;
                    // cparse: strchr(".eE", *nextChar) находит и завершающий ноль строки, поэтому
                    // целое в самом конце выражения читается как вещественное ("5" -> 5.0)
                    if (i == n || ((s.charAt(i) == '.' || s.charAt(i) == 'e' || s.charAt(i) == 'E')
                            && !(s.charAt(i) == '.' && i + 1 < n && s.charAt(i + 1) == '.'))) {
                        i = scanDouble(s, start);
                        value = Value.of(Double.parseDouble(s.substring(start, i)));
                    } else {
                        value = Value.of(Long.parseLong(s.substring(start, i)));
                    }
                }
                tokens.add(new Token(TokenType.NUMBER, s.substring(start, i), value));
            } else if (isIdentStart(c)) {
                int start = i;
                while (i < n && isIdentChar(s.charAt(i))) i++;
                String word = s.substring(start, i);
                switch (word) {
                    case "and":
                        tokens.add(new Token(TokenType.OP, "&&", null));
                        break;
                    case "or":
                        tokens.add(new Token(TokenType.OP, "||", null));
                        break;
                    case "not":
                        tokens.add(new Token(TokenType.OP, "!", null));
                        break;
                    case "nil":
                    case "None":
                        tokens.add(new Token(TokenType.NUMBER, word, Value.NONE));
                        break;
                    case "True":
                        tokens.add(new Token(TokenType.NUMBER, word, Value.TRUE));
                        break;
                    case "False":
                        tokens.add(new Token(TokenType.NUMBER, word, Value.FALSE));
                        break;
                    default:
                        tokens.add(new Token(TokenType.IDENT, word, null));
                        break;
                }
            } else {
                String op = null;
                for (String candidate : OPERATORS) {
                    if (s.startsWith(candidate, i)) {
                        op = candidate;
                        break;
                    }
                }
                if (op == null) {
                    throw new PlayerException("Invalid character: " + c);
                }
                i += op.length();
                if (op.equals("~=")) {
                    op = "!=";  // Lua-неравенство
                } else if (op.equals("..")) {
                    op = "+";   // Lua-конкатенация
                }
                tokens.add(new Token(TokenType.OP, op, null));
            }
        }
        tokens.add(new Token(TokenType.END, "", null));
        return tokens;
    }

    /**
     * Конец вещественного числа, как его прочитал бы strtod
     */
    private static int scanDouble(String s, int start) {
        int i = start;
        final int n = s.length();
        while (i < n && isDigit(s.charAt(i))) i++;
        if (i < n && s.charAt(i) == '.') {
            i++;
            while (i < n && isDigit(s.charAt(i))) i++;
        }
        if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            int expStart = i;
            i++;
            if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
            if (i < n && isDigit(s.charAt(i))) {
                while (i < n && isDigit(s.charAt(i))) i++;
            } else {
                i = expStart;
            }
        }
        return i;
    }

    // ------------------------------------------------------------------ разбор и вычисление

    private Token peek() {
        return m_tokens.get(m_pos);
    }

    private Token next() {
        return m_tokens.get(m_pos++);
    }

    /**
     * Бинарные операции с приоритетом не слабее maxPrecedence (левая ассоциативность)
     */
    private Value parseBinary(int maxPrecedence) {
        Value left = parseUnary();
        while (true) {
            Token token = peek();
            if (token.m_type != TokenType.OP) {
                break;
            }
            Integer precedence = BINARY_PRECEDENCE.get(token.m_text);
            if (precedence == null || precedence > maxPrecedence) {
                break;
            }
            next();
            Value right = parseBinary(precedence - 1);
            left = binary(token.m_text, left, right);
        }
        return left;
    }

    private Value parseUnary() {
        Token token = peek();
        if (token.m_type == TokenType.OP
                && (token.m_text.equals("!") || token.m_text.equals("-") || token.m_text.equals("+"))) {
            next();
            return unary(token.m_text, parseUnary());
        }
        return parsePrimary();
    }

    private Value parsePrimary() {
        Token token = next();
        switch (token.m_type) {
            case NUMBER:
            case STRING:
                return token.m_value;
            case IDENT: {
                if (peek().m_type == TokenType.OP && peek().m_text.equals("(")) {
                    throw new PlayerException("Unknown function: " + token.m_text);
                }
                Value value = m_scope.get(token.m_text);
                if (value == null) {
                    throw new PlayerException("Undefined variable: " + token.m_text);
                }
                return value;
            }
            case OP:
                if (token.m_text.equals("(")) {
                    Value value = parseBinary(Integer.MAX_VALUE);
                    Token close = next();
                    if (close.m_type != TokenType.OP || !close.m_text.equals(")")) {
                        throw new PlayerException("Missing ')' in " + m_source);
                    }
                    return value;
                }
                throw new PlayerException("Unexpected operator '" + token.m_text + "'");
            case END:
            default:
                throw new PlayerException("Unexpected end of expression");
        }
    }

    private static PlayerException undefinedOperation(String op, Value left, Value right) {
        return new PlayerException("Unexpected operation with operator '" + op + "' and operands: "
                + (left == null ? "" : left.toDisplayString() + " ") + "and " + right.toDisplayString());
    }

    /**
     * Операнд годится для числовой операции cparse (маска NUM совпадает и с nil — тогда
     * asDouble сообщит, что это не число)
     */
    private static boolean numeric(Value value) {
        return value.isNumber() || value.isNone();
    }

    static Value unary(String op, Value operand) {
        if (op.equals("!")) {
            if (operand.isNumber()) {
                return Value.of(!operand.asBool());
            }
            throw undefinedOperation(op, null, operand);
        }
        if (!numeric(operand)) {
            throw undefinedOperation(op, null, operand);
        }
        if (op.equals("-")) {
            return Value.of(-operand.asDouble());
        }
        return operand;  // унарный плюс
    }

    static Value binary(String op, Value left, Value right) {
        if (op.equals("==")) {
            return Value.of(left.equalsValue(right));
        }
        if (op.equals("!=")) {
            return Value.of(!left.equalsValue(right));
        }
        if (numeric(left) && numeric(right)) {
            return numeral(op, left, right);
        }
        if (left.isString() && right.isString()) {
            if (op.equals("+")) {
                return Value.of(left.asString() + right.asString());
            }
            throw undefinedOperation(op, left, right);
        }
        if (left.isString() && numeric(right)) {
            if (op.equals("+")) {
                return Value.of(left.asString() + Value.formatStreamDouble(right.asDouble()));
            }
            throw undefinedOperation(op, left, right);
        }
        if (numeric(left) && right.isString()) {
            if (op.equals("+")) {
                return Value.of(Value.formatStreamDouble(left.asDouble()) + right.asString());
            }
            throw undefinedOperation(op, left, right);
        }
        throw undefinedOperation(op, left, right);
    }

    private static Value numeral(String op, Value left, Value right) {
        final double l = left.asDouble();
        final double r = right.asDouble();
        final long li = left.asInt();
        final long ri = right.asInt();
        switch (op) {
            case "+":
                return Value.of(l + r);
            case "-":
                return Value.of(l - r);
            case "*":
                return Value.of(l * r);
            case "/":
                return Value.of(l / r);
            case "**":
                return Value.of(Math.pow(l, r));
            case "%":
                return Value.of(li % ri);
            case "<<":
                return Value.of(li << ri);
            case ">>":
                return Value.of(li >> ri);
            case "<":
                return Value.of(l < r);
            case ">":
                return Value.of(l > r);
            case "<=":
                return Value.of(l <= r);
            case ">=":
                return Value.of(l >= r);
            case "&&":
                return Value.of(li != 0 && ri != 0);
            case "||":
                return Value.of(li != 0 || ri != 0);
            case "&":
                return Value.of(li & ri);
            case "^":
                return Value.of(li ^ ri);
            case "|":
                return Value.of(li | ri);
            default:
                throw undefinedOperation(op, left, right);
        }
    }
}
