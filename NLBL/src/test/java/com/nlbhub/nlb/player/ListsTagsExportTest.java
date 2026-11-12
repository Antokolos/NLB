package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.AbstractModifyingItem;
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
 * Списки, CLEAR, OBJS, HAVE и теги в экспортах QSP, jsIQ2, AXMA и URQ — как в PlayerEngine
 * (nlb.lua): PUSH/POP — начало списка, INJECT/EJECT — конец; элемент — объект, страница или
 * значение; снятый объект — переменная-ссылка (в QSP — строковая), которую можно передать вместо
 * объекта (TAG, ADD…); TAG без объекта — свой тег; GETTAG — тег или тег по умолчанию
 */
public class ListsTagsExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private PageImpl m_shop;
    private ObjImpl m_apple;
    private ObjImpl m_box;

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

    /**
     * Лавка: яблоко и ящик. Модификации: PUSH listA apple; SIZE n listA; POP got listA (got —
     * ссылка на яблоко); TAG got red; TAG (без объекта — сама лавка) shop; GETTAG t apple;
     * OBJS lb box; CLEAR box; HAVE h apple; SPUSH listS
     */
    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        m_shop = b.page("Лавка.");
        PageImpl out = b.page("Улица.");
        b.link(m_shop, out, "Выйти");
        m_apple = b.obj(m_shop, "apple", "{Яблоко}.", "Яблоко", true);
        m_box = b.obj(m_shop, "box", "{Ящик}.", "Ящик", false);
        Variable.DataType auto = Variable.DataType.AUTO;
        mod(b, m_shop, Modification.Type.PUSH, "listA", auto, "apple");
        mod(b, m_shop, Modification.Type.SIZE, "n", Variable.DataType.NUMBER, "listA");
        mod(b, m_shop, Modification.Type.POP, "got", auto, "listA");
        mod(b, m_shop, Modification.Type.TAG, "got", auto, "red");
        mod(b, m_shop, Modification.Type.TAG, null, auto, "shop");
        mod(b, m_shop, Modification.Type.GETTAG, "t", Variable.DataType.STRING, "apple");
        mod(b, m_shop, Modification.Type.OBJS, "lb", auto, "box");
        mod(b, m_shop, Modification.Type.CLEAR, null, auto, "box");
        mod(b, m_shop, Modification.Type.HAVE, "h", Variable.DataType.BOOLEAN, "apple");
        mod(b, m_shop, Modification.Type.SPUSH, null, auto, "listS");
        return b.nlb;
    }

    @Test
    public void qsp() throws Exception {
        File file = new File(m_temp.getRoot(), "book.txt");
        book().exportToQSPTextFile(file);
        String qsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE).replace("\r\n", "\n");
        String apple = m_apple.getId();
        assertTrue(qsp, qsp.contains("GOSUB 'nlb_lpush', 'listA', '" + apple + "'\n"));
        assertTrue(qsp, qsp.contains("n = nlb_ln['listA']\n"));
        assertTrue("снятый объект — строковая ссылка: " + qsp, qsp.contains("$got = $FUNC('nlb_lpop', 'listA')\n"));
        assertTrue("тег по ссылке: " + qsp, qsp.contains("$nlb_tag[$got] = 'red'\n"));
        assertTrue("свой тег: " + qsp, qsp.contains("$nlb_tag['" + m_shop.getId() + "'] = 'shop'\n"));
        assertTrue(qsp, qsp.contains("$t = $nlb_tag['" + apple + "']\n"));
        assertTrue(qsp, qsp.contains("GOSUB 'nlb_lobjs', 'lb', '" + m_box.getId() + "'\n"));
        assertTrue(qsp, qsp.contains("GOSUB 'nlb_clear', '" + m_box.getId() + "'\n"));
        assertTrue(qsp, qsp.contains("h = 0\nIF nlb_at['" + apple + "' + '|nlb_inv'] = 1: h = 1\n"));
        assertTrue("SPUSH — сама страница: " + qsp, qsp.contains("GOSUB 'nlb_lpush', 'listS', '" + m_shop.getId() + "'\n"));
        assertTrue("служебные локации списков: " + qsp, qsp.contains("#nlb_lpop\n") && qsp.contains("#nlb_lshuffle\n"));
    }

    @Test
    public void jsiq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.xml");
        book().exportToJSIQFile(file);
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String apple = m_apple.getId();
        assertTrue(xml, xml.contains("NLB.push(vars, 'listA', '" + apple + "'); "));
        assertTrue(xml, xml.contains("vars.n = NLB.size(vars, 'listA'); "));
        assertTrue(xml, xml.contains("vars.got = NLB.pop(vars, 'listA'); "));
        assertTrue(xml, xml.contains("NLB.setTag(vars, String(vars.got), 'red'); "));
        assertTrue(xml, xml.contains("NLB.setTag(vars, '" + m_shop.getId() + "', 'shop'); "));
        assertTrue(xml, xml.contains("vars.t = NLB.tag(vars, '" + apple + "'); "));
        assertTrue(xml, xml.contains("NLB.objsTo(vars, 'lb', '" + m_box.getId() + "'); "));
        assertTrue(xml, xml.contains("NLB.clear(vars, '" + m_box.getId() + "'); "));
        assertTrue(xml, xml.contains("vars.h = NLB.has(vars, '" + apple + "'); "));
    }

    @Test
    public void asm() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book().exportToASMFile(file);
        String sm = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue("элемент — номер объекта: " + sm, sm.contains("<<set $nlb_lists[\"listA\"] = $nlb_lists[\"listA\"] ? "
                + "$nlb_lists[\"listA\"] : []>><<set $nlb_lists[\"listA\"].unshift(1)>>\n"));
        assertTrue(sm, sm.contains("<<set $n = $nlb_lists[\"listA\"] ? $nlb_lists[\"listA\"].length : 0>>\n"));
        assertTrue(sm, sm.contains("<<set $got = ($nlb_lists[\"listA\"] && $nlb_lists[\"listA\"].length) ? $nlb_lists[\"listA\"].shift() : false>>"));
        assertTrue(sm, sm.contains("<<set $nlb_tags[$got] = \"red\">>\n"));
        assertTrue(sm, sm.contains("<<set $nlb_tags[\"" + m_shop.getId() + "\"] = \"shop\">>\n"));
        assertTrue(sm, sm.contains("<<set $nlb_c = \"" + m_box.getId() + "\">><<display 'nlb_clear'>>\n"));
        assertTrue(sm, sm.contains("<<set $h = (($nlb_cs[\"nlb_inv\"] && $nlb_cs[\"nlb_inv\"].indexOf(1) != -1) ? 1 : 0)>>\n"));
        assertTrue("<<loop>> — по массиву $nlb_a, не по индексу в скобках: " + sm,
                sm.contains(":: nlb_addall[::]") && !sm.contains("<<loop $nlb_lists["));
    }

    @Test
    public void urq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.qst");
        book().exportToURQTextFile(file);
        String urq = new String(Files.readAllBytes(file.toPath()), Charset.forName("windows-1251")).replace("\r\n", "\n");
        assertTrue(urq, urq.contains("nlb_lname=\"listA\"\nnlb_lval = 1\nproc nlb_lpush\n"));
        assertTrue(urq, urq.contains("n = nlb_ln_listA\n"));
        assertTrue(urq, urq.contains("nlb_lname=\"listA\"\nproc nlb_lpop\ngot = nlb_lres\n"));
        assertTrue("тег по ссылке — имя с подстановкой: " + urq, urq.contains("nlb_tag_#%got$=\"red\"\n"));
        assertTrue("контейнер — ключ oN: " + urq, java.util.regex.Pattern.compile("nlb_c=\"o\\d+\"\nproc nlb_clear\n").matcher(urq).find());
        assertTrue(urq, urq.contains("h=0\nif nlb_at_1_inv=1 then h=1\n"));
        assertTrue("CLEAR — по списку контейнера: " + urq, urq.contains("if nlb_m>=nlb_ln_#%nlb_lc$ then goto nlb_clear_end\n"));
    }
}
