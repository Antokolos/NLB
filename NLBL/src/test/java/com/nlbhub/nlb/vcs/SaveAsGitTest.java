package com.nlbhub.nlb.vcs;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.exception.NLBIOException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.StoredConfig;
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * #163: «Сохранить как» под Git. В папку с уже готовым репозиторием (клон пустого репозитория с
 * сервера) книга сохраняется в него — раньше JGit бросал IllegalStateException «Repository
 * already exists», редактор его не показывал, и книга оставалась привязанной к недосохранённой
 * папке. В папку с чужими файлами и в ту же папку «Сохранить как» отказывает, книга остаётся где была
 */
public class SaveAsGitTest {
    private Path m_dir;
    private NonLinearBookFacade m_facade;
    private File m_original;

    @Before
    public void setUp() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        m_dir = Files.createTempDirectory("saveas-git");
        final Author author = new Author("t", "t@e");
        // как редактор (NLBBMain): пути книги в Windows — через «\», Git их ждёт через «/»
        m_facade = new NonLinearBookFacade(author, new GitAdapterWithPathDecoration(author));
        m_facade.createNewBook();
        m_facade.addPage(m_facade.createPage(10, 10));
        m_original = m_dir.resolve("original").toFile();
        m_facade.saveAs(m_original, new DummyProgressData());
        m_facade.commit("book");
    }

    @After
    public void tearDown() throws IOException {
        try (Stream<Path> walk = Files.walk(m_dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    private static int pagesCount(File folder) {
        final File[] pages = new File(folder, "pages").listFiles();
        return pages == null ? 0 : pages.length;
    }

    @Test
    public void saveAsIntoCloneOfEmptyRepository() throws Exception {
        final File clone = m_dir.resolve("clone").toFile();
        try (Git git = Git.init().setDirectory(clone).call()) {
            Files.writeString(clone.toPath().resolve("README.md"), "book", StandardCharsets.UTF_8);
            git.add().addFilepattern("README.md").call();
            git.commit().setAuthor("t", "t@e").setCommitter("t", "t@e").setMessage("Initial commit")
                    .setSign(false).setGpgConfig(new GpgConfig(new Config())).call();
            final StoredConfig config = git.getRepository().getConfig();
            config.setString("remote", "origin", "url", "https://example.com/book.git");
            config.save();
        }

        m_facade.saveAs(clone, new DummyProgressData());
        assertEquals(clone, m_facade.getNlb().getRootDir());
        assertEquals(1, pagesCount(clone));
        m_facade.commit("book in the clone");

        try (Git git = Git.open(clone)) {
            assertTrue("всё сохранённое закоммичено: " + git.status().call().getUncommittedChanges()
                    + git.status().call().getUntracked(), git.status().call().isClean());
            int commits = 0;
            for (Object ignored : git.log().call()) {
                commits++;
            }
            assertEquals("история клона сохранена", 2, commits);
            assertEquals("remote сохранён", "https://example.com/book.git",
                    git.getRepository().getConfig().getString("remote", "origin", "url"));
            assertTrue(new File(clone, "README.md").exists());
        }
    }

    @Test
    public void saveAsIntoFolderWithAnotherBookIsRefused() throws Exception {
        final File other = m_dir.resolve("other").toFile();
        assertTrue(new File(other, "pages").mkdirs());
        try {
            m_facade.saveAs(other, new DummyProgressData());
            fail("папка с чужими файлами");
        } catch (NLBIOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not empty"));
        }
        assertEquals("книга осталась в прежней папке", m_original, m_facade.getNlb().getRootDir());
        assertEquals(0, pagesCount(other));

        // обычное сохранение после отказа — в прежнюю папку и её репозиторий
        m_facade.addPage(m_facade.createPage(100, 10));
        m_facade.save(false, new DummyProgressData());
        m_facade.commit("second page");
        assertEquals(2, pagesCount(m_original));
        try (Git git = Git.open(m_original)) {
            assertTrue(git.status().call().isClean());
        }
    }

    @Test
    public void saveAsIntoSameFolderIsRefused() throws Exception {
        try {
            m_facade.saveAs(m_original, new DummyProgressData());
            fail("та же папка");
        } catch (NLBIOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Use Save"));
        }
        assertEquals(m_original, m_facade.getNlb().getRootDir());
    }

    @Test
    public void saveAsIntoNewFolderCreatesRepository() throws Exception {
        final File copy = m_dir.resolve("copy").toFile();
        m_facade.saveAs(copy, new DummyProgressData());
        m_facade.commit("copy");
        try (Git git = Git.open(copy)) {
            assertTrue(git.status().call().isClean());
        }
        int pages = 0;
        for (Page page : m_facade.getNlb().getPages().values()) {
            if (!page.isDeleted()) {
                pages++;
            }
        }
        assertEquals(pages, pagesCount(copy));
    }
}
