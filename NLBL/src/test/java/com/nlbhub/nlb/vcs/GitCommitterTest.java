package com.nlbhub.nlb.vcs;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
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
 * Кто коммитит из редактора: user.name и user.email в настройках репозитория книги (.git/config —
 * в клоне, не в файлах книги), задаются в свойствах книги; commit — от этого имени.
 */
public class GitCommitterTest {
    private Path m_dir;

    @Before
    public void setUp() throws Exception {
        m_dir = Files.createTempDirectory("nlb-committer");
        Files.writeString(m_dir.resolve("title"), "Книга", StandardCharsets.UTF_8);
        try (Git git = Git.init().setDirectory(m_dir.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setAuthor("init", "init@example.com").setMessage("init").setSign(false)
                    .setGpgConfig(new GpgConfig(new Config())).call();
        }
    }

    @After
    public void tearDown() throws IOException {
        try (Stream<Path> walk = Files.walk(m_dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    @Test
    public void committerIsStoredInRepositoryConfigAndUsedForCommits() throws Exception {
        GitAdapter adapter = new GitAdapter(new Author("author", "author@example.com"));
        assertFalse("репозиторий не открыт — записать некуда", adapter.canSetCommitter());
        adapter.openRepo(m_dir.toString());
        assertTrue(adapter.canSetCommitter());

        adapter.setCommitter(new Author("Анна Автор", "anna@example.com"));
        assertEquals("Анна Автор", adapter.getCommitter().getName());
        assertEquals("anna@example.com", adapter.getCommitter().getEmail());
        // в .git/config клона, а не в файлах книги
        FileBasedConfig local = new FileBasedConfig(m_dir.resolve(".git/config").toFile(), FS.DETECTED);
        local.load();
        assertEquals("Анна Автор", local.getString("user", null, "name"));
        try (Git git = Git.open(m_dir.toFile())) {
            assertTrue(git.status().call().isClean());
        }

        Files.writeString(m_dir.resolve("title"), "Книга 2", StandardCharsets.UTF_8);
        adapter.add("title");
        adapter.commit("правка");
        try (Git git = Git.open(m_dir.toFile())) {
            RevCommit head = git.log().setMaxCount(1).call().iterator().next();
            assertEquals("Анна Автор", head.getAuthorIdent().getName());
            assertEquals("anna@example.com", head.getAuthorIdent().getEmailAddress());
            assertEquals("Анна Автор", head.getCommitterIdent().getName());
        }

        // явный автор (внешние модули коммитятся от имени того же человека, что и книга)
        Files.writeString(m_dir.resolve("title"), "Книга 3", StandardCharsets.UTF_8);
        adapter.add("title");
        adapter.commit("модуль", new Author("Борис", "boris@example.com"));
        try (Git git = Git.open(m_dir.toFile())) {
            assertEquals("Борис", git.log().setMaxCount(1).call().iterator().next().getAuthorIdent().getName());
        }

        // пустые поля — настройка убирается из .git/config (действуют общие настройки git)
        adapter.setCommitter(null);
        local.load();
        assertNull(local.getString("user", null, "name"));
        assertNull(local.getString("user", null, "email"));
        adapter.closeAdapter();
    }
}
