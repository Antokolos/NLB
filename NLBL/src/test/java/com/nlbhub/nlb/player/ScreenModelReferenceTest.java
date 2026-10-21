package com.nlbhub.nlb.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.config.Export;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import org.junit.Assume;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;

/**
 * Приёмка этапа 4: снимок экрана Java-плеера совпадает с PlayerEngine::screen() NLBNavigator.
 * Эталон (tools/player-reference/screen/make-screens.sh) — снимки C++ после каждого шага
 * эталонных прохождений: после старта, каждой команды и внешней игры. Java воспроизводит тот
 * же ввод и сравнивает свой снимок на каждом шаге поле за полем (кроме revision), а заодно
 * порции текста VN (TextPortions — GameScreen::portions).
 */
public class ScreenModelReferenceTest {
    private static final File CONFIG = new File("../NLBW/cfg/config.xml");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    public void screensMatchNavigator() throws Exception {
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
            boolean vn = "vn".equals(manifest.getProperty(book + ".mode"));
            int walks = Integer.parseInt(manifest.getProperty(book + ".walks"));
            for (int walk = 1; walk <= walks; walk++) {
                String name = book + "/walk-" + walk;
                List<String> expected = readScreens(book + "/walk-" + walk + ".screens.jsonl.gz");
                List<ObjectNode> actual = play(nlb, vn, export, ReferenceDataTest.readLines(name + ".input"));
                String mismatch = null;
                for (int step = 0; step < Math.max(expected.size(), actual.size()) && mismatch == null; step++) {
                    if (step >= expected.size() || step >= actual.size()) {
                        mismatch = "шагов: ожидалось " + expected.size() + ", получено " + actual.size();
                        break;
                    }
                    String difference = difference("", JSON.readTree(expected.get(step)), actual.get(step));
                    if (difference != null) {
                        mismatch = "шаг " + step + ": " + difference;
                    }
                }
                if (mismatch != null) {
                    mismatches.add(name + ": " + mismatch);
                }
                checked++;
            }
        }
        Assume.assumeTrue("Тестовые книги не найдены в " + booksDir + " (см. tools/player-reference)", checked > 0);
        assertEquals("Снимки экрана расходятся с NLBNavigator:\n" + String.join("\n", mismatches), 0, mismatches.size());
    }

    private static List<ObjectNode> play(NonLinearBookImpl book, boolean vn, Export export, List<String> input)
            throws Exception {
        PlayerEngine.Settings settings = PlayerEngine.Settings.fromExport(export, book.getLanguage());
        settings.exportMode = vn ? PlayerEngine.ExportMode.VN : PlayerEngine.ExportMode.STANDARD;
        settings.deterministic = true;
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", input) + "\n"));
        ConsolePlayer player = new ConsolePlayer(book, settings, in, new PrintWriter(new StringWriter()));
        List<ObjectNode> screens = new ArrayList<>();
        player.setStepListener(() -> {
            ScreenModel model = player.engine().screen();
            ObjectNode screen = model.toJson();
            screen.remove("revision");
            // порции текста VN (GameScreen::portions в NLBNavigator)
            com.fasterxml.jackson.databind.node.ArrayNode portions = screen.putArray("portions");
            for (List<ScreenModel.Paragraph> portion : model.portions()) {
                com.fasterxml.jackson.databind.node.ArrayNode paragraphs = portions.addArray();
                portion.forEach(p -> paragraphs.add(p.toJson()));
            }
            screens.add(screen);
        });
        player.explore();
        return screens;
    }

    private static List<String> readScreens(String path) throws Exception {
        try (InputStream stream = ScreenModelReferenceTest.class.getResourceAsStream("/player-screens/" + path)) {
            if (stream == null) {
                throw new IllegalStateException("Нет эталона /player-screens/" + path);
            }
            String text = new String(new GZIPInputStream(stream).readAllBytes(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            for (String line : text.split("\n")) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }

    /**
     * Первое отличие: путь к полю и оба значения; null — совпадают
     */
    private static String difference(String path, JsonNode expected, JsonNode actual) {
        if (expected.isObject() && actual.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = expected.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                JsonNode other = actual.get(field.getKey());
                if (other == null) {
                    return path + "." + field.getKey() + ": нет поля";
                }
                String d = difference(path + "." + field.getKey(), field.getValue(), other);
                if (d != null) {
                    return d;
                }
            }
            for (Iterator<String> it = actual.fieldNames(); it.hasNext(); ) {
                String fieldName = it.next();
                if (!expected.has(fieldName)) {
                    return path + "." + fieldName + ": лишнее поле";
                }
            }
            return null;
        }
        if (expected.isArray() && actual.isArray()) {
            for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
                String d = difference(path + "[" + i + "]", expected.get(i), actual.get(i));
                if (d != null) {
                    return d;
                }
            }
            return expected.size() == actual.size() ? null
                    : path + ": элементов ожидалось " + expected.size() + ", получено " + actual.size();
        }
        return expected.equals(actual) ? null : path + ": ожидалось " + expected + ", получено " + actual;
    }
}
