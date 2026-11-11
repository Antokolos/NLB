package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.domain.LinkImpl;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.ObjImpl;
import com.nlbhub.nlb.domain.PageImpl;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Объекты в экспорте QSP: место объекта — $nlb_in[id], объект сцены — ссылка EXEC в тексте
 * страницы, переносимый — в инвентарь QSP, применение предмета к объекту — nlb_use
 */
public class QspObjectsExportTest {
    /** Конец локации: txt2gam ищет его по префиксу «--» */
    private static final String END = "---------------------------------";

    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private ObjImpl m_key;
    private ObjImpl m_door;
    private PageImpl m_hall;

    /** Зал: ключ (переносимый, «На столе {ключ}»), дверь; ключ применяется к двери */
    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        m_hall = b.page("Зал.");
        PageImpl out = b.page("Улица.");
        b.link(m_hall, out, "Выйти");
        m_key = b.obj(m_hall, "key", "На столе {ключ} Пети.", "Ключ Пети", true);
        m_key.setActTexts(b.text("Вы взяли ключ."));
        m_door = b.obj(m_hall, "door", "Впереди {дверь}.", "Дверь", false);
        LinkImpl use = new LinkImpl(m_key, m_door.getId());
        use.setTexts(b.text("Дверь открылась."));
        m_key.addLink(use);
        return b.nlb;
    }

    private String export() throws Exception {
        File file = new File(m_temp.getRoot(), "book.txt");
        book().exportToQSPTextFile(file);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE).replace("\r\n", "\n");
    }

    @Test
    public void startAndPlaces() throws Exception {
        String qsp = export();
        assertTrue("первая локация — начало игры: " + qsp, qsp.startsWith("! Generated with Non-Linear Book Builder"));
        assertTrue(qsp, qsp.contains("#nlb_start\nUSEHTML = 1\n$ONOBJSEL = 'nlb_onobjsel'\n"));
        assertTrue(qsp, qsp.contains("$nlb_in['" + m_key.getId() + "'] = '" + m_hall.getId() + "'\n"));
        assertTrue("подпись в инвентаре вычисляется при каждом ходе: " + qsp,
                qsp.contains("#nlb_o_" + m_key.getId() + "_disp\n$RESULT = 'Ключ Пети'\n" + END + "\n"));
        assertTrue("затем — стартовая страница: " + qsp, qsp.contains("GOTO '" + m_hall.getId() + "'\n" + END + "\n#nlb_show_in\n"));
    }

    @Test
    public void sceneObjectIsExecLink() throws Exception {
        String qsp = export();
        assertTrue(qsp, qsp.contains("#nlb_o_" + m_key.getId() + "_text\n'На столе <a href=\"EXEC:GOSUB ''nlb_act'', ''"
                + m_key.getId() + "''\">ключ</a> Пети.'\n" + END + "\n"));
        assertTrue(qsp, qsp.contains("#nlb_o_" + m_key.getId() + "_dsc\nGOSUB 'nlb_o_" + m_key.getId()
                + "_text'\nGOSUB 'nlb_show_in', '" + m_key.getId() + "'\n" + END + "\n"));
        assertTrue("страница показывает свои объекты: " + qsp,
                qsp.contains("GOSUB 'nlb_show_in', '" + m_hall.getId() + "'\n" + END + "\n#" + m_hall.getId() + "_turn\nCLA\nGOSUB 'nlb_turn'\nGOSUB 'nlb_inv_sync'\n"));
    }

    @Test
    public void takeAndUse() throws Exception {
        String qsp = export();
        assertTrue(qsp, qsp.contains("#nlb_o_" + m_key.getId() + "_acttext\n'Вы взяли ключ.'\n" + END + "\n"));
        assertTrue("переносимый — в инвентарь: " + qsp,
                qsp.contains("GOSUB 'nlb_place', '" + m_key.getId() + "', 'nlb_inv'\n" + END + "\n#nlb_o_" + m_key.getId() + "_use\n"));
        assertTrue("применение к двери: " + qsp, qsp.contains("IF $ARGS[0] = '" + m_door.getId() + "':\n'Дверь открылась.'\nnlb_printed = 1\nEND\n"));
        assertTrue("без ответа — game.nouse: " + qsp, qsp.contains("#nlb_o_" + m_door.getId() + "_nouse\n'"));
        assertFalse("дверь не переносимая: " + qsp,
                qsp.contains("GOSUB 'nlb_place', '" + m_door.getId() + "', 'nlb_inv'"));
    }

    /**
     * Callback-объект (часы): в конце хода, если игрок с прошлого хода что-то сделал, — его
     * действие без «взять» (_acta), как update_action_count в INSTEAD; nlb_cnt_prev — до вызова
     */
    @Test
    public void callbackObjects() throws Exception {
        TestBook b = new TestBook();
        PageImpl hall = b.page("Зал.");
        PageImpl out = b.page("Улица.");
        b.link(hall, out, "Выйти");
        ObjImpl clock = b.obj(hall, "clock", "{Часы}.", "Часы", true);
        clock.setActTexts(b.text("Тик."));
        clock.setCallback(true);
        File file = new File(m_temp.getRoot(), "cb.txt");
        b.nlb.exportToQSPTextFile(file);
        String qsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE).replace("\r\n", "\n");
        String sum = "(nlb_cnt_inv - nlb_cntr_inv) + (nlb_cnt_act - nlb_cntr_act) + (nlb_cnt_use - nlb_cntr_use) + (nlb_cnt_walk - nlb_cntr_walk)";
        assertTrue(qsp, qsp.contains("#nlb_turn\nnlb_diff = " + sum + "\nIF nlb_diff > nlb_cnt_prev:\n"
                + "    nlb_cnt_prev = nlb_diff\n    GOSUB 'nlb_o_" + clock.getId() + "_acta'\nEND\nnlb_cnt_prev = nlb_diff\n" + END + "\n"));
        assertTrue("взять — только по щелчку: " + qsp, qsp.contains("#nlb_o_" + clock.getId() + "_act\nGOSUB 'nlb_o_"
                + clock.getId() + "_acta'\n"));
    }

    @Test
    public void textIsEscaped() throws Exception {
        TestBook b = new TestBook();
        b.page("It's <b> & 'quoted'");
        File file = new File(m_temp.getRoot(), "esc.txt");
        b.nlb.exportToQSPTextFile(file);
        String qsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE).replace("\r\n", "\n");
        assertTrue(qsp, qsp.contains("'It''s &lt;b&gt; &amp; ''quoted'''"));
    }

    /**
     * Графический объект (VN): картинок нет, поэтому если у него есть действие, ссылкой EXEC
     * становится его текст, а надпись-картинка без действия — просто текст
     */
    @Test
    public void graphicalObjects() throws Exception {
        TestBook b = new TestBook();
        PageImpl hall = b.page("Навыки.");
        ObjImpl skill = b.obj(hall, "skill", "Грамотность – читать надписи.", "Грамотность", true);
        skill.setGraphical(true);
        ObjImpl label = b.obj(hall, "label", "", "ВЫБЕРИТЕ НАВЫК", false);
        label.setGraphical(true);
        File file = new File(m_temp.getRoot(), "gfx.txt");
        b.nlb.exportToQSPTextFile(file);
        String qsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE).replace("\r\n", "\n");
        assertTrue(qsp, qsp.contains("#nlb_o_" + skill.getId() + "_text\n'<a href=\"EXEC:GOSUB ''nlb_act'', ''"
                + skill.getId() + "''\">Грамотность – читать надписи.</a>'\n" + END + "\n"));
        assertTrue(qsp, qsp.contains("#nlb_o_" + label.getId() + "_text\n'ВЫБЕРИТЕ НАВЫК'\n" + END + "\n"));
    }

    /**
     * txt2gam (QSPFoundation) разбирает текст по концам локаций «--…» и не видит их внутри строк —
     * в том числе строк, начатых апострофом в комментарии «!»: раньше конец был «-» (вся игра —
     * одна локация), а апостроф в названии страницы проглатывал следующие локации
     */
    @Test
    public void txt2gamStructure() throws Exception {
        TestBook b = new TestBook();
        PageImpl room = b.page("Комната.");
        room.setCaptions(b.text("Beobras' room"));
        PageImpl next = b.page("Дальше.");
        b.link(room, next, "Go to Beobras' {room}");
        File file = new File(m_temp.getRoot(), "t2g.txt");
        b.nlb.exportToQSPTextFile(file);
        String qsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE).replace("\r\n", "\n");
        assertFalse("локация не кончается одиночным «-»: " + qsp, qsp.contains("\n-\n"));
        int starts = 0, ends = 0;
        for (String line : qsp.split("\n")) {
            if (line.startsWith("#")) {
                starts++;
            }
            if (line.startsWith("--")) {
                ends++;
            }
            if (line.startsWith("!")) {
                assertFalse("кавычки и скобки в комментарии: " + line, line.matches(".*['\"{}].*"));
            }
        }
        assertEquals("у каждой локации — конец: " + qsp, starts, ends);
    }
}
