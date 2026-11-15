package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.ModificationImpl;
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

import static org.junit.Assert.assertTrue;

/**
 * Клоны и USE в экспортах, как в Java-плеере (PlayerContext.cloneInstance, ModificationInterpreter):
 * CLONE создаёт экземпляр (тег копируется), ID — его прототип, описание, действие и применения —
 * у прототипа, «s» в коде объекта — экземпляр; USE применяет объект к объекту из переменной или к
 * каждому элементу списка (бой «Фронтира»: карты в руке — клоны, enemyAttack применяется к
 * сыгранной карте). В AXMA ссылка вычисляет код при щелчке, а не при показе, поэтому у клона k —
 * заранее заведённый абзац nlb_lnk_k со ссылкой на свой номер (до 2000, как метки nlb_act_N в URQ)
 */
public class ClonesExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private ObjImpl m_card;
    private ObjImpl m_attack;

    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        PageImpl room = b.page("Бой.");
        PageImpl out = b.page("Конец.");
        b.link(room, out, "Сдаться");
        m_card = b.obj(room, "card", "{Карта}", "Карта", false);
        m_attack = b.obj(room, "attack", "", "Атака", false);
        mod(b, room, Modification.Type.CLONE, "copy", "card");
        mod(b, room, Modification.Type.ID, "proto", "copy");
        mod(b, room, Modification.Type.PUSH, "hand", "copy");
        mod(b, room, Modification.Type.USE, "attack", "copy");
        mod(b, room, Modification.Type.USE, "attack", "hand");
        return b.nlb;
    }

    /** Модификация страницы (переменные — с целью: страница;модификация, как в книге) */
    private static void mod(TestBook b, PageImpl page, Modification.Type type, String varName, String expr) {
        ModificationImpl mod = new ModificationImpl(page);
        mod.setType(type.name());
        VariableImpl var = b.variable(Variable.Type.VAR, Variable.DataType.AUTO, varName, null);
        var.setTarget(page.getId() + ";" + mod.getId());
        mod.setVarId(var.getId());
        VariableImpl ex = b.variable(Variable.Type.EXPRESSION, Variable.DataType.AUTO, null, expr);
        ex.setTarget(page.getId() + ";" + mod.getId());
        mod.setExprId(ex.getId());
        page.addModification(mod);
    }

    private String read(File file, Charset charset) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), charset).replace("\r\n", "\n");
    }

    @Test
    public void qsp() throws Exception {
        File file = new File(m_temp.getRoot(), "book.txt");
        book().exportToQSPTextFile(file);
        String qsp = read(file, StandardCharsets.UTF_16LE);
        assertTrue("CLONE: " + qsp, qsp.contains("$copy = $FUNC('nlb_clone', '" + m_card.getId() + "')\n"));
        assertTrue("ID — прототип: " + qsp, qsp.contains("$proto = $FUNC('nlb_p', $copy)\n"));
        assertTrue("USE к переменной или списку: " + qsp, qsp.contains("GOSUB 'nlb_useon', '" + m_attack.getId() + "', $copy, 'copy'\n")
                && qsp.contains("GOSUB 'nlb_useon', '" + m_attack.getId() + "', $hand, 'hand'\n"));
        assertTrue("клон — «прототип#N», тег копируется: " + qsp, qsp.contains("$RESULT = $nlb_q + '#' + $STR(nlb_cc[$nlb_q])")
                && qsp.contains("$nlb_tag[$RESULT] = $nlb_tag[$ARGS[0]]"));
        assertTrue("щелчок — через прототип, с экземпляром: " + qsp, qsp.contains("GOSUB 'nlb_o_' + $FUNC('nlb_p', $ARGS[0]) + '_act', $ARGS[0]"));
        assertTrue("ссылка — на экземпляр: " + qsp, qsp.contains("EXEC:GOSUB ''nlb_act'', ''' + $nlb_me + '''"));
    }

    @Test
    public void urq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.qst");
        book().exportToURQTextFile(file);
        String urq = read(file, Charset.forName("windows-1251"));
        assertTrue("CLONE: " + urq, urq.contains("nlb_cs = 1\nproc nlb_clone\ncopy = nlb_cres\n"));
        assertTrue("ID — прототип: " + urq, urq.contains("proto = nlb_proto_#%copy$\n"));
        assertTrue("USE: " + urq, urq.contains("nlb_us=2\nnlb_ut = copy\nnlb_ul=\"copy\"\nproc nlb_useon\n")
                && urq.contains("nlb_us=2\nnlb_ut = hand\nnlb_ul=\"hand\"\nproc nlb_useon\n"));
        assertTrue("клоны — номера от 100001, метки щелчка: " + urq, urq.contains(":nlb_act_100001\nnlb_o=100001\ngoto nlb_act\n"));
        assertTrue("ссылка — на экземпляр: " + urq, urq.contains("[[Карта|nlb_act_#nlb_me$]]"));
    }

    @Test
    public void jsiq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.xml");
        book().exportToJSIQFile(file);
        String xml = read(file, StandardCharsets.UTF_8);
        assertTrue("CLONE: " + xml, xml.contains("vars.copy = NLB.clone(vars, '" + m_card.getId() + "'); "));
        assertTrue("ID — прототип: " + xml, xml.contains("vars.proto = NLB.p(String(vars.copy)); "));
        assertTrue("USE: " + xml, xml.contains("NLB.useAny(vars, '" + m_attack.getId() + "', vars.copy, 'copy'); ")
                && xml.contains("NLB.useAny(vars, '" + m_attack.getId() + "', vars.hand, 'hand'); "));
        assertTrue("описание — с экземпляром: " + xml, xml.contains("text: function (vars, me) { me = me || '" + m_card.getId() + "'; "));
    }

    @Test
    public void asm() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book().exportToASMFile(file);
        String sm = read(file, StandardCharsets.UTF_8);
        assertTrue("USE к списку или переменной: " + sm, sm.contains("<<if $nlb_lists.hasOwnProperty(\"hand\")>>")
                && sm.contains("<<else>><<set $nlb_t = $hand>><<set $nlb_o = 2>><<set $nlb_printed = 0>><<display 'nlb_o_2_use'>><<endif>>"));
        assertTrue("CLONE: " + sm, sm.contains("<<set $nlb_cq = 1>><<display 'nlb_clone'>><<set $copy = $nlb_cres>>"));
        assertTrue("ID — прототип: " + sm, sm.contains("<<set $nlb_iv = $copy>><<set $proto = ($nlb_proto[$nlb_iv] ? $nlb_proto[$nlb_iv] : $nlb_iv)>>"));
        assertTrue("клон — номер от 100001, прототип, тег копируется: " + sm, sm.contains("<<set $nlb_cres = 100000 + $nlb_ccount>>"
                + "<<set $nlb_proto[$nlb_cres] = ($nlb_proto[$nlb_cq] ? $nlb_proto[$nlb_cq] : $nlb_cq)>><<set $nlb_tags[$nlb_cres] = $nlb_tags[$nlb_cq]>>"));
        assertTrue("обход контейнера — абзацы прототипа, экземпляр — в $nlb_ia: " + sm,
                sm.contains("<<set $nlb_p = \"nlb_o_\" + ($nlb_proto[$nlb_iv] ? $nlb_proto[$nlb_iv] : $nlb_iv) + \"_dsc\">><<set $nlb_si[$nlb_sd] = $nlb_k2 + 1>><<set $nlb_ia = $nlb_iv>><<display $nlb_p>>"));
        assertTrue("абзац объекта — от имени экземпляра: " + sm, sm.contains(":: nlb_o_1_dsc[::]10-10-0\n<<set $nlb_ms = $nlb_ms + 1>>"
                + "<<set $nlb_mv[$nlb_ms] = $nlb_me>><<set $nlb_me = $nlb_ia ? $nlb_ia : 1>><<set $nlb_ia = 0>>\n"));
        assertTrue("ссылка клона — его абзац nlb_lnk_k: " + sm, sm.contains("<<if $nlb_me eq 1>>[[Карта|nlb_act {$nlb_o = 1}]]<<else>>")
                && sm.contains(":: nlb_lnk_1[::]10-10-0\n[[$nlb_d|nlb_lclick {$nlb_o3 = 100001}]]\n")
                && sm.contains(":: nlb_lnk_2000[::]10-10-0\n") && !sm.contains(":: nlb_lnk_2001["));
    }

    /** Книга без клонов (нет CLONE, ADD без unique, ADDINV) — без абзацев nlb_lnk_k */
    @Test
    public void asmWithoutClones() throws Exception {
        TestBook b = new TestBook();
        PageImpl room = b.page("Комната.");
        b.link(room, b.page("Улица."), "Выйти");
        b.obj(room, "stone", "{Камень}", "Камень", false);
        File file = new File(m_temp.getRoot(), "plain.sm");
        b.nlb.exportToASMFile(file);
        String sm = read(file, StandardCharsets.UTF_8);
        assertTrue(sm, sm.contains(":: nlb_o_1_dsc[") && !sm.contains(":: nlb_lnk_"));
    }
}
