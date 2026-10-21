/**
 * @(#)Value.java
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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Значение переменной или выражения книги. Типы и правила — как в вычислителе выражений
 * NLBNavigator (cparse), с которым сверяется плеер: NONE (nil), BOOL, INT, REAL, STR.
 * BOOL, INT и REAL — числа: логическое значение в арифметике и сравнениях — 0 или 1.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public final class Value {
    public enum Type {NONE, BOOL, INT, REAL, STR}

    public static final Value NONE = new Value(Type.NONE, false, 0, 0.0, null);
    public static final Value TRUE = new Value(Type.BOOL, true, 1, 1.0, null);
    public static final Value FALSE = new Value(Type.BOOL, false, 0, 0.0, null);

    private final Type m_type;
    private final boolean m_bool;
    private final long m_int;
    private final double m_real;
    private final String m_str;

    private Value(Type type, boolean bool, long intValue, double real, String str) {
        m_type = type;
        m_bool = bool;
        m_int = intValue;
        m_real = real;
        m_str = str;
    }

    public static Value of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static Value of(long value) {
        return new Value(Type.INT, value != 0, value, value, null);
    }

    public static Value of(double value) {
        return new Value(Type.REAL, value != 0, (long) value, value, null);
    }

    public static Value of(String value) {
        return new Value(Type.STR, !value.isEmpty(), 0, 0.0, value);
    }

    public Type getType() {
        return m_type;
    }

    public boolean isNumber() {
        return m_type == Type.BOOL || m_type == Type.INT || m_type == Type.REAL;
    }

    public boolean isString() {
        return m_type == Type.STR;
    }

    public boolean isNone() {
        return m_type == Type.NONE;
    }

    public boolean isBool() {
        return m_type == Type.BOOL;
    }

    /**
     * cparse asDouble: только для чисел
     */
    public double asDouble() {
        if (!isNumber()) {
            throw new PlayerException("The Token is not a number!");
        }
        return m_real;
    }

    /**
     * cparse asInt: вещественное отбрасывает дробную часть
     */
    public long asInt() {
        if (!isNumber()) {
            throw new PlayerException("The Token is not a number!");
        }
        return m_int;
    }

    /**
     * cparse asBool: число — не ноль, строка — не пустая, nil — ложь
     */
    public boolean asBool() {
        return m_bool;
    }

    public String asString() {
        if (m_type != Type.STR) {
            throw new PlayerException("The Token is not a string!");
        }
        return m_str;
    }

    /**
     * Истинность значения в условиях книги (как в Lua): ложны только nil и false, а число 0
     * и пустая строка — истинны
     */
    public boolean isTruthy() {
        if (m_type == Type.NONE) {
            return false;
        }
        if (m_type == Type.BOOL) {
            return m_bool;
        }
        return true;
    }

    /**
     * Равенство cparse (==): числа (в том числе логические) сравниваются как вещественные,
     * значения разных типов не равны, остальные — по строковому представлению
     */
    public boolean equalsValue(Value other) {
        if (isNumber() && other.isNumber()) {
            return asDouble() == other.asDouble();
        }
        if (m_type != other.m_type) {
            return false;
        }
        return m_type == Type.NONE || m_str.equals(other.m_str);
    }

    /**
     * Текст значения для подстановки $var$ и PRN — как toDisplayString в NLBNavigator: целое
     * вещественное печатается без дробной части (Lua 5.1: 5.0 -> 5), остальные — %.14g
     */
    public String toDisplayString() {
        switch (m_type) {
            case NONE:
                return "";
            case BOOL:
                return m_bool ? "true" : "false";
            case INT:
                return Long.toString(m_int);
            case REAL:
                if (Double.isFinite(m_real) && m_real == Math.floor(m_real) && Math.abs(m_real) < 1e15) {
                    return Long.toString((long) m_real);
                }
                return formatG(m_real, 14);
            case STR:
            default:
                return m_str;
        }
    }

    /**
     * Число при склейке со строкой — как вывод double в std::ostream (printf %g, 6 значащих цифр)
     */
    public static String formatStreamDouble(double value) {
        return formatG(value, 6);
    }

    /**
     * printf("%.{precision}g", value)
     */
    static String formatG(double value, int precision) {
        if (Double.isNaN(value)) {
            return (Double.doubleToRawLongBits(value) < 0) ? "-nan" : "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        if (value == 0) {
            return (1 / value < 0) ? "-0" : "0";
        }
        if (precision == 0) {
            precision = 1;
        }
        BigDecimal rounded = new BigDecimal(value).round(new MathContext(precision, RoundingMode.HALF_EVEN));
        int exponent = rounded.precision() - rounded.scale() - 1;
        String result;
        if (exponent < -4 || exponent >= precision) {
            BigDecimal mantissa = rounded.movePointLeft(exponent);
            String digits = stripZeros(mantissa.setScale(precision - 1, RoundingMode.HALF_EVEN).toPlainString());
            String sign = exponent < 0 ? "-" : "+";
            int abs = Math.abs(exponent);
            result = digits + "e" + sign + (abs < 10 ? "0" : "") + abs;
        } else {
            int scale = Math.max(precision - 1 - exponent, 0);
            result = stripZeros(rounded.setScale(scale, RoundingMode.HALF_EVEN).toPlainString());
        }
        return result;
    }

    private static String stripZeros(String number) {
        if (number.indexOf('.') < 0) {
            return number;
        }
        int end = number.length();
        while (end > 0 && number.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && number.charAt(end - 1) == '.') {
            end--;
        }
        return number.substring(0, end);
    }

    @Override
    public String toString() {
        return m_type + ":" + toDisplayString();
    }
}
