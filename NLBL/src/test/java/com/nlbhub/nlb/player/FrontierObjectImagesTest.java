package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.config.Export;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Картинки объектов в тексте темы STANDARD (img() в {..} экспорта STEAD) и порядок содержимого
 * контейнеров (файл content) — на «Фронтире»: «Правила игры» (пример карт посреди текста) и экран
 * карточного боя (как в INSTEAD: карты противника, под ними анимированный фон, под ним карты
 * игрока). Книга ищется там же, где для эталонных прохождений (NLB_TEST_BOOKS); нет — пропуск.
 */
public class FrontierObjectImagesTest {
    private static final File CONFIG = new File("../NLBW/cfg/config.xml");

    private PlayerEngine m_engine;

    @Before
    public void setUp() throws Exception {
        File bookDir = new File(ReferenceDataTest.booksDir(), "Frontier");
        Assume.assumeTrue("Нет книги " + bookDir, new File(bookDir, "startpoint").isFile());
        NonLinearBookImpl book = ConsolePlayer.loadBook(bookDir);
        assertNotNull(book);
        Export export = PropertyManager.readSettings(CONFIG).getDefaultConfig().getExport();
        PlayerEngine.Settings settings = PlayerEngine.Settings.fromExport(export, book.getLanguage());
        settings.exportMode = PlayerEngine.ExportMode.STANDARD;
        settings.deterministic = true;
        m_engine = new PlayerEngine(book, settings);
        m_engine.setWarnings(w -> { });
        m_engine.start();
        choose("OK");
        act("Пропустить заставку");
        choose("Далее");
        choose("Далее");
    }

    @Test
    public void rulesShowCardsExampleInsideText() {
        choose("Правила игры");
        List<ScreenModel.Paragraph> objects = m_engine.screen().getObjectsText();
        int example = paragraphWithImage(objects, "cardsexample.png");
        assertTrue("пример карт — картинкой в описании объекта", example >= 0);
        ScreenModel.TextRun run = objects.get(example).getRuns().get(0);
        assertEquals(ScreenModel.TextRun.Kind.ObjectLink, run.getKind());
        assertEquals("метка {} — ссылка-картинка без текста", "", run.getText());
        // текст правил после примера — абзацами («^» и переводы строк), а не одной строкой
        assertTrue(objects.size() > example + 5);
        int officer = paragraphWithImage(objects, "securityofficer.png");
        assertTrue(officer > example);
        assertEquals(" Офицер по безопасности", objects.get(officer).getRuns().get(1).getText());
    }

    @Test
    public void battleShowsEnemyCardsBackgroundAndPlayerCards() {
        choose("Начать игру");
        for (int i = 0; i < 4; i++) {
            m_engine.actOnObject(m_engine.view().getSceneObjects().get(0).getInstanceId());
        }
        choose("Далее");
        choose("Таргос");
        m_engine.advanceTime(13000);  // гиперпереход
        choose("Далее");
        choose("Принять бой");
        choose("Принять бой");
        choose("Начать игру");
        m_engine.advanceTime(1000);

        List<ScreenModel.Paragraph> objects = m_engine.screen().getObjectsText();
        int background = paragraphWithImage(objects, "btla%d.jpg");
        assertTrue("фон боя — в тексте сцены", background > 0);
        ScreenModel.ImageRef bg = objects.get(background).getRuns().get(0).getImage();
        assertEquals(999, bg.getFrameCount());
        assertTrue(bg.isLooped());
        assertTrue(bg.getCurrentFrame() >= 1);
        // заглушка карты лежит в обеих руках: над фоном (противник) и под ним (игрок)
        assertTrue(paragraphWithImage(objects.subList(0, background), "card_placeholder.png") >= 0);
        assertTrue(paragraphWithImage(objects.subList(background + 1, objects.size()), "card_placeholder.png") >= 0);

        // «Концентрация»: обе стороны открывают карту
        String concentration = null;
        for (PlayerEngine.ObjectView item : m_engine.view().getInventory()) {
            if (item.getDisp().contains("Концентрация")) {
                concentration = item.getInstanceId();
            }
        }
        assertNotNull(concentration);
        m_engine.clickInventory(concentration);
        m_engine.advanceTime(8000);
        objects = m_engine.screen().getObjectsText();
        background = paragraphWithImage(objects, "btla%d.jpg");
        assertTrue(background > 0);
        List<String> enemy = images(objects.subList(0, background));
        List<String> player = images(objects.subList(background + 1, objects.size()));
        assertTrue("карта противника — картинка с тегом ENM (…e.png): " + enemy,
                enemy.stream().anyMatch(f -> f.endsWith("e.png") && !f.contains("placeholder")));
        assertTrue("карта игрока — картинка с тегом PLR: " + player,
                player.stream().anyMatch(f -> !f.endsWith("e.png") && !f.contains("placeholder")));
        assertFalse(player.stream().anyMatch(f -> f.endsWith("e.png") && !f.contains("placeholder")));
    }

    private void choose(String text) {
        List<PlayerEngine.Choice> choices = m_engine.view().getChoices();
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).getText().contains(text)) {
                assertTrue(m_engine.choose(i));
                return;
            }
        }
        throw new AssertionError("нет варианта «" + text + "» на " + m_engine.screen().getRoomTitle());
    }

    private void act(String label) {
        for (PlayerEngine.ObjectView o : m_engine.view().getSceneObjects()) {
            if (o.getDisp().contains(label)) {
                assertTrue(m_engine.actOnObject(o.getInstanceId()));
                return;
            }
        }
        throw new AssertionError("нет объекта «" + label + "»");
    }

    private static int paragraphWithImage(List<ScreenModel.Paragraph> paragraphs, String file) {
        for (int i = 0; i < paragraphs.size(); i++) {
            for (ScreenModel.TextRun run : paragraphs.get(i).getRuns()) {
                ScreenModel.ImageRef image = run.getImage();
                if (image.getFile().endsWith(file) || image.getFramePattern().endsWith(file)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> images(List<ScreenModel.Paragraph> paragraphs) {
        List<String> result = new ArrayList<>();
        for (ScreenModel.Paragraph p : paragraphs) {
            for (ScreenModel.TextRun run : p.getRuns()) {
                if (!run.getImage().isEmpty()) {
                    result.add(run.getImage().getFile());
                }
            }
        }
        return result;
    }
}
