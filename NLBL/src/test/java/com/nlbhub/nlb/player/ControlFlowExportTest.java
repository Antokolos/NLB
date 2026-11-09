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
 * Условия, циклы, GOTO и RND в модификациях — в экспортах QSP, AXMA Story Maker, jsIQ2, URQ и
 * ChoiceScript
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

    @Test
    public void urq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.qst");
        book().exportToURQTextFile(file);
        String urq = read(file, Charset.forName("windows-1251")).replace("\r\n", "\n");
        assertInOrder(urq,
                "counter=0\n",
                ":nlb_while_1\n", "if not (", "counter", "<", "3", ") then goto nlb_while_1_end\n",
                "counter=", "counter", "+", "1", "\n",
                "goto nlb_while_1\n:nlb_while_1_end\n",
                "dice=rnd#6$\n",
                "if not (", "dice", ">", "3", ") then goto nlb_if_2_1\n",
                "lucky=",
                "goto nlb_if_2_end\n:nlb_if_2_1\n",
                "if not (", "dice", ">", "1", ") then goto nlb_if_2_2\n",
                "goto nlb_if_2_end\n:nlb_if_2_2\n",
                "goto " + m_bad.getId() + "\n",
                ":nlb_if_2_3\n:nlb_if_2_end\n");
        assertInOrder(urq, ":", "goto " + m_secret.getId() + "\n", "goto ");
    }

    @Test
    public void choiceScript() throws Exception {
        File file = new File(m_temp.getRoot(), "startup.txt");
        book().exportToChoiceScript(file);
        String cs = read(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertInOrder(cs,
                "*create implicit_control_flow true\n",
                "*set counter 0\n",
                "*label nlb_while_1\n*if ", "counter", "<", "3", "\n",
                "    *set counter ", "counter", "+", "1", "\n",
                "    *goto nlb_while_1\n",
                "*rand dice 1 6\n",
                "*if ", "dice", ">", "3", "\n",
                "    *set lucky ",
                "*elseif ", "dice", ">", "1", "\n",
                "    *set lucky ",
                "*else\n",
                "    *goto " + m_bad.getId() + "\n");
        assertInOrder(cs, "*goto " + m_secret.getId() + "\n");
    }

    /** WHILE i &lt; 3: IF i = 1: x = x + 10 ELSE (пусто) END; i = i + 1 END — вложенные блоки */
    NonLinearBookImpl nestedBook() {
        TestBook b = new TestBook();
        PageImpl start = b.page("Начало.");
        String t = start.getId();
        Variable.DataType num = Variable.DataType.NUMBER;
        Variable.DataType bool = Variable.DataType.BOOLEAN;
        mod(b, start, t, Modification.Type.ASSIGN, "i", num, "0");
        mod(b, start, t, Modification.Type.ASSIGN, "x", num, "0");
        mod(b, start, t, Modification.Type.WHILE, null, bool, "i < 3");
        mod(b, start, t, Modification.Type.IF, null, bool, "i == 1");
        mod(b, start, t, Modification.Type.ASSIGN, "x", num, "x + 10");
        mod(b, start, t, Modification.Type.ELSE, null, null, null);
        mod(b, start, t, Modification.Type.END, null, null, null);
        mod(b, start, t, Modification.Type.ASSIGN, "i", num, "i + 1");
        mod(b, start, t, Modification.Type.END, null, null, null);
        return b.nlb;
    }

    @Test
    public void nestedChoiceScript() throws Exception {
        File file = new File(m_temp.getRoot(), "startup.txt");
        nestedBook().exportToChoiceScript(file);
        String cs = read(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertInOrder(cs,
                "*label nlb_while_1\n*if ", "\n",
                "    *if ", "\n",
                "        *set x ", "\n",
                "    *else\n        *comment\n",
                "    *set i ", "\n",
                "    *goto nlb_while_1\n");
    }

    @Test
    public void nestedUrq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.qst");
        nestedBook().exportToURQTextFile(file);
        String urq = read(file, Charset.forName("windows-1251")).replace("\r\n", "\n");
        assertInOrder(urq,
                ":nlb_while_1\n", ") then goto nlb_while_1_end\n",
                ") then goto nlb_if_2_1\n",
                "x=", "\n",
                "goto nlb_if_2_end\n:nlb_if_2_1\n",
                ":nlb_if_2_2\n:nlb_if_2_end\n",
                "i=", "\n",
                "goto nlb_while_1\n:nlb_while_1_end\n");
    }

    /** ADDINV key; IFHAVE key: x = 1 ELSE x = 2 END — блок «если есть предмет» */
    NonLinearBookImpl ifHaveBook() {
        TestBook b = new TestBook();
        PageImpl start = b.page("Начало.");
        b.obj(start, "key", "Ключ", "Ключ от двери", true);
        String t = start.getId();
        Variable.DataType num = Variable.DataType.NUMBER;
        mod(b, start, t, Modification.Type.ADDINV, null, Variable.DataType.STRING, "key");
        mod(b, start, t, Modification.Type.IFHAVE, "has", Variable.DataType.BOOLEAN, "key");
        mod(b, start, t, Modification.Type.ASSIGN, "x", num, "1");
        mod(b, start, t, Modification.Type.ELSE, null, null, null);
        mod(b, start, t, Modification.Type.ASSIGN, "x", num, "2");
        mod(b, start, t, Modification.Type.END, null, null, null);
        return b.nlb;
    }

    /**
     * IFHAVE открывает блок, который закрывает END: без него END оставался без пары и ломал
     * код страницы (лишняя «}» в jsIQ2, END в QSP, <<endif>> в AXMA)
     */
    @Test
    public void ifHave() throws Exception {
        File qspFile = new File(m_temp.getRoot(), "book.txt");
        ifHaveBook().exportToQSPTextFile(qspFile);
        assertInOrder(read(qspFile, StandardCharsets.UTF_16LE),
                "GOSUB 'nlb_place', '", "', 'nlb_inv'\n", "IF $nlb_in['", "'] = 'nlb_inv':\n", "x = ", "ELSE\n", "x = ", "END\n");
        File smFile = new File(m_temp.getRoot(), "book.sm");
        ifHaveBook().exportToASMFile(smFile);
        assertInOrder(read(smFile, StandardCharsets.UTF_8), "<<if false>>\n", "<<else>>\n", "<<endif>>\n");
        File xmlFile = new File(m_temp.getRoot(), "book.xml");
        ifHaveBook().exportToJSIQFile(xmlFile);
        assertInOrder(read(xmlFile, StandardCharsets.UTF_8),
                "addItem({'name': '", "if (checkItem({'name': '", "})) { ", "} else { ", "} ");
        File qstFile = new File(m_temp.getRoot(), "book.qst");
        ifHaveBook().exportToURQTextFile(qstFile);
        String urq = read(qstFile, Charset.forName("windows-1251")).replace("\r\n", "\n");
        assertInOrder(urq, "Inv+ Ключ от двери\nnlb_inv_", "=1\n",
                "if not (nlb_inv_", ") then goto nlb_if_1_1\n", "goto nlb_if_1_end\n:nlb_if_1_1\n",
                ":nlb_if_1_2\n:nlb_if_1_end\n");
        File csFile = new File(m_temp.getRoot(), "startup.txt");
        ifHaveBook().exportToChoiceScript(csFile);
        assertInOrder(read(csFile, StandardCharsets.UTF_8).replace("\r\n", "\n"),
                "*if false\n    *set x ", "*else\n    *set x ");
    }

    /**
     * ChoiceScript: «Далее» (текст по умолчанию) рядом с другими вариантами — вариант #Далее, а не
     * голый *goto внутри *choice; одинаковые видимые варианты различаются невидимым суффиксом
     */
    @Test
    public void choiceScriptTrivialAndDuplicateOptions() throws Exception {
        TestBook b = new TestBook();
        PageImpl start = b.page("Начало.");
        PageImpl a = b.page("А.");
        PageImpl c = b.page("Б.");
        b.link(start, a, "Налево");
        LinkImpl next = b.link(start, c, "");
        next.setTexts(b.text("Далее"));
        b.link(start, a, "Закрыть");
        b.link(start, c, "Закрыть");
        File file = new File(m_temp.getRoot(), "startup.txt");
        b.nlb.exportToChoiceScript(file);
        String cs = read(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertInOrder(cs, "*choice\n", "    #Налево\n", "    #Далее\n", "    #Закрыть\n", "    #Закрыть\u200B\n");
    }
}
