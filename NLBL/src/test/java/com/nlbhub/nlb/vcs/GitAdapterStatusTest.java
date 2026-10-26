package com.nlbhub.nlb.vcs;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

/**
 * Статусы файлов книги считаются в фоне после openRepo, а операции адаптера их дожидаются:
 * результат тот же, что был при подсчёте сразу при открытии.
 */
public class GitAdapterStatusTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @Test
    public void statusesAreReadyForOperations() throws Exception {
        File book = m_temp.newFolder("book");
        try (Git git = Git.init().setDirectory(book).call()) {
            StoredConfig config = git.getRepository().getConfig();
            config.setString("gpg", null, "format", "openpgp");  // глобальные настройки машины тесту не нужны
            config.setBoolean("commit", null, "gpgsign", false);
            config.save();
            write(book, "clean", "1");
            write(book, "changed", "1");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("book").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
        }
        write(book, "changed", "изменён");  // другой длины: при той же длине и секунде JGit не видит правку (racy git)
        write(book, "fresh", "1");

        GitAdapter adapter = new GitAdapter(new Author("t", "t@example.com"));
        adapter.openRepo(book.getCanonicalPath());
        assertEquals(VCSAdapter.Status.Clean, adapter.getStatus("clean"));
        assertEquals(VCSAdapter.Status.Modified, adapter.getStatus("changed"));
        assertEquals(VCSAdapter.Status.Unknown, adapter.getStatus("fresh"));
        adapter.add("fresh");
        assertEquals(VCSAdapter.Status.Added, adapter.getStatus("fresh"));
        adapter.closeAdapter();

        // закрытие сразу после открытия дожидается фонового подсчёта, а не рвёт его
        GitAdapter other = new GitAdapter(new Author("t", "t@example.com"));
        other.openRepo(book.getCanonicalPath());
        other.closeAdapter();
    }

    private static void write(File dir, String name, String text) throws Exception {
        Files.write(new File(dir, name).toPath(), text.getBytes("UTF-8"));
    }
}
