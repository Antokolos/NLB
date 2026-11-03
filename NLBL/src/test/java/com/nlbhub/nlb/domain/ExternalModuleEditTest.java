package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.player.ReferenceDataTest;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import com.nlbhub.nlb.vcs.GitAdapter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Внешний модуль (modules/<имя>) правится прямо в книге: вкладка показывает копию модуля, а при
 * сохранении книги правки из копии переносятся в книгу модуля и она сохраняется в modules/<имя>
 * (своим репозиторием); commit книги сначала коммитит модуль. Только для чтения: внешний модуль
 * внутри внешнего модуля и второе подключение того же модуля.
 * <p>
 * Книга собирается из bookU (NLB_TEST_BOOKS): главная книга, её внешний модуль ext (тоже bookU) и
 * внутри него — внешний модуль inner. Нет bookU — пропуск.
 */
public class ExternalModuleEditTest {
    private Path m_dir;
    private File m_main;
    private File m_ext;
    private String m_extPage;
    private String m_secondExtPage;
    private String m_innerPage;

    @Before
    public void setUp() throws Exception {
        File bookU = new File(ReferenceDataTest.booksDir(), "bookU");
        Assume.assumeTrue("Нет книги " + bookU, new File(bookU, "startpoint").isFile());
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        m_dir = Files.createTempDirectory("nlb-extmod");
        m_main = m_dir.resolve("main").toFile();
        m_ext = new File(m_main, "modules/ext");
        copy(bookU.toPath(), m_main.toPath());
        copy(bookU.toPath(), m_ext.toPath());
        copy(bookU.toPath(), new File(m_ext, "modules/inner").toPath());
        // две страницы главной книги подключают ext, одна страница ext — inner
        List<String> pages = pageIds(m_main);
        m_extPage = pages.get(0);
        m_secondExtPage = pages.get(1);
        makeExternal(new File(m_main, "pages/" + m_extPage), "ext");
        makeExternal(new File(m_main, "pages/" + m_secondExtPage), "ext");
        m_innerPage = pageIds(m_ext).get(0);
        makeExternal(new File(m_ext, "pages/" + m_innerPage), "inner");
    }

