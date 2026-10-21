package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.AbstractModifyingItem;
import com.nlbhub.nlb.domain.LinkImpl;
import com.nlbhub.nlb.domain.ModificationImpl;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.PageImpl;
import com.nlbhub.nlb.domain.VariableImpl;
import com.nlbhub.nlb.util.MultiLangString;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Модификации и навигация на маленьких книгах, собранных в памяти: выражения и подстановка
 * в текст, ветвления и циклы, RND, списки, таймер страницы, однократные ссылки, GOTO и RETURN
 * в ссылках, достижения после «Начать заново».
 */
public class PlayerEngineTest {
    /**
     * Книга в памяти: страницы, ссылки, переменные и модификации
     */
    private static final class Book {
        final NonLinearBookImpl nlb = new NonLinearBookImpl();

        /**
         * Свой объект текста: setText у новой страницы или ссылки поменял бы общий текст по
         * умолчанию (DEFAULT_TEXT) сразу у всех
         */
        MultiLangString text(String text) {
            MultiLangString result = MultiLangString.createEmptyText();
            result.put(nlb.getLanguage(), text);
            return result;
        }

        PageImpl page(String text) {
            PageImpl page = new PageImpl(nlb);
            page.setTexts(text(text));
            nlb.addPage(page);
            if (nlb.getStartPoint() == null || nlb.getStartPoint().isEmpty()) {
                nlb.setStartPoint(page.getId());
            }
            return page;
        }

        LinkImpl link(PageImpl from, PageImpl to, String text) {
            LinkImpl link = new LinkImpl(from, to.getId());
            link.setTexts(text(text));
            from.addLink(link);
            return link;
        }

        VariableImpl variable(Variable.Type type, Variable.DataType dataType, String name, String value) {
            VariableImpl variable = new VariableImpl(nlb, type, dataType, name, value, null);
            nlb.addVariable(variable);
            return variable;
        }

        /**
         * Модификация: varName — имя переменной-получателя (null — без неё), expr — выражение
         * или операнд (null — без него)
         */
        void mod(AbstractModifyingItem item, Modification.Type type, String varName, String expr) {
            ModificationImpl mod = new ModificationImpl(item);
            mod.setType(type.name());
            if (varName != null) {
                mod.setVarId(variable(Variable.Type.VAR, Variable.DataType.AUTO, varName, null).getId());
            }
            if (expr != null) {
                mod.setExprId(variable(Variable.Type.EXPRESSION, Variable.DataType.AUTO, null, expr).getId());
            }
            item.addModification(mod);
        }

        /**
         * Ограничение ссылки (выражение)
         */
        void constraint(LinkImpl link, String expr) {
            link.setConstrId(variable(Variable.Type.LINKCONSTRAINT, Variable.DataType.BOOLEAN, null, expr).getId());
        }

        PlayerEngine start() {
            PlayerEngine.Settings settings = new PlayerEngine.Settings();
            settings.exportMode = PlayerEngine.ExportMode.STANDARD;
            settings.deterministic = true;
            PlayerEngine engine = new PlayerEngine(nlb, settings);
            engine.start();
            return engine;
        }
    }

    private static List<String> texts(PlayerEngine engine, PlayerEngine.Event.Kind kind) {
        List<String> result = new ArrayList<>();
        for (PlayerEngine.Event event : engine.takeEvents()) {
            if (event.getKind() == kind) {
                result.add(event.getText());
            }
        }
        return result;
    }

    private static List<String> choices(PlayerEngine engine) {
        List<String> result = new ArrayList<>();
        for (PlayerEngine.Choice choice : engine.view().getChoices()) {
            result.add(choice.getText());
        }
        return result;
    }

    @Test
    public void assignmentIsExpandedInPageText() {
        Book book = new Book();
        PageImpl start = book.page("x = $x$, s = $s$");
        book.mod(start, Modification.Type.ASSIGN, "x", "2 + 3 * 4");
        book.mod(start, Modification.Type.ASSIGN, "s", "\"a\" + x");
        PlayerEngine engine = book.start();
        assertEquals(List.of("x = 14, s = a14"), texts(engine, PlayerEngine.Event.Kind.PageText));
    }

