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
 * Каталог проверки экспортов в браузере (export-preview): игры, экспортированные в ChoiceScript,
 * jsIQ2, QSP, URQ и AXMA, и движки, которые их играют.
 * <pre>
 *   export-preview/
 *     choicescript/   — экспорт в ChoiceScript: startup.txt (и images/)
 *     jsiq/           — экспорт в jsIQ2: *.xml (и картинки)
 *     qsp/            — экспорт в QSP: book.txt (текст QSP, UTF-16LE) и картинки, звук
 *     urq/            — экспорт в URQ: book.qst (windows-1251) и картинки, звук
 *     axma/           — экспорт в AXMA: book.sm и картинки, звук
 *     runtime/        — движки, скачиваются при первом открытии страницы своего формата
 * </pre>
 * Движков в NLB нет и в дистрибутив они не кладутся — каждый скачивается со своего места:
 * <ul>
 *   <li>ChoiceScript (только некоммерческое использование) — файлы закреплённого коммита
 *   github.com/dfabulich/choicescript;</li>
 *   <li>jsIQ (лицензии нет) — плеер с quest-book.ru и jQuery 1.10.2 (MIT);</li>
 *   <li>QSP — пакеты npm @qsp/wasm-engine (движок QSP, WebAssembly) и @qsp/converters (текст QSP
 *   -> игра), MIT, как в веб-плеере qSpider;</li>
 *   <li>URQ — UrqW (GPL) закреплённого коммита github.com/urqw/UrqW и его библиотеки из npm
 *   (jQuery, Bootstrap, i18next, JSZip — MIT);</li>
 *   <li>AXMA — редактор AXMA Story Maker с hyperbook.ru/editor: он собирает игру из .sm и
 *   показывает её. Редактор бесплатный, но распространять его нельзя — поэтому он скачивается на
 *   машину автора книги и только там и используется, без изменений.</li>
 * </ul>
 * Пакеты npm сверяются с SHA-512 из реестра (integrity), файлы GitHub — по коммиту.
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

    /** Движки по форматам: каждый скачивается, когда открывают страницу его формата */
    public enum Engine { CHOICESCRIPT, JSIQ, QSP, URQ, AXMA }

    /** Пакет npm: имя, версия, SHA-512 (integrity реестра) */
    static final class NpmPackage {
        final String name;
        final String version;
        final String integrity;

        NpmPackage(String name, String version, String integrity) {
            this.name = name;
            this.version = version;
            this.integrity = integrity;
        }

        String tarball() {
            String simple = name.substring(name.indexOf('/') + 1);
            return "https://registry.npmjs.org/" + name + "/-/" + simple + "-" + version + ".tgz";
        }
    }

    /** Движок QSP (WebAssembly) и преобразование текста QSP в игру — как в qSpider */
    static final NpmPackage QSP_ENGINE = new NpmPackage("@qsp/wasm-engine", "1.6.1",
            "sha512-KTgGTpYix0Dh9jLQGHRc1kZhKXwhhTCgAoZB7gidnUoKVYWmUgaMrYO1pjEOcI5PSPBfKJ2U8TnTAGixsOg9LQ==");
    static final NpmPackage QSP_CONVERTERS = new NpmPackage("@qsp/converters", "2.0.0",
            "sha512-TSgJl0zenqyQUefqazdGItvHlbWS1meDzncRAzMQSF9Aq0Aqg9pJTda6/0hPjhrVgEDV0hFKBYBPP37MHcD38Q==");

    /** Коммит UrqW (проверен: экспорт NLB играется, стенд tools/export-engines/urqw-run.js) */
    public static final String URQW_COMMIT = "cbab60a6ffa350dc8c73173a2f9baaef300f0c70";
    static final String URQW_RAW = "https://raw.githubusercontent.com/urqw/UrqW/" + URQW_COMMIT + "/";
    /** Файлы UrqW: страница index-dev.html (берёт библиотеки из node_modules), скрипты, локали */
    static final String[] URQW_FILES = {
            "LICENSE-GPL.txt", "index-dev.html", "css/style.css", "logo.svg", "rss.svg", "favicon.png",
            "js/tools.js", "js/player/player.js", "js/player/engine.js", "js/quest.js", "js/parser.js",
            "js/functions.js", "js/evaluator.js", "js/client.js", "js/ctrl.js", "js/hotkeys.js", "js/i18n.js",
            "js/main.js", "locale/be.js", "locale/en.js", "locale/eo.js", "locale/es.js", "locale/hy.js",
            "locale/isv_Latn.js", "locale/ru.js", "locale/uk.js"};
    /** Библиотеки UrqW — версии из его package-lock.json */
    static final NpmPackage[] URQW_PACKAGES = {
            new NpmPackage("bootstrap", "3.4.1",
                    "sha512-yN5oZVmRCwe5aKwzRj6736nSmKDX7pLYwsXiCj/EYmo16hODaBiT4En5btW/jhBF/seV+XMx3aYwukYC3A49DA=="),
            new NpmPackage("i18next", "24.2.3",
                    "sha512-lfbf80OzkocvX7nmZtu7nSTNbrTYR52sLWxPtlXX1zAhVw8WEnFk4puUkCR4B1dNQwbSpEHHHemcZu//7EcB7A=="),
            new NpmPackage("i18next-browser-languagedetector", "8.0.4",
                    "sha512-f3frU3pIxD50/Tz20zx9TD9HobKYg47fmAETb117GKGPrhwcSSPJDoCposXlVycVebQ9GQohC3Efbpq7/nnJ5w=="),
            new NpmPackage("jquery", "2.1.4",
                    "sha512-wWR+eCq/T/Qt0NcFyM+QVho0ZVzWxFYANijmSMImXiM5mjr1aOaf4SF0eOEPc92bbK2L2vDpxw3lIszus7eO8Q=="),
            new NpmPackage("jszip", "2.7.0",
                    "sha512-JIsRKRVC3gTRo2vM4Wy9WBC3TRcfnIZU8k65Phi3izkvPH975FowRYtKGT6PxevA0XnJ/yO8b0QwV0ydVyQwfw=="),
            new NpmPackage("jszip-utils", "0.0.2",
                    "sha512-6VaNqoaj25tYdRY6ADcrGT9PKZfeBm2ZsYqbR1eeoVd9rBmaX0dSlXxBi1cBzAed4Yxu3jRIe5GSC9iHuQuziQ==")};

    /** Редактор AXMA Story Maker: страница, скрипт и стили (картинки кнопок редактора не нужны) */
    static final String AXMA_EDITOR = "https://hyperbook.ru/editor/";
    static final String[] AXMA_FILES = {"index.html", "axmastorymaker.js", "axmastorymaker.css"};

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

    public File getQspDir() {
        return new File(m_root, "qsp");
    }

    public File getUrqDir() {
        return new File(m_root, "urq");
    }

    public File getAxmaDir() {
        return new File(m_root, "axma");
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

    File getQspRuntime() {
        return new File(getRuntimeDir(), "qsp");
    }

    File getUrqwRuntime() {
        return new File(getRuntimeDir(), "urqw");
    }

    File getAxmaRuntime() {
        return new File(getRuntimeDir(), "axma");
    }

    /** Каталоги игр и README (если их ещё нет) */
    public void createDirs() throws IOException {
        for (File dir : new File[]{getChoiceScriptDir(), getJsiqDir(), getQspDir(), getUrqDir(), getAxmaDir()}) {
            Files.createDirectories(dir.toPath());
        }
        Path readme = new File(m_root, "README.txt").toPath();
        if (!Files.exists(readme)) {
            Files.writeString(readme, String.join(System.lineSeparator(),
                    "Проверка экспорта NLB в браузере (ChoiceScript, jsIQ2, QSP, URQ, AXMA)",
                    "",
                    "choicescript/  — файлы экспорта в ChoiceScript: startup.txt (и каталог images)",
                    "jsiq/          — файлы экспорта в jsIQ2: .xml (например, example.xml)",
                    "qsp/           — файлы экспорта в QSP: book.txt (и каталоги images, sound)",
                    "urq/           — файлы экспорта в URQ: book.qst (и каталоги images, sound)",
                    "axma/          — файлы экспорта в AXMA: book.sm (и каталоги images, sound)",
                    "",
                    "Запуск: export-preview.sh (export-preview.bat) из каталога NLB, затем в браузере",
                    "http://localhost:8112/preview/ ; или кнопка «Проверить экспорт в браузере» в редакторе",
                    "(она сама экспортирует открытую книгу сюда и откроет http://localhost:8111/preview/).",
                    "",
                    "runtime/ — движки: скачиваются при первом открытии игры своего формата с их мест",
                    "(github.com/dfabulich/choicescript, quest-book.ru, code.jquery.com, registry.npmjs.org,",
                    "github.com/urqw/UrqW, hyperbook.ru). В NLB они не входят: ChoiceScript — только для",
                    "некоммерческого использования (LICENSE.txt рядом), у плеера jsIQ лицензии нет, UrqW —",
                    "GPL, редактор AXMA Story Maker распространять нельзя (он используется только здесь).",
                    ""), StandardCharsets.UTF_8);
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

    boolean hasQspRuntime() {
        return new File(getQspRuntime(), "wasm-engine/dist/qsp-engine.wasm").isFile()
                && new File(getQspRuntime(), "converters/dist/index.js").isFile();
    }

    boolean hasUrqwRuntime() {
        return new File(getUrqwRuntime(), "index-dev.html").isFile();
    }

    boolean hasAxmaRuntime() {
        return new File(getAxmaRuntime(), "axmastorymaker.js").isFile();
    }

    /** Скачать все движки, которых ещё нет; ошибка загрузки — исключение с адресом */
    public void ensureRuntime() throws IOException {
        IOException first = null;
        for (Engine engine : Engine.values()) {
            try {
                ensureRuntime(engine);
            } catch (IOException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    /** Скачать движок формата, если его ещё нет; ошибка загрузки — исключение с адресом */
    public synchronized void ensureRuntime(Engine engine) throws IOException {
        createDirs();
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30)).build();
        switch (engine) {
            case CHOICESCRIPT:
                if (!hasChoiceScriptRuntime()) {
                    Path tmp = part(getChoiceScriptRuntime());
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
                    commit(tmp, getChoiceScriptRuntime());
                }
                break;
            case JSIQ:
                if (!hasJsiqRuntime()) {
                    Files.createDirectories(getJsiqRuntime().toPath());
                    download(client, JQUERY, new File(getJsiqRuntime(), "jquery.js").toPath());
                    download(client, new String[]{JSIQ_PLAYER}, new File(getJsiqRuntime(), "jsiqapi212.js").toPath());
                }
                break;
            case QSP:
                if (!hasQspRuntime()) {
                    Path tmp = part(getQspRuntime());
                    npm(client, QSP_ENGINE, tmp.resolve("wasm-engine"));
                    npm(client, QSP_CONVERTERS, tmp.resolve("converters"));
                    commit(tmp, getQspRuntime());
                }
                break;
            case URQ:
                if (!hasUrqwRuntime()) {
                    Path tmp = part(getUrqwRuntime());
                    for (NpmPackage pkg : URQW_PACKAGES) {
                        npm(client, pkg, tmp.resolve("node_modules").resolve(pkg.name));
                    }
                    for (String file : URQW_FILES) {
                        download(client, new String[]{URQW_RAW + file}, tmp.resolve(file));
                    }
                    commit(tmp, getUrqwRuntime());
                }
                break;
            case AXMA:
                if (!hasAxmaRuntime()) {
                    Path tmp = part(getAxmaRuntime());
                    for (String file : AXMA_FILES) {
                        // страница редактора — сам каталог editor/ (index.html по этому адресу нет)
                        String url = AXMA_EDITOR + (file.equals("index.html") ? "" : file);
                        download(client, new String[]{url}, tmp.resolve(file));
                    }
                    commit(tmp, getAxmaRuntime());
                }
                break;
            default:
                break;
        }
    }

    /** Временный каталог загрузки движка (dir.part): движок появляется целиком или не появляется */
    private Path part(File dir) throws IOException {
        Path tmp = getRuntimeDir().toPath().resolve(dir.getName() + ".part");
        clearPath(tmp);
        Files.createDirectories(tmp);
        return tmp;
    }

    private static void commit(Path tmp, File dir) throws IOException {
        clearPath(dir.toPath());
        Files.move(tmp, dir.toPath());
    }

    /** Скачать пакет npm, сверить SHA-512 и распаковать его каталог package/ в target */
    private static void npm(HttpClient client, NpmPackage pkg, Path target) throws IOException {
        Path tgz = target.resolveSibling(target.getFileName() + ".tgz");
        download(client, pkg.tarball(), tgz);
        try {
            byte[] data = Files.readAllBytes(tgz);
            String integrity = "sha512-" + java.util.Base64.getEncoder().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-512").digest(data));
            if (!integrity.equals(pkg.integrity)) {
                throw new IOException("Пакет " + pkg.name + " " + pkg.version + " не совпал с реестром npm (SHA-512): "
                        + pkg.tarball());
            }
            Tar.extract(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data)), "package/", target);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        } finally {
            Files.deleteIfExists(tgz);
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
