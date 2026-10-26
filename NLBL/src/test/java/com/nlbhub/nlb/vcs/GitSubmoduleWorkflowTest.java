package com.nlbhub.nlb.vcs;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.exception.NLBVCSException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Внешний модуль книги — подмодуль git — в операциях VCS редактора (GitAdapter), на локальных
 * «серверах» (голых репозиториях): автор A открывает модуль (HEAD переводится с отсоединённого на
 * ветку), правит и коммитит его, коммитит книгу (в коммит попадает новый коммит модуля), делает
 * push книги (сначала уходит модуль); автор B делает pull книги — модуль обновлён и на ветке.
 */
public class GitSubmoduleWorkflowTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @Test
    public void editModuleCommitPushAndPull() throws Exception {
        File moduleServer = m_temp.newFolder("module.git");
        File bookServer = m_temp.newFolder("book.git");
        Git.init().setBare(true).setDirectory(moduleServer).setInitialBranch("master").call().close();
        Git.init().setBare(true).setDirectory(bookServer).setInitialBranch("master").call().close();

        // модуль и книга с ним на «серверах»
        File moduleWork = m_temp.newFolder("module-work");
        try (Git git = Git.init().setDirectory(moduleWork).setInitialBranch("master").call()) {
            neutralConfig(git.getRepository());
            write(moduleWork, "startpoint", "p1");
            git.add().addFilepattern(".").call();
            commit(git, "module");
            git.push().setRemote(moduleServer.toURI().toString()).setRefSpecs(new RefSpec("master:master")).call();
        }
        File bookWork = m_temp.newFolder("book-work");
        try (Git git = Git.init().setDirectory(bookWork).setInitialBranch("master").call()) {
            neutralConfig(git.getRepository());
            write(bookWork, "startpoint", "q1");
            git.add().addFilepattern("startpoint").call();
            git.submoduleAdd().setPath("modules/m").setURI(moduleServer.toURI().toString()).call().close();
            commit(git, "book");
            git.push().setRemote(bookServer.toURI().toString()).setRefSpecs(new RefSpec("master:master")).call();
        }

        File bookA = cloneWithModules(bookServer, "a");
        File bookB = cloneWithModules(bookServer, "b");
        File moduleA = new File(bookA, "modules/m");
        try (Repository sub = open(moduleA)) {
            assertTrue("после клонирования модуль в отсоединённом HEAD", !sub.getFullBranch().startsWith(Constants.R_HEADS));
        }

        // автор A: модуль открыт как книга — HEAD на ветке; правка и commit модуля
        GitAdapter module = new GitAdapter(new Author("a", "a@example.com"));
        module.openRepo(moduleA.getCanonicalPath());
        try (Repository sub = open(moduleA)) {
            assertEquals("refs/heads/master", sub.getFullBranch());
        }
        write(moduleA, "startpoint", "p2 — правка модуля");
        module.add("startpoint");
        module.commit("Правка модуля");
        module.closeAdapter();
        ObjectId moduleCommit;
        try (Repository sub = open(moduleA)) {
            moduleCommit = sub.resolve(Constants.HEAD);
        }

        // commit книги — с новым коммитом модуля; push — сначала модуль, потом книга
        GitAdapter book = new GitAdapter(new Author("a", "a@example.com"));
        book.openRepo(bookA.getCanonicalPath());
        book.commit("Книга с новым модулем");
        assertEquals(moduleCommit, recordedModule(bookA));
        book.push("u", "p", new DummyProgressData());
        book.closeAdapter();
        try (Repository server = new FileRepositoryBuilder().setGitDir(moduleServer).build()) {
            assertEquals("модуль отправлен", moduleCommit, server.resolve("master"));
        }
        try (Repository server = new FileRepositoryBuilder().setGitDir(bookServer).build();
             Repository local = open(bookA)) {
            assertEquals("книга отправлена", local.resolve(Constants.HEAD), server.resolve("master"));
        }

        // автор B: pull книги — модуль на новом коммите и на ветке
        GitAdapter bookOfB = new GitAdapter(new Author("b", "b@example.com"));
        bookOfB.openRepo(bookB.getCanonicalPath());
        bookOfB.pull("u", "p", new DummyProgressData());
        bookOfB.closeAdapter();
        File moduleB = new File(bookB, "modules/m");
        try (Repository sub = open(moduleB)) {
            assertEquals(moduleCommit, sub.resolve(Constants.HEAD));
            assertEquals("refs/heads/master", sub.getFullBranch());
        }
        assertEquals("p2 — правка модуля", new String(Files.readAllBytes(new File(moduleB, "startpoint").toPath()), "UTF-8"));

        // отклонённый push (на сервере коммит, которого нет у автора B) — ошибка, а не тишина
        GitAdapter again = new GitAdapter(new Author("a", "a@example.com"));
        again.openRepo(bookA.getCanonicalPath());
        write(bookA, "startpoint", "q2");
        again.add("startpoint");
        again.commit("A");
        again.push("u", "p", new DummyProgressData());
        again.closeAdapter();
        GitAdapter behind = new GitAdapter(new Author("b", "b@example.com"));
        behind.openRepo(bookB.getCanonicalPath());
        write(bookB, "title", "B");
        behind.add("title");
        behind.commit("B");
        try {
            behind.push("u", "p", new DummyProgressData());
            fail("push поверх чужого коммита должен быть отклонён");
        } catch (NLBVCSException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("rejected"));
        }
        behind.closeAdapter();
    }

    private File cloneWithModules(File server, String name) throws Exception {
        File dir = new File(m_temp.getRoot(), name);
        try (Git git = Git.cloneRepository().setURI(server.toURI().toString()).setDirectory(dir).setCloneSubmodules(true).call()) {
            neutralConfig(git.getRepository());
        }
        try (Repository sub = open(new File(dir, "modules/m"))) {
            neutralConfig(sub);
        }
        return dir;
    }

    private static ObjectId recordedModule(File book) throws Exception {
        try (Repository repo = open(book); SubmoduleWalk walk = SubmoduleWalk.forIndex(repo)) {
            assertTrue(walk.next());
            RevCommit head = repo.parseCommit(repo.resolve(Constants.HEAD));
            assertNotNull(head);
            return SubmoduleWalk.forPath(repo, head.getTree(), "modules/m").getObjectId();
        }
    }

    private static Repository open(File workTree) throws Exception {
        return new FileRepositoryBuilder().setWorkTree(workTree).build();
    }

    private static void commit(Git git, String message) throws Exception {
        git.commit().setMessage(message).setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
    }

    /**
     * Глобальные настройки git машины (подпись коммитов, gpg.format, которого не знает JGit)
     * тесту не нужны
     */
    private static void neutralConfig(Repository repo) throws Exception {
        StoredConfig config = repo.getConfig();
        config.setString("gpg", null, "format", "openpgp");
        config.setBoolean("commit", null, "gpgsign", false);
        config.setString("user", null, "name", "t");
        config.setString("user", null, "email", "t@example.com");
        config.save();
    }

    private static void write(File dir, String name, String text) throws Exception {
        Files.write(new File(dir, name).toPath(), text.getBytes("UTF-8"));
    }
}
