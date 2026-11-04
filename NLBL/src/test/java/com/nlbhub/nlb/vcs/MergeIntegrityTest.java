package com.nlbhub.nlb.vcs;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.player.ReferenceDataTest;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Целостность после слияния на bookU: автор удалил страницу «Hall» в редакторе (вместе с её
 * ссылками), а соавтор тем временем сослался на неё (новая ссылка со страницы «Pantry») или
 * добавил в неё ссылку. Pull возвращает «Hall» целиком, сообщает об этом, и книга открывается.
 */
public class MergeIntegrityTest {
    private static final String PANTRY = "pages/0098f758-2bfd-5605-bdca-705985829a50";
    private static final String HALL_ID = "67d07b3b-152e-5d39-8c7e-d73c0fbd1945";
    private static final String HALL = "pages/" + HALL_ID;
    private static final String PANTRY_LINK = "593c8954-6a6a-5b15-a892-f9f599b6fb77";
    private Path m_dir;
    private File m_mine;
    private File m_theirs;

    @Before
    public void setUp() throws Exception {
        final File bookU = new File(ReferenceDataTest.booksDir(), "bookU");
        Assume.assumeTrue("Нет книги " + bookU, new File(bookU, "startpoint").isFile());
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        m_dir = Files.createTempDirectory("nlb-integrity");
        final File server = m_dir.resolve("server.git").toFile();
        Git.init().setBare(true).setDirectory(server).setInitialBranch("master").call().close();
        final File seed = m_dir.resolve("seed").toFile();
        try (Git git = Git.init().setDirectory(seed).setInitialBranch("master").call()) {
            copy(bookU.toPath(), seed.toPath());
            git.add().addFilepattern(".").call();
            commit(git, "book");
            git.remoteAdd().setName("origin").setUri(new org.eclipse.jgit.transport.URIish(server.toURI().toString())).call();
            git.push().setRemote("origin").call();
        }
        m_mine = clone(server, "mine");
        m_theirs = clone(server, "theirs");
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
    public void pageDeletedHereButLinkedThere() throws Exception {
        // соавтор: новая ссылка со страницы Pantry на Hall
        addLink(m_theirs, PANTRY, "bbbbbbbb-0000-0000-0000-000000000001", HALL_ID, "To the hall");
        pushTheirs();
        final List<RestoredElement> restored = deleteHallAndPull();
        assertEquals(1, restored.size());
        assertEquals(HALL, restored.get(0).getPath());
        assertTrue("удалена у автора", restored.get(0).isDeletedLocally());

        final NonLinearBookFacade book = open();
        final Page hall = book.getNlb().getPageById(HALL_ID);
        assertNotNull("Hall восстановлена", hall);
        assertEquals("Hall", hall.getCaption());
        boolean linked = false;
        for (Link link : book.getNlb().getPageById("0098f758-2bfd-5605-bdca-705985829a50").getLinks()) {
            linked |= HALL_ID.equals(link.getTarget());
        }
        assertTrue("ссылка соавтора на месте", linked);
        book.clear();
    }

    @Test
    public void pageDeletedHereButExtendedThere() throws Exception {
        // соавтор: ссылка из Hall в Pantry — в удалённой у автора странице остался бы обрывок
        addLink(m_theirs, HALL, "bbbbbbbb-0000-0000-0000-000000000002", "0098f758-2bfd-5605-bdca-705985829a50", "Back");
        pushTheirs();
        final List<RestoredElement> restored = deleteHallAndPull();
        assertEquals(Collections.singletonList(HALL), List.of(restored.get(0).getPath()));
        final NonLinearBookFacade book = open();
        final Page hall = book.getNlb().getPageById(HALL_ID);
        assertNotNull(hall);
        assertEquals("Back", hall.getLinks().get(hall.getLinks().size() - 1).getText());
        assertTrue("текст страницы восстановлен", hall.getText() != null && !hall.getText().isEmpty());
        book.clear();
    }

    @Test
    public void unrelatedDeletionIsKept() throws Exception {
        addLink(m_theirs, PANTRY, "bbbbbbbb-0000-0000-0000-000000000003", "0c1aa98b-88f5-587c-ae9f-7cd6e89048d2", "Gate again");
        pushTheirs();
        assertTrue("ничего не восстановлено", deleteHallAndPull().isEmpty());
        final NonLinearBookFacade book = open();
        assertEquals(null, book.getNlb().getPageById(HALL_ID));
        book.clear();
    }

    /**
     * Автор: удалить Hall в редакторе, сохранить, закоммитить; затем pull
     */
    private List<RestoredElement> deleteHallAndPull() throws Exception {
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new GitAdapter(new Author("t", "t@e")));
        facade.createNewBook();
        facade.load(m_mine.getPath(), new DummyProgressData());
        facade.deleteNode(facade.getNlb().getPageById(HALL_ID));
        facade.save(false, new DummyProgressData());
        try (Git git = Git.open(m_mine)) {
            git.add().setUpdate(true).addFilepattern(".").call();
        }
        facade.commit("delete Hall");
        assertTrue("Hall удалена", !new File(m_mine, HALL).exists());
        facade.pull(null, null, new DummyProgressData());
        final List<RestoredElement> restored = facade.getRestoredByLastPull();
        facade.clear();
        try (Git git = Git.open(m_mine)) {
            assertTrue(git.status().call().getUncommittedChanges().isEmpty());
        }
        return restored;
    }

    private NonLinearBookFacade open() throws Exception {
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        facade.createNewBook();
        facade.load(m_mine.getPath(), new DummyProgressData());
        return facade;
    }

    /**
     * Как редактор: новая ссылка (копия ссылки Pantry с новым id и целью) — в конец lnkorder
     */
    private static void addLink(File root, String page, String id, String target, String text) throws Exception {
        final Path to = root.toPath().resolve(page + "/links/" + id);
        copy(root.toPath().resolve(PANTRY + "/links/" + PANTRY_LINK), to);
        Files.writeString(to.resolve("text/en"), text, StandardCharsets.UTF_8);
        Files.writeString(to.resolve("target"), target, StandardCharsets.UTF_8);
        final Path order = root.toPath().resolve(page + "/lnkorder");
        final String current = Files.exists(order) ? Files.readString(order, StandardCharsets.UTF_8) : "";
        Files.writeString(order, current.isEmpty() ? id : current + "\n" + id, StandardCharsets.UTF_8);
        try (Git git = Git.open(root)) {
            git.add().addFilepattern(".").call();
            commit(git, "link " + text);
        }
    }

    private void pushTheirs() throws Exception {
        try (Git git = Git.open(m_theirs)) {
            git.push().call();
        }
    }

    private File clone(File server, String name) throws Exception {
        final File dir = m_dir.resolve(name).toFile();
        Git.cloneRepository().setURI(server.toURI().toString()).setDirectory(dir).call().close();
        return dir;
    }

    private static void commit(Git git, String message) throws Exception {
        git.commit().setAuthor("t", "t@e").setCommitter("t", "t@e").setMessage(message).setSign(false)
                .setGpgConfig(new GpgConfig(new Config())).call();
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path source : (Iterable<Path>) walk::iterator) {
                if (source.toString().contains("/.git")) {
                    continue;
                }
                final Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