    @After
    public void tearDown() throws IOException {
        if (m_dir != null) {
            try (Stream<Path> walk = Files.walk(m_dir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    @Test
    public void editedExternalModuleIsSavedToItsBook() throws Exception {
        NonLinearBookFacade facade = open(new DummyVCSAdapter());
        NonLinearBookFacade module = facade.createModuleFacade(m_extPage);
        assertNull(module.getReadOnlyReason());
        assertEquals("ext", module.getEditedExternalModuleName());

        // второе подключение того же модуля — только для чтения (две копии разошлись бы)
        NonLinearBookFacade second = facade.createModuleFacade(m_secondExtPage);
        assertNotNull(second.getReadOnlyReason());
        assertNull(second.getEditedExternalModuleName());
        // внешний модуль внутри внешнего — только для чтения: он в modules/ того модуля
        NonLinearBookFacade inner = module.createModuleFacade(m_innerPage);
        assertNotNull(inner.getReadOnlyReason());
        assertTrue(inner.getReadOnlyReason(), inner.getReadOnlyReason().contains("another external module"));

        // правка на вкладке модуля: новая стартовая точка и удалённая страница
        String start = module.getNlb().getStartPoint();
        String newStart = null;
        String deleted = null;
        for (Page page : module.getNlb().getPages().values()) {
            if (page.getId().equals(start) || page.getId().equals(m_innerPage)) {
                continue;
            }
            if (newStart == null) {
                newStart = page.getId();
            } else if (deleted == null && page.getModule().isEmpty()) {
                deleted = page.getId();
            }
        }
        assertNotNull(newStart);
        assertNotNull(deleted);
        module.changeStartPoint(newStart);
        module.deleteNode(module.getNlb().getPageById(deleted));
        assertTrue(facade.hasChanges());
        String title = read(new File(m_ext, "title"));
        facade.save(false, new DummyProgressData());

        // правки — в книге модуля modules/ext; её свойства и вложенный модуль — как были
        assertEquals(newStart, read(new File(m_ext, "startpoint")));
        assertFalse(new File(m_ext, "pages/" + deleted).exists());
        assertEquals(title, read(new File(m_ext, "title")));
        assertTrue(new File(m_ext, "modules/inner/startpoint").isFile());
        assertEquals("inner", read(new File(m_ext, "pages/" + m_innerPage + "/modname")));
        // главная книга копию модуля к себе не пишет
        assertFalse(new File(m_main, "pages/" + m_extPage + "/module").exists());
        assertFalse(facade.hasChanges());

        // книга открывается заново — модуль уже с правками
        NonLinearBookFacade reopened = open(new DummyVCSAdapter());
        Page extPage = reopened.getNlb().getPageById(m_extPage);
        assertEquals(newStart, extPage.getModule().getStartPoint());
        // getPageById искал бы и в книге-родителе (она тоже из bookU, с теми же id)
        assertFalse(extPage.getModule().getPages().containsKey(deleted));
        // модуль открывается и отдельной книгой
        NonLinearBookFacade asBook = new NonLinearBookFacade(new Author("test", ""), new DummyVCSAdapter());
        asBook.createNewBook();
        asBook.load(m_ext.getPath(), new DummyProgressData());
        assertEquals(newStart, asBook.getNlb().getStartPoint());
    }

    @Test
    public void commitOfBookCommitsEditedExternalModuleFirst() throws Exception {
        Author author = new Author("test", "test@example.com");
        try (Git ext = Git.init().setDirectory(m_ext).call()) {
            ext.add().addFilepattern(".").call();
            ext.commit().setAuthor("test", "test@example.com").setMessage("модуль").setSign(false)
                    .setGpgConfig(new org.eclipse.jgit.lib.GpgConfig(new org.eclipse.jgit.lib.Config())).call();
        }
        try (Git main = Git.init().setDirectory(m_main).call()) {
            main.add().addFilepattern(".").call();
            main.commit().setAuthor("test", "test@example.com").setMessage("книга").setSign(false)
                    .setGpgConfig(new org.eclipse.jgit.lib.GpgConfig(new org.eclipse.jgit.lib.Config())).call();
        }
        NonLinearBookFacade facade = open(new GitAdapter(author));
        NonLinearBookFacade module = facade.createModuleFacade(m_extPage);
        assertNull(module.getReadOnlyReason());
        String start = module.getNlb().getStartPoint();
        String newStart = null;
        for (Page page : module.getNlb().getPages().values()) {
            if (!page.getId().equals(start) && !page.getId().equals(m_innerPage)) {
                newStart = page.getId();
                break;
            }
        }
        module.changeStartPoint(newStart);
        facade.save(false, new DummyProgressData());
        facade.commit("правка модуля из книги");
        try (Git ext = Git.open(m_ext)) {
            RevCommit head = ext.log().setMaxCount(1).call().iterator().next();
            assertEquals("правка модуля из книги", head.getFullMessage());
            assertTrue("startpoint закоммичен в репозиторий модуля", ext.status().call().isClean()
                    || !ext.status().call().getModified().contains("startpoint"));
        }
        try (Git main = Git.open(m_main)) {
            RevCommit head = main.log().setMaxCount(1).call().iterator().next();
            assertEquals("правка модуля из книги", head.getFullMessage());
        }
        facade.clear();
    }

    private NonLinearBookFacade open(com.nlbhub.nlb.vcs.VCSAdapter vcs) throws Exception {
        NonLinearBookFacade facade = new NonLinearBookFacade(new Author("test", "test@example.com"), vcs);
        facade.createNewBook();
        facade.load(m_main.getPath(), new DummyProgressData());
        return facade;
    }

    private static void makeExternal(File pageDir, String module) throws IOException {
        Files.writeString(new File(pageDir, "modname").toPath(), module, StandardCharsets.UTF_8);
        Files.writeString(new File(pageDir, "extmod").toPath(), "true", StandardCharsets.UTF_8);
    }

    private static List<String> pageIds(File book) throws IOException {
        String start = read(new File(book, "startpoint"));
        List<String> result = new ArrayList<>();
        try (Stream<Path> list = Files.list(new File(book, "pages").toPath())) {
            list.map(p -> p.getFileName().toString()).filter(id -> !id.equals(start)).sorted().forEach(result::add);
        }
        return result;
    }

    private static String read(File file) throws IOException {
        return Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (p.getFileName().toString().equals(".git")) {
                    continue;
                }
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target);
                }
            }
        }
    }
}
