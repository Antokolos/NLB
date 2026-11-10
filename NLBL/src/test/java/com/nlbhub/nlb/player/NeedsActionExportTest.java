package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.domain.LinkImpl;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.PageImpl;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Автоссылка с флагом «needs action» (has_action() в nlb.lua) в текстовых экспортах: срабатывает,
 * только когда игрок сделал needs-action-count действий (переходов по обычным ссылкам), и обнуляет
 * счётчик. Раньше флаг не учитывался, и автоссылка срабатывала сразу — в «Слуге Ра» это давало
 * вечный цикл «Холл — боль — Холл». Книга: «Шаг» ведёт на ту же страницу, автоссылка — дальше.
 */
public class NeedsActionExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    private String m_stepId;
    private String m_autoId;

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        PageImpl room = b.page("Комната.");
        PageImpl out = b.page("Наконец-то.");
        m_stepId = b.link(room, room, "Шаг").getId();
        LinkImpl auto = b.link(room, out, "Авто");
        auto.setAuto(true);
        auto.setNeedsAction(true);
        m_autoId = auto.getId();
        return b.nlb;
    }

    private String export(String name, Charset charset, Exporter exporter) throws Exception {
        File file = new File(m_temp.getRoot(), name);
        exporter.export(book(), file);
        return new String(Files.readAllBytes(file.toPath()), charset).replace("\r\n", "\n");
    }

    private interface Exporter {
        void export(NonLinearBookImpl nlb, File file) throws Exception;
    }

    @Test
    public void urq() throws Exception {
        String urq = export("book.qst", Charset.forName("windows-1251"), NonLinearBookImpl::exportToURQTextFile);
        assertTrue(urq, urq.contains("if (nlb_action_count>=3) then goto " + m_autoId + "\n"));
        assertTrue("шаг — действие: " + urq, urq.contains(":" + m_stepId + "\nnlb_action_count=nlb_action_count+1\n"));
        assertTrue("автоссылка обнуляет счётчик: " + urq, urq.contains(":" + m_autoId + "\nnlb_action_count=0\n"));
    }

    @Test
    public void choiceScript() throws Exception {
        String cs = export("startup.txt", StandardCharsets.UTF_8, NonLinearBookImpl::exportToChoiceScript);
        assertTrue(cs, cs.contains("*create nlb_action_count 0\n"));
        assertTrue(cs, cs.contains("*if (nlb_action_count >= 3)\n    *goto " + m_autoId + "\n"));
        assertTrue(cs, cs.contains("*label " + m_stepId + "\n*set nlb_action_count +1\n"));
        assertTrue(cs, cs.contains("*label " + m_autoId + "\n*set nlb_action_count 0\n"));
    }

    @Test
    public void qsp() throws Exception {
        String qsp = export("book.txt", StandardCharsets.UTF_16LE, NonLinearBookImpl::exportToQSPTextFile);
        assertTrue(qsp, qsp.contains("ACT 'Шаг':\n    $nlb_sel = ''\n    UNSEL\n    nlb_action_count = nlb_action_count + 1\n"));
        assertTrue("автоссылка сбрасывает счётчики, как COUNTRST: " + qsp,
                qsp.contains("IF (nlb_action_count >= 3):\nnlb_action_count = 0\nnlb_cntr_inv = nlb_cnt_inv\n"));
    }

    @Test
    public void asm() throws Exception {
        String sm = export("book.sm", StandardCharsets.UTF_8, NonLinearBookImpl::exportToASMFile);
        assertTrue("счётчик объявлен в Start: " + sm, sm.contains(":: Start[::]0-0-0\n<<set $nlb_action_count = 0>>\n"));
        assertTrue(sm, sm.contains("<<if ($nlb_action_count >= 3)>>\n[[Авто|" + m_autoId + "]]\n<<else>>\n"));
        assertTrue(sm, sm.contains("<<set $nlb_action_count = $nlb_action_count + 1>>"));
    }

    @Test
    public void jsiq2() throws Exception {
        String xml = export("book.xml", StandardCharsets.UTF_8, NonLinearBookImpl::exportToJSIQFile);
        // действия считают те же счётчики, что COUNTGET; автоссылка ждёт их с последнего сброса
        assertTrue(xml, xml.contains("if ((NLB.countGet(vars, 0) >= 3)) { NLB.countReset(vars); "));
        assertTrue("шаг — действие: " + xml, xml.contains("NLB.walk(vars, true); "));
        assertFalse("go() в окружении jsIQ нет: " + xml, xml.contains("go("));
    }
}
