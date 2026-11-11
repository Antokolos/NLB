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
import java.nio.charset.Charset;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Объекты в экспорте URQ (UrqW): объект N — место nlb_in_N, ссылка [[текст|nlb_act_N]], предмет
 * nlbo_N с подписью display_use_nlbo_N; ответ на действие — после повторного показа страницы
 */
public class UrqObjectsExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private PageImpl m_hall;
    private ObjImpl m_key;

    /** Зал: ключ (переносимый, «На столе {ключ}»), дверь; ключ применяется к двери; «Счёт: $score$» */
    private String export() throws Exception {
        TestBook b = new TestBook();
        m_hall = b.page("Зал & кухня.");
        PageImpl out = b.page("Улица.");
        b.link(m_hall, out, "Выйти");
        ObjImpl key = b.obj(m_hall, "key", "На столе {ключ}.", "Ключ", true);
        m_key = key;
        key.setActTexts(b.text("Вы взяли ключ."));
        ObjImpl door = b.obj(m_hall, "door", "Впереди {дверь}.", "Дверь", false);
        LinkImpl use = new LinkImpl(key, door.getId());
        use.setTexts(b.text("Дверь открылась."));
        key.addLink(use);
        File file = new File(m_temp.getRoot(), "book.qst");
        b.nlb.exportToURQTextFile(file);
        return new String(Files.readAllBytes(file.toPath()), Charset.forName("windows-1251")).replace("\r\n", "\n");
    }

    @Test
    public void startAndScene() throws Exception {
        String urq = export();
        assertTrue(urq, urq.contains(":nlb_start\n"));
        assertTrue("места объектов: " + urq, urq.contains("nlb_in_1=\"" + m_hall.getId() + "\"\nnlb_id_1=\"" + m_key.getId() + "\"\nnlb_isobj_1=1\nnlb_in_2=\"" + m_hall.getId() + "\"\n"));
        assertTrue("& — не разделитель команд: " + urq, urq.contains("pln Зал ##38$ кухня."));
        assertTrue("страница — вход, показ, ход: " + urq, urq.contains("nlb_page = \"" + m_hall.getId() + "\"\nproc "
                + m_hall.getId() + "_show\ngoto " + m_hall.getId() + "_turn\n:" + m_hall.getId() + "_show\n"));
        assertTrue("объект — ссылка: " + urq, urq.contains(":nlb_o_1_dsc\npln На столе [[ключ|nlb_act_1]].\n"));
        assertTrue("подпись предмета: " + urq, urq.contains("display_use_nlbo_1=\"Ключ\"\n"));
    }

    @Test
    public void takeAndUse() throws Exception {
        String urq = export();
        assertTrue(urq, urq.contains(":nlb_o_1_acttext\nnlb_reply=nlb_reply+\"Вы взяли ключ.<br>\"\nend\n"));
        assertTrue("переносимый — в инвентарь: " + urq, urq.contains("nlb_po=1\nnlb_pd=\"nlb_inv\"\nproc nlb_place\nend\n:nlb_o_1_use\n"));
        assertTrue("применение к двери: " + urq, urq.contains("if nlb_t<>2 then goto nlb_o_1_use_2\nnlb_reply=nlb_reply+\"Дверь открылась.<br>\"\nnlb_printed=1\n"));
        assertTrue("щелчок по предмету: " + urq, urq.contains(":use_nlbo_1\nnlb_o2=1\ngoto nlb_invclick\n"));
        assertFalse("дверь не переносимая: " + urq, urq.contains("nlb_po=2\nnlb_pd=\"nlb_inv\""));
        assertFalse("меток до сборки не осталось: " + urq, urq.contains("\u0001") || urq.contains("\u0003"));
    }

    /**
     * Callback-объект (часы): в конце хода (:id_turn), если игрок с прошлого хода что-то сделал, —
     * его действие без «взять» (_acta), ответ выводится сразу; присваивания — с пробелами вокруг
     * «=»: «x=(a-b)» UrqW принимает за вызов функции и не присваивает
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
        File file = new File(m_temp.getRoot(), "cb.qst");
        b.nlb.exportToURQTextFile(file);
        String urq = new String(Files.readAllBytes(file.toPath()), Charset.forName("windows-1251")).replace("\r\n", "\n");
        assertTrue(urq, urq.contains(":nlb_turn\nnlb_diff = (nlb_cnt_inv-nlb_cntr_inv)+(nlb_cnt_act-nlb_cntr_act)"
                + "+(nlb_cnt_use-nlb_cntr_use)+(nlb_cnt_walk-nlb_cntr_walk)\n"
                + "if nlb_diff<=nlb_cnt_prev then goto nlb_turn_end\nnlb_cnt_prev=nlb_diff\nproc nlb_o_1_acta\n"
                + ":nlb_turn_end\nnlb_cnt_prev=nlb_diff\nend\n"));
        assertTrue("конец хода страницы: " + urq, urq.contains(":" + hall.getId() + "_turn\nproc nlb_turn\n"
                + "if nlb_reply<>\"\" then pln #%nlb_reply$\nnlb_reply=\"\"\n"));
        assertTrue("взять — только по щелчку: " + urq, urq.contains(":nlb_o_1_act\nproc nlb_o_1_acta\nnlb_po=1\n"));
    }
}