    @Test
    public void ifElseAndWhile() {
        Book book = new Book();
        PageImpl start = book.page("$branch$ $sum$");
        book.mod(start, Modification.Type.ASSIGN, "i", "0");
        book.mod(start, Modification.Type.ASSIGN, "sum", "0");
        book.mod(start, Modification.Type.WHILE, null, "i < 5");
        book.mod(start, Modification.Type.ASSIGN, "i", "i + 1");
        book.mod(start, Modification.Type.ASSIGN, "sum", "sum + i");
        book.mod(start, Modification.Type.END, null, null);
        book.mod(start, Modification.Type.IF, null, "sum > 100");
        book.mod(start, Modification.Type.ASSIGN, "branch", "\"big\"");
        book.mod(start, Modification.Type.ELSEIF, null, "sum == 15");
        book.mod(start, Modification.Type.ASSIGN, "branch", "\"fifteen\"");
        book.mod(start, Modification.Type.ELSE, null, null);
        book.mod(start, Modification.Type.ASSIGN, "branch", "\"other\"");
        book.mod(start, Modification.Type.END, null, null);
        PlayerEngine engine = book.start();
        assertEquals(List.of("fifteen 15"), texts(engine, PlayerEngine.Event.Kind.PageText));
    }

    @Test
    public void deterministicRandom() {
        Book book = new Book();
        PageImpl start = book.page("$a$ $b$ $c$");
        book.mod(start, Modification.Type.RND, "a", "6");
        book.mod(start, Modification.Type.RND, "b", "6");
        book.mod(start, Modification.Type.RND, "c", "2");
        PlayerEngine engine = book.start();
        // k-й вызов rnd(n) даёт (k mod n) + 1
        assertEquals(List.of("1 2 1"), texts(engine, PlayerEngine.Event.Kind.PageText));
    }

    @Test
    public void listsPushPopEject() {
        Book book = new Book();
        PageImpl start = book.page("$n$ $top$ $tail$ $left$");
        // Операнд PUSH — имя переменной (или объекта), значение берётся из неё
        book.mod(start, Modification.Type.ASSIGN, "v", "1");
        book.mod(start, Modification.Type.PUSH, "lst", "v");
        book.mod(start, Modification.Type.ASSIGN, "v", "2");
        book.mod(start, Modification.Type.PUSH, "lst", "v");
        book.mod(start, Modification.Type.ASSIGN, "v", "3");
        book.mod(start, Modification.Type.PUSH, "lst", "v");
        book.mod(start, Modification.Type.SIZE, "n", "lst");
        book.mod(start, Modification.Type.POP, "top", "lst");
        book.mod(start, Modification.Type.EJECT, "tail", "lst");
        book.mod(start, Modification.Type.SIZE, "left", "lst");
        PlayerEngine engine = book.start();
        // PUSH и POP — с головы списка, EJECT — с хвоста
        assertEquals(List.of("3 3 1 1"), texts(engine, PlayerEngine.Event.Kind.PageText));
    }

    @Test
    public void timerTicksOncePerTurn() {
        Book book = new Book();
        PageImpl start = book.page("Ждём");
        PageImpl done = book.page("Дождались: $t$");
        start.setTimerVarId(book.variable(Variable.Type.TIMER, Variable.DataType.NUMBER, "t", null).getId());
        LinkImpl auto = book.link(start, done, "auto");
        auto.setAuto(true);
        book.constraint(auto, "t >= 3");
        PageImpl elsewhere = book.page("Другое");
        book.link(start, elsewhere, "Уйти");
        PlayerEngine engine = book.start();
        engine.takeEvents();
        // Есть выбор — время само не идёт: тик — один ход игрока (здесь — «подождать»)
        assertEquals(start.getId(), engine.currentPage().getId());
        engine.waitTick();
        engine.waitTick();
        assertEquals(2L, engine.getVariable("t").asInt());
        assertEquals(start.getId(), engine.currentPage().getId());
        engine.waitTick();
        assertEquals(done.getId(), engine.currentPage().getId());
        assertEquals(List.of("Дождались: 3"), texts(engine, PlayerEngine.Event.Kind.PageText));
    }

