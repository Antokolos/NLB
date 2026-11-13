package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.LinkImpl;
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
 * #135: альтернативный текст ссылки (виден, когда условие ссылки не выполнено, — как в экспорте в
 * INSTEAD и в плеере) во всех экспортах: QSP, URQ, jsIQ2, ChoiceScript, ASM, текст и HTML
 */
public class AltTextExportTest {
    private static final String ALT = "Дверь заперта, нужен ключ.";

    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    /** Зал: «Открыть дверь» (условие has_key, альтернативный текст), «Вернуться» (без условия) */
    private static NonLinearBookImpl book() {
        TestBook b = new TestBook();
        PageImpl hall = b.page("Вы в зале. Впереди дверь.");
        PageImpl door = b.page("Вы за дверью.");
        PageImpl back = b.page("Вы вернулись.");
        ModificationImpl mod = new ModificationImpl(hall);
        mod.setType(Modification.Type.ASSIGN.name());
        VariableImpl var = b.variable(Variable.Type.VAR, Variable.DataType.BOOLEAN, "has_key", null);
        var.setTarget(hall.getId() + ";" + mod.getId());
        mod.setVarId(var.getId());
        VariableImpl expr = b.variable(Variable.Type.EXPRESSION, Variable.DataType.BOOLEAN, null, "false");
        expr.setTarget(hall.getId() + ";" + mod.getId());
        mod.setExprId(expr.getId());
        hall.addModification(mod);
        LinkImpl open = b.link(hall, door, "Открыть дверь");
        VariableImpl constraint = b.variable(Variable.Type.LINKCONSTRAINT, Variable.DataType.BOOLEAN, null, "has_key");
        constraint.setTarget(open.getFullId());
        open.setConstrId(constraint.getId());
        open.setAltTexts(b.text(ALT));
        LinkImpl ret = b.link(hall, back, "Вернуться");
        ret.setAltTexts(b.text("Без условия — не показывается"));
        return b.nlb;
    }

    private String read(File file, Charset charset) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), charset).replace("\r\n", "\n");
    }

    private File file(String name) {
        return new File(m_temp.getRoot(), name);
    }

    @Test
    public void qsp() throws Exception {
        book().exportToQSPTextFile(file("book.txt"));
        String qsp = read(file("book.txt"), StandardCharsets.UTF_16LE);
        assertTrue(qsp, qsp.contains("ACT 'Открыть дверь':"));
        assertTrue("ELSE с выводом текста в основное окно: " + qsp,
                qsp.contains("ELSE\n  'Дверь заперта, нужен ключ.'\nEND"));
        assertFalse(qsp.contains("Без условия"));
    }

    @Test
    public void urq() throws Exception {
        book().exportToURQTextFile(file("book.qst"));
        String urq = read(file("book.qst"), Charset.forName("windows-1251"));
        assertTrue("одна строка if … then btn … else pln …: " + urq,
                urq.matches("(?s).*if \\(.*\\) then btn [-0-9a-f]+, Открыть дверь else pln " + ALT + "\n.*"));
        assertFalse("подпись кнопки — без «pln »: " + urq, urq.contains(", pln "));
        assertTrue(urq, urq.matches("(?s).*\nbtn [-0-9a-f]+, Вернуться\n.*"));
        assertFalse(urq.contains("Без условия"));
    }

    @Test
    public void jsiq2() throws Exception {
        book().exportToJSIQFile(file("book.xml"));
        String xml = read(file("book.xml"), StandardCharsets.UTF_8);
        assertTrue(xml, xml.matches("(?s).*<action (do=\"do_0\" )?goto=\"\\d+\" if=\"condition_0\"><!\\[CDATA\\[Открыть дверь\\]\\]></action>.*"));
        assertTrue("текст, видимый при ложном условии: " + xml,
                xml.contains("<text ifnot=\"condition_0\"><![CDATA[" + ALT + "]]></text>"));
        assertTrue("основной текст статьи — как был", xml.contains("<text><![CDATA[<div class=\"nlb_prev\"></div>Вы в зале. Впереди дверь.]]></text>"));
        assertFalse(xml.contains("Без условия"));
    }

    @Test
    public void choiceScript() throws Exception {
        book().exportToChoiceScript(file("startup.txt"));
        String cs = read(file("startup.txt"), StandardCharsets.UTF_8);
        int alt = cs.indexOf("*if not(has_key)\n    " + ALT + "\n");
        assertTrue(cs, alt > 0);
        assertTrue("до вариантов", alt < cs.indexOf("*choice"));
        assertFalse(cs.contains("Без условия"));
    }

    @Test
    public void asm() throws Exception {
        book().exportToASMFile(file("book.sm"));
        String asm = read(file("book.sm"), StandardCharsets.UTF_8);
        assertTrue(asm, asm.matches("(?s).*\\[\\[Открыть дверь\\|[-0-9a-f]+\\]\\]\n<<else>>\n" + ALT + "\n<<endif>>.*"));
        assertFalse(asm.contains("Без условия"));
    }

    @Test
    public void printed() throws Exception {
        NonLinearBookImpl nlb = book();
        nlb.exportToTXTFile(file("book.txt"));
        String txt = read(file("book.txt"), StandardCharsets.UTF_8);
        assertTrue(txt, txt.matches("(?s).*Если \\(has_key\\), то вы можете открыть дверь \\[\\d+\\], иначе: " + ALT + ".*"));
        assertFalse(txt.contains("Без условия"));
        nlb.exportToHTMLFile(file("index.html"));
        String html = read(file("index.html"), StandardCharsets.UTF_8);
        assertTrue(html, html.matches("(?s).*открыть дверь \\(\\d+\\)</a>, иначе: " + ALT + ".*"));
    }
}
