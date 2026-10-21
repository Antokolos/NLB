package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Проверка эталонных прохождений NLBNavigator (tools/player-reference/make-reference.sh):
 * у каждой книги есть пары «ввод + журнал», и если книги скачаны, Java загружает их так же,
 * как C++ (название и автор совпадают со строкой «Loaded:» журнала).
 * <p>
 * Каталог книг: переменная окружения NLB_TEST_BOOKS, по умолчанию
 * ~/.cache/nlb-player-reference/books. Если книги нет, проверка загрузки пропускается.
 */
public class ReferenceDataTest {
    static final String REFERENCE_ROOT = "/player-reference/";
    private static final String LOADED_PREFIX = "Loaded: ";

    @Test
    public void everyBookHasWalkthroughs() throws IOException {
        Properties manifest = loadManifest();
        List<String> books = bookNames(manifest);
        assertFalse("В манифесте нет книг", books.isEmpty());
        for (String book : books) {
            int walks = Integer.parseInt(manifest.getProperty(book + ".walks"));
            assertTrue(book + ": нет прохождений", walks > 0);
            for (int seed = 1; seed <= walks; seed++) {
                List<String> input = readLines(book + "/walk-" + seed + ".input");
                List<String> log = readLines(book + "/walk-" + seed + ".log");
                assertFalse(book + "/walk-" + seed + ": пустой ввод", input.isEmpty());
                assertNotNull(book + "/walk-" + seed + ": в журнале нет строки «Loaded:»", findLoadedLine(log));
            }
        }
    }

    @Test
    public void javaLoadsBooksLikeNavigator() throws Exception {
        File booksDir = booksDir();
        Properties manifest = loadManifest();
        int checked = 0;
        for (String book : bookNames(manifest)) {
            File bookDir = new File(booksDir, book);
            if (!new File(bookDir, "startpoint").isFile()) {
                continue;
            }
            NonLinearBookImpl nlb = new NonLinearBookImpl();
            assertTrue(book + ": книга не загрузилась", nlb.load(bookDir.getPath(), new DummyProgressData()));
            String loaded = findLoadedLine(readLines(book + "/walk-1.log"));
            assertEquals(book, LOADED_PREFIX + nlb.getTitle() + " by " + nlb.getAuthor(), loaded);
            checked++;
        }
        Assume.assumeTrue("Тестовые книги не найдены в " + booksDir + " (см. tools/player-reference)", checked > 0);
    }

    static File booksDir() {
        String env = System.getenv("NLB_TEST_BOOKS");
        if (env != null && !env.isEmpty()) {
            return new File(env);
        }
        return new File(System.getProperty("user.home"), ".cache/nlb-player-reference/books");
    }

    static Properties loadManifest() throws IOException {
        Properties manifest = new Properties();
        try (InputStream stream = resource("manifest.properties");
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            manifest.load(reader);
        }
        return manifest;
    }

    /** Книги манифеста в порядке, в котором они перечислены (по ключам «имя.commit») */
    static List<String> bookNames(Properties manifest) throws IOException {
        List<String> result = new ArrayList<>();
        try (InputStream stream = resource("manifest.properties")) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                if (line.contains(".commit=") && !line.startsWith("navigator.")) {
                    result.add(line.substring(0, line.indexOf(".commit=")));
                }
            }
        }
        return result;
    }

    static List<String> readLines(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
            if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            return lines;
        }
    }

    private static InputStream resource(String path) throws IOException {
        InputStream stream = ReferenceDataTest.class.getResourceAsStream(REFERENCE_ROOT + path);
        if (stream == null) {
            throw new IOException("Нет эталонного файла " + REFERENCE_ROOT + path);
        }
        return stream;
    }

    private static String findLoadedLine(List<String> log) {
        for (String line : log) {
            if (line.startsWith(LOADED_PREFIX)) {
                return line;
            }
        }
        return null;
    }
}
