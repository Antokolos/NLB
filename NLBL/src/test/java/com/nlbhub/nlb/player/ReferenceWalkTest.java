package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.config.Export;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import org.junit.Assume;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * Приёмка этапа 1: Java-плеер проходит книги так же, как NLBNavigator. Каждое эталонное
 * прохождение (ввод walk-N.input) воспроизводится консольным плеером, и его вывод построчно
 * сравнивается с журналом nlb_main (walk-N.log), начиная со строки «Loaded:».
 * <p>
 * Книги ищутся там же, где в {@link ReferenceDataTest} (NLB_TEST_BOOKS); если книг нет, тест
 * пропускается. Книги должны быть на коммитах из манифеста (tools/player-reference/books.txt).
 */
public class ReferenceWalkTest {
    /**
     * Настройки экспорта (needs-action-count, тексты game.inv/game.nouse) — те же, что у NLBW
     */
    private static final File CONFIG = new File("../NLBW/cfg/config.xml");

    @Test
    public void walksMatchNavigator() throws Exception {
        File booksDir = ReferenceDataTest.booksDir();
        Properties manifest = ReferenceDataTest.loadManifest();
        Export export = PropertyManager.readSettings(CONFIG).getDefaultConfig().getExport();
        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        for (String book : ReferenceDataTest.bookNames(manifest)) {
            File bookDir = new File(booksDir, book);
            if (!new File(bookDir, "startpoint").isFile()) {
                continue;
            }
            NonLinearBookImpl nlb = ConsolePlayer.loadBook(bookDir);
            if (nlb == null) {
                mismatches.add(book + ": книга не загрузилась");
                continue;
            }
            boolean vn = "vn".equals(manifest.getProperty(book + ".mode"));
            int walks = Integer.parseInt(manifest.getProperty(book + ".walks"));
            for (int walk = 1; walk <= walks; walk++) {
                String name = book + "/walk-" + walk;
                List<String> input = ReferenceDataTest.readLines(name + ".input");
                List<String> expected = fromLoaded(ReferenceDataTest.readLines(name + ".log"));
                List<String> actual = play(nlb, vn, export, input);
                String mismatch = firstMismatch(expected, actual);
                if (mismatch != null) {
                    mismatches.add(name + " (книга на коммите " + manifest.getProperty(book + ".commit") + "): "
                            + mismatch);
                }
                checked++;
            }
        }
        Assume.assumeTrue("Тестовые книги не найдены в " + booksDir + " (см. tools/player-reference)", checked > 0);
        assertEquals("Расхождения с NLBNavigator:\n" + String.join("\n", mismatches), 0, mismatches.size());
    }

    private static List<String> play(NonLinearBookImpl book, boolean vn, Export export, List<String> input)
            throws Exception {
        PlayerEngine.Settings settings = PlayerEngine.Settings.fromExport(export, book.getLanguage());
        settings.exportMode = vn ? PlayerEngine.ExportMode.VN : PlayerEngine.ExportMode.STANDARD;
        settings.deterministic = true;
        StringWriter output = new StringWriter();
        PrintWriter out = new PrintWriter(output);
        out.println("Loaded: " + book.getTitle() + " by " + book.getAuthor());
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", input) + "\n"));
        ConsolePlayer player = new ConsolePlayer(book, settings, in, out);
        player.setEchoInput(true);
        player.explore();
        out.flush();
        List<String> lines = new ArrayList<>(List.of(output.toString().split("\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static List<String> fromLoaded(List<String> log) {
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i).startsWith("Loaded: ")) {
                return log.subList(i, log.size());
            }
        }
        return log;
    }

    /**
     * Первое расхождение с несколькими строками до него; null — журналы совпадают
     */
    private static String firstMismatch(List<String> expected, List<String> actual) {
        int n = Math.min(expected.size(), actual.size());
        for (int i = 0; i <= n; i++) {
            boolean expectedEnded = i >= expected.size();
            boolean actualEnded = i >= actual.size();
            if (expectedEnded && actualEnded) {
                return null;
            }
            if (expectedEnded || actualEnded || !expected.get(i).equals(actual.get(i))) {
                StringBuilder context = new StringBuilder("строка " + (i + 1) + "\n");
                for (int j = Math.max(0, i - 3); j < i; j++) {
                    context.append("    ").append(expected.get(j)).append('\n');
                }
                context.append("  ожидалось: ").append(expectedEnded ? "<конец журнала>" : expected.get(i)).append('\n');
                context.append("  получено:  ").append(actualEnded ? "<конец вывода>" : actual.get(i));
                return context.toString();
            }
        }
        return null;
    }
}
