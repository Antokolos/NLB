package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.ModificationImpl;
import com.nlbhub.nlb.domain.PageImpl;
import com.nlbhub.nlb.domain.VariableImpl;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Присваивание логического выражения в AXMA: в &lt;&lt;set&gt;&gt; движок не переводит and/or и &lt;&gt; и оставляет
 * &lt; и &gt; сущностями — выражение падало с «Unexpected token ';'» и обрывало абзац (бой «Фронтира»:
 * агрессия противника; «Слуга Ра»: ввод кода). Такое значение присваивается через &lt;&lt;if&gt;&gt;, где всё
 * переводится; обычное присваивание — как было
 */
public class AsmAssignmentExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private static void assign(TestBook b, PageImpl page, String varName, String expr) {
        ModificationImpl mod = new ModificationImpl(page);
        mod.setType(Modification.Type.ASSIGN.name());
        VariableImpl var = b.variable(Variable.Type.VAR, Variable.DataType.AUTO, varName, null);
        var.setTarget(page.getId() + ";" + mod.getId());
        mod.setVarId(var.getId());
        VariableImpl ex = b.variable(Variable.Type.EXPRESSION, Variable.DataType.AUTO, null, expr);
        ex.setTarget(page.getId() + ";" + mod.getId());
        mod.setExprId(ex.getId());
        page.addModification(mod);
    }

    @Test
    public void logicThroughIf() throws Exception {
        TestBook b = new TestBook();
        PageImpl room = b.page("Бой.");
        PageImpl out = b.page("Конец.");
        b.link(room, out, "Дальше");
        assign(b, room, "cards", "4");
        assign(b, room, "hp", "20");
        assign(b, room, "a", "1");
        assign(b, room, "b", "2");
        assign(b, room, "aggressive", "(cards > 3) || (hp <= 15)");
        assign(b, room, "same", "a == b");
        assign(b, room, "sum", "a + 1");
        assign(b, room, "label", "\"a > b\"");
        File file = new File(m_temp.getRoot(), "book.sm");
        b.nlb.exportToASMFile(file);
        String sm = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue("and/or и сравнения — через <<if>>: " + sm,
                sm.contains("<<if ($cards > 3) or ($hp <= 15)>><<set $aggressive = true>><<else>><<set $aggressive = false>><<endif>>"));
        assertTrue("== — тоже: " + sm, sm.contains("<<if $a == $b>><<set $same = true>><<else>><<set $same = false>><<endif>>"));
        assertTrue("арифметика — как было: " + sm, sm.contains("<<set $sum = $a + 1>>"));
        assertTrue("знак в строке — не логика: " + sm, sm.contains("<<set $label = \"a > b\">>"));
        assertFalse(sm, sm.contains("<<set $aggressive = ("));
    }
}
