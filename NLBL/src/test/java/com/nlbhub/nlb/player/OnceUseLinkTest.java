package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.SpecialVariablesNameHelper;
import com.nlbhub.nlb.api.config.Settings;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.LinkImpl;
import com.nlbhub.nlb.domain.ModificationImpl;
import com.nlbhub.nlb.domain.VariableImpl;
import com.nlbhub.nlb.domain.ObjImpl;
import com.nlbhub.nlb.domain.PageImpl;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * «Once» у ссылки объекта (применить ключ к двери) — срабатывает один раз, как у ссылки
 * страницы (#302): первое применение — текст успеха и модификации, дальше — альтернативный
 * текст, модификации не выполняются. Экспорт в INSTEAD и Java-плеер — одинаково
 */
public class OnceUseLinkTest {
    @BeforeClass
    public static void settings() throws Exception {
        Settings settings = PropertyManager.readSettings(new File("../NLBW/cfg/config.xml"));
        Field field = PropertyManager.class.getDeclaredField("m_settings");
        field.setAccessible(true);
        field.set(null, settings);
    }

    private static final class Book {
        final TestBook book = new TestBook();
        final ObjImpl key;
        final ObjImpl door;
        final LinkImpl use;

        Book(boolean once) {
            final PageImpl hall = book.page("Зал");
            key = book.obj(hall, "key", "{Ключ}", "Ключ", true);
            door = book.obj(hall, "door", "{Дверь}", "Дверь", false);
            use = new LinkImpl(key, door.getId());
            use.setTexts(book.text("Дверь открылась."));
            use.setAltTexts(book.text("Дверь уже открыта."));
            use.setOnce(once);
            key.addLink(use);
            // opened = opened + 1 (переменным экспорт нужна цель «объект;ссылка;модификация»)
            final ModificationImpl mod = new ModificationImpl(use);
            mod.setType(Modification.Type.ASSIGN.name());
            final String target = key.getId() + ";" + use.getId() + ";" + mod.getId();
            final VariableImpl var = book.variable(Variable.Type.VAR, Variable.DataType.AUTO, "opened", null);
            final VariableImpl expr = book.variable(Variable.Type.EXPRESSION, Variable.DataType.AUTO, null, "opened + 1");
            var.setTarget(target);
            expr.setTarget(target);
            mod.setVarId(var.getId());
            mod.setExprId(expr.getId());
            use.addModification(mod);
        }
    }

    private static String reaction(PlayerEngine engine) {
        final StringBuilder text = new StringBuilder();
        for (ScreenModel.Paragraph paragraph : engine.screen().getReactionText()) {
            text.append(paragraph.getText());
        }
        return text.toString().trim();
    }

    @Test
    public void playerUsesOnce() {
        final Book book = new Book(true);
        final PlayerEngine engine = book.book.start();
        engine.giveItem("key");
        assertTrue(engine.use(book.key.getId(), book.door.getId()));
        assertEquals("Дверь открылась.", reaction(engine));
        assertEquals(1, (int) engine.getVariable("opened").asDouble());
        engine.use(book.key.getId(), book.door.getId());
        assertEquals("Дверь уже открыта.", reaction(engine));
        assertEquals("модификации — один раз", 1, (int) engine.getVariable("opened").asDouble());
    }

    @Test
    public void playerWithoutOnceUsesEveryTime() {
        final Book book = new Book(false);
        final PlayerEngine engine = book.book.start();
        engine.giveItem("key");
        engine.use(book.key.getId(), book.door.getId());
        engine.use(book.key.getId(), book.door.getId());
        assertEquals("Дверь открылась.", reaction(engine));
        assertEquals(2, (int) engine.getVariable("opened").asDouble());
    }

    @Test
    public void exportUsesOnce() throws Exception {
        final Book book = new Book(true);
        final File file = File.createTempFile("nlb-export", ".lua");
        try {
            book.book.nlb.exportToSTEADFile(file);
            final String lua = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            final String lvs = "_" + SpecialVariablesNameHelper.decorateLinkVisitStateVar(book.use.getId());
            assertTrue("условие применения — ещё не применяли", lua.contains("not " + lvs));
            assertTrue("после применения — отметка", lua.contains(lvs + " = true;"));
            // отметка — в usef (после текста usep), иначе первый раз печатался бы альтернативный текст
            final int usef = lua.indexOf("usef = function");
            final int usep = lua.indexOf("usep = function");
            final int mark = lua.indexOf(lvs + " = true;");
            assertTrue(lua, usef >= 0 && usep > usef && mark > usef && mark < usep);
        } finally {
            file.delete();
        }
    }
}
