package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.PageImpl;
import com.nlbhub.nlb.domain.export.ExportProgress;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Прогресс экспорта: этап «страницы и объекты» считает каждую страницу и объект, затем — запись
 * файла; слушатель может отменить экспорт (следующий шаг бросает Cancelled); без слушателя
 * экспорт идёт как раньше
 */
public class ExportProgressTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @BeforeClass
    public static void settings() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
    }

    @After
    public void clear() {
        ExportProgress.setListener(null);
    }

    private NonLinearBookImpl book() {
        TestBook b = new TestBook();
        PageImpl room = b.page("Комната.");
        PageImpl out = b.page("Конец.");
        b.link(room, out, "Выйти");
        b.obj(room, "box", "{Ящик}", "Ящик", false);
        return b.nlb;
    }

    @Test
    public void stagesAndCounts() throws Exception {
        List<String> events = new ArrayList<>();
        ExportProgress.setListener(new ExportProgress.Listener() {
            @Override
            public void progress(String format, String stage, int done, int total) {
                events.add(format + "|" + stage + "|" + done + "/" + total);
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        });
        ExportProgress.format("QSP");
        book().exportToQSPTextFile(new File(m_temp.getRoot(), "book.txt"));
        assertEquals("QSP|" + ExportProgress.STAGE_BLOCKS + "|0/3", events.get(0));
        assertTrue(events.toString(), events.contains("QSP|" + ExportProgress.STAGE_BLOCKS + "|3/3"));
        assertEquals("QSP|" + ExportProgress.STAGE_WRITE + "|0/0", events.get(events.size() - 1));
    }

    @Test
    public void cancel() throws Exception {
        ExportProgress.setListener(new ExportProgress.Listener() {
            @Override
            public void progress(String format, String stage, int done, int total) {
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        });
        File file = new File(m_temp.getRoot(), "book.qst");
        try {
            book().exportToURQTextFile(file);
            fail("экспорт не отменён");
        } catch (ExportProgress.Cancelled e) {
            assertTrue("файл не записан", !file.exists());
        }
    }

    @Test
    public void noListener() throws Exception {
        File file = new File(m_temp.getRoot(), "book.sm");
        book().exportToASMFile(file);
        assertTrue(file.isFile());
    }
}
