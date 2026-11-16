package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.AbstractModifyingItem;
import com.nlbhub.nlb.domain.LinkImpl;
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
 * Каркас экспорта в AXMA Story Maker JS: файл нового формата (ASMJS), страница — абзац с кодом на
 * JavaScript (переменные книги — v.имя, текст — print, ссылки — button), ссылка — абзац с
 * модификациями и goto, автоссылка без текста страницы — сразу goto, GOTO и RND — встроенные
 * goto() и random(), условия — JavaScript
 */
public class AsmJsExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private PageImpl m_room;
    private PageImpl m_hall;
    private PageImpl m_pass;
    private LinkImpl m_door;
    private LinkImpl m_auto;

    /** Модификация (переменные — с целью «страница[;ссылка];модификация», как в книге) */
    private static void mod(TestBook b, AbstractModifyingItem item, String target, Modification.Type type, String varName, String expr) {
        ModificationImpl mod = new ModificationImpl(item);
        mod.setType(type.name());
        if (varName != null) {
            VariableImpl var = b.variable(Variable.Type.VAR, Variable.DataType.AUTO, varName, null);
            var.setTarget(target + ";" + mod.getId());
            mod.setVarId(var.getId());
        }
        VariableImpl ex = b.variable(Variable.Type.EXPRESSION, type == Modification.Type.GOTO ? Variable.DataType.STRING
                : Variable.DataType.AUTO, null, expr);
        ex.setTarget(target + ";" + mod.getId());
        mod.setExprId(ex.getId());
        item.addModification(mod);
    }

    private String export() throws Exception {
        TestBook b = new TestBook();
        m_room = b.page("Комната. \"Сила\": $power$^Вторая строка <b>");
        m_hall = b.page("Зал.");
        m_pass = b.page("");
        m_door = b.link(m_room, m_hall, "Дверь ($power$)");
        b.constraint(m_door, "power > 2");
        mod(b, m_door, m_room.getId() + ";" + m_door.getId(), Modification.Type.ASSIGN, "power", "power + 1");
        mod(b, m_room, m_room.getId(), Modification.Type.RND, "power", "6");
        LinkImpl back = b.link(m_hall, m_pass, "Назад");
        mod(b, back, m_hall.getId() + ";" + back.getId(), Modification.Type.GOTO, null, m_room.getId());
        m_auto = b.link(m_pass, m_room, "");
        m_auto.setAuto(true);
        File file = new File(m_temp.getRoot(), "book.sm");
        b.nlb.exportToASMJSFile(file);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void fileAndStart() throws Exception {
        String sm = export();
        assertTrue(sm, sm.startsWith("ASMJS[::]128[::]0[::]0[::]0[::]mw[::]html\n"));
        assertTrue("обязательные для библиотеки абзацы: " + sm, sm.contains(":: StoryTitle[::]")
                && sm.contains(":: StoryAuthor[::]") && sm.contains(":: StorySubtitle[::]"));
        assertTrue("Start — начальное состояние и первая страница: " + sm, sm.contains(":: Start[::]10-10-0\ndisplay(\"nlb_init\"); display(\"nlb_lib\");\ngoto(\""
                + m_room.getId() + "\");"));
        assertTrue("переменные книги — свойства v: " + sm, sm.contains("v = {}; nlb = {page: \"\""));
    }

    @Test
    public void pageTextAndLinks() throws Exception {
        String sm = export();
        assertTrue("модификации входа — до текста, при повторном показе — нет: " + sm,
                sm.contains("if (nlb.redraw) { nlb.redraw = 0; } else { v.power = random(1, 6); }"));
        assertTrue("текст — выражение: кавычки экранированы, переменная — значением, '^' — <br>, HTML — сущностями: " + sm,
                sm.contains("print(\"Комната. \\\"Сила\\\": \" + nlb.str(v.power) + \"\\u003cbr>Вторая строка &lt;b&gt;\" + \"<br><br>\");"));
        assertTrue("ссылка с условием — кнопкой, подпись с переменной: " + sm,
                sm.contains("if ((v.power > 2)) { print(nlbf.a(\"Дверь (\" + nlb.str(v.power) + \")\", \"" + m_door.getId() + "\", undefined, 1)); "));
        assertTrue("абзац ссылки — действие игрока, модификации, переход: " + sm,
                sm.contains(":: " + m_door.getId() + "[::]10-10-0\nif (!window.nlbf) { display(\"nlb_lib\"); }\n"
                        + "nlb.ms = []; nlb.me = \"\"; nlb.rp = nlb.page; nlb.ac++; nlb.cnt.walk++; nlb.sel = \"\"; ")
                        && sm.contains("v.power = v.power + 1; goto(\"" + m_hall.getId() + "\");"));
    }

    @Test
    public void gotoAndAutoLink() throws Exception {
        String sm = export();
        assertTrue("GOTO из модификаций ссылки — раньше её цели: " + sm,
                sm.contains("goto(\"" + m_room.getId() + "\"); goto(\"" + m_pass.getId() + "\");"));
        assertTrue("автоссылка со страницы без текста — сразу goto: " + sm, sm.contains("if (nlb.redraw) { nlb.redraw = 0; } else { }\nnlbf.turn();\ngoto(\"" + m_auto.getId() + "\"); "));
        assertFalse("старых макросов нет: " + sm, sm.contains("<<"));
    }
}
