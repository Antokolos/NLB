package com.nlbhub.nlb.util;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.player.ReferenceDataTest;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Кэш книги одним файлом (BookFiles): книга, прочитанная через кэш, — ровно та же, что без него
 * (модели сравниваются целиком: сохраняются в новые каталоги, деревья файлов сравниваются
 * побайтно), в том числе после правки, удаления и добавления файлов книги снаружи и при
 * испорченном кэше. Книги — тестовые (NLB_TEST_BOOKS); нет книги — пропуск.
 */
public class BookCacheTest {
    private static final Pattern SUMMARY = Pattern.compile("(\\d+) files from cache, (\\d+) read from disk");

    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();
    private File m_cacheDir;

    @Before
    public void setUp() throws Exception {
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        m_cacheDir = m_temp.newFolder("cache");
    }

    @After
    public void tearDown() {
        BookFiles.setCacheDir(null);
    }

    @Test
    public void bookU() throws Exception {
        check("bookU");
    }

    @Test
    public void frontier() throws Exception {
        check("Frontier");
    }

    private void check(String name) throws Exception {
        File source = new File(ReferenceDataTest.booksDir(), name);
        Assume.assumeTrue("Нет книги " + source, new File(source, "startpoint").isFile());
        Path book = m_temp.newFolder(name).toPath();
        copyTree(source.toPath(), book);

        Map<String, String> plain = loadAndDump(book, false);
        // первая загрузка с кэшем — всё с диска, вторая — всё из кэша; книга та же
        int[] first = new int[2];
        assertEquals(plain, loadAndDump(book, true, first));
        assertEquals(0, first[0]);
        assertTrue(first[1] > 0);
        int[] second = new int[2];
        assertEquals(plain, loadAndDump(book, true, second));
        assertEquals(first[1], second[0]);
        assertEquals(0, second[1]);

        // правка текста страницы снаружи (та же длина, другое время изменения) — читается с диска
        Path text = firstFile(book.resolve("pages"), "text");
        byte[] bytes = Files.readAllBytes(text);
        bytes[0] = (byte) (bytes[0] == 'x' ? 'y' : 'x');
        Files.write(text, bytes);
        Files.setLastModifiedTime(text, FileTime.fromMillis(Files.getLastModifiedTime(text).toMillis() + 2000));
        // удаление и добавление необязательных файлов страницы снаружи: картинка страницы и
        // флажок «не сохранять»
        Path page = firstDir(book.resolve("pages"));
        Files.deleteIfExists(firstFileOrNull(book.resolve("pages"), "image"));
        Files.write(page.resolve("nosave"), "true".getBytes("UTF-8"));
        Map<String, String> changedPlain = loadAndDump(book, false);
        int[] changed = new int[2];
        assertEquals(changedPlain, loadAndDump(book, true, changed));
        assertTrue("правка и новый файл прочитаны с диска: " + changed[1], changed[1] >= 2);
        assertTrue("книга изменилась", !changedPlain.equals(plain));

        // испорченный кэш не используется, книга та же, кэш переписывается
        File[] caches = m_cacheDir.listFiles();
        assertNotNull(caches);
        assertEquals(1, caches.length);
        try (RandomAccessFile f = new RandomAccessFile(caches[0], "rw")) {
            f.setLength(f.length() / 2);
        }
        int[] broken = new int[2];
        assertEquals(changedPlain, loadAndDump(book, true, broken));
        assertEquals(0, broken[0]);
        int[] repaired = new int[2];
        assertEquals(changedPlain, loadAndDump(book, true, repaired));
        assertEquals(0, repaired[1]);
    }

    private Map<String, String> loadAndDump(Path book, boolean cache) throws Exception {
        return loadAndDump(book, cache, new int[2]);
    }

    /**
     * Книга целиком: загрузить (с кэшем или без) и сохранить в новый каталог; результат —
     * дерево сохранённых файлов. stats — [из кэша, с диска]
     */
    private Map<String, String> loadAndDump(Path book, boolean cache, int[] stats) throws Exception {
        BookFiles.setCacheDir(cache ? m_cacheDir : null);
        NonLinearBookFacade facade = new NonLinearBookFacade(new Author("test", ""), new DummyVCSAdapter());
        facade.createNewBook();
        facade.load(book.toString(), new DummyProgressData());
        String summary = BookFiles.lastLoadSummary();
        if (cache) {
            assertNotNull(summary);
            Matcher m = SUMMARY.matcher(summary);
            assertTrue(summary, m.find());
            stats[0] = Integer.parseInt(m.group(1));
            stats[1] = Integer.parseInt(m.group(2));
        }
        BookFiles.setCacheDir(null);
        File out = m_temp.newFolder();
        facade.saveAs(out, new DummyProgressData());
        return tree(out.toPath());
    }

    private static Map<String, String> tree(Path root) throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : (Iterable<Path>) paths::iterator) {
                if (Files.isRegularFile(p)) {
                    result.put(root.relativize(p).toString().replace(File.separatorChar, '/'),
                            new String(Files.readAllBytes(p), "UTF-8"));
                }
            }
        }
        return result;
    }

    private static Path firstDir(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(Files::isDirectory).sorted().findFirst().orElse(null);
        }
    }

    /**
     * Первый файл с таким именем (не каталог) или null
     */
    private static Path firstFileOrNull(Path dir, String name) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals(name))
                    .sorted().findFirst().orElse(dir.resolve("no-such-file"));
        }
    }

    private static Path firstFile(Path dir, String name) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(p -> Files.isRegularFile(p) && p.getParent().getFileName().toString().equals(name))
                    .sorted().findFirst().orElseThrow(() -> new AssertionError("нет файла " + name));
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path p : (Iterable<Path>) paths::iterator) {
                Path rel = from.relativize(p);
                if (rel.toString().startsWith(".git")) {
                    continue;
                }
                Path target = to.resolve(rel.toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else if (isMediaPayload(rel)) {
                    Files.createFile(target);  // сами картинки и звуки не нужны — только их имена
                } else {
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static boolean isMediaPayload(Path rel) {
        String file = rel.getFileName().toString();
        for (Path part : rel) {
            String name = part.toString();
            if ((name.equals("images") || name.equals("sound")) && !part.equals(rel.getFileName())) {
                return !file.endsWith(".constrid") && !file.endsWith(".redirect")
                        && !file.endsWith(".flag") && !file.endsWith(".preset");
            }
        }
        return false;
    }

}
