package com.nlbhub.nlb.api;

import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * #166: книга хранит наибольшую версию NLBB, которым её сохраняли (nlbversion); более старый
 * редактор советует обновиться и версию не понижает
 */
public class NLBVersionTest {
    private Path m_dir;

    @Before
    public void setUp() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        m_dir = Files.createTempDirectory("nlbversion");
    }

    @After
    public void tearDown() throws IOException {
        try (Stream<Path> walk = Files.walk(m_dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    @Test
    public void comparesVersions() {
        assertTrue(NLBVersion.compare("1.0.7", "1.0.6-SNAPSHOT") > 0);
        assertEquals("SNAPSHOT — тот же код, что выпуск", 0, NLBVersion.compare("1.0.6-SNAPSHOT", "1.0.6"));
        assertTrue(NLBVersion.compare("1.10.0", "1.9.9") > 0);
        assertEquals(0, NLBVersion.compare("1.0", "1.0.0"));
        assertEquals("1.0.7", NLBVersion.max("1.0.7", "1.0.6"));
        assertEquals("1.0.6", NLBVersion.max("", "1.0.6"));
        assertEquals("1.0.6", NLBVersion.max("непонятно", "1.0.6"));
        assertFalse(NLBVersion.isKnown(""));
    }

    @Test
    public void currentVersionComesFromBuild() {
        assertTrue("версия из pom.xml подставлена при сборке: " + NLBVersion.current(),
                NLBVersion.isKnown(NLBVersion.current()));
    }

    private NonLinearBookFacade open(File folder) throws Exception {
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        facade.createNewBook();
        facade.load(folder.getPath(), new DummyProgressData());
        return facade;
    }

    private static String nlbVersionFile(File folder) throws IOException {
        return Files.readString(new File(folder, NonLinearBookImpl.NLB_VERSION_FILE_NAME).toPath(), StandardCharsets.UTF_8);
    }

    @Test
    public void bookStoresNewestEditorVersion() throws Exception {
        final File book = m_dir.resolve("book").toFile();
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        facade.createNewBook();
        facade.addPage(facade.createPage(10, 10));
        facade.saveAs(book, new DummyProgressData());
        assertEquals("сохранение записывает версию редактора", NLBVersion.current(), nlbVersionFile(book));
        assertNull("своя версия — без предупреждения", open(book).getNewerNlbVersion());

        // книгу сохранили более новым NLBB
        Files.writeString(new File(book, NonLinearBookImpl.NLB_VERSION_FILE_NAME).toPath(), "99.0.0");
        final NonLinearBookFacade older = open(book);
        assertEquals("совет обновиться", "99.0.0", older.getNewerNlbVersion());
        older.save(false, new DummyProgressData());
        assertEquals("старый редактор версию не понижает", "99.0.0", nlbVersionFile(book));

        // книга от более старого NLBB — версия повышается до этой, без предупреждения
        Files.writeString(new File(book, NonLinearBookImpl.NLB_VERSION_FILE_NAME).toPath(), "0.9");
        final NonLinearBookFacade newer = open(book);
        assertNull(newer.getNewerNlbVersion());
        newer.save(false, new DummyProgressData());
        assertEquals(NLBVersion.current(), nlbVersionFile(book));
    }

    @Test
    public void bookWithoutVersionFileOpensSilently() throws Exception {
        final File book = m_dir.resolve("old").toFile();
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        facade.createNewBook();
        facade.saveAs(book, new DummyProgressData());
        assertTrue(new File(book, NonLinearBookImpl.NLB_VERSION_FILE_NAME).delete());
        assertNull("книга от редактора до #166", open(book).getNewerNlbVersion());
    }
}
