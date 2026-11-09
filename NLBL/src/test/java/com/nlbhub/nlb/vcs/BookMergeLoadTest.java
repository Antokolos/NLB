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
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * Приёмка на bookU: оба соавтора добавили ссылку со страницы «Pantry» и подвинули её на схеме
 * (пересечения в lnkorder и coords) — pull сливает без вопросов, и книга открывается с обеими
 * ссылками.
 */
public class BookMergeLoadTest {
    private static final String PANTRY = "pages/0098f758-2bfd-5605-bdca-705985829a50";
    private static final String LINK = "593c8954-6a6a-5b15-a892-f9f599b6fb77";
    private Path m_dir;

    @After
    public void tearDown() throws IOException {
        if (m_dir != null) {
            TestDirs.delete(m_dir);
        }
    }

    @Test
    public void mergedBookOpens() throws Exception {
        final File bookU = new File(ReferenceDataTest.booksDir(), "bookU");
        Assume.assumeTrue("Нет книги " + bookU, new File(bookU, "startpoint").isFile());
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        m_dir = Files.createTempDirectory("nlb-merge-load");
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
        final File mine = clone(server, "mine");
        final File theirs = clone(server, "theirs");

        addLinkAndMove(theirs, "aaaaaaaa-0000-0000-0000-000000000002", "From the server", "400.0");
        try (Git git = Git.open(theirs)) {
            git.push().call();
        }
        addLinkAndMove(mine, "aaaaaaaa-0000-0000-0000-000000000001", "Mine", "300.0");

        final GitAdapter adapter = new GitAdapter(new Author("t", "t@e"));
        adapter.openRepo(mine.getPath());
        adapter.pull(null, null, new DummyProgressData());
        adapter.closeAdapter();

        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        facade.createNewBook();
        facade.load(mine.getPath(), new DummyProgressData());
        final Page pantry = facade.getNlb().getPageById("0098f758-2bfd-5605-bdca-705985829a50");
        final List<String> texts = new ArrayList<>();
        for (Link link : pantry.getLinks()) {
            texts.add(link.getText());
        }
        assertEquals(List.of("To the gate", "Mine", "From the server"), texts);
        assertEquals(300.0f, pantry.getCoords().getLeft(), 0.001f);
        facade.clear();
    }

    /**
     * Как редактор: новая ссылка (копия существующей с новым id), её id — в конец lnkorder; страница
     * подвинута; коммит
     */
    private static void addLinkAndMove(File root, String id, String text, String left) throws Exception {
        final Path from = root.toPath().resolve(PANTRY + "/links/" + LINK);
        final Path to = root.toPath().resolve(PANTRY + "/links/" + id);
        copy(from, to);
        Files.writeString(to.resolve("text/en"), text, StandardCharsets.UTF_8);
        final Path order = root.toPath().resolve(PANTRY + "/lnkorder");
        Files.writeString(order, Files.readString(order, StandardCharsets.UTF_8) + "\n" + id, StandardCharsets.UTF_8);
        Files.writeString(root.toPath().resolve(PANTRY + "/coords/left"), left, StandardCharsets.UTF_8);
        try (Git git = Git.open(root)) {
            git.add().addFilepattern(".").call();
            commit(git, "link " + text);
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
                if (source.toString().replace('\\', '/').contains("/.git")) {  // и в Windows
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