    @Test
    public void timerIsFastForwardedWhenNothingToChoose() {
        Book book = new Book();
        PageImpl start = book.page("Заставка");
        PageImpl done = book.page("После заставки");
        start.setTimerVarId(book.variable(Variable.Type.TIMER, Variable.DataType.NUMBER, "t", null).getId());
        LinkImpl auto = book.link(start, done, "auto");
        auto.setAuto(true);
        book.constraint(auto, "t >= 10");
        PlayerEngine engine = book.start();
        assertEquals(done.getId(), engine.currentPage().getId());
        assertEquals(List.of("Заставка", "После заставки"), texts(engine, PlayerEngine.Event.Kind.PageText));
    }

    @Test
    public void onceLinkDisappearsAfterUse() {
        Book book = new Book();
        PageImpl hub = book.page("Хаб");
        PageImpl room = book.page("Комната");
        LinkImpl once = book.link(hub, room, "Один раз");
        once.setOnce(true);
        book.link(room, hub, "Назад");
        PlayerEngine engine = book.start();
        assertEquals(List.of("Один раз"), choices(engine));
        assertTrue(engine.choose(0));
        assertTrue(engine.choose(0));
        assertEquals(hub.getId(), engine.currentPage().getId());
        assertEquals(List.of(), choices(engine));
    }

    @Test
    public void gotoInLinkOverridesTarget() {
        Book book = new Book();
        PageImpl start = book.page("Старт");
        PageImpl target = book.page("Цель ссылки");
        PageImpl other = book.page("Цель GOTO");
        LinkImpl link = book.link(start, target, "Идти");
        book.mod(link, Modification.Type.GOTO, null, other.getId());
        PlayerEngine engine = book.start();
        engine.choose(0);
        assertEquals(other.getId(), engine.currentPage().getId());
    }

    @Test
    public void returnInLinkCancelsTransition() {
        Book book = new Book();
        PageImpl start = book.page("Старт");
        PageImpl target = book.page("Закрыто");
        LinkImpl link = book.link(start, target, "Дверь");
        book.mod(link, Modification.Type.ASSIGN, "tries", "tries + 1");
        book.mod(link, Modification.Type.RETURN, null, null);
        PlayerEngine engine = book.start();
        engine.choose(0);
        engine.choose(0);
        assertEquals(start.getId(), engine.currentPage().getId());
        assertEquals(2L, engine.getVariable("tries").asInt());
    }

    @Test
    public void achievementsSurviveRestart() {
        Book book = new Book();
        PageImpl start = book.page("Старт");
        book.mod(start, Modification.Type.ACHIEVE, null, "first");
        book.mod(start, Modification.Type.ACHIEVED, "count", "first");
        PlayerEngine engine = book.start();
        assertEquals(List.of("Получено достижение: first"), texts(engine, PlayerEngine.Event.Kind.Achievement));
        engine.restart();
        // Достижение уже получено: второй раз не объявляется, счётчик по id модификации тот же
        assertEquals(List.of(), texts(engine, PlayerEngine.Event.Kind.Achievement));
        assertEquals(1L, engine.getVariable("count").asInt());
        assertTrue(engine.achievements().isGranted("first"));
    }

    @Test
    public void pageWithoutMovesFinishesGame() {
        Book book = new Book();
        PageImpl start = book.page("Старт");
        PageImpl end = book.page("Конец");
        book.link(start, end, "Дальше");
        PlayerEngine engine = book.start();
        assertFalse(engine.isFinished());
        engine.takeEvents();
        engine.choose(0);
        assertTrue(engine.isFinished());
        assertEquals(List.of("Конец"), texts(engine, PlayerEngine.Event.Kind.PageText));
    }
}
