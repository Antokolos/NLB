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
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Автоссылки в экспортах URQ и AXMA Story Maker: переход срабатывает сам и важнее обычных
 * ссылок страницы (как в INSTEAD), а не выводится ещё одной кнопкой рядом с ними
 */
public class AutoLinkExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    private String m_alarmId;
    private String m_bridgeId;

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    /**
     * Зал: обычная ссылка «Уйти» и автоссылка «Тревога!» с условием alarm. Мост — без текста,
     * безусловная автоссылка в зал и обычная ссылка, которая уже не нужна.
     */
    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        PageImpl hall = b.page("Вы в зале.");
        PageImpl out = b.page("Вы ушли.");
        PageImpl alarm = b.page("Звучит сирена.");
        PageImpl bridge = b.page("");
        ModificationImpl mod = new ModificationImpl(hall);
        mod.setType(Modification.Type.ASSIGN.name());
        VariableImpl var = b.variable(Variable.Type.VAR, Variable.DataType.BOOLEAN, "alarm", null);
        var.setTarget(hall.getId() + ";" + mod.getId());
        mod.setVarId(var.getId());
        VariableImpl expr = b.variable(Variable.Type.EXPRESSION, Variable.DataType.BOOLEAN, null, "true");
        expr.setTarget(hall.getId() + ";" + mod.getId());
        mod.setExprId(expr.getId());
        hall.addModification(mod);
        b.link(hall, out, "Уйти");
        LinkImpl auto = b.link(hall, alarm, "Тревога!");
        auto.setAuto(true);
        VariableImpl constraint = b.variable(Variable.Type.LINKCONSTRAINT, Variable.DataType.BOOLEAN, null, "alarm");
        constraint.setTarget(auto.getFullId());
        auto.setConstrId(constraint.getId());
        m_alarmId = auto.getId();
        LinkImpl back = b.link(bridge, hall, "");
        back.setAuto(true);
        m_bridgeId = back.getId();
        b.link(bridge, out, "Лишняя");
        return b.nlb;
    }

    private String read(File file, Charset charset) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), charset).replace("\r\n", "\n");
    }

    @Test
    public void urq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.qst");
        book().exportToURQTextFile(file);
        String urq = read(file, Charset.forName("windows-1251"));
        int autoGoto = urq.indexOf(" then goto " + m_alarmId + "\n");
        assertTrue("автоссылка — переход по условию: " + urq, autoGoto > 0);
        assertTrue("до кнопок страницы: " + urq, autoGoto < urq.indexOf(", Уйти\n"));
        assertFalse("а не кнопкой: " + urq, urq.contains(", Тревога!"));
        assertTrue("безусловная — просто goto: " + urq, urq.contains("\ngoto " + m_bridgeId + "\n"));
        assertFalse("обычные ссылки после безусловной автоссылки не нужны: " + urq, urq.contains(", Лишняя"));
    }

    @Test
    public void asm() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book().exportToASMFile(file);
        String sm = read(file, StandardCharsets.UTF_8);
        assertTrue("на странице с текстом — кнопкой вместо обычных ссылок: " + sm,
                Pattern.compile("Вы в зале\\.\n<<set [^\n]*>>\n<<if [^\n]*alarm[^\n]*>>\n\\[\\[Тревога!\\|" + m_alarmId
                        + "\\]\\]\n<<else>>\n\\[\\[Уйти\\|[-0-9a-f]+\\]\\]\n<<endif>>\n").matcher(sm).find());
        assertTrue("на странице без текста — сразу переход: " + sm,
                sm.contains("<<goto '" + m_bridgeId + "'>>\n"));
        assertFalse("обычные ссылки после безусловной автоссылки не нужны: " + sm, sm.contains("[[Лишняя|"));
    }
}
