/**
 * @(#)ExportPreviewServlet.java
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

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Проверка экспортов в браузере: /preview/ — список игр по форматам из каталога
 * {@link ExportPreview}.
 * <ul>
 *   <li>ChoiceScript (/preview/choicescript/) — страница игры из самого ChoiceScript
 *   (web/mygame/index.html); она грузит интерпретатор из каталога выше (/preview/ui.js…), сцены —
 *   из scenes/ (startup.txt экспорта), список сцен — mygame.js (startup и остальные .txt);</li>
 *   <li>jsIQ2 (/preview/jsiq/play.html?book=…) — страница с плеером jsiqapi212.js (как на
 *   quest-book.ru) и книгой .xml экспорта;</li>
 *   <li>QSP (/preview/qsp/play.html?book=…) — своя страница поверх движка @qsp/wasm-engine: текст
 *   QSP экспорта переводится в игру (@qsp/converters, как в qSpider) прямо в браузере; текст,
 *   действия, инвентарь, окна MSG и MENU, звук, таймер;</li>
 *   <li>URQ (/preview/urq/) — UrqW: страница index-dev.html?id=&lt;книга&gt;; UrqW ищет игру в
 *   quests/&lt;книга&gt;/main.qst — сервер отдаёт под этим именем файл .qst экспорта, а
 *   картинки и звук — из каталога экспорта;</li>
 *   <li>AXMA (/preview/axma/play.html?book=…) — редактор AXMA Story Maker в рамке: страница
 *   загружает в него .sm экспорта и запускает игру (Compile), как кнопка «Запуск» редактора;
 *   картинки игры — из каталога экспорта (адреса игры — относительно страницы редактора).</li>
 * </ul>
 * Движок скачивается при первом открытии игры своего формата
 * ({@link ExportPreview#ensureRuntime(ExportPreview.Engine)}).
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class ExportPreviewServlet extends HttpServlet {
    /** Путь сервлета в контексте */
    public static final String PATH = "/preview/*";

    private final ExportPreview m_preview;

    public ExportPreviewServlet(ExportPreview preview) {
        m_preview = preview;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo() == null ? "/" : request.getPathInfo();
        if (request.getPathInfo() == null) {
            response.sendRedirect(request.getRequestURI() + "/");
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        ExportPreview.Engine engine = entryEngine(path);
        if (engine != null) {
            try {
                m_preview.ensureRuntime(engine);
            } catch (IOException e) {
                html(response, page("Проверка экспорта", "<p class='error'>Не удалось скачать движок: " + escape(e.getMessage())
                        + "</p><p>Нужен доступ в интернет к " + escape(hosts(engine)) + " (только при первом запуске; "
                        + "движок сохраняется в " + escape(m_preview.getRoot().getPath()) + "/runtime).</p>"
                        + (engine == ExportPreview.Engine.AXMA ? axmaFallback() : "")
                        + "<p><a href='../'>← к списку</a></p>"));
                return;
            }
        }
        if (path.equals("/")) {
            html(response, index());
        } else if (path.startsWith("/choicescript/")) {
            choiceScript(path.substring("/choicescript/".length()), response);
        } else if (path.startsWith("/jsiq/")) {
            jsiq(path.substring("/jsiq/".length()), request, response);
        } else if (path.startsWith("/qsp/")) {
            qsp(path.substring("/qsp/".length()), request, response);
        } else if (path.startsWith("/urq/")) {
            urq(path.substring("/urq/".length()), response);
        } else if (path.startsWith("/axma/")) {
            axma(path.substring("/axma/".length()), request, response);
        } else {
            // интерпретатор ChoiceScript: страница игры подключает его из каталога выше (../ui.js)
            file(new File(m_preview.getChoiceScriptRuntime(), "web"), path.substring(1), response);
        }
    }

    /** Страница входа в игру формата — перед ней движок скачивается (если его ещё нет) */
    private static ExportPreview.Engine entryEngine(String path) {
        switch (path) {
            case "/choicescript/":
            case "/choicescript/index.html":
                return ExportPreview.Engine.CHOICESCRIPT;
            case "/jsiq/":
            case "/jsiq/play.html":
                return ExportPreview.Engine.JSIQ;
            case "/qsp/":
            case "/qsp/play.html":
                return ExportPreview.Engine.QSP;
            case "/urq/":
            case "/urq/index-dev.html":
                return ExportPreview.Engine.URQ;
            case "/axma/":
            case "/axma/play.html":
                return ExportPreview.Engine.AXMA;
            default:
                return null;
        }
    }

    private static String hosts(ExportPreview.Engine engine) {
        switch (engine) {
            case CHOICESCRIPT:
                return "raw.githubusercontent.com";
            case JSIQ:
                return "quest-book.ru и code.jquery.com";
            case QSP:
                return "registry.npmjs.org";
            case URQ:
                return "raw.githubusercontent.com и registry.npmjs.org";
            case AXMA:
                return "hyperbook.ru";
            default:
                return "";
        }
    }

    private String index() {
        File[] scenes = m_preview.getChoiceScriptDir().listFiles((d, n) -> n.endsWith(".txt"));
        File scenesDir = new File(m_preview.getChoiceScriptDir(), "scenes");
        boolean hasChoiceScript = new File(m_preview.getChoiceScriptDir(), "startup.txt").isFile()
                || new File(scenesDir, "startup.txt").isFile();
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Каталог: <code>").append(escape(m_preview.getRoot().getPath())).append("</code></p>");
        sb.append("<h2>ChoiceScript</h2>");
        if (hasChoiceScript) {
            sb.append("<p><a href='choicescript/'>Играть</a> (сцен: ").append(scenes == null ? 0 : scenes.length).append(")</p>");
        } else {
            empty(sb, "ChoiceScript (startup.txt и каталог images)", m_preview.getChoiceScriptDir());
        }
        books(sb, "jsIQ2", "jsiq/play.html?book=", jsiqBooks(), "jsIQ2 (.xml)", m_preview.getJsiqDir());
        books(sb, "QSP", "qsp/play.html?book=", books(m_preview.getQspDir(), ".txt", ".qsp", ".gam"),
                "QSP (book.txt — текст QSP, или .qsp)", m_preview.getQspDir());
        books(sb, "URQ (UrqW)", "urq/index-dev.html?encoding=CP1251&id=", urqIds(), "URQ (.qst)", m_preview.getUrqDir());
        books(sb, "AXMA", "axma/play.html?book=", books(m_preview.getAxmaDir(), ".sm"), "AXMA (.sm)", m_preview.getAxmaDir());
        sb.append("<p class='note'>Движки скачаны с их сайтов в <code>runtime</code> при первом открытии игры. ChoiceScript — только "
                + "для некоммерческого использования (<a href='license-choicescript'>лицензия</a>); UrqW — GPL; редактор "
                + "AXMA Story Maker (hyperbook.ru) — бесплатный, без права распространения: он скачан только на эту машину.</p>");
        return page("Проверка экспорта NLB", sb.toString());
    }

    private static void empty(StringBuilder sb, String what, File dir) {
        sb.append("<p>Положите файлы экспорта в ").append(escape(what)).append(" в <code>").append(escape(dir.getPath()))
                .append("</code></p>");
    }

    private static void books(StringBuilder sb, String title, String href, List<String> books, String what, File dir) {
        sb.append("<h2>").append(escape(title)).append("</h2>");
        if (books.isEmpty()) {
            empty(sb, what, dir);
            return;
        }
        sb.append("<ul>");
        for (String book : books) {
            sb.append("<li><a href='").append(href).append(escape(java.net.URLEncoder.encode(book, StandardCharsets.UTF_8)))
                    .append("'>").append(escape(book)).append("</a></li>");
        }
        sb.append("</ul>");
    }

    /** Файлы игр в каталоге (по расширениям), по имени */
    private static List<String> books(File dir, String... extensions) {
        String[] names = dir.list((d, n) -> {
            String lower = n.toLowerCase(Locale.ROOT);
            for (String ext : extensions) {
                if (lower.endsWith(ext) && new File(d, n).isFile()) {
                    return true;
                }
            }
            return false;
        });
        List<String> books = new ArrayList<>(names == null ? List.of() : Arrays.asList(names));
        books.sort(null);
        return books;
    }

    /** Игры URQ — имена .qst без расширения (UrqW открывает игру по имени: ?id=book) */
    private List<String> urqIds() {
        List<String> ids = new ArrayList<>();
        for (String name : books(m_preview.getUrqDir(), ".qst")) {
            ids.add(name.substring(0, name.length() - 4));
        }
        return ids;
    }

    private List<String> jsiqBooks() {
        return books(m_preview.getJsiqDir(), ".xml");
    }

    private void choiceScript(String path, HttpServletResponse response) throws IOException {
        File runtime = new File(m_preview.getChoiceScriptRuntime(), "web/mygame");
        File game = m_preview.getChoiceScriptDir();
        switch (path) {
            case "":
            case "index.html":
                file(runtime, "index.html", response);
                return;
            case "version.js":
                text(response, "text/javascript", "window.version = \"NLB export preview\";\n");
                return;
            case "mygame.js":
                text(response, "text/javascript", myGame(game));
                return;
            default:
                break;
        }
        if (path.startsWith("scenes/")) {
            // сцены: каталог scenes/ или сам каталог игры (экспорт NLB кладёт startup.txt туда)
            String name = path.substring("scenes/".length());
            File inScenes = new File(game, "scenes/" + name);
            file(inScenes.isFile() ? new File(game, "scenes") : game, name, response);
        } else if (new File(game, path).isFile()) {
            file(game, path, response);
        } else {
            file(runtime, path, response);  // credits.html и т. п.
        }
    }

    /** Список сцен (как mygamegenerator.js ChoiceScript): startup, затем остальные .txt по имени */
    private static String myGame(File game) {
        List<String> scenes = new ArrayList<>();
        scenes.add("startup");
        for (File dir : new File[]{game, new File(game, "scenes")}) {
            String[] names = dir.list((d, n) -> n.endsWith(".txt"));
            if (names != null) {
                Arrays.sort(names);
                for (String n : names) {
                    String scene = n.substring(0, n.length() - 4);
                    if (!scenes.contains(scene) && !scene.equals("choicescript_stats")) {
                        scenes.add(scene);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder("nav = new SceneNavigator([");
        for (int i = 0; i < scenes.size(); i++) {
            sb.append(i > 0 ? ", " : "").append('"').append(scenes.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append("]);\nstats = {};\n").toString();
    }

    private void jsiq(String path, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (path.isEmpty()) {
            List<String> books = jsiqBooks();
            if (books.size() == 1) {
                response.sendRedirect("play.html?book=" + java.net.URLEncoder.encode(books.get(0), StandardCharsets.UTF_8));
            } else {
                response.sendRedirect("../");
            }
        } else if (path.equals("play.html")) {
            String book = request.getParameter("book");
            if (book == null || !jsiqBooks().contains(book)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Нет книги jsIQ2: " + book);
                return;
            }
            html(response, jsiqPage(book));
        } else if (path.startsWith("runtime/")) {
            file(m_preview.getJsiqRuntime(), path.substring("runtime/".length()), response);
        } else {
            file(m_preview.getJsiqDir(), path, response);
        }
    }

    /** Страница плеера jsIQ: контейнеры текста, действий и листа персонажа, старт с первой статьи */
    private static String jsiqPage(String book) {
        String bookJs = jsString(book);
        return "<!doctype html>\n<html><head><meta charset='utf-8'><title>" + escape(book) + " — jsIQ2</title>\n"
                + "<script src='runtime/jquery.js'></script>\n"
                + "<script src='runtime/jsiqapi212.js' charset='windows-1251'></script>\n"
                + "<style>body{font-family:Georgia,serif;max-width:46em;margin:2em auto;padding:0 1em;line-height:1.5;"
                + "background:#faf8f2;color:#222}#title{font-size:1.4em;font-weight:bold}"
                + "#actions .actionContainer.Visible{margin:.4em 0}"
                + "#actions .actionContainer.Visible a{display:inline-block;padding:.3em .8em;border:1px solid #886;"
                + "border-radius:.4em;text-decoration:none;color:#223}#actions .actionContainer.nonVisible{display:none}"
                + "#errors{color:#a00;white-space:pre-wrap}.back{font-size:.85em}"
                // лист персонажа jsIQ: инвентарь экспорт NLB и так показывает под текстом статьи
                + "#charsheet{display:none}</style>\n"
                + "</head><body>\n<p class='back'><a href='../'>← к списку</a></p>\n"
                + "<div id='title'></div><div id='main_text'></div><div id='actions'></div><div id='charsheet'></div>"
                + "<div id='errors'></div>\n<script>\n"
                + "jsIQ.addEventHandler('jsiq_init_start', function (params, vars, process) {\n"
                + "  this.containers.text = $('#main_text'); this.containers.actions = $('#actions');\n"
                + "  this.containers.title = $('#title'); this.containers.charsheet = $('#charsheet');\n"
                + "  process.next();\n});\n"
                + "jsIQ.addEventHandler('jsiq_init_done', function (params, vars, process) {\n"
                + "  this.showArticle('0'); process.next();\n});\n"
                + "window.onerror = function (m) { $('#errors').append(document.createTextNode(m + '\\n')); };\n"
                + "$(document).ready(function () { jsIQ.init('" + bookJs + "'); });\n"
                + "</script>\n</body></html>\n";
    }

    /** Одна игра — сразу её страница, иначе — к списку */
    private static void single(List<String> books, String playPrefix, HttpServletResponse response) throws IOException {
        if (books.size() == 1) {
            response.sendRedirect(playPrefix + java.net.URLEncoder.encode(books.get(0), StandardCharsets.UTF_8));
        } else {
            response.sendRedirect("../");
        }
    }

    private void qsp(String path, HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<String> books = books(m_preview.getQspDir(), ".txt", ".qsp", ".gam");
        if (path.isEmpty()) {
            single(books, "play.html?book=", response);
        } else if (path.equals("play.html")) {
            String book = request.getParameter("book");
            if (book == null || !books.contains(book)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Нет игры QSP: " + book);
                return;
            }
            html(response, QspPage.html(book));
        } else if (path.startsWith("runtime/")) {
            file(m_preview.getQspRuntime(), path.substring("runtime/".length()), response);
        } else {
            file(m_preview.getQspDir(), path, response);
        }
    }

    private void urq(String path, HttpServletResponse response) throws IOException {
        List<String> ids = urqIds();
        if (path.isEmpty()) {
            single(ids, "index-dev.html?encoding=CP1251&id=", response);
        } else if (path.startsWith("quests/")) {
            // UrqW: quests/<имя>/main.qst — игра, остальное (картинки, звук) — рядом с ней
            String rest = path.substring("quests/".length());
            int slash = rest.indexOf('/');
            String id = slash < 0 ? rest : rest.substring(0, slash);
            String inGame = slash < 0 ? "" : rest.substring(slash + 1);
            if (!ids.contains(id)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } else if (inGame.equals("main.qst")) {
                file(m_preview.getUrqDir(), id + ".qst", response);
            } else {
                file(m_preview.getUrqDir(), inGame, response);
            }
        } else {
            file(m_preview.getUrqwRuntime(), path.isEmpty() ? "index-dev.html" : path, response);
        }
    }

    private void axma(String path, HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<String> books = books(m_preview.getAxmaDir(), ".sm");
        if (path.isEmpty()) {
            single(books, "play.html?book=", response);
        } else if (path.equals("play.html")) {
            String book = request.getParameter("book");
            if (book == null || !books.contains(book)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Нет игры AXMA: " + book);
                return;
            }
            html(response, axmaPage(book));
        } else if (path.startsWith("editor/")) {
            // редактор — из runtime; игра, которую он показывает, берёт картинки и звук относительно
            // страницы редактора — они из каталога экспорта
            String rest = path.substring("editor/".length());
            if (Arrays.asList(ExportPreview.AXMA_FILES).contains(rest)) {
                file(m_preview.getAxmaRuntime(), rest, response);
            } else {
                file(m_preview.getAxmaDir(), rest, response);
            }
        } else {
            file(m_preview.getAxmaDir(), path, response);
        }
    }

    /** Без редактора: .sm можно открыть в онлайн-редакторе (Загрузить -> Запуск) */
    private static String axmaFallback() {
        return "<p>Игру можно проверить и в онлайн-редакторе: откройте <a href='https://hyperbook.ru/editor/' target='_blank'>"
                + "hyperbook.ru/editor</a>, загрузите в него файл .sm экспорта (кнопка «Загрузить») и нажмите «Запуск».</p>";
    }

    /**
     * Страница AXMA: редактор AXMA Story Maker в рамке во всё окно; когда он загрузится, страница
     * передаёт ему текст .sm (LoadStory) и запускает игру (Compile — как кнопка «Запуск»). Закрытие
     * игры (крестик, Esc) возвращает к списку
     */
    private static String axmaPage(String book) {
        String bookJs = jsString(book);
        return "<!doctype html>\n<html><head><meta charset='utf-8'><title>" + escape(book) + " — AXMA</title>\n"
                + "<style>html,body{margin:0;height:100%;overflow:hidden;font-family:sans-serif}"
                + "#editor{border:0;width:100%;height:100%}"
                + "#status{position:fixed;left:0;right:0;top:0;padding:.5em 1em;background:#fffbe6;border-bottom:1px solid #cc9}"
                + "#status.hidden{display:none}.error{color:#a00}</style>\n"
                + "</head><body>\n<div id='status'>Загрузка редактора AXMA Story Maker…</div>\n"
                + "<iframe id='editor' src='editor/index.html'></iframe>\n<script>\n"
                + "var book = '" + bookJs + "';\n"
                + "var bar = document.getElementById('status');\n"
                + "function fail(e) {\n"
                + "  bar.className = 'error';\n"
                + "  bar.innerHTML = 'Не удалось запустить игру в редакторе: ' + String(e).replace(/</g, '&lt;')\n"
                + "    + '. <a href=\"' + encodeURIComponent(book) + '\" download>Скачать .sm</a> и открыть в '\n"
                + "    + '<a href=\"https://hyperbook.ru/editor/\" target=\"_blank\">онлайн-редакторе</a> (Загрузить → Запуск). '\n"
                + "    + '<a href=\"../\">← к списку</a>';\n"
                + "}\n"
                + "document.getElementById('editor').addEventListener('load', function () {\n"
                + "  var w = this.contentWindow;\n"
                + "  fetch(encodeURIComponent(book)).then(function (r) {\n"
                + "    if (!r.ok) throw new Error('HTTP ' + r.status);\n"
                + "    return r.text();\n"
                + "  }).then(function (story) {\n"
                + "    if (typeof w.LoadStory !== 'function' || typeof w.Compile !== 'function') throw new Error('в редакторе нет LoadStory/Compile');\n"
                + "    w.ClosePreview = function () { location.href = '../'; };\n"
                + "    w.LoadStory(0, false, story);\n"
                + "    setTimeout(function () {\n"
                + "      try { w.Compile(true, -1); bar.className = 'hidden'; } catch (e) { fail(e); }\n"
                + "    }, 300);\n"
                + "  }).catch(fail);\n"
                + "});\n"
                + "</script>\n</body></html>\n";
    }

    private static String jsString(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\x3c");
    }

    private void file(File dir, String relative, HttpServletResponse response) throws IOException {
        if (relative.equals("license-choicescript") ) {
            dir = m_preview.getChoiceScriptRuntime();
            relative = "LICENSE.txt";
        }
        File base = dir.getCanonicalFile();
        File file = new File(base, relative).getCanonicalFile();
        if (!file.toPath().startsWith(base.toPath()) || !file.isFile()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType(contentType(file.getName()));
        response.setContentLengthLong(file.length());
        Files.copy(file.toPath(), response.getOutputStream());
    }

    private static String contentType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.equals("jsiqapi212.js")) return "text/javascript; charset=windows-1251";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
        if (n.endsWith(".xml")) return "application/xml; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".wasm")) return "application/wasm";
        if (n.endsWith(".sm")) return "text/plain; charset=utf-8";
        if (n.endsWith(".qst")) return "text/plain; charset=windows-1251";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".woff")) return "font/woff";
        if (n.endsWith(".ogg")) return "audio/ogg";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    private static void text(HttpServletResponse response, String type, String text) throws IOException {
        response.setContentType(type + "; charset=utf-8");
        response.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void html(HttpServletResponse response, String html) throws IOException {
        text(response, "text/html", html);
    }

    private static String page(String title, String body) {
        return "<!doctype html>\n<html><head><meta charset='utf-8'><title>" + escape(title) + "</title>"
                + "<style>body{font-family:sans-serif;max-width:46em;margin:2em auto;padding:0 1em;line-height:1.5}"
                + "code{background:#eee;padding:0 .2em}.error{color:#a00}.note{color:#666;font-size:.9em}</style></head><body>"
                + "<h1>" + escape(title) + "</h1>" + body + "</body></html>\n";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }
}
