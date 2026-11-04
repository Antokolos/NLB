package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.MediaFile;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.player.ReferenceDataTest;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import com.nlbhub.nlb.vcs.GitAdapter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * #175: «Save As» — копия книги со всем, чего нет в её модели: картинки и звуки (с метаданными),
 * внешние модули (обычными папками, без служебных файлов git), тема игры __theme__, .gitignore;
 * всё это — в новом репозитории книги
 */
public class SaveAsTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3, 0, (byte) 0xFF};
    private static final byte[] WAV = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
    private Path m_dir;
    private File m_book;

    @Before
    public void setUp() throws Exception {
        final File bookU = new File(ReferenceDataTest.booksDir(), "bookU");
        Assume.assumeTrue("Нет книги " + bookU, new File(bookU, "startpoint").isFile());
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        m_dir = Files.createTempDirectory("nlb-saveas");
        m_book = m_dir.resolve("book").toFile();
        copy(bookU.toPath(), m_book.toPath());
        write("images/door.png", PNG);
        write("images/door.png.constrid", "1428e0a9-6766-5be7-8e33-812ab3632fe0".getBytes(StandardCharsets.UTF_8));
        write("sound/bell2.wav", WAV);
        write("modules/ext/title", "External".getBytes(StandardCharsets.UTF_8));
        write("modules/ext/.git", "gitdir: ../../.git/modules/modules/ext".getBytes(StandardCharsets.UTF_8));
        write("__theme__/theme.ini", "scr.w = 800".getBytes(StandardCharsets.UTF_8));
        write(".gitignore", "*.bak".getBytes(StandardCharsets.UTF_8));
        try (Git git = Git.init().setDirectory(m_book).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setAuthor("t", "t@e").setCommitter("t", "t@e").setMessage("book").setSign(false)
                    .setGpgConfig(new GpgConfig(new Config())).call();
        }
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
    public void copyHasMediaModulesAndTheme() throws Exception {
        final NonLinearBookFacade facade = new NonLinearBookFacade(new Author("t", "t@e"), new GitAdapter(new Author("t", "t@e")));
        facade.createNewBook();
        facade.load(m_book.getPath(), new DummyProgressData());
        final File copy = m_dir.resolve("copy").toFile();
        facade.saveAs(copy, new DummyProgressData());
        final int pages = facade.getNlb().getPages().size();
        facade.clear();

        assertArrayEquals("картинка", PNG, Files.readAllBytes(new File(copy, "images/door.png").toPath()));
        assertEquals("её метаданные", "1428e0a9-6766-5be7-8e33-812ab3632fe0",
                Files.readString(new File(copy, "images/door.png.constrid").toPath()));
        assertArrayEquals("звук книги", Files.readAllBytes(new File(m_book, "sound/bell.wav").toPath()),
                Files.readAllBytes(new File(copy, "sound/bell.wav").toPath()));
        assertArrayEquals("звук", WAV, Files.readAllBytes(new File(copy, "sound/bell2.wav").toPath()));
        assertEquals("External", Files.readString(new File(copy, "modules/ext/title").toPath()));
        assertFalse("служебный файл подмодуля не копируется", new File(copy, "modules/ext/.git").exists());
        assertEquals("scr.w = 800", Files.readString(new File(copy, "__theme__/theme.ini").toPath()));
        assertEquals("*.bak", Files.readString(new File(copy, ".gitignore").toPath()));

        // всё — в новом репозитории книги
        final Set<String> indexed = new HashSet<>();
        try (Git git = Git.open(copy)) {
            final DirCache cache = git.getRepository().readDirCache();
            for (int i = 0; i < cache.getEntryCount(); i++) {
                indexed.add(cache.getEntry(i).getPathString());
            }
        }
        for (String path : new String[]{"images/door.png", "images/door.png.constrid", "sound/bell.wav", "sound/bell2.wav",
                "modules/ext/title", "__theme__/theme.ini", ".gitignore", "startpoint"}) {
            assertTrue(path + " в репозитории копии", indexed.contains(path));
        }

        // копия открывается, медиа на месте
        final NonLinearBookFacade reopened = new NonLinearBookFacade(new Author("t", "t@e"), new DummyVCSAdapter());
        reopened.createNewBook();
        reopened.load(copy.getPath(), new DummyProgressData());
        final Set<String> images = new HashSet<>();
        for (MediaFile image : reopened.getNlb().getImageFiles()) {
            images.add(image.getFileName() + ":" + image.getConstrId());
        }
        assertTrue(images.toString(), images.contains("door.png:1428e0a9-6766-5be7-8e33-812ab3632fe0"));
        assertEquals(pages, reopened.getNlb().getPages().size());
        reopened.clear();
        // прежняя книга не тронута
        try (Git git = Git.open(m_book)) {
            assertTrue(git.status().call().isClean());
        }
    }

    private void write(String path, byte[] content) throws IOException {
        final File file = new File(m_book, path);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content);
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
