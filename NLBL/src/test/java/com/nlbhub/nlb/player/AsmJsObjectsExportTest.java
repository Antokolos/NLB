package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.LinkImpl;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Объекты в экспорте AXMA Story Maker JS: состояние — nlb.cs (содержимое контейнеров, id
 * экземпляров), nlb.own (последний владелец), nlb.proto (клоны); код объекта — функции nlbo[id]
 * (невидимы для сохранения), каждая — оператором в одну строку (AXMA JS склеивает строки абзаца
 * через «;»); щелчок — ссылка на абзац nlb_act, экземпляр — в функции ссылки
 */
public class AsmJsObjectsExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private ObjImpl m_key;
    private ObjImpl m_door;
    private PageImpl m_hall;

    /** Зал: ключ (переносимый, «На столе {ключ}»), дверь; ключ применяется к двери; CLONE */
    private NonLinearBookImpl book(TestBook b) {
        m_hall = b.page("Зал.");
        PageImpl out = b.page("Улица.");
        b.link(m_hall, out, "Выйти");
        m_key = b.obj(m_hall, "key", "На столе {ключ} Пети.", "Ключ \"Пети\" $power$", true);
        m_key.setActTexts(b.text("Вы взяли ключ."));
        m_door = b.obj(m_hall, "door", "Впереди {дверь}.", "Дверь", false);
        LinkImpl use = new LinkImpl(m_key, m_door.getId());
        use.setTexts(b.text("Дверь открылась."));
        m_key.addLink(use);
        mod(b, m_door, Modification.Type.CLONE, "copy", "key");
        mod(b, m_door, Modification.Type.ADDINV, null, "copy");
        return b.nlb;
    }

    /** Модификация объекта (переменные — с целью «объект;модификация», как в книге) */
    private static void mod(TestBook b, ObjImpl obj, Modification.Type type, String varName, String expr) {
        ModificationImpl mod = new ModificationImpl(obj);
        mod.setType(type.name());
        if (varName != null) {
            VariableImpl var = b.variable(Variable.Type.VAR, Variable.DataType.AUTO, varName, null);
            var.setTarget(obj.getId() + ";" + mod.getId());
            mod.setVarId(var.getId());
        }
        VariableImpl ex = b.variable(Variable.Type.EXPRESSION, Variable.DataType.AUTO, null, expr);
        ex.setTarget(obj.getId() + ";" + mod.getId());
        mod.setExprId(ex.getId());
        obj.addModification(mod);
    }

    private String export() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book(new TestBook()).exportToASMJSFile(file);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String q(String id) {
        return "\"" + id + "\"";
    }

    @Test
    public void startState() throws Exception {
        String sm = export();
        assertTrue("Start — состояние и библиотека: " + sm, sm.contains(":: Start[::]10-10-0\ndisplay(\"nlb_init\"); display(\"nlb_lib\");\n"));
        assertTrue("место объекта при старте: " + sm, sm.contains("nlb.own[" + q(m_key.getId()) + "] = " + q(m_hall.getId()) + ";"));
        assertTrue("содержимое контейнера — по порядку книги: " + sm, sm.contains("nlb.cs[" + q(m_hall.getId()) + "] = ["
                + q(m_key.getId()) + ", " + q(m_door.getId()) + "];"));
        assertTrue("код — невидимое для сохранения свойство окна: " + sm, sm.contains(
                "Object.defineProperty(window, \"nlbo\", {value: {}, writable: true, configurable: true, enumerable: false});"));
    }

    @Test
    public void pageShowsSceneReplyAndInventory() throws Exception {
        String sm = export();
        assertTrue("абзац-ход подгружает библиотеку и сбрасывает стек экземпляров: " + sm, sm.contains(":: " + m_hall.getId()
                + "[::]10-10-0\nif (!window.nlbf) { display(\"nlb_lib\"); }\nnlb.ms = []; nlb.me = \"\"; nlb.page = " + q(m_hall.getId())));
        assertTrue(sm, sm.contains("nlbf.before(\"Зал.\"); \nprint(\"Зал.\" + \"<br><br>\");\nnlb.gen++; nlbf.show(" + q(m_hall.getId())
                + "); nlbf.reply(); nlbf.inv();"));
    }

    @Test
    public void sceneObjectIsLink() throws Exception {
        String sm = export();
        assertTrue(sm, sm.contains("nlbo[" + q(m_key.getId()) + "].dsc = function () { if (!nlbf.on(nlb.me)) { return; } "
                + "print(\"На столе \" + nlbf.lnk(\"ключ\", nlb.me) + \" Пети.\" + \"<br>\"); nlbf.show(nlb.me); };"));
        // функция ссылки сохраняется исходным текстом (замыкание теряется) — собирается при показе
        assertTrue(sm, sm.contains("nlbf.lnk = function (t, x) { return nlbf.a(t, \"nlb_act\", new Function(\"nlb.o = \" + JSON.stringify(x) + \";\"), 0); };"));
    }

    @Test
    public void takeUseAndItem() throws Exception {
        String sm = export();
        assertTrue(sm, sm.contains("nlbo[" + q(m_key.getId()) + "].acta = function () { nlbf.say(\"Вы взяли ключ.\"); };"));
        assertTrue("переносимый — в инвентарь: " + sm, sm.contains("nlbo[" + q(m_key.getId()) + "].take = 1;"));
        assertFalse("дверь не переносимая: " + sm, sm.contains("nlbo[" + q(m_door.getId()) + "].take"));
        assertTrue("применение к двери — по прототипу цели: " + sm, sm.contains("nlbo[" + q(m_key.getId()) + "].use = function (t) { var tp = nlbf.P(t), p = 0; if (tp == "
                + q(m_door.getId()) + ") { nlbf.say(\"Дверь открылась.\"); p = 1; } return p; };"));
        assertTrue("предмет — кнопка, подпись с переменной — выражением, кавычки экранированы: " + sm, sm.contains("nlbo[" + q(m_key.getId())
                + "].item = function () { var d = \"Ключ \\\"Пети\\\" \" + nlb.str(v.power); if (nlb.sel == nlb.me) { d = \"» \" + d; } print(nlbf.btn(d, nlb.me)); };"));
    }

    @Test
    public void cloneAndAddInv() throws Exception {
        String sm = export();
        assertTrue("CLONE и ADDINV: " + sm, sm.contains("v.copy = nlbf.clone(" + q(m_key.getId()) + "); nlbf.addinv(v.copy); "));
        assertTrue("клон — прототип и тег: " + sm, sm.contains("nlbf.clone = function (q) { nlb.cc++; var c = \"nlb_c\" + nlb.cc; nlb.proto[c] = nlbf.P(q); "));
    }

    /** Подпись ссылки AXMA JS вставляет в onclick — в кавычки: она ставится только в текст ссылки */
    @Test
    public void quotesInLabelsStayOutOfOnclick() throws Exception {
        String sm = export();
        assertTrue(sm, sm.contains("nlbf.a = function (t, p, code, b) { var h = (b ? button : link)(\"x\", p, \"\", code)"));
        assertTrue("ссылка страницы: " + sm, sm.contains("print(nlbf.a(\"Выйти\", "));
    }

    /** Каждая строка абзаца — законченный оператор: строка не начинается с else, «,» или «.» */
    @Test
    public void linesAreStatements() throws Exception {
        String sm = export();
        for (String line : sm.split("\n")) {
            String l = line.strip();
            assertFalse("строка продолжает предыдущую: " + line, l.startsWith("else") || l.startsWith(",") || l.startsWith(".")
                    || l.startsWith("+"));
        }
    }
}
