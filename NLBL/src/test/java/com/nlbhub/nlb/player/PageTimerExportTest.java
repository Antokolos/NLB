package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.LinkImpl;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.ObjImpl;
import com.nlbhub.nlb.domain.PageImpl;
import com.nlbhub.nlb.domain.VariableImpl;
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
 * Таймер страницы (tvarid) в пошаговых экспортах — как в PlayerEngine: при входе на страницу
 * (после её модификаций) переменная таймера обнуляется, ход игрока на странице (действие с
 * объектом, повторный показ) — тик +1, переход по ссылке — не тик. Если выбирать нечего (ни одна
 * обычная ссылка не доступна), а автоссылки ждут таймера, время проматывается: тик за тиком,
 * пока не сработает автоссылка (autoWaitTicks: 100000, на странице с объектами — 50)
 */
public class PageTimerExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private PageImpl m_intro;
    private PageImpl m_room;
    private PageImpl m_ai;

    private static String timer(TestBook b, PageImpl page, String name) {
        VariableImpl v = b.variable(Variable.Type.TIMER, Variable.DataType.NUMBER, name, null);
        v.setTarget(page.getId());
        return v.getId();
    }

    /**
     * Заставка: только автоссылка «t >= 10». Комната: колокольчик (щелчок — тик), автоссылка
     * «t >= 3» и обычная ссылка «Уйти» (время само не идёт). Ход ИИ: обычная ссылка с условием
     * «f == 1» и автоссылка «t2 >= 5» (время идёт, пока ссылка недоступна)
     */
    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        m_intro = b.page("Заставка.");
        m_room = b.page("Комната, t=$t$.");
        PageImpl waited = b.page("Дождались.");
        m_ai = b.page("Ход ИИ.");
        PageImpl end = b.page("Конец.");
        PageImpl away = b.page("Ушли.");
        m_intro.setTimerVarId(timer(b, m_intro, "t"));
        m_room.setTimerVarId(timer(b, m_room, "t"));
        m_ai.setTimerVarId(timer(b, m_ai, "t2"));
        LinkImpl l1 = b.link(m_intro, m_room, "");
        l1.setAuto(true);
        b.constraint(l1, "t >= 10");
        ObjImpl bell = b.obj(m_room, "bell", "Висит {колокольчик}.", "Колокольчик", false);
        bell.setActTexts(b.text("Динь."));
        LinkImpl l2 = b.link(m_room, waited, "");
        l2.setAuto(true);
        b.constraint(l2, "t >= 3");
        b.link(m_room, away, "Уйти");
        b.link(waited, m_ai, "Дальше");
        LinkImpl hidden = b.link(m_ai, away, "Скрытая");
        b.constraint(hidden, "f == 1");
        LinkImpl l3 = b.link(m_ai, end, "");
        l3.setAuto(true);
        b.constraint(l3, "t2 >= 5");
        // f = 0 — модификацией заставки (тип переменной экспорт выводит из её присваиваний)
        com.nlbhub.nlb.domain.ModificationImpl mod = new com.nlbhub.nlb.domain.ModificationImpl(m_intro);
        mod.setType("ASSIGN");
        VariableImpl f = b.variable(Variable.Type.VAR, Variable.DataType.NUMBER, "f", null);
        f.setTarget(m_intro.getId() + ";" + mod.getId());
        mod.setVarId(f.getId());
        VariableImpl zero = b.variable(Variable.Type.EXPRESSION, Variable.DataType.NUMBER, null, "0");
        zero.setTarget(m_intro.getId() + ";" + mod.getId());
        mod.setExprId(zero.getId());
        m_intro.addModification(mod);
        return b.nlb;
    }

    /** Текст локации/метки от строки start до следующей строки, начинающейся с endPrefix */
    private static String section(String text, String start, String endPrefix) {
        int from = text.indexOf(start);
        assertTrue("нет " + start + ": " + text, from >= 0);
        int to = text.indexOf("\n" + endPrefix, from + start.length());
        return text.substring(from, to < 0 ? text.length() : to);
    }

    @Test
    public void qsp() throws Exception {
        File file = new File(m_temp.getRoot(), "book.txt");
        book().exportToQSPTextFile(file);
        String qsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE).replace("\r\n", "\n");
        assertTrue("при входе — обнуление: " + qsp, qsp.contains("t = 0\nnlb_tick = 0\nGOSUB '" + m_room.getId() + "_show'\n"));
        assertTrue("ход игрока — тик до показа: " + qsp, qsp.contains("#" + m_room.getId()
                + "_show\nKILLVAR 'nlb_seen'\nIF nlb_tick = 1:\n    nlb_tick = 0\n    t = t + 1\nEND\n"));
        assertTrue("повторный показ — ход игрока: " + qsp, qsp.contains("#nlb_refresh\nnlb_tick = 1\n"));
        assertTrue("заставка проматывается: " + qsp, section(qsp, "#" + m_intro.getId() + "_turn", "--")
                .contains("nlb_wait = 0\nLOOP WHILE nlb_wait < 100000:\n    nlb_wait = nlb_wait + 1\n    t = t + 1\n"));
        assertFalse("в комнате есть «Уйти» — время само не идёт: " + qsp,
                section(qsp, "#" + m_room.getId() + "_turn", "--").contains("LOOP"));
        assertTrue("ход ИИ — пока ссылка недоступна (в QSP — NO): " + qsp, section(qsp, "#" + m_ai.getId() + "_turn", "--")
                .contains("IF NO (((f = 1))):\nnlb_wait = 0\nLOOP WHILE nlb_wait < 100000:\n"));
    }

    @Test
    public void urq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.qst");
        book().exportToURQTextFile(file);
        String urq = new String(Files.readAllBytes(file.toPath()), Charset.forName("windows-1251")).replace("\r\n", "\n");
        assertTrue(urq, urq.contains("t=0\nnlb_tick=0\nnlb_page = \"" + m_room.getId() + "\"\n"));
        assertTrue(urq, urq.contains(":" + m_room.getId() + "_show\nnlb_gen=nlb_gen+1\nif nlb_tick=1 then t=t+1\nnlb_tick=0\n"));
        assertTrue(urq, urq.contains(":nlb_redraw\nnlb_tick=1\n"));
        String intro = m_intro.getId();
        assertTrue("заставка проматывается: " + urq, urq.contains(":" + intro + "_wait\nnlb_wait=0\n:" + intro + "_wait_loop\n"
                + "if nlb_wait>=100000 then goto " + intro + "_wait_end\nnlb_wait=nlb_wait+1\nt=t+1\n"));
        assertFalse(urq, urq.contains(":" + m_room.getId() + "_wait"));
        assertTrue(urq, urq.contains("if not (((f = 1))) then goto " + m_ai.getId() + "_wait\n"));
    }

    @Test
    public void jsiq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.xml");
        book().exportToJSIQFile(file);
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue("повторный показ — тик, вход — обнуление: " + xml, xml.contains("if (vars.nlb_redraw) { vars.nlb_redraw = false; "
                + "vars.t = vars.t + 1; } else { vars.t = 0; }"));
        assertTrue("автоссылки сообщают о переходе: " + xml, xml.contains("return true; } return false;"));
        assertTrue("заставка проматывается: " + xml, xml.contains("if (autos()) { return; } { for (var nlb_w = 0; nlb_w < 100000; nlb_w++) "
                + "{ vars.t = vars.t + 1; if (autos()) { return; } } }"));
        assertTrue("ход ИИ — пока ссылка недоступна: " + xml, xml.contains("if (!(((vars.f == 1)))) { for (var nlb_w = 0; nlb_w < 100000;"));
    }

    @Test
    public void asm() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book().exportToASMFile(file);
        String sm = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue("повторный показ — тик: " + sm, sm.contains("<<set $nlb_page = \"" + m_room.getId()
                + "\">><<if $nlb_redraw eq 1>><<set $nlb_redraw = 0>><<set $t = $t + 1>><<else>>\n"));
        assertTrue("вход — обнуление: " + sm, sm.contains("<<set $t = 0>>\n<<endif>>"));
        assertTrue("заставка проматывается: " + sm,
                sm.contains("<<loop 100000>><<if (($t >= 10))>><<break>><<endif>><<set $t = $t + 1>><<endloop>>\n"));
        assertTrue("ход ИИ — пока ссылка недоступна: " + sm, sm.contains("<<if !((($f == 1)))>><<loop 100000>>"));
    }

    @Test
    public void choiceScript() throws Exception {
        File file = new File(m_temp.getRoot(), "startup.txt");
        book().exportToChoiceScript(file);
        String cs = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue("обнуление — до текста: " + cs, cs.contains("*label " + m_room.getId() + "\n*set t 0\nКомната, t=${t}."));
        String intro = m_intro.getId();
        assertTrue("заставка проматывается: " + cs, cs.contains("*label " + intro + "_wait\n*if (t >= 10)\n"));
        assertTrue(cs, cs.contains("*if t < 10000\n    *set t +1\n    *goto " + intro + "_wait\n"));
        assertTrue("ход ИИ — пока ссылка недоступна: " + cs, cs.contains("*if not((f = 1))\n    *if t2 < 10000\n"));
    }
}
