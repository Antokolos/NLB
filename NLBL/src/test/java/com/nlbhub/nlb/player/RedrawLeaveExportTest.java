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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

/**
 * Действие с объектом, после которого ход уводит со страницы автоссылкой («Слуга Ра»: щелчок по
 * панно, автоссылка импланта — в «Больно вам, больно»). В плеере действие страницу заново не
 * выводит: на экране ответ, тексты хода и новая страница. QSP и URQ показывают страницу заново до
 * хода — если ход уводит с неё, повторный показ убирается (nlb_unshow): остаются ответ и тексты
 * хода, без текста и картинки прежней комнаты
 */
public class RedrawLeaveExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        PageImpl room = b.page("Комната.");
        PageImpl pain = b.page("Больно вам, больно!");
        ObjImpl panno = b.obj(room, "panno", "На стене {панно}.", "панно", false);
        panno.setActTexts(b.text("На панно — солнце."));
        LinkImpl implant = b.link(room, pain, "");
        implant.setAuto(true);
        implant.setNeedsAction(true);
        b.link(pain, room, "Далее");
        return b.nlb;
    }

    private String read(File file, Charset charset) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), charset).replace("\r\n", "\n");
    }

    @Test
    public void qsp() throws Exception {
        File file = new File(m_temp.getRoot(), "book.txt");
        book().exportToQSPTextFile(file);
        String qsp = read(file, StandardCharsets.UTF_16LE);
        assertTrue("повторный показ после действия помечается: " + qsp,
                qsp.contains("GOSUB $CURLOC + '_show'\nnlb_showlen = LEN($MAINTXT)\nIF $nlb_reply <> '': *P $nlb_reply\nnlb_redrawn = 1\nGOSUB $CURLOC + '_turn'\nnlb_redrawn = 0\n"));
        assertTrue("убирается — остаётся то, что выведено после него: " + qsp,
                qsp.contains("#nlb_unshow\nnlb_redrawn = 0\n$nlb_said = MID($MAINTXT, nlb_showlen + 1)\n*CLR\n*P $nlb_said\n"));
        assertTrue("автоссылка: перед XGOTO: " + qsp,
                qsp.contains("    IF nlb_redrawn = 1: GOSUB 'nlb_unshow'\n    XGOTO '"));
    }

    @Test
    public void urq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.qst");
        book().exportToURQTextFile(file);
        String urq = read(file, Charset.forName("windows-1251"));
        assertTrue("повторный показ после действия помечается, ответ запоминается: " + urq,
                urq.contains("nlb_said=nlb_reply\nnlb_reply=\"\"\nnlb_redrawn=1\n"));
        assertTrue("тексты хода — к ответу: " + urq, urq.contains("if nlb_redrawn=1 then nlb_said=nlb_said+nlb_reply\n"));
        assertTrue("убирается — остаются ответ и тексты хода: " + urq,
                urq.contains(":nlb_unshow\nnlb_redrawn=0\ncls\nif nlb_said<>\"\" then pln #%nlb_said$\n"));
        assertTrue("автоссылка: в начале её метки: " + urq, urq.contains("if nlb_redrawn=1 then proc nlb_unshow\n"));
    }
}
