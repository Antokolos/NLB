package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.LinkImpl;
import com.nlbhub.nlb.domain.ObjImpl;
import com.nlbhub.nlb.domain.PageImpl;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Модификации и навигация на маленьких книгах, собранных в памяти: выражения и подстановка
 * в текст, ветвления и циклы, RND, списки, таймер страницы, однократные ссылки, GOTO и RETURN
 * в ссылках, достижения после «Начать заново».
 */
public class PlayerEngineTest {
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
        TestBook book = new TestBook();
        PageImpl start = book.page("x = $x$, s = $s$");
        book.mod(start, Modification.Type.ASSIGN, "x", "2 + 3 * 4");
        book.mod(start, Modification.Type.ASSIGN, "s", "\"a\" + x");
        PlayerEngine engine = book.start();
        assertEquals(List.of("x = 14, s = a14"), texts(engine, PlayerEngine.Event.Kind.PageText));
    }

    @Test
    public void ifElseAndWhile() {
        TestBook book = new TestBook();
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
        TestBook book = new TestBook();
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
        TestBook book = new TestBook();
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
        TestBook book = new TestBook();
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
        TestBook book = new TestBook();
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
        TestBook book = new TestBook();
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
        TestBook book = new TestBook();
        PageImpl start = book.page("Старт");
        PageImpl target = book.page("Цель ссылки");
        PageImpl other = book.page("Цель GOTO");
        LinkImpl link = book.link(start, target, "Идти");
        book.mod(link, Modification.Type.GOTO, null, other.getId());
        PlayerEngine engine = book.start();
        engine.choose(0);
        assertEquals(other.getId(), engine.currentPage().getId());
    }

    /**
     * Ответ на действие после перехода (автоссылкой) показывается перед текстом новой страницы, чтобы
     * игрок успел его прочитать; ответ, дословно совпадающий с текстом новой страницы, не
     * повторяется («Шапочка»: цветы в вазу -> VN-страница с той же фразой)
     */
    @Test
    public void reactionEqualToNewPageTextIsNotRepeated() {
        for (String pageText : new String[]{"Цветы в вазе. Красиво!", "Другая страница"}) {
            TestBook book = new TestBook();
            PageImpl start = book.page("Старт");
            PageImpl next = book.page(pageText);
            ObjImpl vase = book.obj(start, "vase", "Здесь {ваза}.", "ваза", false);
            vase.setActTexts(book.text("Цветы  в вазе.^Красиво! "));
            book.mod(vase, Modification.Type.ASSIGN, "done", "true");
            LinkImpl auto = book.link(start, next, "auto");
            auto.setAuto(true);
            book.constraint(auto, "done");
            PlayerEngine engine = book.start();
            assertTrue(engine.actOnObject(vase.getId()));
            assertEquals(next.getId(), engine.currentPage().getId());
            List<String> reaction = new ArrayList<>();
            for (ScreenModel.Paragraph p : engine.screen().getReactionText()) {
                reaction.add(p.getText());
            }
            if (pageText.equals("Другая страница")) {
                assertEquals(java.util.Arrays.asList("Цветы  в вазе.", "Красиво!"), reaction);
            } else {
                assertTrue(reaction.toString(), reaction.isEmpty());
            }
        }
    }

    @Test
    public void returnInLinkCancelsTransition() {
        TestBook book = new TestBook();
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
        TestBook book = new TestBook();
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
        TestBook book = new TestBook();
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

    // ------------------------------------------------------------------ сохранения

    @Test
    public void loadRestoresWorldAndShowsPageAgain() {
        TestBook book = new TestBook();
        PageImpl start = book.page("Старт");
        PageImpl room = book.page("Комната: $x$");
        PageImpl far = book.page("Дальше");
        book.mod(start, Modification.Type.ASSIGN, "x", "1");
        LinkImpl go = book.link(start, room, "В комнату");
        book.mod(go, Modification.Type.ASSIGN, "x", "x + 1");
        book.mod(go, Modification.Type.ASSIGN, "v", "\"a\"");
        book.mod(go, Modification.Type.PUSH, "lst", "v");
        LinkImpl once = book.link(room, far, "Один раз");
        once.setOnce(true);
        book.link(far, room, "Назад");
        PlayerEngine engine = book.start();
        engine.choose(0);
        String save = engine.saveGame();
        // После сохранения мир меняется...
        engine.choose(0);
        engine.choose(0);
        assertEquals(List.of(), choices(engine));
        engine.takeEvents();
        // ...а загрузка возвращает его: страница показывается заново, её модификации не исполняются
        engine.loadGame(save);
        assertEquals(room.getId(), engine.currentPage().getId());
        assertEquals(List.of("Комната: 2"), texts(engine, PlayerEngine.Event.Kind.PageText));
        assertEquals(2L, engine.getVariable("x").asInt());
        assertEquals(1, engine.context().listSize("lst"));
        assertEquals(List.of("Один раз"), choices(engine));
        // То же в новом движке (другой игрок, другой процесс)
        PlayerEngine other = book.create(true);
        other.loadGame(save);
        assertEquals(room.getId(), other.currentPage().getId());
        assertEquals(2L, other.getVariable("x").asInt());
    }

    @Test
    public void randomContinuesAfterLoad() {
        TestBook book = new TestBook();
        PageImpl hub = book.page("Хаб");
        PageImpl roll = book.page("Выпало $r$");
        book.mod(roll, Modification.Type.RND, "r", "1000000");
        book.link(hub, roll, "Бросить");
        book.link(roll, hub, "Назад");
        PlayerEngine engine = book.create(false);
        engine.start();
        String save = engine.saveGame();
        engine.choose(0);
        long first = engine.getVariable("r").asInt();
        PlayerEngine other = book.create(false);
        other.loadGame(save);
        other.choose(0);
        assertEquals(first, other.getVariable("r").asInt());
    }

    @Test
    public void noSavePageForbidsSaving() {
        TestBook book = new TestBook();
        PageImpl start = book.page("Нельзя");
        start.setNoSave(true);
        PlayerEngine engine = book.start();
        assertFalse(engine.canSave());
        try {
            engine.saveGame();
            fail("сохранение на странице nosave");
        } catch (PlayerException e) {
            assertEquals("здесь сохраняться нельзя", e.getMessage());
        }
    }

    @Test
    public void saveOfOtherBookIsRejected() {
        TestBook first = new TestBook();
        first.page("Первая");
        TestBook second = new TestBook();
        second.page("Вторая");
        String save = first.start().saveGame();
        PlayerEngine engine = second.start();
        assertFalse(engine.readSaveInfo(save).isCompatible());
        assertTrue(engine.readSaveInfo(save).isValid());
        assertFalse(engine.readSaveInfo("{}").isValid());
        try {
            engine.loadGame(save);
            fail("загрузилось сохранение другой книги");
        } catch (PlayerException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("сохранение сделано для другой книги"));
        }
        assertEquals(List.of("Вторая"), texts(engine, PlayerEngine.Event.Kind.PageText));
    }

    @Test
    public void tryAgainReturnsToSnapshot() {
        TestBook book = new TestBook();
        PageImpl start = book.page("Старт");
        PageImpl checkpoint = book.page("Контрольная точка: $x$");
        PageImpl end = book.page("Конец");
        book.link(start, checkpoint, "Дальше");
        book.mod(checkpoint, Modification.Type.ASSIGN, "x", "x + 1");
        book.mod(checkpoint, Modification.Type.SNAPSHOT, null, null);
        LinkImpl die = book.link(checkpoint, end, "Погибнуть");
        book.mod(die, Modification.Type.ASSIGN, "x", "100");
        PlayerEngine engine = book.start();
        engine.choose(0);
        engine.choose(0);
        assertTrue(engine.isFinished());
        engine.takeEvents();
        // Снимок сделан после входа на страницу: x уже 1, модификации повторно не исполняются
        engine.tryAgain();
        assertEquals(checkpoint.getId(), engine.currentPage().getId());
        assertEquals(1L, engine.getVariable("x").asInt());
        assertEquals(List.of("Контрольная точка: 1"), texts(engine, PlayerEngine.Event.Kind.PageText));
        // Снимок входит в сохранение и остаётся после «ещё раз»
        engine.choose(0);
        String save = engine.saveGame();
        PlayerEngine other = book.create(true);
        other.loadGame(save);
        other.tryAgain();
        assertEquals(checkpoint.getId(), other.currentPage().getId());
        // «Начать заново» снимок забывает: без нового SNAPSHOT «ещё раз» — это начало игры
        other.restart();
        other.tryAgain();
        assertEquals(start.getId(), other.currentPage().getId());
    }

    @Test
    public void tryAgainWithoutSnapshotRestarts() {
        TestBook book = new TestBook();
        PageImpl start = book.page("Старт: $x$");
        PageImpl end = book.page("Конец");
        book.mod(start, Modification.Type.ASSIGN, "x", "x + 1");
        book.link(start, end, "Дальше");
        PlayerEngine engine = book.start();
        engine.choose(0);
        engine.takeEvents();
        engine.tryAgain();
        assertEquals(start.getId(), engine.currentPage().getId());
        assertEquals(List.of("Старт: 1"), texts(engine, PlayerEngine.Event.Kind.PageText));
    }

    @Test
    public void valuesAndAchievementsSurviveJson() throws Exception {
        for (Value value : List.of(Value.TRUE, Value.FALSE, Value.NONE, Value.of(-7L), Value.of(2.5),
                Value.of(1e300), Value.of(Double.POSITIVE_INFINITY), Value.of(""), Value.of("i:5 строка\tс табом"))) {
            Value decoded = Value.decode(value.encode());
            assertEquals(value.encode(), value.getType(), decoded.getType());
            assertEquals(value.encode(), value.toDisplayString(), decoded.toDisplayString());
        }
        Achievements achievements = new Achievements();
        achievements.register("a");
        achievements.addId("a", "m1");
        achievements.addId("a", "m2");
        achievements.setMax("a", 2);
        achievements.grant("a");
        Achievements restored = new Achievements();
        restored.restore(new com.fasterxml.jackson.databind.ObjectMapper().readTree(achievements.toJson().toString()));
        assertEquals(2, restored.count("a"));
        assertEquals(Integer.valueOf(2), restored.max("a"));
        assertTrue(restored.isGranted("a"));
    }

    private static List<String> music(PlayerEngine engine) {
        return texts(engine, PlayerEngine.Event.Kind.Music);
    }

    @Test
    public void musicAfterTryAgainAndRestart() {
        TestBook book = new TestBook();
        PageImpl title = book.page("Титул");
        PageImpl checkpoint = book.page("Контрольная точка");
        PageImpl end = book.page("Конец");
        book.link(title, checkpoint, "Дальше");
        book.link(checkpoint, end, "Погибнуть");
        end.setSoundFileName("finale.ogg");
        book.mod(title, Modification.Type.SNAPSHOT, null, null);
        PlayerEngine engine = book.start();
        engine.choose(0);
        engine.choose(0);
        assertEquals(List.of("finale.ogg"), music(engine));
        // снимок на титуле, музыки тогда не было — после «ещё раз» тишина
        engine.tryAgain();
        assertEquals(List.of(""), music(engine));
        // снимок с музыкой — музыка страницы снимка
        checkpoint.setSoundFileName("cave.ogg");
        book.mod(checkpoint, Modification.Type.SNAPSHOT, null, null);
        engine.restart();
        engine.takeEvents();
        engine.choose(0);
        engine.choose(0);
        engine.takeEvents();
        engine.tryAgain();
        assertEquals(List.of("cave.ogg"), music(engine));
        // «Начать заново»: игравшая музыка останавливается, у титула её нет
        engine.restart();
        assertEquals(List.of(""), music(engine));
        engine.restart();
        assertEquals(List.of(), music(engine));
    }

    @Test
    public void onlyLastMusicOfTurn() {
        // Цепочка страниц без текста, у каждой своя музыка: за ход — только последняя
        TestBook book = new TestBook();
        PageImpl start = book.page("Старт");
        PageImpl hub = book.page("");
        PageImpl corridor = book.page("");
        PageImpl room = book.page("Комната");
        hub.setSoundFileName("hub.ogg");
        corridor.setSoundFileName("corridor.ogg");
        room.setSoundFileName("room.ogg");
        book.link(start, hub, "Дальше");
        book.link(hub, corridor, "auto").setAuto(true);
        book.link(corridor, room, "auto").setAuto(true);
        PlayerEngine engine = book.start();
        engine.takeEvents();
        engine.choose(0);
        assertEquals(List.of("room.ogg"), music(engine));
    }
}
