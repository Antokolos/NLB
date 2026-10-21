/**
 * @(#)ConsolePlayer.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2026 Anton P. Kolosov
 * Authors: Anton P. Kolosov, et al.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * ANTON P. KOLOSOV. ANTON P. KOLOSOV DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the Non-Linear Book software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Anton P. Kolosov at this
 * address: antokolos@gmail.com
 *
 * Copyright (c) 2026 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.config.Export;
import com.nlbhub.nlb.api.config.Settings;
import com.nlbhub.nlb.domain.NonLinearBookImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Консольный плеер книги NLB — порт NLBExplorer из NLBNavigator: тот же вывод и те же команды,
 * поэтому журнал сессии можно сравнивать с журналом {@code nlb_main --log} построчно.
 * <pre>
 *   N        — перейти по ссылке N
 *   oN       — действие с объектом N на странице (переносимый объект берётся в инвентарь)
 *   iN       — щелчок по предмету N в инвентаре
 *   uN oM    — применить предмет инвентаря N к объекту страницы M (uN iM — к предмету инвентаря)
 *   w        — подождать (тик таймера страницы)
 *   l        — осмотреться: заново показать текущую страницу
 *   m        — меню: начать заново, выйти
 *   0        — выход (с подтверждением)
 * </pre>
 * Запуск: {@code java -cp NLBL.jar com.nlbhub.nlb.player.ConsolePlayer <каталог книги> [vn|standard]
 * [--deterministic[=n]] [--config cfg/config.xml] [--echo]}
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class ConsolePlayer {
    /**
     * Обработчик внешней (нетекстовой) игры в консоли: получает движок и потоки ввода-вывода;
     * по возврату управление уходит новелле
     */
    public interface ExternalGameHandler {
        void play(PlayerEngine engine, LineReader readLine, PrintWriter out) throws IOException;
    }

    public interface LineReader {
        /**
         * @return строка ввода или null, если ввод закончился
         */
        String readLine() throws IOException;
    }

    private static final String SEPARATOR = "==================================================";

    private final PlayerEngine m_engine;
    private final BufferedReader m_in;
    private final PrintWriter m_out;
    private final List<String> m_externalNames = new ArrayList<>();
    private final List<ExternalGameHandler> m_externalHandlers = new ArrayList<>();
    /**
     * Повторять введённую строку в вывод — как журнал сессии nlb_main (ввод не с терминала)
     */
    private boolean m_echoInput = false;

    public ConsolePlayer(NonLinearBook book, PlayerEngine.Settings settings, BufferedReader in, PrintWriter out) {
        m_engine = new PlayerEngine(book, settings);
        m_in = in;
        m_out = out;
        // Предупреждения книги — в тот же вывод, в момент исполнения (как stderr в журнале nlb_main)
        m_engine.setWarnings(message -> {
            m_out.println(message);
            m_out.flush();
        });
        m_engine.setExternalGameFilter(name -> findExternalGame(name) != null);
        // Мини-игры «Большая свинья» из INSTEAD-модуля big_pig.lua (play_bp_two_ais, ...)
        registerExternalGame("play_bp_*", ConsolePlayer::demoDiceGame);
    }

    public void setEchoInput(boolean echoInput) {
        m_echoInput = echoInput;
    }

    public PlayerEngine engine() {
        return m_engine;
    }

    /**
     * name — имя игры; "префикс*" — все игры с таким префиксом
     */
    public void registerExternalGame(String name, ExternalGameHandler handler) {
        m_externalNames.add(name);
        m_externalHandlers.add(handler);
    }

    /**
     * Интерактивная игра до конца книги или команды выхода
     */
    public void explore() throws IOException {
        try {
            m_engine.start();
            while (true) {
                printEvents();
                if (m_engine.externalGame() != null) {
                    runExternalGame();
                    continue;
                }
                PlayerEngine.PageView view = m_engine.view();
                if (view.isFinished()) {
                    // VN-экспорт на последней странице предлагает начать заново (_try_again)
                    if (!m_engine.isVnPage(m_engine.currentPage())) {
                        break;
                    }
                    prompt("\n1. Начать заново\n0. Выход\n\nВаш выбор: ");
                    String line = readLine();
                    if (line == null || !line.equals("1")) {
                        break;
                    }
                    m_engine.restart();
                    continue;
                }
                printView(view);
                prompt("\nВаш выбор: ");
                String line = readLine();
                if (line == null || !handleCommand(line, view)) {
                    break;
                }
            }
        } catch (PlayerException e) {
            m_out.println();
            m_out.println("!!! Ошибка исполнения книги: " + e.getMessage());
        }
        m_out.flush();
    }

    private void prompt(String text) {
        m_out.print(text);
        m_out.flush();
    }

    private String readLine() throws IOException {
        String line = m_in.readLine();
        if (line != null && line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        if (line != null && m_echoInput) {
            m_out.println(line);
        }
        return line;
    }

    private static String frame(String pattern, int n) {
        int pos = pattern.indexOf("%d");
        return pos < 0 ? pattern : pattern.substring(0, pos) + n + pattern.substring(pos + 2);
    }

    private static String suffix(String separator, String value) {
        return value.isEmpty() ? "" : separator + value;
    }

    private void printEvents() {
        for (PlayerEngine.Event event : m_engine.takeEvents()) {
            String text = event.getText();
            switch (event.getKind()) {
                case PageCaption:
                    m_out.println();
                    m_out.println(SEPARATOR);
                    m_out.println(text);
                    m_out.println(SEPARATOR);
                    break;
                case PageText:
                    m_out.println();
                    m_out.println(text);
                    break;
                case ObjectText:
                case AltText:
                case Text:
                case Info:
                    m_out.println(text);
                    break;
                case Image:
                    m_out.println("[IMAGE: " + text + suffix(" — ", event.getSubject())
                            + suffix(" @ ", event.getPosition()) + "]");
                    break;
                case Animation:
                    // В INSTEAD кадры меняются по таймеру; в консоли — только описание анимации
                    m_out.println("[ANIMATION: " + frame(text, 1) + " ... " + frame(text, event.getFrames())
                            + ", кадров: " + event.getFrames() + ", смена по таймеру"
                            + suffix(" — ", event.getSubject()) + suffix(" @ ", event.getPosition()) + "]");
                    break;
                case Sound:
                    m_out.println("[SOUND: " + text + "]");
                    break;
                case Voice:
                    m_out.println("[VOICE: " + text + "]");
                    break;
                case Music:
                    m_out.println(text.isEmpty() ? "[MUSIC: стоп]" : "[MUSIC: " + text + "]");
                    break;
                case Achievement:
                    m_out.println("*** " + text + " ***");
                    break;
                case Finish:
                    m_out.println();
                    m_out.println("=== КОНЕЦ ===");
                    break;
                case ExternalGame:
                default:
                    break;  // внешняя игра обрабатывается в runExternalGame
            }
        }
        m_out.flush();
    }

    /**
     * Подпись объекта в списке: многострочную (панель HP и т.п.) сводим в одну строку
     */
    private static String withPosition(PlayerEngine.ObjectView o) {
        String disp = o.getDisp().replace('\n', ' ');
        return o.getPosition().isEmpty() ? disp : disp + " [" + o.getPosition() + "]";
    }

    private void printList(String title, List<PlayerEngine.ObjectView> list, char prefix) {
        if (list.isEmpty()) {
            return;
        }
        // Графические объекты выводятся деревом: по строке на объект, с отступом по вложенности
        // и положением на экране; активные объекты получают номера, неактивные — «· »
        boolean tree = false;
        for (PlayerEngine.ObjectView o : list) {
            tree = tree || o.getPlacement().isGraphical() || o.getDepth() > 0;
        }
        int n = 0;
        if (tree) {
            m_out.println(title);
            for (PlayerEngine.ObjectView o : list) {
                StringBuilder line = new StringBuilder(" ".repeat(2 + 4 * o.getDepth()));
                line.append(o.isActive() ? prefix + String.valueOf(++n) + ". " : "· ");
                line.append(withPosition(o)).append(o.isCollapsed() ? " (свёрнут)" : "")
                        .append(o.isTakable() ? " (можно взять)" : "");
                m_out.println(line);
            }
            return;
        }
        StringBuilder line = new StringBuilder(title);
        for (PlayerEngine.ObjectView o : list) {
            if (o.isActive()) {
                line.append("  ").append(prefix).append(++n).append(". ").append(withPosition(o))
                        .append(o.isTakable() ? " (можно взять)" : "");
            }
        }
        m_out.println(line);
        for (PlayerEngine.ObjectView o : list) {
            if (!o.isActive()) {
                m_out.println("   · " + withPosition(o));
            }
        }
    }

    private void printView(PlayerEngine.PageView view) {
        if (!view.getSceneObjects().isEmpty() || !view.getInventory().isEmpty()) {
            m_out.println();
        }
        printList("Здесь:", view.getSceneObjects(), 'o');
        printList("Инвентарь:", view.getInventory(), 'i');
        m_out.println();
        List<PlayerEngine.Choice> choices = view.getChoices();
        for (int i = 0; i < choices.size(); i++) {
            m_out.println((i + 1) + ". " + choices.get(i).getText());
        }
        if (m_engine.hasTimer()) {
            m_out.println("w. Подождать");
        }
        m_out.println("l. Осмотреться");
        m_out.println("m. Меню");
    }

    /**
     * Как std::stol: необязательные пробелы и знак, затем цифры; остаток строки не важен.
     * null — числа нет
     */
    private static Long parseLong(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        int start = i;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            i++;
        }
        int digits = i;
        while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
        }
        if (i == digits) {
            return null;
        }
        try {
            return Long.parseLong(s.substring(start, i));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * "o3" -> индекс 2 в списке; некорректная ссылка -> -1
     */
    private static int parseRef(String token, char expectedPrefix) {
        if (token == null || token.length() < 2 || token.charAt(0) != expectedPrefix) {
            return -1;
        }
        Long n = parseLong(token.substring(1));
        return (n == null || n < 1 || n > Integer.MAX_VALUE) ? -1 : (int) (n - 1);
    }

    private static List<String> activeOnly(List<PlayerEngine.ObjectView> list) {
        List<String> ids = new ArrayList<>();
        for (PlayerEngine.ObjectView o : list) {
            if (o.isActive()) {
                ids.add(o.getInstanceId());
            }
        }
        return ids;
    }

    private static String refId(List<String> list, int index) {
        return (index >= 0 && index < list.size()) ? list.get(index) : "";
    }

    /**
     * @return false — выход
     */
    private boolean handleCommand(String line, PlayerEngine.PageView view) throws IOException {
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return true;
        }
        String first = tokens[0];
        if (first.equals("0")) {
            // Подтверждение — чтобы не завершить игру случайно
            return !confirm("Выйти из игры?");
        }
        if (first.equals("m")) {
            return runMenu();
        }
        if (first.equals("w")) {
            m_engine.waitTick();
            return true;
        }
        if (first.equals("l")) {
            m_engine.look();
            return true;
        }
        // Номера oN/iN — только у активных объектов
        List<String> sceneIds = activeOnly(view.getSceneObjects());
        List<String> invIds = activeOnly(view.getInventory());
        boolean ok;
        char kind = first.charAt(0);
        if (kind == 'u') {
            String second = tokens.length > 1 ? tokens[1] : "";
            String source = refId(invIds, parseRef("i" + first.substring(1), 'i'));
            String target = second.startsWith("o")
                    ? refId(sceneIds, parseRef(second, 'o'))
                    : refId(invIds, parseRef(second, 'i'));
            ok = !source.isEmpty() && !target.isEmpty() && m_engine.use(source, target);
        } else if (kind == 'o') {
            String id = refId(sceneIds, parseRef(first, 'o'));
            ok = !id.isEmpty() && m_engine.actOnObject(id);
        } else if (kind == 'i') {
            String id = refId(invIds, parseRef(first, 'i'));
            ok = !id.isEmpty() && m_engine.clickInventory(id);
        } else {
            Long n = parseLong(first);
            ok = n != null && n >= 1 && n <= Integer.MAX_VALUE && m_engine.choose((int) (n - 1));
        }
        if (!ok) {
            m_out.println("Неверный выбор. Команды: N, oN, iN, uN oM | uN iM, w — подождать, l — осмотреться, m — меню.");
        }
        return true;
    }

    private boolean confirm(String question) throws IOException {
        prompt(question + " 1 — да, любой другой ввод — нет: ");
        String answer = readLine();
        // ввод закончился — считаем согласием (выход)
        return answer == null || answer.equals("1");
    }

    /**
     * Меню; false — выход из игры. Сохранения в консольном плеере пока не поддерживаются
     */
    private boolean runMenu() throws IOException {
        while (true) {
            prompt("\n=== Меню ===\n3. Начать заново\n4. Выйти из игры\n0. Вернуться в игру\n\nВаш выбор: ");
            String line = readLine();
            if (line == null) {
                return false;
            }
            if (line.equals("0") || line.isEmpty()) {
                m_engine.look();  // как после закрытия меню в INSTEAD: снова видна текущая сцена
                return true;
            }
            if (line.equals("3")) {
                if (confirm("Начать игру заново? Несохранённый прогресс будет потерян.")) {
                    m_engine.restart();
                    return true;
                }
            } else if (line.equals("4")) {
                if (confirm("Выйти из игры?")) {
                    return false;
                }
            } else {
                m_out.println("Неверный выбор.");
            }
        }
    }

    private ExternalGameHandler findExternalGame(String name) {
        for (int i = 0; i < m_externalNames.size(); i++) {
            String pattern = m_externalNames.get(i);
            boolean prefix = pattern.endsWith("*");
            if (prefix ? name.startsWith(pattern.substring(0, pattern.length() - 1)) : name.equals(pattern)) {
                return m_externalHandlers.get(i);
            }
        }
        return null;
    }

    private void runExternalGame() throws IOException {
        PlayerEngine.ExternalGame game = m_engine.externalGame();
        m_out.println();
        m_out.println("=== Внешняя игра: " + game.getName() + " ===");
        ExternalGameHandler handler = findExternalGame(game.getName());
        if (handler != null) {
            handler.play(m_engine, this::readLine, m_out);
        }
        m_out.println("=== Возврат в новеллу ===");
        m_engine.finishExternalGame();
    }

    /**
     * Консольная заглушка мини-игры. Настоящая игра подключается так же: обработчик получает
     * движок и через getVariable/setVariable, giveItem/takeItem и evaluate читает и меняет
     * состояние новеллы, а вернувшись, отдаёт ход ей
     */
    private static void demoDiceGame(PlayerEngine engine, LineReader readLine, PrintWriter out) throws IOException {
        final String money = "money";  // в «Большой свинье» выигрыш — переменная money
        Runnable show = () -> out.println("Деньги (" + money + "): " + engine.getVariable(money).toDisplayString());
        out.println("Здесь могла бы быть мини-игра (кости, 3D-сцена). В консольном плеере — заглушка.");
        while (true) {
            show.run();
            out.print("1. Бросить кубик: +100 монет и вернуться в новеллу\n"
                    + "2. Переменные: показать все (v) или изменить (v имя=выражение)\n"
                    + "0. Закончить игру и вернуться в новеллу\n\nВаш выбор: ");
            out.flush();
            String line = readLine.readLine();
            if (line == null) {
                return;
            }
            if (line.equals("1")) {
                Value value = engine.getVariable(money);
                boolean numeric = value.getType() == Value.Type.BOOL || value.getType() == Value.Type.INT
                        || value.getType() == Value.Type.REAL;
                double current = numeric ? value.asDouble() : 0.0;
                engine.setVariable(money, Value.of((long) (current + 100)));
                out.println("Кубик брошен — вы получили 100 монет.");
                show.run();
                return;
            }
            if (line.equals("0") || line.isEmpty()) {
                return;
            }
            if (line.equals("2") || line.equals("v")) {
                for (String name : engine.variableNames()) {
                    out.println("  " + name + " = " + engine.getVariable(name).toDisplayString());
                }
                continue;
            }
            if (line.startsWith("v ")) {
                int eq = line.indexOf('=');
                if (eq < 0) {
                    out.println("Формат: v имя=выражение");
                    continue;
                }
                String name = line.substring(2, eq);
                try {
                    engine.setVariable(name, engine.evaluate(line.substring(eq + 1)));
                    out.println(name + " = " + engine.getVariable(name).toDisplayString());
                } catch (PlayerException e) {
                    out.println("Ошибка: " + e.getMessage());
                }
                continue;
            }
            out.println("Неверный выбор.");
        }
    }

    /**
     * Загружает книгу; null — не удалось
     */
    public static NonLinearBookImpl loadBook(File bookDir) {
        try {
            NonLinearBookImpl book = new NonLinearBookImpl();
            return book.load(bookDir.getPath(), new DummyProgressData()) ? book : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> positional = new ArrayList<>();
        String configPath = null;
        boolean echo = false;
        long deterministicStart = -1;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--config") && i + 1 < args.length) {
                configPath = args[++i];
            } else if (arg.equals("--deterministic")) {
                deterministicStart = 0;
            } else if (arg.startsWith("--deterministic=")) {
                deterministicStart = Long.parseLong(arg.substring("--deterministic=".length()));
            } else if (arg.equals("--echo")) {
                echo = true;
            } else {
                positional.add(arg);
            }
        }
        PrintStream stdout = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        if (positional.isEmpty()) {
            stdout.println("Usage: ConsolePlayer <path_to_nlb_directory> [vn|standard] [--config <config.xml>]"
                    + " [--deterministic[=<n>]] [--echo]");
            System.exit(1);
        }
        File bookDir = new File(positional.get(0));
        NonLinearBookImpl book = loadBook(bookDir);
        if (book == null) {
            stdout.println("Cannot load book: " + bookDir);
            System.exit(1);
        }
        stdout.println("Loaded: " + book.getTitle() + " by " + book.getAuthor());
        Export export = null;
        File config = new File(configPath != null ? configPath : "cfg/config.xml");
        if (config.isFile()) {
            Settings settings = PropertyManager.readSettings(config);
            export = settings.getDefaultConfig().getExport();
        }
        PlayerEngine.Settings settings = PlayerEngine.Settings.fromExport(export, book.getLanguage());
        settings.exportMode = positional.size() > 1 && positional.get(1).equals("standard")
                ? PlayerEngine.ExportMode.STANDARD : PlayerEngine.ExportMode.VN;
        if (deterministicStart >= 0) {
            settings.deterministic = true;
            settings.deterministicStart = deterministicStart;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(stdout, StandardCharsets.UTF_8));
        ConsolePlayer player = new ConsolePlayer(book, settings, in, out);
        player.setEchoInput(echo);
        player.explore();
    }
}
