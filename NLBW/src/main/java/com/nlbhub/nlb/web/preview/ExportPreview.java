/**
 * @(#)ExportPreview.java
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
package com.nlbhub.nlb.web.preview;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Каталог проверки экспортов в браузере (export-preview): игры, экспортированные в ChoiceScript и
 * jsIQ2, и движки, которые их играют.
 * <pre>
 *   export-preview/
 *     choicescript/   — экспорт в ChoiceScript: startup.txt (и images/)
 *     jsiq/           — экспорт в jsIQ2: *.xml (и картинки)
 *     runtime/        — движки, скачиваются при первом запуске
 * </pre>
 * Движков в NLB нет и в дистрибутив они не кладутся: ChoiceScript — под лицензией ChoiceScript
 * (только некоммерческое использование), у плеера jsIQ лицензии нет вовсе. Поэтому они скачиваются
 * с их собственных мест: ChoiceScript — файлы закреплённого коммита github.com/dfabulich/choicescript,
 * jsIQ — плеер с quest-book.ru и jQuery 1.10.2 (MIT; code.jquery.com или зеркала), на которой он написан.
 * Каталог — свойство nlb.exportPreview, по умолчанию export-preview в текущем каталоге (в
 * дистрибутиве — рядом с run.sh).
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public final class ExportPreview {
    /** Коммит ChoiceScript (проверен: экспорт NLB играется, randomtest проходит) */
    public static final String CHOICESCRIPT_COMMIT = "60a6c3ee7393c9ee7e42650b8e1642ae38e3cb6a";
    /** Файлы ChoiceScript с raw.githubusercontent.com (архив с codeload.github.com закрыт в части сетей) */
    static final String CHOICESCRIPT_RAW = "https://raw.githubusercontent.com/dfabulich/choicescript/" + CHOICESCRIPT_COMMIT + "/";
    /** Интерпретатор и страница игры; без них ChoiceScript не играется */
    static final String[] CHOICESCRIPT_FILES = {
            "LICENSE.txt", "web/persist.js", "web/alertify.min.js", "web/util.js", "web/style.css", "web/ui.js",
            "web/scene.js", "web/navigator.js", "web/alertify.css", "web/mygame/index.html", "web/mygame/credits.html"};
    /** Шрифты для режима «для читателей с дислексией»: без них игра работает */
    static final String[] CHOICESCRIPT_OPTIONAL = {
            "web/OpenDyslexic-Regular.woff2", "web/OpenDyslexic-Bold.woff2", "web/OpenDyslexic-Italic.woff2",
            "web/OpenDyslexic-Bold-Italic.woff2"};
    static final String JSIQ_PLAYER = "https://quest-book.ru/online/player/mitril/libs/jsiqapi212.js";
    /** jQuery 1.10.2 (на ней написан jsIQ), первый доступный адрес */
    static final String[] JQUERY = {
            "https://code.jquery.com/jquery-1.10.2.min.js",
            "https://ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js",
            "https://cdnjs.cloudflare.com/ajax/libs/jquery/1.10.2/jquery.min.js"};

    private final File m_root;

    public ExportPreview(File root) {
        m_root = root;
    }

    /** Каталог по умолчанию: свойство nlb.exportPreview или export-preview в текущем каталоге */
    public static ExportPreview defaultPreview() {
        String dir = System.getProperty("nlb.exportPreview");
        return new ExportPreview(new File(dir != null && !dir.isEmpty() ? dir : "export-preview").getAbsoluteFile());
    }

    public File getRoot() {
        return m_root;
    }

    public File getChoiceScriptDir() {
        return new File(m_root, "choicescript");
    }

    public File getJsiqDir() {
        return new File(m_root, "jsiq");
    }

    File getRuntimeDir() {
        return new File(m_root, "runtime");
    }

    File getChoiceScriptRuntime() {
        return new File(getRuntimeDir(), "choicescript");
    }

    File getJsiqRuntime() {
        return new File(getRuntimeDir(), "jsiq");
    }

    /** Каталоги игр и README (если их ещё нет) */
    public void createDirs() throws IOException {
        Files.createDirectories(getChoiceScriptDir().toPath());
        Files.createDirectories(getJsiqDir().toPath());
        Path readme = new File(m_root, "README.txt").toPath();
        if (!Files.exists(readme)) {
            Files.writeString(readme, String.join(System.lineSeparator(),
                    "Проверка экспорта NLB в браузере (ChoiceScript, jsIQ2)",
                    "",
                    "choicescript/  — сюда файлы экспорта в ChoiceScript: startup.txt (и каталог images)",
                    "jsiq/          — сюда файлы экспорта в jsIQ2: .xml (например, example.xml)",
                    "",
                    "Запуск: export-preview.sh (export-preview.bat) из каталога NLB, затем в браузере",
                    "http://localhost:8112/preview/ ; или кнопка «Проверить экспорт в браузере» в редакторе",
                    "(она сама экспортирует открытую книгу сюда и откроет http://localhost:8111/preview/).",
                    "",
                    "runtime/ — движки ChoiceScript и jsIQ: скачиваются при первом запуске с их сайтов",
                    "(github.com/dfabulich/choicescript, quest-book.ru, code.jquery.com). В NLB они не",
                    "входят: ChoiceScript — только для некоммерческого использования (LICENSE.txt рядом),",
                    "у плеера jsIQ лицензии нет.", ""), StandardCharsets.UTF_8);
        }
    }

    /** Очистить каталог игры перед новым экспортом (движки не трогаются) */
    public static void clear(File gameDir) throws IOException {
        if (gameDir.isDirectory()) {
            try (var paths = Files.walk(gameDir.toPath())) {
                paths.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile)
                        .filter(f -> !f.equals(gameDir)).forEach(File::delete);
            }
        }
        Files.createDirectories(gameDir.toPath());
    }

    boolean hasChoiceScriptRuntime() {
        return new File(getChoiceScriptRuntime(), "web/scene.js").isFile();
    }

    boolean hasJsiqRuntime() {
        return new File(getJsiqRuntime(), "jsiqapi212.js").isFile() && new File(getJsiqRuntime(), "jquery.js").isFile();
    }

    /** Скачать движки, которых ещё нет; ошибка загрузки — исключение с адресом */
    public synchronized void ensureRuntime() throws IOException {
        createDirs();
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30)).build();
        if (!hasChoiceScriptRuntime()) {
            Path tmp = getRuntimeDir().toPath().resolve("choicescript.part");
            clearPath(tmp);
            for (String file : CHOICESCRIPT_FILES) {
                download(client, new String[]{CHOICESCRIPT_RAW + file}, tmp.resolve(file));
            }
            for (String file : CHOICESCRIPT_OPTIONAL) {
                try {
                    download(client, new String[]{CHOICESCRIPT_RAW + file}, tmp.resolve(file));
                } catch (IOException e) {
                    // шрифт для дислексии — не обязателен
                }
            }
            clearPath(getChoiceScriptRuntime().toPath());
            Files.move(tmp, getChoiceScriptRuntime().toPath());
        }
        if (!hasJsiqRuntime()) {
            Files.createDirectories(getJsiqRuntime().toPath());
            download(client, JQUERY, new File(getJsiqRuntime(), "jquery.js").toPath());
            download(client, new String[]{JSIQ_PLAYER}, new File(getJsiqRuntime(), "jsiqapi212.js").toPath());
        }
    }

    /** Скачать файл с первого доступного адреса */
    private static Path download(HttpClient client, String[] urls, Path target) throws IOException {
        IOException last = null;
        for (String url : urls) {
            try {
                return download(client, url, target);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    private static Path download(HttpClient client, String url, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            HttpResponse<InputStream> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("Не удалось скачать " + url + ": HTTP " + response.statusCode());
            }
            Path part = target.resolveSibling(target.getFileName() + ".part");
            try (InputStream in = response.body()) {
                Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Загрузка прервана: " + url, e);
        } catch (java.net.ConnectException | java.net.http.HttpTimeoutException e) {
            throw new IOException("Не удалось скачать " + url + ": " + e, e);
        }
    }

    private static void clearPath(Path path) throws IOException {
        if (Files.exists(path)) {
            try (var paths = Files.walk(path)) {
                paths.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }
}
