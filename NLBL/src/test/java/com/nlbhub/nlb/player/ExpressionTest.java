package com.nlbhub.nlb.player;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Сверка вычислителя выражений с cparse (NLBNavigator). Эталон
 * player-expressions/cparse-reference.tsv получен программой на исходниках cparse, которая
 * вычисляет выражения так же, как ModificationInterpreter::evaluate: «выражение \t ТИП:значение»
 * или ERROR. Переменные: n5 = 5, r25 = 2.5, s = "abc", e = "", b = true, z = 0; неизвестные — false.
 */
public class ExpressionTest {
    private static final Map<String, Value> VARS = new HashMap<>();

    static {
        VARS.put("true", Value.TRUE);
        VARS.put("false", Value.FALSE);
        VARS.put("n5", Value.of(5L));
        VARS.put("r25", Value.of(2.5));
        VARS.put("s", Value.of("abc"));
        VARS.put("e", Value.of(""));
        VARS.put("b", Value.TRUE);
        VARS.put("z", Value.of(0L));
    }

    private static String describe(String expression) {
        try {
            Value value = Expression.evaluate(expression, name -> VARS.getOrDefault(name, Value.FALSE));
            return value.getType() + ":" + value.toDisplayString();
        } catch (PlayerException e) {
            return "ERROR";
        }
    }

    @Test
    public void matchesCparse() throws IOException {
        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        try (InputStream stream = ExpressionTest.class.getResourceAsStream("/player-expressions/cparse-reference.tsv")) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                if (line.isEmpty()) {
                    continue;
                }
                int tab = line.lastIndexOf('\t');
                String expression = line.substring(0, tab);
                String expected = line.substring(tab + 1);
                String actual = describe(expression);
                if (!expected.equals(actual)) {
                    mismatches.add(expression + "  ожидалось " + expected + ", получено " + actual);
                }
                checked++;
            }
        }
        assertTrue("Нет выражений для проверки", checked > 0);
        assertEquals("Расхождения с cparse:\n" + String.join("\n", mismatches), 0, mismatches.size());
    }

    @Test
    public void emptyExpressionIsFalse() {
        assertEquals(Value.FALSE, Expression.evaluate("  ", name -> null));
    }

    @Test
    public void streamDoubleFormat() {
        assertEquals("1", Value.formatStreamDouble(1.0));
        assertEquals("0.333333", Value.formatStreamDouble(1.0 / 3));
        assertEquals("1e+06", Value.formatStreamDouble(1e6));
        assertEquals("123457", Value.formatStreamDouble(123456.7));
        assertEquals("-2.5", Value.formatStreamDouble(-2.5));
    }
}
