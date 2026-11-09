package com.nlbhub.nlb.vcs;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.exception.NLBVCSException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Скачивание книги без установки git: clone с внешними модулями на любую глубину (книга -> модуль ->
 * модуль модуля, как у «Шапочки»), каждый модуль — на ветке (иначе push из редактора невозможен);
 * не удалось — недоклонированная папка удаляется.
 */
public class GitCloneTest {
    private Path m_dir;

    @Before
    public void setUp() throws IOException {
        m_dir = Files.createTempDirectory("nlb-clone");
    }

    @After
    public void tearDown() throws IOException {
        TestDirs.delete(m_dir);
    }

    private File repo(String name, String file) throws Exception {
        File dir = m_dir.resolve("server").resolve(name).toFile();
        try (Git git = Git.init().setDirectory(dir).setInitialBranch("master").call()) {
            Files.writeString(new File(dir, file).toPath(), name, StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            commit(git, name);
        }
        return dir;
    }

    private static void commit(Git git, String message) throws Exception {
        git.commit().setAuthor("t", "t@e").setCommitter("t", "t@e").setMessage(message).setSign(false)
                .setGpgConfig(new GpgConfig(new Config())).call();
    }

    private static void addModule(File parent, String path, File module) throws Exception {
        try (Git git = Git.open(parent)) {
            git.submoduleAdd().setPath(path).setURI(module.toURI().toString()).call().close();
            commit(git, "module " + path);
        }
    }

    @Test
    public void bookIsClonedWithModulesOnAnyDepth() throws Exception {
        File leaf = repo("leaf", "title");
        File mid = repo("mid", "title");
        File book = repo("book", "startpoint");
        addModule(mid, "modules/leaf", leaf);
        addModule(book, "modules/mid", mid);

        File target = m_dir.resolve("work/book").toFile();
        new GitAdapter(new Author("t", "t@e")).cloneRepo(book.toURI().toString(), target.getPath(), null, null, new DummyProgressData());

        assertTrue(new File(target, "startpoint").isFile());
        assertTrue("модуль", new File(target, "modules/mid/title").isFile());
        assertTrue("модуль модуля", new File(target, "modules/mid/modules/leaf/title").isFile());
        for (String path : new String[]{"modules/mid", "modules/mid/modules/leaf"}) {
            try (Repository sub = new FileRepositoryBuilder().setWorkTree(new File(target, path)).build()) {
                assertEquals(path + " — на ветке", "refs/heads/master", sub.getFullBranch());
            }
        }
    }

    @Test
    public void failedCloneLeavesNoFolder() {
        File target = m_dir.resolve("work/missing").toFile();
        try {
            new GitAdapter(new Author("t", "t@e")).cloneRepo(
                    m_dir.resolve("server/no-such-book").toUri().toString(), target.getPath(), null, null, new DummyProgressData()
            );
            fail("книги нет — ошибка");
        } catch (NLBVCSException e) {
            assertFalse("недоклонированная папка удалена", target.exists());
        }
    }
}
