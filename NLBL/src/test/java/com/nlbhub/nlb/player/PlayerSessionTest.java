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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Сессия игрока и снимок экрана: правила щелчков INSTEAD, реальное время, озвучка, текст и объекты
 */
public class PlayerSessionTest {
    private static String reaction(ScreenModel screen) {
        List<String> lines = new ArrayList<>();
        for (ScreenModel.Paragraph p : screen.getReactionText()) {
            lines.add(p.getText());
        }
        return String.join("\n", lines);
    }

    @Test
    public void clickRulesLikeInstead() {
        TestBook book = new TestBook();
        PageImpl room = book.page("Комната");
        ObjImpl key = book.obj(room, "key", "На полу {ключ}.", "Ключ", true);
        ObjImpl door = book.obj(room, "door", "Здесь {дверь}.", "Дверь", false);
        LinkImpl use = new LinkImpl(key, door.getId());
        use.setTexts(book.text("Дверь открылась."));
        key.addLink(use);
        PlayerSession session = book.session();
        ScreenModel screen = session.screen();
        // Описания объектов — абзацы с ссылками на объекты; метка — подпись
        ScreenModel.Paragraph keyDsc = screen.getPageText().get(1);
        assertEquals("На полу ключ.", keyDsc.getText());
        assertEquals(ScreenModel.TextRun.Kind.ObjectLink, keyDsc.getRuns().get(1).getKind());
        assertEquals(key.getId(), keyDsc.getRuns().get(1).getTarget());
        assertEquals("ключ", screen.getSceneObjects().get(0).getLabel());
        // Взять ключ
        assertTrue(session.clickObject(key.getId()));
        assertEquals("Ключ", session.screen().getInventory().get(0).getLabel());
        // Щелчок по предмету — выбор (снимок тот же, вид — нет), затем по двери — применить
        long before = session.revision();
        assertTrue(session.clickItem(key.getId()));
        assertEquals(key.getId(), session.selectedItem());
        assertTrue(session.revision() > before);
        assertTrue(session.clickObject(door.getId()));
        assertEquals("", session.selectedItem());
        assertEquals("Дверь открылась.", reaction(session.screen()));
        // Повторный щелчок по выбранному предмету — осмотр (game.inv)
        session.clickItem(key.getId());
        session.clickItem(key.getId());
        assertEquals("", session.selectedItem());
        assertEquals(1, session.screen().getReactionText().size());
    }

    @Test
    public void menuItemActsOnFirstClick() {
        // Предмет без применений (menu экспорта: menu = act) — срабатывает с первого щелчка, как
        // кнопки боя «Фронтира»; предмет с применением (obj) — по-прежнему выбирается; при
        // выбранном предмете щелчок по menu INSTEAD игнорирует (game.c), выбор остаётся
        TestBook book = new TestBook();
        PageImpl room = book.page("Комната");
        ObjImpl button = book.obj(room, "button", "Тут {кнопка}.", "Кнопка", true);
        button.setActTexts(book.text("Щёлк."));
        ObjImpl key = book.obj(room, "key", "На полу {ключ}.", "Ключ", true);
        ObjImpl door = book.obj(room, "door", "Здесь {дверь}.", "Дверь", false);
        key.addLink(new LinkImpl(key, door.getId()));
        ObjImpl lever = book.obj(room, "lever", "Тут {рычаг}.", "Рычаг", true);
        PlayerSession session = book.session();
        assertTrue(session.clickObject(button.getId()));
        assertTrue(session.clickObject(key.getId()));
        PlayerEngine engine = session.engine();
        assertTrue(engine.isMenuItem(button.getId()));
        assertFalse(engine.isMenuItem(key.getId()));
        assertTrue(session.clickItem(button.getId()));
        assertEquals("", session.selectedItem());
        assertEquals("Щёлк.", reaction(session.screen()));
        // выбранный предмет и щелчок по menu (в инвентаре и на сцене) — ничего, выбор остаётся
        assertTrue(engine.isMenuItem(lever.getId()));
        assertTrue(session.clickItem(key.getId()));
        assertEquals(key.getId(), session.selectedItem());
        long before = engine.screen().getRevision();
        assertFalse(session.clickItem(button.getId()));
        assertFalse(session.clickObject(lever.getId()));
        assertEquals(key.getId(), session.selectedItem());
        assertEquals(before, engine.screen().getRevision());
        // а щелчок по obj на сцене — применение
        assertTrue(session.clickObject(door.getId()));
        assertEquals("", session.selectedItem());
    }

