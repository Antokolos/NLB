package com.nlbhub.nlb.vcs;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.exception.NLBVCSConflictException;
import com.nlbhub.nlb.exception.NLBVCSException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RepositoryState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pull, когда правки соавтора пересекаются с нашими: книга остаётся такой, какой была до pull
 * (без маркеров конфликта в файлах, без незавершённого слияния), а исключение называет
 * пересёкшиеся файлы. Непересекающиеся правки сливаются как прежде.
 */
public class GitPullConflictTest {
    private static final String TEXT = "pages/p1/text/en";
    private static final String LINK = "pages/p1/links/L0";
    private Path m_dir;
    private File m_server;
    private File m_mine;
    private File m_theirs;

    @Before
    public void setUp() throws Exception {
        m_dir = Files.createTempDirectory("nlb-conflict");
        m_server = m_dir.resolve("server.git").toFile();
        Git.init().setBare(true).setDirectory(m_server).setInitialBranch("master").call().close();
        final File seed = m_dir.resolve("seed").toFile();
        try (Git git = Git.cloneRepository().setURI(m_server.toURI().toString()).setDirectory(seed).call()) {
            write(seed, TEXT, "The shelves are empty.");
            write(seed, "pages/p1/coords/left", "100.0");
            write(seed, "pages/p2/text/en", "Gate.");
            write(seed, "pages/p2/coords/left", "500.0");
            // ссылка L0 со страницы p1
            write(seed, "pages/p1/lnkorder", "L0");
            write(seed, LINK + "/target", "p2");
            write(seed, LINK + "/text/en", "To the gate");
            write(seed, LINK + "/coords/left", "1.0");
            git.add().addFilepattern(".").call();
            commit(git, "book");
            git.push().call();
        }
        m_mine = clone("mine");
        m_theirs = clone("theirs");
    }

