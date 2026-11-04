package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.api.config.Settings;
import com.nlbhub.nlb.domain.ModificationImpl;
import com.nlbhub.nlb.domain.PageImpl;
import com.nlbhub.nlb.domain.VariableImpl;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Экспорт в INSTEAD: аргумент PRN, PUSH и INJECT — выражение или константа, а не только имя
 * переменной. Раньше «PRN codeValue / codePos» давало «_codeValue / codePos» (#299), а «list
 * INJECT 1» — «nlb:inject(_list.listnam, _1)» (#300)
 */
public class SteadExportValuesTest {
    @BeforeClass
    public static void settings() throws Exception {
        Settings settings = PropertyManager.readSettings(new File("../NLBW/cfg/config.xml"));
        Field field = PropertyManager.class.getDeclaredField("m_settings");
        field.setAccessible(true);
        field.set(null, settings);
    }

    /**
     * Модификация страницы: [переменная] ТИП выражение
     */
    private static void modification(TestBook book, PageImpl page, Modification.Type type, String varName,
                                      Variable.DataType exprType, String expression) {
        final ModificationImpl mod = new ModificationImpl(page);
        mod.setType(type.name());
        if (varName != null) {
            final VariableImpl var = book.variable(Variable.Type.VAR, Variable.DataType.AUTO, varName, null);
            var.setTarget(page.getId() + ";" + mod.getId());
            mod.setVarId(var.getId());
        }
        final VariableImpl expr = book.variable(Variable.Type.EXPRESSION, exprType, null, expression);
        expr.setTarget(page.getId() + ";" + mod.getId());
        mod.setExprId(expr.getId());
        page.addModification(mod);
    }

    /**
     * Плеер — как INSTEAD на экспорте той же книги: «2.5 2 5 1» (снято в sdl-instead 3.3.2):
     * PRN выражения, размер списка после PUSH в конец и INJECT в начало, POP с конца
     */
    @Test
    public void playerPrintsWhatInsteadPrints() {
        final TestBook book = new TestBook();
        final PageImpl start = book.page("Старт");
        modification(book, start, Modification.Type.ASSIGN, "codeValue", Variable.DataType.AUTO, "10");
        modification(book, start, Modification.Type.ASSIGN, "codePos", Variable.DataType.AUTO, "4");
        modification(book, start, Modification.Type.PRN, null, Variable.DataType.AUTO, "codeValue / codePos");
        modification(book, start, Modification.Type.PUSH, "list", Variable.DataType.AUTO, "codePos + 1");
        modification(book, start, Modification.Type.INJECT, "list", Variable.DataType.AUTO, "1");
        modification(book, start, Modification.Type.SIZE, "sz", Variable.DataType.AUTO, "list");
        modification(book, start, Modification.Type.PRN, null, Variable.DataType.AUTO, "sz");
        modification(book, start, Modification.Type.POP, "x", Variable.DataType.AUTO, "list");
        modification(book, start, Modification.Type.PRN, null, Variable.DataType.AUTO, "x");
        modification(book, start, Modification.Type.POP, "y", Variable.DataType.AUTO, "list");
        modification(book, start, Modification.Type.PRN, null, Variable.DataType.AUTO, "y");
        final PlayerEngine engine = book.start();
        final StringBuilder printed = new StringBuilder();
        for (ScreenModel.Paragraph paragraph : engine.screen().getModText()) {
            printed.append(paragraph.getText()).append(' ');
        }
        assertEquals("2.5 2 5 1", printed.toString().trim());
    }

    @Test
    public void prnPushAndInjectTakeExpressions() throws Exception {
        final TestBook book = new TestBook();
        final PageImpl start = book.page("Старт");
        modification(book, start, Modification.Type.ASSIGN, "codeValue", Variable.DataType.AUTO, "10");
        modification(book, start, Modification.Type.ASSIGN, "codePos", Variable.DataType.AUTO, "2");
        modification(book, start, Modification.Type.PRN, null, Variable.DataType.AUTO, "codeValue / codePos");
        modification(book, start, Modification.Type.PRN, null, Variable.DataType.AUTO, "codePos");
        modification(book, start, Modification.Type.INJECT, "list", Variable.DataType.AUTO, "1");
        modification(book, start, Modification.Type.PUSH, "list", Variable.DataType.AUTO, "codePos + 1");
        modification(book, start, Modification.Type.INJECT, "list", Variable.DataType.AUTO, "codeValue");
        final File file = File.createTempFile("nlb-export", ".lua");
        try {
            book.nlb.exportToSTEADFile(file);
            final String lua = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            assertFalse("#299: второй операнд — тоже переменная", lua.contains("/ codePos"));
            assertTrue(lua, lua.contains("p(nlb:num(_codeValue) / nlb:num(_codePos))"));
            assertTrue("имя переменной — как раньше", lua.contains("p(_codePos)"));
            assertFalse("#300: число — не переменная «_1»", lua.contains("_1)"));
            assertTrue(lua, lua.contains("nlb:inject(_list.listnam, 1)"));
            assertTrue(lua, lua.contains("nlb:push(_list.listnam, nlb:num(_codePos) + 1)"));
            assertTrue("имя переменной — как раньше", lua.contains("nlb:inject(_list.listnam, _codeValue)"));
        } finally {
            file.delete();
        }
    }
}
