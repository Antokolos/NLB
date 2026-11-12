package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.AbstractModifyingItem;
import com.nlbhub.nlb.domain.ModificationImpl;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
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
 * Достижения в экспортах QSP, URQ, jsIQ2, AXMA и ChoiceScript — как PlayerContext.achieve
 * (nlb.lua setAchievement): каждое место ACHIEVE засчитывается один раз, ACHIEVED — число мест,
 * ACHMAX — порог (без него — первое же место), набран порог — объявление один раз
 */
public class AchievementsExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private PageImpl m_shop;
    private PageImpl m_hall;

    private static void mod(TestBook b, AbstractModifyingItem item, Modification.Type type, String varName,
                            Variable.DataType dataType, String expr) {
        ModificationImpl mod = new ModificationImpl(item);
        mod.setType(type.name());
        String target = item.getId() + ";" + mod.getId();
        if (varName != null) {
            VariableImpl var = b.variable(Variable.Type.VAR, dataType, varName, null);
            var.setTarget(target);
            mod.setVarId(var.getId());
        }
        if (expr != null) {
            VariableImpl ex = b.variable(Variable.Type.EXPRESSION, dataType, null, expr);
            ex.setTarget(target);
            mod.setExprId(ex.getId());
        }
        item.addModification(mod);
    }

    /** Лавка: ACHMAX COLLECTOR 2, ACHIEVE COLLECTOR; зал: ACHIEVE COLLECTOR, ACHIEVED got */
    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        m_shop = b.page("Лавка.");
        m_hall = b.page("Зал.");
        b.link(m_shop, m_hall, "В зал");
        mod(b, m_shop, Modification.Type.ACHMAX, "COLLECTOR", Variable.DataType.NUMBER, "2");
        mod(b, m_shop, Modification.Type.ACHIEVE, null, Variable.DataType.STRING, "COLLECTOR");
        mod(b, m_hall, Modification.Type.ACHIEVE, null, Variable.DataType.STRING, "COLLECTOR");
        mod(b, m_hall, Modification.Type.ACHIEVED, "got", Variable.DataType.NUMBER, "COLLECTOR");
        return b.nlb;
    }

    @Test
    public void qsp() throws Exception {
        File file = new File(m_temp.getRoot(), "book.txt");
        book().exportToQSPTextFile(file);
        String qsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE).replace("\r\n", "\n");
        assertTrue(qsp, qsp.contains("nlb_achmax['COLLECTOR'] = 2\n"));
        assertTrue(qsp, qsp.contains("GOSUB 'nlb_achieve', 'COLLECTOR', '"));
        assertTrue(qsp, qsp.contains("got = nlb_ach['COLLECTOR']\n"));
        assertTrue("место — один раз, порог, объявление: " + qsp, qsp.contains("#nlb_achieve\n"
                + "IF nlb_achid[$ARGS[0] + '|' + $ARGS[1]] = 0:\n"));
        assertTrue(qsp, qsp.contains("    'Получено достижение: ' + $ARGS[0]\n"));
    }

    @Test
    public void urq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.qst");
        book().exportToURQTextFile(file);
        String urq = new String(Files.readAllBytes(file.toPath()), Charset.forName("windows-1251")).replace("\r\n", "\n");
        assertTrue(urq, urq.contains("nlb_achmax_COLLECTOR=2\n"));
        assertTrue("два места — номера 1 и 2: " + urq, urq.contains("nlb_an=\"COLLECTOR\"\nnlb_ak=1\nproc nlb_achieve\n")
                && urq.contains("nlb_an=\"COLLECTOR\"\nnlb_ak=2\nproc nlb_achieve\n"));
        assertTrue(urq, urq.contains("got = nlb_ach_COLLECTOR\n"));
        assertTrue(urq, urq.contains(":nlb_achieve\nif nlb_achid_#%nlb_an$_#nlb_ak$=1 then goto nlb_achieve_got\n"));
    }

    @Test
    public void jsiq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.xml");
        book().exportToJSIQFile(file);
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(xml, xml.contains("NLB.achMax('COLLECTOR', 2); "));
        assertTrue(xml, xml.contains("NLB.achieve(vars, 'COLLECTOR', '"));
        assertTrue(xml, xml.contains("vars.got = NLB.ach('COLLECTOR'); "));
        assertTrue("между прохождениями — localStorage: " + xml, xml.contains("window.localStorage.setItem(N.achKey"));
    }

    @Test
    public void asm() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book().exportToASMFile(file);
        String sm = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(sm, sm.contains("<<set $nlb_achs[\"max:COLLECTOR\"] = 2>>"));
        assertTrue(sm, sm.contains("<<set $nlb_an = \"COLLECTOR\">><<set $nlb_ak = \""));
        assertTrue(sm, sm.contains("<<set $got = $nlb_achs[\"n:COLLECTOR\"] ? $nlb_achs[\"n:COLLECTOR\"] : 0>>"));
        String achieve = sm.substring(sm.indexOf(":: nlb_achieve["), sm.indexOf(":: nlb_reply["));
        assertFalse("«>», «<» и «||» в <<set>> AXMA ломают: " + achieve, achieve.replace("<<", "").replace(">>", "")
                .replace("<br>", "").matches("(?s).*([<>]|\\|\\|).*"));
    }

    @Test
    public void choiceScript() throws Exception {
        File file = new File(m_temp.getRoot(), "startup.txt");
        book().exportToChoiceScript(file);
        String cs = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue(cs, cs.contains("*create nlb_ach_collector 0\n"));
        assertTrue("достижение ChoiceScript: " + cs, cs.contains("*achievement collector visible 100 COLLECTOR\n    COLLECTOR\n"));
        assertTrue(cs, cs.contains("*if (not(nlb_achgot_collector)) and ((nlb_achmax_collector = 0) or (nlb_ach_collector >= nlb_achmax_collector))\n"
                + "    *set nlb_achgot_collector true\n    *achieve collector\n"));
        assertTrue(cs, cs.contains("*set got nlb_ach_collector\n"));
        assertTrue(cs, cs.contains("*label nlb_ach_perfect\n"));
    }
}