    @Test
    public void realTimeTimer() {
        TestBook book = new TestBook();
        PageImpl start = book.page("Ждём: $t$");
        PageImpl done = book.page("Дождались");
        start.setTimerVarId(book.variable(Variable.Type.TIMER, Variable.DataType.NUMBER, "t", null).getId());
        LinkImpl auto = book.link(start, done, "auto");
        auto.setAuto(true);
        book.constraint(auto, "t >= 5");
        PlayerSession session = book.session();
        // В реальном времени ожидание не проматывается: таймер — 200 мс на тик
        assertEquals(start.getId(), session.screen().getPageId());
        assertTrue(session.screen().isTimerActive());
        assertEquals(200, session.screen().getTimerIntervalMs());
        session.advance(199);
        assertEquals(0L, session.engine().getVariable("t").asInt());
        session.advance(1 + 200 * 3);
        assertEquals(4L, session.engine().getVariable("t").asInt());
        assertEquals("Ждём: 4", session.screen().getPageText().get(0).getText());
        session.advance(200);
        assertEquals(done.getId(), session.screen().getPageId());
        assertFalse(session.screen().isTimerActive());
    }

    @Test
    public void voicesInFieldOrder() {
        TestBook book = new TestBook();
        PageImpl start = book.page("Первая фраза.\nВторая фраза.");
        start.setSoundFileName("v1.ogg;v2.ogg");
        start.setSoundSFX(true);
        PageImpl next = book.page("Дальше");
        book.link(start, next, "Дальше");
        PlayerSession session = book.session();
        assertEquals(List.of("v1.ogg", "v2.ogg"), session.voices());
        assertEquals(2, session.screen().portions().size());
        assertEquals("v1.ogg", session.nextVoice());
        assertEquals("v2.ogg", session.nextVoice());
        assertNull(session.nextVoice());
        // уход со страницы — очередь прочь
        session.choose(0);
        assertEquals(List.of(), session.voices());
    }

    @Test
    public void inaccessibleLinksShowAltText() {
        TestBook book = new TestBook();
        PageImpl start = book.page("Старт");
        PageImpl closed = book.page("Закрыто");
        LinkImpl link = book.link(start, closed, "Войти");
        link.setAltTexts(book.text("Дверь заперта"));
        book.constraint(link, "false");
        PlayerSession session = book.session();
        ScreenModel.Choice choice = session.screen().getChoices().get(0);
        assertFalse(choice.isEnabled());
        assertEquals(-1, choice.getIndex());
        assertEquals("Дверь заперта", choice.getAltText());
        assertFalse(session.choose(choice.getIndex()));
    }

    @Test
    public void screenJsonHasModelFields() {
        TestBook book = new TestBook();
        PageImpl start = book.page("Старт");
        book.mod(start, Modification.Type.WINGEOM, null, "\"10,10,500,500\"");
        PlayerSession session = book.session();
        com.fasterxml.jackson.databind.node.ObjectNode json = session.screen().toJson();
        assertEquals("Standard", json.get("layout").asText());
        assertEquals(start.getId(), json.get("pageId").asText());
        assertEquals("Старт", json.get("pageText").get(0).get("runs").get(0).get("text").asText());
        assertTrue(json.get("style").has("textGeometry"));
    }
}
