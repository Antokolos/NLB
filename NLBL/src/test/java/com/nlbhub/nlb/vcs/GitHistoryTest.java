package com.nlbhub.nlb.vcs;

import com.nlbhub.nlb.domain.BookHistory;
import com.nlbhub.nlb.exception.NLBVCSException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.GpgConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * История книги в git: версии первой линии истории, пометка «на сервере / только у вас», слияние
 * при pull — одна версия «с сервера», изменения версии (и несохранённых правок), тексты версии
 */
public class GitHistoryTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 13, 0, 1, 2, 3};
    private Path m_dir;
    private File m_server;
    private File m_mine;

    @Before
    public void setUp() throws Exception {
        m_dir = Files.createTempDirectory("nlb-history");
        m_server = m_dir.resolve("server.git").toFile();
        Git.init().setBare(true).setDirectory(m_server).setInitialBranch("master").call().close();
        m_mine = m_dir.resolve("mine").toFile();
        Git.cloneRepository().setURI(m_server.toURI().toString()).setDirectory(m_mine).call().close();
    }

    @After
    public void tearDown() throws IOException {
        TestDirs.delete(m_dir);
    }

    private static void write(File root, String path, String text) throws IOException {
        final File file = new File(root, path);
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    private static void commit(File root, String author, String message) throws Exception {
        try (Git git = Git.open(root)) {
            git.add().addFilepattern(".").call();
            git.add().addFilepattern(".").setUpdate(true).call();  // и удалённые файлы
            git.commit().setAuthor(author, author + "@e").setCommitter(author, author + "@e").setMessage(message)
                    .setSign(false).setGpgConfig(new GpgConfig(new Config())).call();
        }
    }

    private static void push(File root) throws Exception {
        try (Git git = Git.open(root)) {
            git.push().call();
        }
    }

    private GitAdapter adapter() throws NLBVCSException {
        final GitAdapter adapter = new GitAdapter(new Author("t", "t@e"));
        adapter.openRepo(m_mine.getPath());
        return adapter;
    }

    /**
     * Три версии: книга (страницы p1, p2, картинка), правка текста и перемещение на схеме,
     * удаление страницы p2
     */
    private void threeVersions(boolean pushFirstTwo) throws Exception {
        write(m_mine, "title", "Pantry tales");
        write(m_mine, "pages/p1/caption/en", "Pantry");
        write(m_mine, "pages/p1/text/en", "The shelves are empty.");
        write(m_mine, "pages/p1/coords/left", "100.0");
        write(m_mine, "pages/p2/caption/en", "Gate");
        write(m_mine, "pages/p2/text/en", "A gate.");
        final File image = new File(m_mine, "images/a.png");
        image.getParentFile().mkdirs();
        Files.write(image.toPath(), PNG);
        commit(m_mine, "ann", "First version of the book");
        write(m_mine, "pages/p1/text/en", "Only a bell is left on the shelves.");
        write(m_mine, "pages/p1/coords/left", "140.0");
        commit(m_mine, "ann", "Bell\n\nThe pantry got a bell.");
        if (pushFirstTwo) {
            push(m_mine);
        }
        TestDirs.delete(m_mine.toPath().resolve("pages/p2"));
        commit(m_mine, "bob", "Removed the gate");
    }

    @Test
    public void versionsNewestFirstWithServerState() throws Exception {
        threeVersions(true);
        final GitAdapter adapter = adapter();
        try {
            final List<BookVersion> history = adapter.getHistory(0, 100);
            assertEquals(Arrays.asList("Removed the gate", "Bell", "First version of the book"),
                    history.stream().map(BookVersion::getShortMessage).collect(Collectors.toList()));
            assertEquals("Bell\n\nThe pantry got a bell.", history.get(1).getMessage());
            assertEquals("bob", history.get(0).getAuthorName());
            assertEquals(BookVersion.ServerState.LOCAL_ONLY, history.get(0).getServerState());
            assertEquals(BookVersion.ServerState.ON_SERVER, history.get(1).getServerState());
            assertEquals(BookVersion.ServerState.ON_SERVER, history.get(2).getServerState());
            assertFalse(history.get(0).isFromServer());
            // порциями
            assertEquals("Bell", adapter.getHistory(1, 1).get(0).getShortMessage());
            assertEquals(BookVersion.ServerState.ON_SERVER, adapter.getHistory(1, 1).get(0).getServerState());
            assertTrue(adapter.getHistory(3, 10).isEmpty());
        } finally {
            adapter.closeAdapter();
        }
    }

    @Test
    public void unpublishedBookHasNoServerState() throws Exception {
        final File alone = m_dir.resolve("alone").toFile();
        Git.init().setDirectory(alone).setInitialBranch("master").call().close();
        write(alone, "title", "Book");
        commit(alone, "ann", "First version of the book");
        final GitAdapter adapter = new GitAdapter(new Author("t", "t@e"));
        adapter.openRepo(alone.getPath());
        try {
            assertEquals(BookVersion.ServerState.NO_SERVER, adapter.getHistory(0, 10).get(0).getServerState());
        } finally {
            adapter.closeAdapter();
        }
    }

    @Test
    public void changesOfVersions() throws Exception {
        threeVersions(false);
        final GitAdapter adapter = adapter();
        try {
            final List<BookVersion> history = adapter.getHistory(0, 10);
            // первая версия: всё добавлено; картинка — двоичная, с id содержимого
            final List<BookFileChange> first = adapter.getVersionChanges(history.get(2).getId());
            assertTrue(first.stream().allMatch(c -> c.getKind() == BookFileChange.Kind.ADDED));
            final BookFileChange image = first.stream().filter(c -> c.getPath().equals("images/a.png")).findFirst().get();
            assertTrue(image.isBinary());
            assertNull(image.getAfter());
            assertNotNull(image.getAfterObjectId());
            // правка: текст «было / стало», положение на схеме
            final List<BookFileChange> bell = adapter.getVersionChanges(history.get(1).getId());
            assertEquals(Arrays.asList("pages/p1/coords/left", "pages/p1/text/en"),
                    bell.stream().map(BookFileChange::getPath).sorted().collect(Collectors.toList()));
            final BookFileChange text = bell.stream().filter(c -> c.getPath().endsWith("text/en")).findFirst().get();
            assertEquals(BookFileChange.Kind.MODIFIED, text.getKind());
            assertEquals("The shelves are empty.", text.getBefore());
            assertEquals("Only a bell is left on the shelves.", text.getAfter());
            // удаление: тексты «было»
            final List<BookFileChange> removed = adapter.getVersionChanges(history.get(0).getId());
            assertTrue(removed.stream().allMatch(c -> c.getKind() == BookFileChange.Kind.DELETED));
            assertEquals("Gate", removed.stream().filter(c -> c.getPath().equals("pages/p2/caption/en"))
                    .findFirst().get().getBefore());
            // тексты версии и предыдущей
            assertTrue(adapter.readVersionTexts(history.get(0).getId(), "pages/p2").isEmpty());
            final Map<String, String> before = adapter.readVersionTexts(history.get(0).getId() + "^", "pages/p2");
            assertEquals("Gate", before.get("caption/en"));
            assertEquals("A gate.", before.get("text/en"));
        } finally {
            adapter.closeAdapter();
        }
    }

    @Test
    public void uncommittedChangesAreTheIndex() throws Exception {
        threeVersions(false);
        write(m_mine, "pages/p1/text/en", "A bell and a lamp.");
        try (Git git = Git.open(m_mine)) {
            git.add().addFilepattern("pages/p1/text/en").call();
        }
        // записано, но не добавлено в индекс — в Commit не войдёт
        write(m_mine, "pages/p1/caption/en", "Storeroom");
        final GitAdapter adapter = adapter();
        try {
            final List<BookFileChange> changes = adapter.getVersionChanges(null);
            assertEquals(1, changes.size());
            assertEquals("pages/p1/text/en", changes.get(0).getPath());
            assertEquals("Only a bell is left on the shelves.", changes.get(0).getBefore());
            assertEquals("A bell and a lamp.", changes.get(0).getAfter());
        } finally {
            adapter.closeAdapter();
        }
    }

    @Test
    public void mergeFromServerIsOneVersion() throws Exception {
        threeVersions(true);
        push(m_mine);
        // соавтор: две правки на сервере
        final File theirs = m_dir.resolve("theirs").toFile();
        Git.cloneRepository().setURI(m_server.toURI().toString()).setDirectory(theirs).call().close();
        write(theirs, "pages/p3/caption/en", "Cellar");
        commit(theirs, "cat", "Cellar");
        write(theirs, "pages/p3/text/en", "Dark and damp.");
        commit(theirs, "dan", "Cellar text");
        push(theirs);
        // у нас — своя правка, затем слияние с сервером
        write(m_mine, "pages/p1/caption/en", "Storeroom");
        commit(m_mine, "ann", "Renamed the pantry");
        // как pull: fetch и слияние; коммит слияния — без подписи (не зависит от настроек git машины)
        try (Git git = Git.open(m_mine)) {
            git.fetch().call();
            git.merge().include(git.getRepository().resolve("origin/master")).setCommit(false).call();
        }
        commit(m_mine, "ann", "Merge with the server");
        final GitAdapter adapter = adapter();
        try {
            final List<BookVersion> history = adapter.getHistory(0, 10);
            final BookVersion merge = history.get(0);
            assertTrue(merge.isFromServer());
            assertEquals(2, merge.getServerVersions());
            assertEquals(Arrays.asList("dan", "cat"), merge.getServerAuthors());
            // первая линия: правки соавтора отдельными версиями не показываются
            assertEquals(Arrays.asList("Renamed the pantry", "Removed the gate", "Bell", "First version of the book"),
                    history.subList(1, history.size()).stream().map(BookVersion::getShortMessage)
                            .collect(Collectors.toList()));
            // изменения слияния — то, что пришло с сервера (относительно нашей прежней версии)
            assertEquals(Arrays.asList("pages/p3/caption/en", "pages/p3/text/en"),
                    adapter.getVersionChanges(merge.getId()).stream().map(BookFileChange::getPath).sorted()
                            .collect(Collectors.toList()));
            // ещё не отправлено: слияние и наша правка — только у нас
            assertEquals(BookVersion.ServerState.LOCAL_ONLY, merge.getServerState());
            assertEquals(BookVersion.ServerState.LOCAL_ONLY, history.get(1).getServerState());
            assertEquals(BookVersion.ServerState.ON_SERVER, history.get(2).getServerState());
        } finally {
            adapter.closeAdapter();
        }
    }

    @Test
    public void deletedPageIsNamedFromItsVersion() throws Exception {
        threeVersions(false);
        final GitAdapter adapter = adapter();
        try {
            final String id = adapter.getHistory(0, 1).get(0).getId();
            final BookHistory.VersionTexts texts = (dir, previous) -> {
                try {
                    return adapter.readVersionTexts(previous ? id + "^" : id, dir);
                } catch (NLBVCSException e) {
                    throw new IllegalStateException(e);
                }
            };
            final List<BookHistory.ElementChange> elements =
                    BookHistory.group(null, adapter.getVersionChanges(id), texts);
            assertEquals(1, elements.size());
            assertEquals("Page \"Gate\"", elements.get(0).getTitle());
            assertEquals(BookFileChange.Kind.DELETED, elements.get(0).getKind());
        } finally {
            adapter.closeAdapter();
        }
    }

    @Test
    public void restoreFieldsFromVersions() throws Exception {
        threeVersions(false);
        final GitAdapter adapter = adapter();
        try {
            final List<BookVersion> history = adapter.getHistory(0, 10);
            final String bell = history.get(1).getId();
            final String removed = history.get(0).getId();
            final String first = history.get(2).getId();
            // изменённый текст — как было до версии
            adapter.restoreVersionFiles(bell, List.of("pages/p1/text/en"), true);
            assertEquals("The shelves are empty.", read(m_mine, "pages/p1/text/en"));
            // удалённая страница — её поле снова на диске
            adapter.restoreVersionFiles(removed, List.of("pages/p2/caption/en"), true);
            assertEquals("Gate", read(m_mine, "pages/p2/caption/en"));
            // до первой версии файлов не было — удаляется
            adapter.restoreVersionFiles(first, List.of("title"), true);
            assertFalse(new File(m_mine, "title").exists());
            // всё отмечено в индексе: войдёт в следующий Commit
            assertEquals(Arrays.asList("pages/p1/text/en", "pages/p2/caption/en", "title"),
                    adapter.getVersionChanges(null).stream().map(BookFileChange::getPath).sorted()
                            .collect(Collectors.toList()));
            // «стало» версии — как в ней
            adapter.restoreVersionFiles(bell, List.of("pages/p1/text/en"), false);
            assertEquals("Only a bell is left on the shelves.", read(m_mine, "pages/p1/text/en"));
            // несохранённые правки: «было» — последняя версия, правка отменяется
            adapter.restoreVersionFiles(null, List.of("pages/p2/caption/en", "title"), true);
            assertTrue(adapter.getVersionChanges(null).isEmpty());
            assertFalse(new File(m_mine, "pages/p2/caption/en").exists());
            assertEquals("Pantry tales", read(m_mine, "title"));
            // двоичный файл — побайтно
            Files.write(new File(m_mine, "images/a.png").toPath(), new byte[]{1, 2, 3});
            adapter.restoreVersionFiles(first, List.of("images/a.png"), false);
            assertArrayEquals(PNG, Files.readAllBytes(new File(m_mine, "images/a.png").toPath()));
        } finally {
            adapter.closeAdapter();
        }
    }

    private static String read(File root, String path) throws IOException {
        return Files.readString(new File(root, path).toPath(), StandardCharsets.UTF_8);
    }

    @Test
    public void withoutVcsHistoryIsEmpty() throws Exception {
        final DummyVCSAdapter dummy = new DummyVCSAdapter();
        assertTrue(dummy.getHistory(0, 10).isEmpty());
        assertTrue(dummy.getVersionChanges(null).isEmpty());
        assertTrue(dummy.readVersionTexts(null, "pages/p1").isEmpty());
        // обёртка редактора передаёт вызовы адаптеру git
        threeVersions(false);
        final GitAdapterWithPathDecoration decorated = new GitAdapterWithPathDecoration(new Author("t", "t@e"));
        decorated.openRepo(m_mine.getPath());
        try {
            assertEquals(3, decorated.getHistory(0, 10).size());
            assertArrayEquals(new String[]{"pages/p2/caption/en", "pages/p2/text/en"},
                    decorated.getVersionChanges(decorated.getHistory(0, 1).get(0).getId()).stream()
                            .map(BookFileChange::getPath).sorted().toArray());
        } finally {
            decorated.closeAdapter();
        }
    }
}
