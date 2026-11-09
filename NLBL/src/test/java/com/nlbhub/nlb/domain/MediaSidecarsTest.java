package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.player.ReferenceDataTest;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.GitAdapter;
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
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * #208: удаление картинки удаляет и её .preset и .flag (раньше оставались); сохранение убирает
 * сопутствующие файлы, ресурса которых нет (накопились от прежних удалений); у оставшихся
 * ресурсов сопутствующие файлы не трогаются
 */
public class MediaSidecarsTest {
    private Path m_dir;

    @After
    public void tearDown() throws IOException {
        if (m_dir != null) {
            try (Stream<Path> walk = Files.walk(m_dir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    @Test
    public void deletedImageTakesItsSidecars() throws Exception {
        final File bookU = new File(ReferenceDataTest.booksDir(), "bookU");
        Assume.assumeTrue("Нет книги " + bookU, new File(bookU, "startpoint").isFile());
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        m_dir = Files.createTempDirectory("nlb-sidecars");
        final File book = m_dir.toFile();
        try (Stream<Path> walk = Files.walk(bookU.toPath())) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (p.toString().replace('\\', '/').contains("/.git")) {  // и в Windows
                    continue;
                }
                final Path t = book.toPath().resolve(bookU.toPath().relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(t);
                } else {
                    Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        write(book, "images/a.png", "A");
        write(book, "images/a.png.preset", "COMPRESSED");
        write(book, "images/a.png.flag", "true");
        write(book, "images/b.png", "B");
        write(book, "images/b.png.preset", "NOCHANGE");
        write(book, "images/old.png.preset", "COMPRESSED");
        write(book, "sound/gone.wav.flag", "true");
        try (Git git = Git.init().setDirectory(book).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setAuthor("t", "t@e").setCommitter("t", "t@e").setMessage("book").setSign(false)
                    .setGpgConfig(new GpgConfig(new Config())).call();
        }
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new GitAdapter(new Author("t", "t@e")));
        facade.createNewBook();
        facade.load(book.getPath(), new DummyProgressData());
        facade.removeImageFile("a.png");
        facade.save(false, new DummyProgressData());
        facade.clear();

        assertFalse(new File(book, "images/a.png").exists());
        assertFalse("preset удалённой картинки", new File(book, "images/a.png.preset").exists());
        assertFalse("flag удалённой картинки", new File(book, "images/a.png.flag").exists());
        assertFalse("сирота от прежних удалений", new File(book, "images/old.png.preset").exists());
        assertFalse("сирота в sound", new File(book, "sound/gone.wav.flag").exists());
        assertEquals("у оставшейся картинки — как было", "NOCHANGE",
                Files.readString(new File(book, "images/b.png.preset").toPath()));
        assertTrue("звук книги на месте", new File(book, "sound/bell.wav").exists());
        try (Git git = Git.open(book)) {
            git.add().setUpdate(true).addFilepattern(".").call();
            final Set<String> removed = git.status().call().getRemoved();
            for (String path : new String[]{"images/a.png", "images/a.png.preset", "images/a.png.flag",
                    "images/old.png.preset", "sound/gone.wav.flag"}) {
                assertTrue(path + " удалён и из репозитория: " + removed, removed.contains(path));
            }
            assertFalse(removed.contains("images/b.png.preset"));
        }
    }

    private static void write(File root, String path, String text) throws IOException {
        final File file = new File(root, path);
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
