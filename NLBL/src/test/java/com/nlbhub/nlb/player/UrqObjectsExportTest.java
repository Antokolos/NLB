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

    /** Зал: ключ (переносимый, «На столе {ключ}»), дверь; ключ применяется к двери; «Счёт: $score$» */
    private String export() throws Exception {
        TestBook b = new TestBook();
        m_hall = b.page("Зал & кухня.");
        PageImpl out = b.page("Улица.");
        b.link(m_hall, out, "Выйти");
        ObjImpl key = b.obj(m_hall, "key", "На столе {ключ}.", "Ключ", true);
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
        assertTrue("места объектов: " + urq, urq.contains("nlb_in_1=\"" + m_hall.getId() + "\"\nnlb_in_2=\"" + m_hall.getId() + "\"\n"));
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
}
