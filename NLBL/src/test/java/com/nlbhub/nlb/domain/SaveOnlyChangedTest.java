package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.DummyVCSAdapter;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Сохранение книги трогает на диске только то, что изменилось: повторное сохранение без правок
 * не переписывает ни одного файла (время изменения и содержимое прежние), правка текста одной
 * страницы меняет только её файл. Книги — тестовые (NLB_TEST_BOOKS, как для эталонных
 * прохождений); нет книги — пропуск.
 */
public class SaveOnlyChangedTest {
    private static final FileTime PAST = FileTime.fromMillis(946684800000L);  // 2000-01-01

    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    @Test
    public void bookU() throws Exception {
        check("bookU");
    }

    @Test
    public void frontier() throws Exception {
        check("Frontier");
    }

    private void check(String name) throws Exception {
        File source = new File(com.nlbhub.nlb.player.ReferenceDataTest.booksDir(), name);
        Assume.assumeTrue("Нет книги " + source, new File(source, "startpoint").isFile());
        PropertyManager.init(new File("../NLBW/cfg/config.xml"));
        Path book = m_temp.newFolder(name).toPath();
        copyTree(source.toPath(), book);

        // первое сохранение может привести старый формат каталога к текущему
        NonLinearBookFacade facade = load(book);
        facade.save(false, new DummyProgressData());
        snapshot(book);
        Map<String, String> before = snapshot(book);

        // без правок — ни одной записи
        facade = load(book);
        facade.save(false, new DummyProgressData());
        Map<String, String> after = snapshot(book);
        assertEquals("сохранение без правок изменило файлы", Collections.emptySet(), changed(before, after).keySet());

        // правка текста одной страницы — меняется только её файл
        NonLinearBookImpl nlb = (NonLinearBookImpl) facade.getNlb();
        PageImpl page = nlb.getPageImplById(nlb.getStartPoint());
        page.setText(page.getText() + " (правка)");
        facade.save(false, new DummyProgressData());
        Map<String, String> edited = snapshot(book);
        Map<String, String> changed = changed(before, edited);
        assertEquals("изменилось лишнее: " + changed.keySet(), 1, changed.size());
        String path = changed.keySet().iterator().next();
        assertTrue(path, path.startsWith("pages/" + page.getId() + "/text"));
        assertEquals(before.keySet(), edited.keySet());
    }

    /**
     * Файлы, которые появились, пропали или были перезаписаны
     */
    private static Map<String, String> changed(Map<String, String> before, Map<String, String> after) {
        Map<String, String> result = new TreeMap<>();
        for (Map.Entry<String, String> e : after.entrySet()) {
            if (!e.getValue().equals(before.get(e.getKey()))) {
                result.put(e.getKey(), e.getValue());
            }
        }
        for (String path : before.keySet()) {
            if (!after.containsKey(path)) {
                result.put(path, "deleted");
            }
        }
        return result;
    }

    private static NonLinearBookFacade load(Path book) throws Exception {
        NonLinearBookFacade facade = new NonLinearBookFacade(new Author("test", ""), new DummyVCSAdapter());
        facade.createNewBook();
        facade.load(book.toString(), new DummyProgressData());
        return facade;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path p : (Iterable<Path>) paths::iterator) {
                Path rel = from.relativize(p);
                if (rel.startsWith(".git")) {
                    continue;
                }
                Path target = to.resolve(rel.toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else if (isMediaPayload(rel)) {
                    Files.createFile(target);  // сами картинки и звуки не нужны — только их имена
                } else {
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static boolean isMediaPayload(Path rel) {
        String top = rel.getName(0).toString();
        String file = rel.getFileName().toString();
        return (top.equals("images") || top.equals("sound")) && rel.getNameCount() > 1
                && !file.endsWith(".constrid") && !file.endsWith(".redirect")
                && !file.endsWith(".flag") && !file.endsWith(".preset");
    }

    /**
     * Все файлы книги: путь -> время изменения и содержимое. Время ставится в прошлое, чтобы
     * любая перезапись (даже тем же содержимым) была видна
     */
    private static Map<String, String> snapshot(Path book) throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(book)) {
            for (Path p : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String rel = book.relativize(p).toString().replace(File.separatorChar, '/');
                FileTime time = Files.getLastModifiedTime(p);
                result.put(rel, (time.equals(PAST) ? "" : "new ") + new String(Files.readAllBytes(p), "UTF-8").hashCode());
                Files.setLastModifiedTime(p, PAST);
            }
        }
        return result;
    }
}
