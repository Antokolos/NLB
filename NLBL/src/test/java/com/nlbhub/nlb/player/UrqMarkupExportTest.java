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
import java.nio.charset.Charset;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Описания объектов в экспорте URQ — как в Java-плеере (PlayerEngine.displayText, sceneLabel) и
 * экспортах QSP и jsIQ2: абзацы описания и ответа — абзацами (&lt;br&gt;), не в одну строку;
 * пустая метка {} — ссылкой первая непустая строка рядом («Показывать эту страницу» во «Фронтире»),
 * без неё — подпись: disp, иначе первая строка текста действия («Сила карты 5»), иначе имя; у
 * объекта без действия — ни ссылки, ни подписи, только картинка (фон боя «Фронтира»)
 */
public class UrqMarkupExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private String export() throws Exception {
        TestBook b = new TestBook();
        PageImpl room = b.page("Бой.");
        PageImpl out = b.page("Конец.");
        b.link(room, out, "Сдаться");
        ObjImpl rules = b.obj(room, "rules", "Первый абзац.^^Второй абзац.", "", false);
        rules.setActTexts(b.text("Ответ один.^^Ответ два."));
        ObjImpl flag = b.obj(room, "flag", "{}^Показывать эту страницу", "", false);
        flag.setActTexts(b.text("Флажок снят."));
        ObjImpl card = b.obj(room, "card", "{}", "", false);
        card.setActTexts(b.text("Сила карты 5^Подробнее"));
        b.obj(room, "background", "{}", "", false);
        NonLinearBookImpl nlb = b.nlb;
        File file = new File(m_temp.getRoot(), "book.qst");
        nlb.exportToURQTextFile(file);
        return new String(Files.readAllBytes(file.toPath()), Charset.forName("windows-1251")).replace("\r\n", "\n");
    }

    @Test
    public void markup() throws Exception {
        String urq = export();
        assertTrue("абзацы описания — абзацами: " + urq, urq.contains("pln Первый абзац.<br><br>Второй абзац.\n"));
        assertTrue("абзацы ответа — абзацами: " + urq, urq.contains("nlb_reply=nlb_reply+\"Ответ один.<br><br>Ответ два.<br>\"\n"));
        assertTrue("{} — ссылкой строка рядом: " + urq, urq.contains("<br>[[Показывать эту страницу|nlb_act_#nlb_me$]]"));
        assertFalse("{} — не подписью-именем: " + urq, urq.contains("[[flag|"));
        assertTrue("{} без строк рядом — первая строка текста действия: " + urq, urq.contains("pln [[Сила карты 5|nlb_act_#nlb_me$]]\n"));
        assertFalse("{} без действия — без ссылки и подписи: " + urq, urq.contains("[[background|"));
    }
}
