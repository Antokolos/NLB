package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.PropertyManager;
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Разметка INSTEAD в экспорте QSP — как в Java-плеере (PlayerEngine.displayText, sceneLabel) и
 * экспорте jsIQ2: '^' — перевод строки (&lt;br&gt;, USEHTML), в подписи ссылки — пробел, хвостовой
 * '^' пустой строки не даёт; пустая метка {} — ссылкой становится первая непустая строка рядом,
 * без неё — подпись объекта
 */
public class QspMarkupExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private ObjImpl m_flag;
    private ObjImpl m_card;
    private ObjImpl m_status;

    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        PageImpl room = b.page("Первая строка^Вторая строка^");
        PageImpl out = b.page("Улица.");
        b.link(room, out, "^Дальше");
        m_flag = b.obj(room, "flag", "{}^Показывать эту страницу", "", false);
        m_card = b.obj(room, "card", "{}", "Карта", false);
        m_status = b.obj(room, "status", "Статус^", "Слава: 0^Боеспособность: 45", true);
        return b.nlb;
    }

    @Test
    public void markup() throws Exception {
        File file = new File(m_temp.getRoot(), "book.txt");
        book().exportToQSPTextFile(file);
        String qsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE).replace("\r\n", "\n");
        assertTrue("'^' — перевод строки, хвостовой — без пустой строки: " + qsp, qsp.contains("'Первая строка<br>Вторая строка'\n"));
        assertTrue("подпись ссылки — без '^': " + qsp, qsp.contains("ACT 'Дальше':"));
        assertTrue("{} — ссылкой строка рядом: " + qsp, qsp.contains("'<br><a href=\"EXEC:GOSUB ''nlb_act'', ''' + $nlb_me + '''\">Показывать эту страницу</a>'\n"));
        assertTrue("{} без строк рядом — подпись объекта: " + qsp, qsp.contains("'<a href=\"EXEC:GOSUB ''nlb_act'', ''' + $nlb_me + '''\">Карта</a>'\n"));
        assertTrue("подпись предмета — строками: " + qsp, qsp.contains("$RESULT = 'Слава: 0<br>Боеспособность: 45'\n"));
        assertTrue(qsp, qsp.contains("'Статус'\n"));
        assertFalse("'^' не остаётся в тексте: " + qsp, qsp.replaceAll("(?m)^!.*$", "").contains("^"));
    }
}
