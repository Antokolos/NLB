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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Объекты в экспорте AXMA Story Maker: место объекта N — $nlb_in_N, объект сцены — ссылка
 * [[текст|nlb_act {$nlb_o = N}]] в абзаце nlb_o_N_dsc, переносимый — в инвентарь ($nlb_invo),
 * применение предмета к объекту — абзац nlb_o_N_use
 */
public class AsmObjectsExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private ObjImpl m_key;
    private ObjImpl m_door;
    private PageImpl m_hall;

    /** Зал: ключ (переносимый, «На столе {ключ}»), дверь; ключ применяется к двери */
    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        m_hall = b.page("Зал.");
        PageImpl out = b.page("Улица.");
        b.link(m_hall, out, "Выйти");
        m_key = b.obj(m_hall, "key", "На столе {ключ} Пети.", "Ключ Пети", true);
        m_key.setActTexts(b.text("Вы взяли ключ."));
        m_door = b.obj(m_hall, "door", "Впереди {дверь}.", "Дверь \"старая\"", false);
        LinkImpl use = new LinkImpl(m_key, m_door.getId());
        use.setTexts(b.text("Дверь открылась."));
        m_key.addLink(use);
        return b.nlb;
    }

    private String export() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book().exportToASMFile(file);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    public void startAndPlaces() throws Exception {
        String sm = export();
        assertTrue(sm, sm.contains(":: Start[::]0-0-0\n<<display 'nlb_init'>>\n<<goto '" + m_hall.getId() + "'>>"));
        assertTrue("места объектов при старте: " + sm, sm.contains("<<set $nlb_in_1 = \"" + m_hall.getId() + "\">><<set $nlb_in_2 = \""
                + m_hall.getId() + "\">>"));
        assertTrue("объекты места — по строке: " + sm, sm.contains(":: nlb_show_in[::]10-10-0\n<<set $nlb_ws = $nlb_where>>\n"
                + "<<if $nlb_in_1 eq $nlb_where>><<display 'nlb_o_1_dsc'>><<endif>>\n"));
    }

    @Test
    public void pageShowsSceneReplyAndInventory() throws Exception {
        String sm = export();
        assertTrue("модификации — до текста, при повторном показе — нет: " + sm, sm.contains(":: " + m_hall.getId()
                + "[::]150-0-0\n<<set $nlb_page = \"" + m_hall.getId() + "\">><<if $nlb_redraw eq 1>><<set $nlb_redraw = 0>><<else>>\n"));
        assertTrue(sm, sm.contains("Зал.\n<<set $nlb_where = \"" + m_hall.getId() + "\">><<display 'nlb_show_in'>>\n"
                + "<<display 'nlb_reply'>>\n<<display 'nlb_inv'>>\n"));
    }

    @Test
    public void sceneObjectIsLink() throws Exception {
        String sm = export();
        assertTrue(sm, sm.contains(":: nlb_o_1_dsc[::]10-10-0\nНа столе [[ключ|nlb_act {$nlb_o = 1}]] Пети.\n"));
    }

    @Test
    public void takeAndUse() throws Exception {
        String sm = export();
        assertTrue(sm, sm.contains("<<set $nlb_reply = $nlb_reply + \"Вы взяли ключ.\" + \"<br>\">>"));
        assertTrue("переносимый — в инвентарь: " + sm, sm.contains(":: nlb_o_1_act[::]10-10-0\n<<display 'nlb_o_1_acta'>>\n"
                + "<<if ($nlb_in_1 eq \"nlb_inv\") and (\"nlb_inv\" neq \"nlb_inv\")>>"));
        assertTrue("применение к двери: " + sm, sm.contains("<<if $nlb_t eq 2>>\n<<set $nlb_reply = $nlb_reply + \"Дверь открылась.\""));
        assertFalse("дверь не переносимая: " + sm, sm.contains(":: nlb_o_2_act[::]10-10-0\n<<display 'nlb_o_2_acta'>>\n<<if"));
        assertTrue("кавычка в подписи — числовой сущностью (именованную AXMA раскрывает): " + sm,
                sm.contains("<<set $nlb_d = \"Дверь &#34;старая&#34;\">>"));
        assertTrue("предмет — кнопка с подписью-выражением: " + sm, sm.contains("[[$nlb_d|nlb_invclick {$nlb_o2 = 1}]]"));
    }

    private static void mod(TestBook b, com.nlbhub.nlb.domain.AbstractModifyingItem item, String target,
                            com.nlbhub.nlb.api.Modification.Type type, String varName,
                            com.nlbhub.nlb.api.Variable.DataType dataType, String expr) {
        com.nlbhub.nlb.domain.ModificationImpl mod = new com.nlbhub.nlb.domain.ModificationImpl(item);
        mod.setType(type.name());
        if (varName != null) {
            com.nlbhub.nlb.domain.VariableImpl var = b.variable(com.nlbhub.nlb.api.Variable.Type.VAR, dataType, varName, null);
            var.setTarget(target + ";" + mod.getId());
            mod.setVarId(var.getId());
        }
        if (expr != null) {
            com.nlbhub.nlb.domain.VariableImpl ex = b.variable(com.nlbhub.nlb.api.Variable.Type.EXPRESSION, dataType, null, expr);
            ex.setTarget(target + ";" + mod.getId());
            mod.setExprId(ex.getId());
        }
        item.addModification(mod);
    }

    @Test
    public void negationIsJavaScript() throws Exception {
        TestBook b = new TestBook();
        PageImpl hall = b.page("Зал.");
        PageImpl out = b.page("Улица.");
        b.link(hall, out, "Выйти");
        String t = hall.getId();
        com.nlbhub.nlb.api.Variable.DataType bool = com.nlbhub.nlb.api.Variable.DataType.BOOLEAN;
        mod(b, hall, t, com.nlbhub.nlb.api.Modification.Type.ASSIGN, "a", bool, "true");
        mod(b, hall, t, com.nlbhub.nlb.api.Modification.Type.ASSIGN, "b", bool, "false");
        mod(b, hall, t, com.nlbhub.nlb.api.Modification.Type.IF, null, bool, "a && !b");
        mod(b, hall, t, com.nlbhub.nlb.api.Modification.Type.ASSIGN, "a", bool, "false");
        mod(b, hall, t, com.nlbhub.nlb.api.Modification.Type.END, null, null, null);
        File file = new File(m_temp.getRoot(), "not.sm");
        b.nlb.exportToASMFile(file);
        String sm = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertFalse("AXMA ломает «and not»: " + sm, sm.contains(" not "));
        assertTrue(sm, sm.contains("and !"));
        assertFalse("«eq» AXMA переводит только в <<if>>, не в <<set>>: " + sm,
                java.util.regex.Pattern.compile("<<set [^>]* eq ").matcher(sm).find());
        assertTrue(sm, sm.contains("($a == 1)"));
    }

    /**
     * Текст действия $^$ — не переменная, ссылка без текста — с подписью по умолчанию: иначе
     * редактор AXMA не собирает игру («Шапочка», «Фронтир»)
     */
    @Test
    public void actionTextAndEmptyLinkCompile() throws Exception {
        TestBook b = new TestBook();
        PageImpl hall = b.page("Зал. $^$");
        PageImpl out = b.page("Улица.");
        b.link(hall, out, "");
        File file = new File(m_temp.getRoot(), "empty.sm");
        b.nlb.exportToASMFile(file);
        String sm = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertFalse(sm, sm.contains("<<print $^>>"));
        assertFalse(sm, sm.contains("[[|"));
    }
}
