package com.nlbhub.nlb.player;

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
 * Инвентарь в экспортах QSP, URQ, jsIQ2 и AXMA — как PlayerEngine.isVisibleInInventory (в INSTEAD
 * пустой disp — false): предмет без подписи и картинки лежит в инвентаре невидимым (служебный
 * «menuObjsSyncObj» во «Фронтире»), но HAVE его видит. Порядок предметов — по взятию (так уже
 * было: ADDOBJ и inv+ добавляют в конец). В URQ '^' — перевод строки в тексте и пробел в подписи
 */
public class InventoryVisibilityExportTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    private ObjImpl m_status;
    private ObjImpl m_sync;

    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        PageImpl room = b.page("Комната^");
        PageImpl out = b.page("Улица.");
        b.link(room, out, "Выйти");
        m_status = b.obj(room, "status", "{Статус}.", "Слава: 0^Сила: 1", true);
        m_sync = b.obj(room, "sync", "{Синхронизация}.", "", true);
        return b.nlb;
    }

    @Test
    public void qsp() throws Exception {
        File file = new File(m_temp.getRoot(), "book.txt");
        book().exportToQSPTextFile(file);
        String qsp = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_16LE).replace("\r\n", "\n");
        assertTrue("невидимый предмет: " + qsp, qsp.contains("nlb_invhide['" + m_sync.getId() + "'] = 1\n"));
        assertFalse(qsp, qsp.contains("nlb_invhide['" + m_status.getId() + "']"));
        assertTrue("ADDOBJ — только видимых: " + qsp, qsp.contains("IF $nlb_in[$ARGS[0]] <> 'nlb_inv' AND $ARGS[1] = 'nlb_inv' AND nlb_invhide[$ARGS[0]] = 0:\n"));
    }

    @Test
    public void urq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.qst");
        book().exportToURQTextFile(file);
        String urq = new String(Files.readAllBytes(file.toPath()), Charset.forName("windows-1251")).replace("\r\n", "\n");
        assertTrue("невидимый предмет: " + urq, urq.contains("nlb_invhide_2=1\n") && !urq.contains("nlb_invhide_1=1"));
        assertTrue(urq, urq.contains("and nlb_invhide_#nlb_po$=0 then inv+ nlbo_#nlb_po$\n"));
        assertTrue("подпись — одной строкой: " + urq, urq.contains("display_use_nlbo_1=\"Слава: 0 Сила: 1\"\n"));
        assertTrue("хвостовой '^' — без пустой строки: " + urq, urq.contains("pln Комната\n"));
    }

    @Test
    public void jsiq() throws Exception {
        File file = new File(m_temp.getRoot(), "book.xml");
        book().exportToJSIQFile(file);
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String sync = xml.substring(xml.indexOf("N.objs['" + m_sync.getId() + "']"));
        assertTrue(xml, sync.substring(0, sync.indexOf("};")).contains("hidden: true,"));
        assertTrue(xml, xml.contains("if (N.objs[id] && N.objs[id].hidden) { continue; }"));
    }

    @Test
    public void asm() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book().exportToASMFile(file);
        String sm = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue("невидимый предмет — пустой абзац: " + sm, sm.contains(":: nlb_o_2_item[::]10-10-0\n<<set $nlb_d = $nlb_d>>\n"));
        assertTrue(sm, sm.contains(":: nlb_o_1_item[::]10-10-0\n<<set $nlb_d = "));
    }
}
