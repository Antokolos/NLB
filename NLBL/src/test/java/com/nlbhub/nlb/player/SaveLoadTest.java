package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.config.Export;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
 * Приёмка этапа 3: сохранить → загрузить → продолжить даёт тот же журнал, что игра без перерыва.
 * <p>
 * Для каждого эталонного прохождения в нескольких точках (на главном экране, перед очередной
 * командой) игра сохраняется в слот, в новом плеере загружается и продолжается тем же вводом.
 * После загрузки страница показывается заново (как в INSTEAD), поэтому образец — та же игра без
 * перерыва, где в этой точке введено «l» (осмотреться): дальше журналы должны совпасть строка в
 * строку, включая случайность (--deterministic), достижения и таймеры.
 */
public class SaveLoadTest {
    private static final File CONFIG = new File("../NLBW/cfg/config.xml");
    /**
     * В каких долях прохождения сохраняться
     */
    private static final double[] POINTS = {1.0 / 3, 2.0 / 3};
    private static final String LOADED = "Игра загружена из слота 1.";

    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    private static final class Run {
        final ConsolePlayer player;
        final List<String> output;

        Run(ConsolePlayer player, List<String> output) {
            this.player = player;
            this.output = output;
        }
    }

    private static Run play(NonLinearBookImpl book, PlayerEngine.Settings settings, File saves, List<String> input)
            throws Exception {
        StringWriter output = new StringWriter();
        PrintWriter out = new PrintWriter(output);
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", input) + "\n"));
        ConsolePlayer player = new ConsolePlayer(book, settings, in, out);
        player.setEchoInput(true);
        player.setSavesDirectory(saves);
        player.explore();
        out.flush();
        return new Run(player, new ArrayList<>(List.of(output.toString().split("\r?\n", -1))));
    }

    private static PlayerEngine.Settings settings(NonLinearBookImpl book, boolean vn, Export export,
                                                  Achievements achievements) {
        PlayerEngine.Settings settings = PlayerEngine.Settings.fromExport(export, book.getLanguage());
        settings.exportMode = vn ? PlayerEngine.ExportMode.VN : PlayerEngine.ExportMode.STANDARD;
        settings.deterministic = true;
        settings.achievements = achievements;
        return settings;
    }

    private static List<String> concat(List<String> head, List<String> middle, List<String> tail) {
        List<String> result = new ArrayList<>(head);
        result.addAll(middle);
        result.addAll(tail);
        return result;
    }

    @Test
    public void loadedGameContinuesLikeUninterrupted() throws Exception {
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
                List<String> input = ReferenceDataTest.readLines(book + "/walk-" + walk + ".input");
                File saves = m_temp.newFolder();
                // Где ввод — команды игры (главный экран): только там можно сохраниться
                List<Integer> commands = play(nlb, settings(nlb, vn, export, null), saves, input).player.commandInputs();
                for (double point : POINTS) {
                    int k = commands.get((int) (commands.size() * point));
                    if (k >= input.size()) {
                        continue;  // ввод кончился раньше: сохраняться незачем
                    }
                    String name = book + "/walk-" + walk + " перед вводом " + (k + 1);
                    List<String> head = input.subList(0, k);
                    List<String> tail = input.subList(k, input.size());
                    // Без перерыва: в точке сохранения — «осмотреться»
                    List<String> expected = play(nlb, settings(nlb, vn, export, null), m_temp.newFolder(),
                            concat(head, List.of("l"), tail)).output;
                    // С перерывом: сохраниться в слот 1 и выйти (ввод кончился)...
                    Run before = play(nlb, settings(nlb, vn, export, null), saves, concat(head, List.of("m", "1", "1"), List.of()));
                    // ...и в новом плеере загрузиться и продолжить. Достижения — отдельно от сохранения
                    List<String> resumed = play(nlb, settings(nlb, vn, export, before.player.engine().achievements()),
                            saves, concat(List.of("m", "2", "1"), List.of(), tail)).output;
                    String mismatch = compare(expected, resumed);
                    if (mismatch != null) {
                        mismatches.add(name + ": " + mismatch);
                    }
                    checked++;
                }
            }
        }
        Assume.assumeTrue("Тестовые книги не найдены в " + booksDir + " (см. tools/player-reference)", checked > 0);
        assertEquals("Загруженная игра идёт не так, как без перерыва:\n" + String.join("\n", mismatches),
                0, mismatches.size());
    }

    /**
     * Журнал после загрузки должен быть концом журнала без перерыва, сразу после «l»
     */
    private static String compare(List<String> expected, List<String> resumed) {
        int loaded = resumed.indexOf(LOADED);
        if (loaded < 0) {
            int from = Math.max(0, resumed.size() - 5);
            return "игра не загрузилась:\n    " + String.join("\n    ", resumed.subList(from, resumed.size()));
        }
        List<String> after = new ArrayList<>(resumed.subList(loaded + 1, resumed.size()));
        // Загрузка заново включает музыку, «осмотреться» — нет: она и так играет
        if (!after.isEmpty() && after.get(0).startsWith("[MUSIC: ")) {
            after.remove(0);
        }
        int start = expected.size() - after.size();
        if (start < 1) {
            return "после загрузки журнал длиннее, чем без перерыва";
        }
        for (int i = 0; i < after.size(); i++) {
            if (!after.get(i).equals(expected.get(start + i))) {
                return "строка " + (i + 1) + " после загрузки\n  ожидалось: " + expected.get(start + i)
                        + "\n  получено:  " + after.get(i);
            }
        }
        if (!expected.get(start - 1).endsWith("Ваш выбор: l")) {
            return "журналы расходятся раньше точки сохранения: перед совпавшим концом — «"
                    + expected.get(start - 1) + "»";
        }
        return null;
    }
}
