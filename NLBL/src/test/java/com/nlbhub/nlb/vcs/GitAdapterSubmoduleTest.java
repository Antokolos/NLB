package com.nlbhub.nlb.vcs;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Внешний модуль книги, подключённый подмодулем git (modules/<имя>), открывается как отдельная
 * книга: в подмодуле .git — файл «gitdir: ...», а не каталог. Новые файлы модуля попадают в
 * индекс подмодуля, а не книги-родителя.
 */
public class GitAdapterSubmoduleTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @Test
    public void submoduleBookOpensAndAddsToItsOwnIndex() throws Exception {
        File moduleOrigin = m_temp.newFolder("module");
        try (Git git = Git.init().setDirectory(moduleOrigin).call()) {
            neutralConfig(git);
            Files.write(new File(moduleOrigin, "startpoint").toPath(), "p".getBytes("UTF-8"));
            git.add().addFilepattern("startpoint").call();
            git.commit().setMessage("module").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
        }
        File book = m_temp.newFolder("book");
        try (Git git = Git.init().setDirectory(book).call()) {
            neutralConfig(git);
            Files.write(new File(book, "startpoint").toPath(), "q".getBytes("UTF-8"));
            git.add().addFilepattern("startpoint").call();
            git.submoduleAdd().setPath("modules/m").setURI(moduleOrigin.toURI().toString()).call().close();
            git.commit().setMessage("book").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
        }
        File moduleDir = new File(book, "modules/m");
        assertTrue("в подмодуле .git — файл", new File(moduleDir, ".git").isFile());

        GitAdapter adapter = new GitAdapter(new Author("t", "t@example.com"));
        adapter.openRepo(moduleDir.getCanonicalPath());
        assertEquals(VCSAdapter.Status.Clean, adapter.getStatus("startpoint"));
        Files.write(new File(moduleDir, "title").toPath(), "Модуль".getBytes("UTF-8"));
        adapter.add("title");
        adapter.closeAdapter();

        try (Git module = new Git(new FileRepositoryBuilder().setWorkTree(moduleDir).build()); Git parent = Git.open(book)) {
            assertTrue(module.status().call().getAdded().contains("title"));
            assertTrue(parent.status().call().getAdded().isEmpty());
        }
    }

    /**
     * Глобальные настройки git машины (например, gpg.format, которого не знает JGit) тесту не нужны
     */
    private static void neutralConfig(Git git) throws Exception {
        StoredConfig config = git.getRepository().getConfig();
        config.setString("gpg", null, "format", "openpgp");
        config.setBoolean("commit", null, "gpgsign", false);
        config.save();
    }
}
