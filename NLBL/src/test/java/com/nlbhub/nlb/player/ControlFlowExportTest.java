package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.AbstractModifyingItem;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * Условия, циклы, GOTO и RND в модификациях — в экспортах QSP, AXMA Story Maker и jsIQ2
 */
public class ControlFlowExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    private PageImpl m_secret;
    private PageImpl m_bad;

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    /** Модификация с переменной (varName, тип dataType) и выражением expr; null — без них */
    private static void mod(TestBook b, AbstractModifyingItem item, String target, Modification.Type type,
                            String varName, Variable.DataType dataType, String expr) {
        ModificationImpl mod = new ModificationImpl(item);
        mod.setType(type.name());
        if (varName != null) {
            VariableImpl var = b.variable(Variable.Type.VAR, dataType, varName, null);
            var.setTarget(target + ";" + mod.getId());
            mod.setVarId(var.getId());
        }
        if (expr != null) {
            VariableImpl ex = b.variable(Variable.Type.EXPRESSION, dataType, null, expr);
            ex.setTarget(target + ";" + mod.getId());
            mod.setExprId(ex.getId());
        }
        item.addModification(mod);
    }

    /**
     * Старт: counter = 0; WHILE counter &lt; 3: counter = counter + 1 END; dice = RND(6);
     * IF dice &gt; 3: lucky = true ELSEIF dice &gt; 1: lucky = false ELSE GOTO «Неудача» END.
     * Ссылка «Дальше» ведёт на страницу «Дальше», но её модификация GOTO уводит в «Тайник».
     */
    NonLinearBookImpl book() {
        TestBook b = new TestBook();
        PageImpl start = b.page("Начало.");
        PageImpl next = b.page("Дальше.");
        m_secret = b.page("Тайник.");
        m_bad = b.page("Неудача.");
        String t = start.getId();
        Variable.DataType num = Variable.DataType.NUMBER;
        Variable.DataType bool = Variable.DataType.BOOLEAN;
        mod(b, start, t, Modification.Type.ASSIGN, "counter", num, "0");
        mod(b, start, t, Modification.Type.WHILE, null, bool, "counter < 3");
        mod(b, start, t, Modification.Type.ASSIGN, "counter", num, "counter + 1");
        mod(b, start, t, Modification.Type.END, null, null, null);
        mod(b, start, t, Modification.Type.RND, "dice", num, "6");
        mod(b, start, t, Modification.Type.IF, null, bool, "dice > 3");
        mod(b, start, t, Modification.Type.ASSIGN, "lucky", bool, "true");
        mod(b, start, t, Modification.Type.ELSEIF, null, bool, "dice > 1");
        mod(b, start, t, Modification.Type.ASSIGN, "lucky", bool, "false");
        mod(b, start, t, Modification.Type.ELSE, null, null, null);
        mod(b, start, t, Modification.Type.GOTO, null, Variable.DataType.STRING, m_bad.getId());
        mod(b, start, t, Modification.Type.END, null, null, null);
        LinkImpl link = b.link(start, next, "Дальше");
        mod(b, link, link.getFullId(), Modification.Type.GOTO, null, Variable.DataType.STRING, m_secret.getId());
        return b.nlb;
    }

    private String read(File file, Charset charset) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), charset).replace("\r\n", "\n");
    }

    private static void assertInOrder(String text, String... parts) {
        int at = 0;
        for (String part : parts) {
            int found = text.indexOf(part, at);
            assertTrue("нет «" + part + "» после позиции " + at + ":\n" + text, found >= 0);
            at = found + part.length();
        }
    }

    /** Номер статьи jsIQ с текстом text */
    private static String articleId(String xml, String text) {
        Matcher m = Pattern.compile("<article id=\"(\\d+)\">(?:(?!</article>).)*<text><!\\[CDATA\\["
                + Pattern.quote(text) + "\\]\\]></text>", Pattern.DOTALL).matcher(xml);
        assertTrue(text + ": " + xml, m.find());
        return m.group(1);
    }

    @Test
    public void qsp() throws Exception {
        File file = new File(m_temp.getRoot(), "book.txt");
        book().exportToQSPTextFile(file);
        String qsp = read(file, StandardCharsets.UTF_16LE);
        assertInOrder(qsp,
                "counter = 0\n",
                "LOOP WHILE ", "counter", "<", "3", ":\n",
                "counter = ", "counter", "+", "1", "\n",
                "END\n",
                "dice = RAND(1, 6)\n",
                "IF ", "dice", ">", "3", ":\n",
                "lucky = ",
                "ELSEIF ", "dice", ">", "1", ":\n",
                "ELSE\n",
                "GOTO '" + m_bad.getId() + "'\n",
                "END\n",
                "ACT 'Дальше':\n",
                "GOTO '" + m_secret.getId() + "'\n");
    }

    @Test
    public void asm() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book().exportToASMFile(file);
        String sm = read(file, StandardCharsets.UTF_8);
        assertInOrder(sm,
                "<<set $counter = 0>>\n",
                "<<loop 1000000000>>\n<<if not (", "$counter", "<", "3", ")>>\n<<break>>\n<<endif>>\n",
                "<<set $counter = ", "$counter", "+", "1", ">>\n",
                "<<endloop>>\n",
                "<<set $dice = Math.floor(Math.random() * (6)) + 1>>\n",
                "<<if ", "$dice", ">", "3", ">>\n",
                "<<elseif ", "$dice", ">", "1", ">>\n",
                "<<else>>\n<<goto '" + m_bad.getId() + "'>>\n<<endif>>\n");
        assertInOrder(sm, "[[Дальше|", ":: ", "<<goto '" + m_secret.getId() + "'>>\n");
    }

    @Test
    public void jsiq2() throws Exception {
        File file = new File(m_temp.getRoot(), "book.xml");
        book().exportToJSIQFile(file);
        String xml = read(file, StandardCharsets.UTF_8);
        String secret = "nlbGoto({to: '" + articleId(xml, "Тайник.") + "'}); return;";
        String bad = "nlbGoto({to: '" + articleId(xml, "Неудача.") + "'}); return;";
        assertInOrder(xml,
                "while (", "vars.counter", "<", "3", ") { ",
                "} ",
                "vars.dice = Math.floor(Math.random() * (6)) + 1; ",
                "if (", "vars.dice", ">", "3", ") { ",
                "} else if (", "} else { ", bad, "} ");
        assertTrue("GOTO в модификации ссылки: " + xml, xml.contains(secret));
        assertTrue("модификации страницы — до показа: " + xml, xml.contains("<script type=\"preload\"><![CDATA[") );
        assertTrue("глобальный nlbGoto: " + xml, xml.contains("type=\"nlbGoto\""));
    }
}
