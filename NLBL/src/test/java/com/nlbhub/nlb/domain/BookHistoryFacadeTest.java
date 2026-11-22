package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.player.ReferenceDataTest;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.BookFileChange;
import com.nlbhub.nlb.vcs.BookVersion;
import com.nlbhub.nlb.vcs.GitAdapterWithPathDecoration;
import com.nlbhub.nlb.vcs.TestDirs;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.GpgConfig;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * История книги через фасад редактора на bookU в git: имена — из открытой книги, удалённая
 * страница — по своему заголовку из версии, несохранённые правки
 */
public class BookHistoryFacadeTest {
    private static final String PANTRY = "pages/0098f758-2bfd-5605-bdca-705985829a50";
    private Path m_dir;
    private File m_book;
    private GitAdapterWithPathDecoration m_adapter;

    @Before
    public void setUp() throws Exception {
        final File bookU = new File(ReferenceDataTest.booksDir(), "bookU");
        Assume.assumeTrue("Нет книги " + bookU, new File(bookU, "startpoint").isFile());
        m_dir = Files.createTempDirectory("nlb-history-book");
        m_book = m_dir.resolve("bookU").toFile();
        copy(bookU.toPath(), m_book.toPath());
        try (Git git = Git.init().setDirectory(m_book).setInitialBranch("master").call()) {
            git.add().addFilepattern(".").call();
            commit(git, "First version of the book");
        }
    }

    @After
    public void tearDown() throws IOException {
        TestDirs.delete(m_dir);
    }

    private static void copy(Path source, Path to) throws IOException {
        final Path from = source.toRealPath();  // папка книг может быть символической ссылкой
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                final Path relative = from.relativize(path);
                if (relative.getNameCount() > 0 && relative.getName(0).toString().equals(".git")) {
                    continue;  // свой репозиторий копии — новый
                }
                final Path target = to.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void commit(Git git, String message) throws Exception {
        git.add().addFilepattern(".").setUpdate(true).call();
        git.commit().setAuthor("ann", "ann@e").setCommitter("ann", "ann@e").setMessage(message).setSign(false)
                .setGpgConfig(new GpgConfig(new Config())).call();
    }

    private NonLinearBookFacade open() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        m_adapter = new GitAdapterWithPathDecoration(new Author("ann", "ann@e"));
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("ann", "ann@e"), m_adapter);
        facade.createNewBook();
        facade.load(m_book.getPath(), new DummyProgressData());
        return facade;
    }

    @Test
    public void versionsOfARealBook() throws Exception {
        // правка текста страницы и удаление другой страницы (со ссылками на неё — как редактор)
        try (Git git = Git.open(m_book)) {
            final File text = new File(m_book, PANTRY + "/text/en");
            Files.writeString(text.toPath(), Files.readString(text.toPath(), StandardCharsets.UTF_8) + " A bell.",
                    StandardCharsets.UTF_8);
            commit(git, "Bell in the pantry");
        }
        final NonLinearBookFacade facade = open();
        try {
            final List<BookVersion> history = facade.getHistory(0, 10);
            assertEquals(2, history.size());
            assertEquals(BookVersion.ServerState.NO_SERVER, history.get(0).getServerState());
            final List<BookHistory.ElementChange> bell = facade.getVersionChanges(history.get(0).getId());
            assertEquals(1, bell.size());
            assertEquals("Page \"Pantry\"", bell.get(0).getTitle());
            assertEquals(BookFileChange.Kind.MODIFIED, bell.get(0).getKind());
            assertTrue(bell.get(0).getFields().get(0).getAfter().endsWith(" A bell."));
            // первая версия — вся книга, по элементам
            final List<BookHistory.ElementChange> first = facade.getVersionChanges(history.get(1).getId());
            assertTrue(first.stream().anyMatch(e -> e.getTitle().equals("Page \"Pantry\"")
                    && e.getKind() == BookFileChange.Kind.ADDED));
            assertTrue(first.stream().noneMatch(e -> e.getType() == BookHistory.Type.FILE));
            // несохранённые правки: записано и добавлено в индекс (так сохраняет редактор)
            try (Git git = Git.open(m_book)) {
                Files.writeString(new File(m_book, PANTRY + "/caption/en").toPath(), "Storeroom", StandardCharsets.UTF_8);
                git.add().addFilepattern(PANTRY + "/caption/en").call();
            }
            final List<BookHistory.ElementChange> unsaved = facade.getVersionChanges(null);
            assertEquals(1, unsaved.size());
            assertEquals(BookFileChange.Kind.MODIFIED, unsaved.get(0).getKind());
            assertEquals("Pantry", unsaved.get(0).getFields().get(0).getBefore());
            assertEquals("Storeroom", unsaved.get(0).getFields().get(0).getAfter());
        } finally {
            m_adapter.closeAdapter();
        }
    }

    @Test
    public void deletedPageIsNamedFromTheVersion() throws Exception {
        try (Git git = Git.open(m_book)) {
            TestDirs.delete(m_book.toPath().resolve(PANTRY));
            commit(git, "Removed the pantry");
        }
        final NonLinearBookFacade facade = open();
        try {
            final List<BookHistory.ElementChange> removed = facade.getVersionChanges(facade.getHistory(0, 1).get(0).getId());
            final List<String> pages = removed.stream()
                    .filter(e -> e.getType() == BookHistory.Type.PAGE).map(BookHistory.ElementChange::getTitle)
                    .collect(Collectors.toList());
            assertEquals(List.of("Page \"Pantry\""), pages);
            assertEquals(BookFileChange.Kind.DELETED, removed.stream()
                    .filter(e -> e.getType() == BookHistory.Type.PAGE).findFirst().get().getKind());
        } finally {
            m_adapter.closeAdapter();
        }
    }
}
