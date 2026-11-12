package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Контейнеры в экспортах QSP, URQ, jsIQ2 и AXMA — как в INSTEAD и Java-плеере
 * (PlayerContext.containerAdd): ADD кладёт объект в контейнер, не убирая из прежнего места, —
 * объект может лежать в нескольких контейнерах сразу (бой «Фронтира»: handDataObj в обоих
 * держателях карт). Содержимое — по порядку (сначала content книги, затем — по мере добавления).
 * Инвентарь показывает и содержимое предметов-контейнеров, сразу за ними
 * (PlayerEngine.inventoryObjects: панель боя с картами), а щелчок по предмету-меню — его
 * действие, без выбора для применения (PlayerEngine.clickInventory)
 */
public class ContainersExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private ObjImpl m_panel;
    private ObjImpl m_card;
    private ObjImpl m_holder1;
    private ObjImpl m_holder2;
    private ObjImpl m_hand;

    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        PageImpl room = b.page("Бой.");
        PageImpl out = b.page("Конец.");
        b.link(room, out, "Сдаться");
        m_holder1 = b.obj(room, "holder1", "", "Держатель 1", false);
        m_holder2 = b.obj(room, "holder2", "", "Держатель 2", false);
        m_hand = b.obj(room, "hand", "{Рука}", "Рука", false);
        m_panel = b.obj(room, "panel", "{Панель}", "Панель", true);
        m_card = b.obj(room, "card", "{Карта}", "Карта", true);
        // карта лежит в панели
        room.removeContainedObjId(m_card.getId());
        m_card.setContainerId(m_panel.getId());
        m_panel.addContainedObjId(m_card.getId());
        mod(b, room, Modification.Type.ADD, "holder1", "hand");
        mod(b, room, Modification.Type.ADD, "holder2", "hand");
        mod(b, room, Modification.Type.ADDINV, null, "panel");
        return b.nlb;
    }

    /** Модификация страницы (переменные — с целью: страница;модификация, как в книге) */
    private static void mod(TestBook b, PageImpl page, Modification.Type type, String varName, String expr) {
        com.nlbhub.nlb.domain.ModificationImpl mod = new com.nlbhub.nlb.domain.ModificationImpl(page);
        mod.setType(type.name());
        if (varName != null) {
            com.nlbhub.nlb.domain.VariableImpl var = b.variable(com.nlbhub.nlb.api.Variable.Type.VAR, com.nlbhub.nlb.api.Variable.DataType.AUTO, varName, null);
            var.setTarget(page.getId() + ";" + mod.getId());
            mod.setVarId(var.getId());
        }
        com.nlbhub.nlb.domain.VariableImpl ex = b.variable(com.nlbhub.nlb.api.Variable.Type.EXPRESSION, com.nlbhub.nlb.api.Variable.DataType.AUTO, null, expr);
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
        assertTrue("ADD — в оба держателя: " + qsp, qsp.contains("GOSUB 'nlb_add', '" + m_hand.getId() + "', '" + m_holder1.getId() + "', 1\n")
                && qsp.contains("GOSUB 'nlb_add', '" + m_hand.getId() + "', '" + m_holder2.getId() + "', 1\n"));
        String add = qsp.substring(qsp.indexOf("#nlb_add\n"), qsp.indexOf("#nlb_del\n"));
        assertFalse("ADD не убирает из прежнего места: " + add, add.contains("nlb_lrmv") || add.contains("nlb_del"));
        assertTrue("содержимое панели — список: " + qsp, qsp.contains("$nlb_li['<" + m_panel.getId() + ",0'] = '" + m_card.getId() + "'\n"));
        assertTrue("инвентарь — с содержимым предметов: " + qsp,
                qsp.contains("#nlb_inv_item\n") && qsp.contains("GOSUB 'nlb_inv_item', $nlb_li['<' + $ARGS[0] + ',' + $STR(nlb_i)], ARGS[1] + 1"));
        assertTrue("карта — предмет-меню: " + qsp, qsp.contains("nlb_menu['" + m_card.getId() + "'] = 1\n"));
        assertTrue("щелчок по меню — действие: " + qsp, qsp.contains("IF nlb_menu[$FUNC('nlb_p', $nlb_o)] = 1: GOSUB 'nlb_o_' + $FUNC('nlb_p', $nlb_o) + '_acta', $nlb_o"));
    }

    @Test
    public void urq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.qst");
        book().exportToURQTextFile(file);
        String urq = read(file, Charset.forName("windows-1251"));
        assertTrue("ADD — в оба держателя (ключи oN): " + urq, urq.contains("nlb_pd=\"o1\"\nnlb_pu=1\nproc nlb_add\n") && urq.contains("nlb_pd=\"o2\"\nnlb_pu=1\nproc nlb_add\n"));
        String add = urq.substring(urq.indexOf(":nlb_add\n"), urq.indexOf(":nlb_clone\n"));
        assertFalse("ADD не убирает из прежнего места: " + add, add.contains("nlb_lrmv"));
        assertTrue("содержимое панели — список: " + urq, urq.contains("nlb_at_5_o4=1\nnlb_li_co4_0=5\nnlb_ln_co4=1\n"));
        assertTrue("инвентарь — с содержимым предметов: " + urq, urq.contains(":nlb_inv_in\n") && urq.contains("invkill\n"));
        assertTrue("карта — предмет-меню: " + urq, urq.contains("nlb_menu_5=1\n"));
        assertFalse("меток до сборки не осталось: " + urq, urq.contains("\u0001"));
    }

    @Test
    public void jsiq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.xml");
        book().exportToJSIQFile(file);
        String xml = read(file, StandardCharsets.UTF_8);
        assertTrue(xml, xml.contains("NLB.add(vars, '" + m_hand.getId() + "', '" + m_holder1.getId() + "', true); ")
                && xml.contains("NLB.add(vars, '" + m_hand.getId() + "', '" + m_holder2.getId() + "', true); "));
        assertTrue("содержимое панели: " + xml, xml.contains("'" + m_panel.getId() + "': ['" + m_card.getId() + "']"));
        assertTrue("инвентарь — с содержимым предметов: " + xml, xml.contains("for (var i = 0; i < l.length; i++) { walk(l[i], depth + 1); }"));
        String card = xml.substring(xml.indexOf("N.objs['" + m_card.getId() + "']"));
        assertTrue("карта — предмет-меню: " + xml, card.substring(0, card.indexOf("};")).contains("menu: 1,"));
    }

    @Test
    public void asm() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book().exportToASMFile(file);
        String sm = read(file, StandardCharsets.UTF_8);
        assertTrue("ADD — в оба держателя: " + sm, sm.contains("<<set $nlb_po = 3>><<set $nlb_pd = \"" + m_holder1.getId() + "\">><<display 'nlb_add'>>")
                && sm.contains("<<set $nlb_po = 3>><<set $nlb_pd = \"" + m_holder2.getId() + "\">><<display 'nlb_add'>>"));
        assertTrue("содержимое панели: " + sm, sm.contains("<<set $nlb_cs[\"" + m_panel.getId() + "\"] = [5]>>"));
        assertTrue("инвентарь — с содержимым предметов: " + sm, sm.contains(":: nlb_o_4_inv[::]") && sm.contains(":: nlb_inv_in[::]"));
        assertTrue("карта — предмет-меню: " + sm, sm.contains("<<set $nlb_menu[5] = 1>>"));
    }
}
