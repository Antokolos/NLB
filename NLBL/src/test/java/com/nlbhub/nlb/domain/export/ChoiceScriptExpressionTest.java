package com.nlbhub.nlb.domain.export;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Выражения ChoiceScript: скобки вокруг каждой бинарной операции, строки — в двойных кавычках
 */
public class ChoiceScriptExpressionTest {
    @Test
    public void andChainIsNested() {
        // «a and b and c» ChoiceScript не разбирает («expected CLOSE_PARENTHESIS»)
        assertEquals("((not(a) and not(b)) and (c > 0))",
                ChoiceScriptExpression.normalize(" not(a) and not(b) and (c > 0)"));
    }

    @Test
    public void comparisonInsideAnd() {
        // без скобок ChoiceScript понимает это как «(not(w) and n) = 3» («Neither true nor false»)
        assertEquals("(not(w) and (n = 3))", ChoiceScriptExpression.normalize("( not(w) and n = 3)"));
    }

    @Test
    public void precedenceAndRedundantParentheses() {
        assertEquals("(a or (b and c))", ChoiceScriptExpression.normalize("((a)) or (b and c)"));
        assertEquals("(x + (y * 2))", ChoiceScriptExpression.normalize("x + y * 2"));
        assertEquals("((x - y) - 1)", ChoiceScriptExpression.normalize("x - y - 1"));
        assertEquals("chip_active", ChoiceScriptExpression.normalize("((chip_active))"));
        assertEquals("not(a or b)", ChoiceScriptExpression.normalize("not((a) or (b))"));
    }

    @Test
    public void stringsUseDoubleQuotes() {
        assertEquals("(export_lang = \"ru\")", ChoiceScriptExpression.normalize("(export_lang = 'ru')"));
        assertEquals("(s = \"(it's)\")", ChoiceScriptExpression.normalize("s = \"(it's)\""));
    }

    @Test
    public void unparsableStaysAsIs() {
        assertEquals("a ? b", ChoiceScriptExpression.normalize("a ? b"));
        assertEquals("(a", ChoiceScriptExpression.normalize("(a"));
    }
}
