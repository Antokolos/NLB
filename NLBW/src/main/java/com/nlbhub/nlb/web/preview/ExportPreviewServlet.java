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
 * Проверка экспортов в браузере: /preview/ — ChoiceScript (/preview/choicescript/) и jsIQ2
 * (/preview/jsiq/) из каталога {@link ExportPreview}.
 * <ul>
 *   <li>ChoiceScript — страница игры из самого ChoiceScript (web/mygame/index.html); она грузит
 *   интерпретатор из каталога выше (/preview/ui.js…), сцены — из scenes/ (startup.txt экспорта),
 *   список сцен — mygame.js (startup и остальные .txt каталога игры);</li>
 *   <li>jsIQ2 — страница с плеером jsiqapi212.js (как на quest-book.ru) и книгой .xml экспорта.</li>
 * </ul>
 * Движки скачиваются при первом обращении ({@link ExportPreview#ensureRuntime()}).
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
        String runtimeError = null;
        if (path.equals("/") || path.equals("/choicescript/") || path.equals("/choicescript/index.html") || path.equals("/jsiq/")
                || path.equals("/jsiq/play.html")) {
            try {
                m_preview.ensureRuntime();
            } catch (IOException e) {
                runtimeError = e.getMessage();
            }
        }
        if (runtimeError != null) {
            html(response, page("Проверка экспорта", "<p class='error'>Не удалось скачать движки: " + escape(runtimeError)
                    + "</p><p>Нужен доступ в интернет к github.com, quest-book.ru и code.jquery.com (только при первом запуске; "
                    + "движки сохраняются в " + escape(m_preview.getRoot().getPath()) + "/runtime).</p>"));
            return;
        }
        if (path.equals("/")) {
            html(response, index());
        } else if (path.startsWith("/choicescript/")) {
            choiceScript(path.substring("/choicescript/".length()), response);
        } else if (path.startsWith("/jsiq/")) {
            jsiq(path.substring("/jsiq/".length()), request, response);
        } else {
            // интерпретатор ChoiceScript: страница игры подключает его из каталога выше (../ui.js)
            file(new File(m_preview.getChoiceScriptRuntime(), "web"), path.substring(1), response);
        }
    }

    private String index() {
        File[] scenes = m_preview.getChoiceScriptDir().listFiles((d, n) -> n.endsWith(".txt"));
        File scenesDir = new File(m_preview.getChoiceScriptDir(), "scenes");
        boolean hasChoiceScript = new File(m_preview.getChoiceScriptDir(), "startup.txt").isFile()
                || new File(scenesDir, "startup.txt").isFile();
        List<String> books = jsiqBooks();
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Каталог: <code>").append(escape(m_preview.getRoot().getPath())).append("</code></p>");
        sb.append("<h2>ChoiceScript</h2>");
        if (hasChoiceScript) {
            sb.append("<p><a href='choicescript/'>Играть</a> (сцен: ").append(scenes == null ? 0 : scenes.length).append(")</p>");
        } else {
            sb.append("<p>Положите файлы экспорта в ChoiceScript (startup.txt и каталог images) в <code>")
                    .append(escape(m_preview.getChoiceScriptDir().getPath())).append("</code></p>");
        }
        sb.append("<h2>jsIQ2</h2>");
        if (books.isEmpty()) {
            sb.append("<p>Положите файл экспорта в jsIQ2 (.xml) в <code>").append(escape(m_preview.getJsiqDir().getPath()))
                    .append("</code></p>");
        } else {
            sb.append("<ul>");
            for (String book : books) {
                sb.append("<li><a href='jsiq/play.html?book=").append(escape(book)).append("'>").append(escape(book)).append("</a></li>");
            }
            sb.append("</ul>");
        }
        sb.append("<p class='note'>Движки скачаны с их сайтов в <code>runtime</code>; ChoiceScript — только для "
                + "некоммерческого использования (<a href='license-choicescript'>лицензия</a>).</p>");
        return page("Проверка экспорта NLB", sb.toString());
    }

    private List<String> jsiqBooks() {
        String[] names = m_preview.getJsiqDir().list((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".xml"));
        List<String> books = new ArrayList<>(names == null ? List.of() : Arrays.asList(names));
        books.sort(null);
        return books;
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
        String bookJs = book.replace("\\", "\\\\").replace("'", "\\'");
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
