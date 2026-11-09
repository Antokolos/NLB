/**
 * @(#)ChoiceScriptExpression.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2026 Anton P. Kolosov
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
package com.nlbhub.nlb.domain.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Выражение ChoiceScript со скобками вокруг каждой бинарной операции.
 * <p>
 * У ChoiceScript нет приоритетов операторов: «a and b and c» — ошибка («expected
 * CLOSE_PARENTHESIS, was NAMED_OPERATOR [and]»), а «not(a) and n = 3» разбирается как
 * «(not(a) and n) = 3» («Neither true nor false»). Строки — только в двойных кавычках.
 * Экспорт переводит выражения NLB регулярными выражениями (ExportManager.translateExpressionBody),
 * поэтому готовое выражение здесь разбирается заново (приоритеты как в NLB: not, * / %, + - &amp;,
 * сравнения, and, or) и записывается так, как его понимает ChoiceScript. Если разобрать не
 * удалось, выражение остаётся как было.
 */
final class ChoiceScriptExpression {
    private enum Kind { NUMBER, STRING, NAME, OP, OPEN, CLOSE }

    private static final class Token {
        private final Kind m_kind;
        private final String m_text;

        private Token(Kind kind, String text) {
            m_kind = kind;
            m_text = text;
        }
    }

    private final List<Token> m_tokens;
    private int m_pos = 0;

    private ChoiceScriptExpression(List<Token> tokens) {
        m_tokens = tokens;
    }

    static String normalize(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return expression;
        }
        try {
            ChoiceScriptExpression parser = new ChoiceScriptExpression(tokenize(expression));
            String result = parser.parseOr();
            if (parser.m_pos != parser.m_tokens.size()) {
                return expression;
            }
            return result;
        } catch (IllegalArgumentException e) {
            return expression;
        }
    }

    private static List<Token> tokenize(String s) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"' || c == '\'') {
                StringBuilder text = new StringBuilder();
                int j = i + 1;
                while (j < s.length() && s.charAt(j) != c) {
                    if (s.charAt(j) == '\\' && j + 1 < s.length()) {
                        j++;
                    }
                    text.append(s.charAt(j));
                    j++;
                }
                if (j >= s.length()) {
                    throw new IllegalArgumentException("незакрытая строка");
                }
                tokens.add(new Token(Kind.STRING, text.toString()));
                i = j + 1;
            } else if (Character.isDigit(c)) {
                int j = i;
                while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) {
                    j++;
                }
                tokens.add(new Token(Kind.NUMBER, s.substring(i, j)));
                i = j;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) {
                    j++;
                }
                String name = s.substring(i, j);
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.equals("and") || lower.equals("or") || lower.equals("not") || lower.equals("modulo")) {
                    tokens.add(new Token(Kind.OP, lower));
                } else {
                    tokens.add(new Token(Kind.NAME, name));
                }
                i = j;
            } else if (c == '(') {
                tokens.add(new Token(Kind.OPEN, "("));
                i++;
            } else if (c == ')') {
                tokens.add(new Token(Kind.CLOSE, ")"));
                i++;
            } else if (s.startsWith("<=", i) || s.startsWith(">=", i) || s.startsWith("!=", i)) {
                tokens.add(new Token(Kind.OP, s.substring(i, i + 2)));
                i += 2;
            } else if ("=<>+-*/%&".indexOf(c) >= 0) {
                tokens.add(new Token(Kind.OP, String.valueOf(c)));
                i++;
            } else {
                throw new IllegalArgumentException("неизвестный знак " + c);
            }
        }
        return tokens;
    }

    private boolean atOp(String... ops) {
        if (m_pos >= m_tokens.size() || m_tokens.get(m_pos).m_kind != Kind.OP) {
            return false;
        }
        for (String op : ops) {
            if (op.equals(m_tokens.get(m_pos).m_text)) {
                return true;
            }
        }
        return false;
    }

    private String binary(String left, String op, String right) {
        return "(" + left + " " + op + " " + right + ")";
    }

    private String parseOr() {
        String left = parseAnd();
        while (atOp("or")) {
            m_pos++;
            left = binary(left, "or", parseAnd());
        }
        return left;
    }

    private String parseAnd() {
        String left = parseComparison();
        while (atOp("and")) {
            m_pos++;
            left = binary(left, "and", parseComparison());
        }
        return left;
    }

    private String parseComparison() {
        String left = parseAdditive();
        while (atOp("=", "!=", "<", ">", "<=", ">=")) {
            String op = m_tokens.get(m_pos++).m_text;
            left = binary(left, op, parseAdditive());
        }
        return left;
    }

    private String parseAdditive() {
        String left = parseMultiplicative();
        while (atOp("+", "-", "&")) {
            String op = m_tokens.get(m_pos++).m_text;
            left = binary(left, op, parseMultiplicative());
        }
        return left;
    }

    private String parseMultiplicative() {
        String left = parseUnary();
        while (atOp("*", "/", "%", "modulo")) {
            String op = m_tokens.get(m_pos++).m_text;
            left = binary(left, "%".equals(op) ? "modulo" : op, parseUnary());
        }
        return left;
    }

    private String parseUnary() {
        if (atOp("not")) {
            m_pos++;
            return "not(" + stripOuter(parseUnary()) + ")";
        }
        if (atOp("-")) {
            m_pos++;
            return "(0 - " + parseUnary() + ")";
        }
        return parsePrimary();
    }

    private String parsePrimary() {
        if (m_pos >= m_tokens.size()) {
            throw new IllegalArgumentException("выражение оборвано");
        }
        Token token = m_tokens.get(m_pos++);
        switch (token.m_kind) {
            case NUMBER:
                return token.m_text;
            case STRING:
                return "\"" + token.m_text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            case NAME:
                return token.m_text;
            case OPEN:
                String inner = parseOr();
                if (m_pos >= m_tokens.size() || m_tokens.get(m_pos).m_kind != Kind.CLOSE) {
                    throw new IllegalArgumentException("нет закрывающей скобки");
                }
                m_pos++;
                return inner;
            default:
                throw new IllegalArgumentException("неожиданный знак " + token.m_text);
        }
    }

    /** Внешние скобки бинарной операции — not(...) их уже даёт */
    private static String stripOuter(String s) {
        if (s.startsWith("(") && s.endsWith(")")) {
            int depth = 0;
            boolean inString = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                    inString = !inString;
                } else if (inString) {
                    continue;
                } else if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0 && i < s.length() - 1) {
                        return s;
                    }
                }
            }
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