    @After
    public void tearDown() throws IOException {
        try (Stream<Path> walk = Files.walk(m_dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    private File clone(String name) throws Exception {
        final File dir = m_dir.resolve(name).toFile();
        Git.cloneRepository().setURI(m_server.toURI().toString()).setDirectory(dir).call().close();
        return dir;
    }

    private static void write(File root, String path, String text) throws IOException {
        final File file = new File(root, path);
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    private static String read(File root, String path) throws IOException {
        return Files.readString(new File(root, path).toPath(), StandardCharsets.UTF_8);
    }

    private static void commit(Git git, String message) throws Exception {
        git.commit().setAuthor("t", "t@e").setCommitter("t", "t@e").setMessage(message).setSign(false)
                .setGpgConfig(new GpgConfig(new Config())).call();
    }

    /**
     * Правка соавтора: записать, закоммитить, отправить
     */
    private void theirEdit(String path, String text) throws Exception {
        try (Git git = Git.open(m_theirs)) {
            write(m_theirs, path, text);
            git.add().addFilepattern(".").call();
            commit(git, "their edit");
            git.push().call();
        }
    }

    private void myCommit(String path, String text) throws Exception {
        try (Git git = Git.open(m_mine)) {
            write(m_mine, path, text);
            git.add().addFilepattern(".").call();
            commit(git, "my edit");
        }
    }

    private GitAdapter adapter() throws NLBVCSException {
        final GitAdapter adapter = new GitAdapter(new Author("t", "t@e"));
        adapter.openRepo(m_mine.getPath());
        return adapter;
    }

    @Test
    public void conflictLeavesTheBookAsItWas() throws Exception {
        theirEdit(TEXT, "The shelves are empty. A bell hangs on a hook.");
        myCommit(TEXT, "Only a bell is left on the shelves.");
        final ObjectId before;
        try (Git git = Git.open(m_mine)) {
            before = git.getRepository().resolve("HEAD");
        }
        final GitAdapter adapter = adapter();
        try {
            adapter.pull(null, null, new DummyProgressData());
            fail("правки пересекаются — pull не сливает");
        } catch (NLBVCSConflictException e) {
            assertEquals(Collections.singletonList(TEXT), e.getPaths());
        } finally {
            adapter.closeAdapter();
        }
        assertEquals("файл — наш, без маркеров", "Only a bell is left on the shelves.", read(m_mine, TEXT));
        try (Git git = Git.open(m_mine)) {
            assertEquals("HEAD — прежний", before, git.getRepository().resolve("HEAD"));
            assertEquals("слияние не начато", RepositoryState.SAFE, git.getRepository().getRepositoryState());
            assertTrue("рабочая копия чистая", git.status().call().isClean());
        }
    }

    @Test
    public void conflictRevertsEverythingTheMergeTouched() throws Exception {
        // соавтор: тот же текст, другая страница и новая страница
        theirEdits();
        myCommit(TEXT, "My text.");
        final GitAdapter adapter = adapter();
        try {
            adapter.pull(null, null, new DummyProgressData());
            fail("правки пересекаются");
        } catch (NLBVCSConflictException e) {
            assertFalse(e.isUncommitted());
            assertEquals(Collections.singletonList(TEXT), e.getPaths());
        } finally {
            adapter.closeAdapter();
        }
        assertEquals("My text.", read(m_mine, TEXT));
        assertEquals("чужая правка другой страницы не применена", "Gate.", read(m_mine, "pages/p2/text/en"));
        assertFalse("новой страницы с сервера нет", new File(m_mine, "pages/p3/text/en").exists());
        try (Git git = Git.open(m_mine)) {
            assertTrue(git.status().call().isClean());
            assertEquals(RepositoryState.SAFE, git.getRepository().getRepositoryState());
        }
    }

    @Test
    public void mergeNeedsCommittedChanges() throws Exception {
        theirEdits();
        myCommit("pages/p2/caption/en", "Gate");
        // сохранено, но не закоммичено: страницу подвинули на схеме (с правками сервера не пересекается)
        try (Git git = Git.open(m_mine)) {
            write(m_mine, "pages/p1/coords/left", "250.0");
            git.add().addFilepattern("pages/p1/coords/left").call();
        }
        final GitAdapter adapter = adapter();
        try {
            adapter.pull(null, null, new DummyProgressData());
            fail("слияние — только закоммиченного");
        } catch (NLBVCSConflictException e) {
            assertTrue(e.isUncommitted());
            assertEquals(Collections.singletonList("pages/p1/coords/left"), e.getPaths());
        } finally {
            adapter.closeAdapter();
        }
        assertEquals("The shelves are empty.", read(m_mine, TEXT));
        assertFalse(new File(m_mine, "pages/p3/text/en").exists());
        assertEquals("незакоммиченная правка цела", "250.0", read(m_mine, "pages/p1/coords/left"));
        try (Git git = Git.open(m_mine)) {
            assertEquals(Collections.singleton("pages/p1/coords/left"), git.status().call().getChanged());
            assertEquals(RepositoryState.SAFE, git.getRepository().getRepositoryState());
        }
    }

    private void pullChoosing(VCSAdapter.ConflictChoice choice) throws Exception {
        pullChoosing(choice, TEXT);
    }

    private void pullChoosing(VCSAdapter.ConflictChoice choice, String listed) throws Exception {
        final GitAdapter adapter = adapter();
        try {
            adapter.pull(null, null, new DummyProgressData(), choice);
        } finally {
            adapter.closeAdapter();
        }
        try (Git git = Git.open(m_mine)) {
            assertTrue(git.status().call().isClean());
            assertEquals(RepositoryState.SAFE, git.getRepository().getRepositoryState());
            final org.eclipse.jgit.revwalk.RevCommit head = git.log().setMaxCount(1).call().iterator().next();
            assertEquals("коммит слияния", 2, head.getParentCount());
            assertTrue(head.getFullMessage(), head.getFullMessage().contains(listed));
            assertFalse("без служебных строк JGit", head.getFullMessage().contains("# Conflicts"));
        }
    }

    @Test
    public void keepMineTakesWholeFieldAndMergesTheRest() throws Exception {
        theirEdits();
        myCommit(TEXT, "My text.");
        pullChoosing(VCSAdapter.ConflictChoice.MINE);
        assertEquals("My text.", read(m_mine, TEXT));
        assertEquals("The gate is open.", read(m_mine, "pages/p2/text/en"));
        assertEquals("A new page.", read(m_mine, "pages/p3/text/en"));
    }

    @Test
    public void takeTheirsTakesWholeFieldAndMergesTheRest() throws Exception {
        theirEdits();
        myCommit(TEXT, "My text.");
        myCommit("pages/p1/coords/left", "300.0");
        pullChoosing(VCSAdapter.ConflictChoice.THEIRS);
        assertEquals("Their text.", read(m_mine, TEXT));
        assertEquals("непересёкшаяся своя правка цела", "300.0", read(m_mine, "pages/p1/coords/left"));
        assertEquals("A new page.", read(m_mine, "pages/p3/text/en"));
    }

    @Test
    public void deletedOnServerAndChangedHere() throws Exception {
        try (Git git = Git.open(m_theirs)) {
            git.rm().addFilepattern(TEXT).call();
            commit(git, "their delete");
            git.push().call();
        }
        myCommit(TEXT, "My text.");
        final GitAdapter adapter = adapter();
        try {
            adapter.pull(null, null, new DummyProgressData());
            fail("удаление против правки — пересечение");
        } catch (NLBVCSConflictException e) {
            assertEquals(Collections.singletonList(TEXT), e.getPaths());
        } finally {
            adapter.closeAdapter();
        }
        assertEquals("My text.", read(m_mine, TEXT));
        pullChoosing(VCSAdapter.ConflictChoice.THEIRS);
        assertFalse("взято удаление", new File(m_mine, TEXT).exists());
    }

    private void pullQuietly() throws Exception {
        final GitAdapter adapter = adapter();
        try {
            adapter.pull(null, null, new DummyProgressData());
        } finally {
            adapter.closeAdapter();
        }
        try (Git git = Git.open(m_mine)) {
            assertTrue(git.status().call().isClean());
            assertEquals(RepositoryState.SAFE, git.getRepository().getRepositoryState());
        }
    }

    private void edit(File root, String message, String... pathsAndTexts) throws Exception {
        try (Git git = Git.open(root)) {
            for (int i = 0; i < pathsAndTexts.length; i += 2) {
                final File file = new File(root, pathsAndTexts[i]);
                if (pathsAndTexts[i + 1] == null) {
                    org.eclipse.jgit.util.FileUtils.delete(file, org.eclipse.jgit.util.FileUtils.RECURSIVE);
                } else {
                    write(root, pathsAndTexts[i], pathsAndTexts[i + 1]);
                }
            }
            git.add().addFilepattern(".").call();
            git.add().setUpdate(true).addFilepattern(".").call();
            commit(git, message);
            if (root.equals(m_theirs)) {
                git.push().call();
            }
        }
    }

    @Test
    public void linksAddedByBothAndPageMovedByBothMergeSilently() throws Exception {
        edit(m_theirs, "their link", "pages/p1/links/L2/target", "p2", "pages/p1/links/L2/text/en", "Theirs",
                "pages/p1/lnkorder", "L0\nL2", "pages/p1/coords/left", "150.0");
        edit(m_mine, "my link", "pages/p1/links/L1/target", "p2", "pages/p1/links/L1/text/en", "Mine",
                "pages/p1/lnkorder", "L0\nL1", "pages/p1/coords/left", "200.0");
        pullQuietly();
        assertEquals("обе ссылки: наша, затем с сервера", "L0\nL1\nL2", read(m_mine, "pages/p1/lnkorder"));
        assertEquals("положение на схеме — наше", "200.0", read(m_mine, "pages/p1/coords/left"));
        assertEquals("Theirs", read(m_mine, "pages/p1/links/L2/text/en"));
    }

    @Test
    public void linkDeletedOnServerAndEditedHere() throws Exception {
        edit(m_theirs, "delete link", LINK, null, "pages/p1/lnkorder", null);
        edit(m_mine, "edit link", LINK + "/text/en", "To the open gate");
        final GitAdapter adapter = adapter();
        try {
            adapter.pull(null, null, new DummyProgressData());
            fail("удалено против изменено — решает автор");
        } catch (NLBVCSConflictException e) {
            assertEquals(Collections.singletonList(LINK + "/text/en"), e.getPaths());
        } finally {
            adapter.closeAdapter();
        }
        pullChoosing(VCSAdapter.ConflictChoice.MINE, LINK + "/text/en");
        assertEquals("To the open gate", read(m_mine, LINK + "/text/en"));
        assertEquals("ссылка возвращена целиком", "p2", read(m_mine, LINK + "/target"));
        assertEquals("1.0", read(m_mine, LINK + "/coords/left"));
        assertEquals("и в порядке ссылок", "L0", read(m_mine, "pages/p1/lnkorder"));
    }

    @Test
    public void linkDeletedOnServerAndEditedHereTakeTheirs() throws Exception {
        edit(m_theirs, "delete link", LINK, null, "pages/p1/lnkorder", null);
        // у себя: правка текста и новая модификация ссылки — удаляется вместе со ссылкой
        edit(m_mine, "edit link", LINK + "/text/en", "To the open gate", LINK + "/modifications/M1/type", "ASSIGN",
                LINK + "/modorder", "M1");
        pullChoosing(VCSAdapter.ConflictChoice.THEIRS, LINK + "/text/en");
        assertFalse("ссылки нет целиком", new File(m_mine, LINK).exists());
        assertFalse(new File(m_mine, "pages/p1/lnkorder").exists());
    }

    @Test
    public void pageDeletedOnServerAndMovedHere() throws Exception {
        edit(m_theirs, "delete page", "pages/p2", null);
        edit(m_mine, "move page", "pages/p2/coords/left", "600.0");
        pullQuietly();
        assertFalse("страница удалена — подвинуть её не повод спрашивать", new File(m_mine, "pages/p2").exists());
    }

    private void theirEdits() throws Exception {
        try (Git git = Git.open(m_theirs)) {
            write(m_theirs, TEXT, "Their text.");
            write(m_theirs, "pages/p2/text/en", "The gate is open.");
            write(m_theirs, "pages/p3/text/en", "A new page.");
            git.add().addFilepattern(".").call();
            commit(git, "their edits");
            git.push().call();
        }
    }

    @Test
    public void uncommittedChangesBlockConflictingPull() throws Exception {
        theirEdit(TEXT, "Their text.");
        // сохранено (в индексе), но не закоммичено — как после «Сохранить» в редакторе
        try (Git git = Git.open(m_mine)) {
            write(m_mine, TEXT, "My unsaved text.");
            git.add().addFilepattern(TEXT).call();
        }
        final GitAdapter adapter = adapter();
        try {
            adapter.pull(null, null, new DummyProgressData());
            fail("незакоммиченная правка того же файла — pull не сливает");
        } catch (NLBVCSConflictException e) {
            assertTrue(e.isUncommitted());
            assertEquals(Collections.singletonList(TEXT), e.getPaths());
        } finally {
            adapter.closeAdapter();
        }
        assertEquals("правка не потеряна", "My unsaved text.", read(m_mine, TEXT));
    }

    @Test
    public void separateEditsAreMerged() throws Exception {
        theirEdit("pages/p2/text/en", "The gate is open.");
        myCommit(TEXT, "Only a bell is left on the shelves.");
        final GitAdapter adapter = adapter();
        try {
            adapter.pull(null, null, new DummyProgressData());
        } finally {
            adapter.closeAdapter();
        }
        assertEquals("The gate is open.", read(m_mine, "pages/p2/text/en"));
        assertEquals("Only a bell is left on the shelves.", read(m_mine, TEXT));
        try (Git git = Git.open(m_mine)) {
            assertFalse("слияние закоммичено", git.getRepository().getRepositoryState() != RepositoryState.SAFE);
        }
    }
}
