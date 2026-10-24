package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.config.Settings;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.LinkImpl;
import com.nlbhub.nlb.domain.ModificationImpl;
import com.nlbhub.nlb.domain.VariableImpl;
import com.nlbhub.nlb.domain.PageImpl;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Экспорт в INSTEAD: AUTO-переменная до первого присваивания равна false (initializeVariables),
 * поэтому в числовом сравнении и арифметике она приводится к числу (nlb:num), а логическое
 * использование и == не меняются («Фронтир»: selCardsSize > 0 ронял INSTEAD)
 */
public class SteadExportAutoVarTest {
    @BeforeClass
    public static void settings() throws Exception {
        // экспорт берёт настройки (пресеты медиа) из PropertyManager, а тот — из cfg/config.xml
        // текущего каталога; в тестах NLBL его нет — настройки NLBW
        Settings settings = PropertyManager.readSettings(new File("../NLBW/cfg/config.xml"));
        Field field = PropertyManager.class.getDeclaredField("m_settings");
        field.setAccessible(true);
        field.set(null, settings);
    }

    private static void constraint(TestBook book, LinkImpl link, PageImpl page, String text) {
        book.constraint(link, text);
        ((VariableImpl) book.nlb.getVariableById(link.getConstrId())).setTarget(page.getId() + ";" + link.getId());
    }

    @Test
    public void autoVarIsNumericOperandOnlyNearNumericOperators() throws Exception {
        TestBook book = new TestBook();
        PageImpl start = book.page("Старт");
        PageImpl more = book.page("Больше");
        PageImpl not = book.page("Нет");
        PageImpl two = book.page("Два");
        // cnt = cnt - 1 (переменные экспорту нужны с целью: «страница;модификация»)
        ModificationImpl mod = new ModificationImpl(start);
        mod.setType(Modification.Type.ASSIGN.name());
        VariableImpl cnt = book.variable(Variable.Type.VAR, Variable.DataType.AUTO, "cnt", null);
        VariableImpl expr = book.variable(Variable.Type.EXPRESSION, Variable.DataType.AUTO, null, "cnt - 1");
        cnt.setTarget(start.getId() + ";" + mod.getId());
        expr.setTarget(start.getId() + ";" + mod.getId());
        mod.setVarId(cnt.getId());
        mod.setExprId(expr.getId());
        start.addModification(mod);
        constraint(book, book.link(start, more, "больше"), start, "cnt > 0");
        constraint(book, book.link(start, not, "нет"), start, "!cnt");
        constraint(book, book.link(start, two, "два"), start, "cnt == 2");
        File file = File.createTempFile("nlb-export", ".lua");
        try {
            book.nlb.exportToSTEADFile(file);
            String lua = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            assertTrue(lua, lua.contains("_cnt = false;"));
            assertTrue(lua, lua.contains("nlb:num(_cnt) > 0"));
            assertTrue(lua, lua.contains("nlb:num(_cnt) - 1"));
            assertTrue(lua, lua.contains("not _cnt"));
            assertTrue(lua, lua.contains("_cnt == 2"));
            assertFalse(lua, lua.contains("nlb:num(_cnt) =="));
        } finally {
            file.delete();
        }
    }
}
