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
 * Объекты в экспорте jsIQ2: общий код NLB (глобальный скрипт nlbInit), место объекта —
 * vars.nlb_in[id], объект сцены — ссылка NLB.click в тексте статьи, переносимый — в инвентарь,
 * применение предмета к объекту — use; переменные в тексте — значениями при показе
 */
public class JsiqObjectsExportTest {
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
        m_hall = b.page("Зал. Попыток: $tries$.");
        PageImpl out = b.page("Улица.");
        b.link(m_hall, out, "Выйти");
        m_key = b.obj(m_hall, "key", "На столе {ключ} Пети.", "Ключ Пети", true);
        m_key.setActTexts(b.text("Вы взяли ключ."));
        m_door = b.obj(m_hall, "door", "Впереди {дверь}.", "Дверь ($tries$)", false);
        LinkImpl use = new LinkImpl(m_key, m_door.getId());
        use.setTexts(b.text("Дверь открылась."));
        m_key.addLink(use);
        return b.nlb;
    }

    private String export() throws Exception {
        File file = new File(m_temp.getRoot(), "book.xml");
        book().exportToJSIQFile(file);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    public void runtimeAndPlaces() throws Exception {
        String xml = export();
        assertTrue("общий код — глобальный скрипт: " + xml,
                xml.contains("isglobal=\"true\" type=\"nlbInit\"><![CDATA[if (window.NLB) { return; }\n"));
        assertTrue("место объекта при старте: " + xml,
                xml.contains("'" + m_key.getId() + "': '" + m_hall.getId() + "'"));
        assertTrue("статья 0 — страница зала: " + xml, xml.contains("N.pages = {'0': '" + m_hall.getId() + "'"));
        assertTrue("preload каждой статьи вызывает общий код: " + xml,
                xml.contains("<script type=\"preload\"><![CDATA[nlbInit(); if (vars.nlb_redraw) { vars.nlb_redraw = false; } else { }]]>"));
        assertTrue("места для объектов сцены, ответа и инвентаря: " + xml, xml.contains(
                "<text><![CDATA[<div class=\"nlb_scene\"></div>]]></text>\n"
                        + "        <text><![CDATA[<div class=\"nlb_reply\"></div>]]></text>\n"
                        + "        <text><![CDATA[<div class=\"nlb_inv\"></div>]]></text>"));
    }

    @Test
    public void sceneObjectIsLink() throws Exception {
        String xml = export();
        assertTrue(xml, xml.contains("text: function (vars) { return 'На столе <a href=\"#\" class=\"nlb_obj\" "
                + "onclick=\"return NLB.click(\\'act\\', \\'" + m_key.getId() + "\\')\">ключ</a> Пети.'; },"));
    }

    @Test
    public void takeAndUse() throws Exception {
        String xml = export();
        assertTrue(xml, xml.contains("acttext: function (vars) { return 'Вы взяли ключ.'; },"));
        assertTrue("переносимый — в инвентарь: " + xml, xml.contains("take: '" + m_key.getId() + "',"));
        assertTrue("применение к двери: " + xml, xml.contains("use: function (vars, target) { if (target === '"
                + m_door.getId() + "') { NLB.sayp(vars, 'Дверь открылась.'); NLB.printed = true; } },"));
        assertFalse("дверь не переносимая: " + xml, xml.contains("take: '" + m_door.getId() + "'"));
    }

    @Test
    public void variablesInText() throws Exception {
        String xml = export();
        assertTrue("в тексте статьи — span со значением: " + xml,
                xml.contains("<text><![CDATA[Зал. Попыток: <span class=\"nlb_v\" data-v=\"tries\"></span>.]]></text>"));
        assertTrue("в тексте объекта — значение: " + xml,
                xml.contains("disp: function (vars) { return 'Дверь (' + NLB.v(vars, 'tries') + ')'; },"));
        assertFalse("переменные больше не выводятся как $имя$: " + xml, xml.contains("$tries$"));
    }

    @Test
    public void modificationsUseRuntime() throws Exception {
        TestBook b = new TestBook();
        PageImpl hall = b.page("Зал.");
        PageImpl out = b.page("Улица.");
        LinkImpl link = b.link(hall, out, "Выйти");
        ObjImpl key = b.obj(hall, "key", "{Ключ}.", "Ключ", true);
        b.mod(link, com.nlbhub.nlb.api.Modification.Type.ADDINV, null, "key");
        b.mod(link, com.nlbhub.nlb.api.Modification.Type.RMINV, null, "key");
        b.mod(link, com.nlbhub.nlb.api.Modification.Type.COUNTRST, null, null);
        File file = new File(m_temp.getRoot(), "mods.xml");
        b.nlb.exportToJSIQFile(file);
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(xml, xml.contains("if (!NLB.has(vars, '" + key.getId() + "')) { NLB.take(vars, '" + key.getId() + "', false); } "));
        assertTrue(xml, xml.contains("NLB.del(vars, '" + key.getId() + "', NLB.INV); "));
        assertTrue(xml, xml.contains("NLB.countReset(vars); "));
    }

    /**
     * Разметка INSTEAD в описании: '^' — перевод строки, пустая метка {} (щелчок по картинке) —
     * ссылкой становится первая непустая строка рядом; графический объект (VN) с действием, но
     * без метки — ссылкой весь текст, а без действия — просто подпись
     */
    @Test
    public void insteadMarkupAndGraphicalObjects() throws Exception {
        TestBook b = new TestBook();
        PageImpl hall = b.page("Настройки.");
        ObjImpl flag = b.obj(hall, "flag", "{}^ Карточные бои^", "", false);
        flag.setActTexts(b.text("Готово."));
        ObjImpl skill = b.obj(hall, "skill", "Грамотность – читать надписи.", "Грамотность", true);
        skill.setGraphical(true);
        ObjImpl label = b.obj(hall, "label", "", "ВЫБЕРИТЕ НАВЫК", false);
        label.setGraphical(true);
        File file = new File(m_temp.getRoot(), "markup.xml");
        b.nlb.exportToJSIQFile(file);
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue("{} — ссылкой первая строка, ^ — <br/>: " + xml, xml.contains("text: function (vars) { return ' <br/>\\n "
                + "<a href=\"#\" class=\"nlb_obj\" onclick=\"return NLB.click(\\'act\\', \\'" + flag.getId()
                + "\\')\">Карточные бои</a> <br/>\\n'; },"));
        assertTrue("графический объект с действием — весь текст ссылкой: " + xml, xml.contains(
                "onclick=\"return NLB.click(\\'act\\', \\'" + skill.getId() + "\\')\">Грамотность – читать надписи.</a>'; },"));
        assertTrue("надпись-картинка без действия — просто текст: " + xml,
                xml.contains("text: function (vars) { return 'ВЫБЕРИТЕ НАВЫК'; },"));
    }
}
