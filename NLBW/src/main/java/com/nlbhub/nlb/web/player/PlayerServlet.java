/**
 * @(#)PlayerServlet.java
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
package com.nlbhub.nlb.web.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.api.config.Export;
import com.nlbhub.nlb.player.Achievements;
import com.nlbhub.nlb.player.PlayerEngine;
import com.nlbhub.nlb.player.PlayerException;
import com.nlbhub.nlb.player.PlayerSession;
import com.nlbhub.nlb.player.ScreenModel;
import com.nlbhub.nlb.player.TextPortions;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Веб-плеер: клиент (HTML/CSS/JS), REST (JSON) поверх ядра плеера и медиафайлы книг.
 * <pre>
 *   GET  /play/                          клиент
 *   GET  /play/static/&lt;файл&gt;             файлы клиента
 *   GET  /play/api/books                 книги библиотеки
 *   POST /play/api/sessions              новая игра {book, languages?: [по предпочтению],
 *                                        mode?: vn|standard (нет — по теме книги), achievements?}
 *   GET  /play/api/sessions/&lt;id&gt;         состояние игры
 *   POST /play/api/sessions/&lt;id&gt;/&lt;команда&gt;
 *        choose {index} · object {id} · item {id} · clear · look · advance {ms} · restart ·
 *        tryAgain · finishExternal · save -&gt; {save, time, label} · load {save} · language {language}
 *   GET  /play/media/&lt;книга&gt;/&lt;имя файла&gt; картинки и звуки книги и её модулей
 *   GET  /play/api/books/&lt;книга&gt;/theme   оформление экрана (тема INSTEAD, см. ScreenTheme) и
 *                                        режим книги по умолчанию
 *   GET  /play/theme/&lt;книга&gt;/&lt;base|game&gt;/&lt;путь&gt; файлы темы (шрифты, кнопка меню)
 * </pre>
 * Каждая команда отвечает состоянием игры: снимок экрана (ScreenModel.toJson), порции текста VN,
 * выбранный предмет, озвучка страницы, текущая музыка, события хода, прогресс достижений.
 * Сохранения и достижения хранит клиент (у игрока нет учётной записи): сервер отдаёт их в JSON.
 * Сессия, к которой долго не обращались, забывается.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class PlayerServlet extends HttpServlet {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long SESSION_IDLE_MS = 6L * 60 * 60 * 1000;
    private static final int MAX_SESSIONS = 10000;
    private static final int MAX_BODY = 16 * 1024 * 1024;
    private static final String CLIENT = "client/";
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf")
    );

    private final BookLibrary m_library;
    private final Export m_export;
    private final Map<String, WebSession> m_sessions = new ConcurrentHashMap<>();
    private final SecureRandom m_random = new SecureRandom();

    /**
     * export — настройки экспорта (тексты по умолчанию, needs-action-count); null — встроенные
     */
    public PlayerServlet(BookLibrary library, Export export) {
        m_library = library;
        m_export = export;
    }

    // ------------------------------------------------------------------ разбор запросов

    private static final class HttpError extends Exception {
        private final int m_status;

        HttpError(int status, String message) {
            super(message);
            m_status = status;
        }
    }

    private static String[] parts(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null || path.equals("/")) {
            return new String[0];
        }
        return path.substring(1).split("/", -1);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getPathInfo() == null) {
            // /play -> /play/: адреса клиента относительные
            String query = request.getQueryString();
            response.sendRedirect(request.getRequestURI() + "/" + (query == null ? "" : "?" + query));
            return;
        }
        String[] p = parts(request);
        try {
            if (p.length == 0) {
                serveClient("index.html", response);
            } else if (p[0].equals("static") && p.length == 2) {
                serveClient(p[1], response);
            } else if (p[0].equals("media") && p.length >= 3) {
                serveMedia(p[1], String.join("/", java.util.Arrays.copyOfRange(p, 2, p.length)), response);
            } else if (p[0].equals("theme") && p.length >= 4) {
                serveTheme(p[1], p[2], String.join("/", java.util.Arrays.copyOfRange(p, 3, p.length)), response);
            } else if (p[0].equals("api") && p.length == 2 && p[1].equals("books")) {
                sendJson(response, books());
            } else if (p[0].equals("api") && p.length == 4 && p[1].equals("books") && p[3].equals("theme")) {
                if (!m_library.exists(p[2])) {
                    throw new HttpError(404, "нет такой книги: " + p[2]);
                }
                ObjectNode theme = JSON.createObjectNode();
                ScreenTheme.Root[] roots = ScreenTheme.roots(m_library.bookDir(p[2]));
                theme.set("vn", ScreenTheme.load(roots, true).toJson());
                theme.set("standard", ScreenTheme.load(roots, false).toJson());
                // режим по умолчанию — для адреса без ?mode=
                NonLinearBook book = m_library.book(p[2], null);
                theme.put("mode", book == null ? "vn" : BookLibrary.defaultMode(book));
                sendJson(response, theme);
            } else if (p[0].equals("api") && p.length == 3 && p[1].equals("sessions")) {
                WebSession session = session(p[2]);
                synchronized (session) {
                    sendJson(response, state(session));
                }
            } else {
                throw new HttpError(404, "нет такого адреса");
            }
        } catch (HttpError e) {
            sendError(response, e.m_status, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] p = parts(request);
        try {
            JsonNode body = body(request);
            if (p.length == 2 && p[0].equals("api") && p[1].equals("sessions")) {
                sendJson(response, createSession(body));
            } else if (p.length == 4 && p[0].equals("api") && p[1].equals("sessions")) {
                WebSession session = session(p[2]);
                synchronized (session) {
                    sendJson(response, command(session, p[3], body));
                }
            } else {
                throw new HttpError(404, "нет такого адреса");
            }
        } catch (HttpError e) {
            sendError(response, e.m_status, e.getMessage());
        } catch (PlayerException e) {
            sendError(response, 400, e.getMessage());
        }
    }

    private static JsonNode body(HttpServletRequest request) throws IOException, HttpError {
        byte[] bytes = request.getInputStream().readNBytes(MAX_BODY + 1);
        if (bytes.length > MAX_BODY) {
            throw new HttpError(413, "слишком большой запрос");
        }
        if (bytes.length == 0) {
            return JSON.createObjectNode();
        }
        try {
            JsonNode node = JSON.readTree(bytes);
            return node == null || !node.isObject() ? JSON.createObjectNode() : node;
        } catch (IOException e) {
            throw new HttpError(400, "запрос — не JSON");
        }
    }

    private WebSession session(String id) throws HttpError {
        WebSession session = m_sessions.get(id);
        if (session == null) {
            throw new HttpError(404, "игра не найдена (сервер перезапущен или игра давно не продолжалась)");
        }
        session.touch();
        return session;
    }

    // ------------------------------------------------------------------ команды

    private ObjectNode books() {
        ObjectNode result = JSON.createObjectNode();
        ArrayNode books = result.putArray("books");
        for (BookLibrary.BookInfo info : m_library.books()) {
            ObjectNode book = books.addObject();
            book.put("id", info.getId());
            book.put("title", info.getTitle());
            book.put("author", info.getAuthor());
            book.put("language", info.getLanguage());
            book.put("mode", info.getMode());
            ArrayNode languages = book.putArray("languages");
            info.getLanguages().forEach(languages::add);
        }
        return result;
    }

    private String newSessionId() {
        byte[] bytes = new byte[18];
        m_random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void expireSessions() {
        long now = System.currentTimeMillis();
        m_sessions.values().removeIf(s -> now - s.lastAccess() > SESSION_IDLE_MS);
    }

    private ObjectNode createSession(JsonNode body) throws HttpError {
        String bookId = body.path("book").asText("");
        expireSessions();
        if (m_sessions.size() >= MAX_SESSIONS) {
            throw new HttpError(503, "слишком много игр на сервере");
        }
        if (!m_library.exists(bookId)) {
            throw new HttpError(404, "нет такой книги: " + bookId);
        }
        String language = chooseLanguage(bookId, preferences(body));
        NonLinearBook book = m_library.book(bookId, language);
        if (book == null) {
            throw new HttpError(500, "книга не загрузилась: " + bookId);
        }
        // режим не задан — по теме книги, как в списке книг (тема STANDARD — standard, иначе vn)
        String modeName = body.path("mode").asText("");
        if (modeName.isEmpty()) {
            modeName = BookLibrary.defaultMode(book);
        }
        PlayerEngine.ExportMode mode = "standard".equalsIgnoreCase(modeName)
                ? PlayerEngine.ExportMode.STANDARD : PlayerEngine.ExportMode.VN;
        Achievements achievements = new Achievements();
        if (body.path("achievements").isObject()) {
            achievements.restore(body.get("achievements"));
        }
        WebSession session = new WebSession(newSessionId(), bookId, book, book.getLanguage(), mode, m_export, achievements);
        m_sessions.put(session.id(), session);
        synchronized (session) {
            return state(session);
        }
    }

    /**
     * Языки по предпочтению из запроса: languages (выбор игрока, язык из адреса, языки браузера)
     * или один language
     */
    private static List<String> preferences(JsonNode body) {
        List<String> result = new java.util.ArrayList<>();
        for (JsonNode language : body.path("languages")) {
            result.add(language.asText());
        }
        if (body.hasNonNull("language")) {
            result.add(body.get("language").asText());
        }
        return result;
    }

    /**
     * «ru_RU», «en-US», «RU» -> «ru»
     */
    static String normalizeLanguage(String code) {
        String s = code == null ? "" : code.trim();
        int cut = s.length();
        for (char c : new char[]{'-', '_', '.', '@'}) {
            int i = s.indexOf(c);
            if (i >= 0) {
                cut = Math.min(cut, i);
            }
        }
        return s.substring(0, cut).toLowerCase(Locale.ROOT);
    }

    /**
     * Язык игры, как chooseLanguage NLBNavigator: первый из предпочтений, что есть в книге, иначе
     * английский, иначе язык книги (null)
     */
    private String chooseLanguage(String bookId, List<String> preferred) {
        List<String> languages = m_library.languages(bookId);
        for (String language : preferred) {
            String code = normalizeLanguage(language);
            if (languages.contains(code)) {
                return code;
            }
        }
        return languages.contains("en") ? "en" : null;
    }

    private ObjectNode command(WebSession web, String command, JsonNode body) throws HttpError {
        PlayerSession s = web.session();
        switch (command) {
            case "choose":
                s.choose(body.path("index").asInt(-1));
                break;
            case "object":
                s.clickObject(body.path("id").asText(""));
                break;
            case "item":
                s.clickItem(body.path("id").asText(""));
                break;
            case "clear":
                s.clearSelection();
                break;
            case "look":
                s.look();
                break;
            case "advance":
                s.advance(Math.max(0, Math.min(60000, body.path("ms").asInt(0))));
                break;
            case "restart":
                s.restart();
                break;
            case "tryAgain":
                s.tryAgain();
                break;
            case "finishExternal":
                s.finishExternalGame();
                break;
            case "save": {
                String save = s.save();
                PlayerEngine.SaveInfo info = s.engine().readSaveInfo(save);
                ObjectNode result = state(web);
                ObjectNode saved = result.putObject("saved");
                saved.put("save", save);
                saved.put("time", info.getTime());
                saved.put("label", info.getLabel());
                return result;
            }
            case "load":
                s.load(body.path("save").asText(""));
                break;
            case "language": {
                String language = chooseLanguage(web.bookId(), preferences(body));
                NonLinearBook book = m_library.book(web.bookId(), language);
                if (book == null) {
                    throw new HttpError(500, "книга не загрузилась: " + web.bookId());
                }
                if (!book.getLanguage().equals(web.language())) {
                    web.switchLanguage(book, book.getLanguage());
                }
                break;
            }
            default:
                throw new HttpError(404, "нет такой команды: " + command);
        }
        return state(web);
    }

    // ------------------------------------------------------------------ ответ

    private ObjectNode state(WebSession web) {
        PlayerSession s = web.session();
        NonLinearBook book = s.engine().rootBook();
        ObjectNode result = JSON.createObjectNode();
        result.put("session", web.id());
        ObjectNode bookNode = result.putObject("book");
        bookNode.put("id", web.bookId());
        bookNode.put("title", book.getTitle());
        bookNode.put("author", book.getAuthor());
        result.put("language", web.language());
        ArrayNode languages = result.putArray("languages");
        m_library.languages(web.bookId()).forEach(languages::add);
        result.put("mode", web.mode() == PlayerEngine.ExportMode.STANDARD ? "standard" : "vn");
        result.put("revision", s.revision());
        ScreenModel screen = s.screen();
        result.set("screen", screen.toJson());
        ArrayNode portions = result.putArray("portions");
        for (List<ScreenModel.Paragraph> portion : screen.portions()) {
            ArrayNode paragraphs = portions.addArray();
            portion.forEach(p -> paragraphs.add(p.toJson()));
        }
        // ответ на действие на той же странице показывается с этой порции
        result.put("pagePortions", TextPortions.of(screen.getPageText()).size());
        result.put("selected", s.selectedItem());
        ArrayNode voices = result.putArray("voices");
        s.voices().forEach(voices::add);
        result.put("music", s.currentMusic());
        ArrayNode events = result.putArray("events");
        for (PlayerEngine.Event event : web.takeEvents()) {
            ObjectNode e = events.addObject();
            e.put("kind", event.getKind().name());
            e.put("text", event.getText());
            e.put("frames", event.getFrames());
            e.put("id", event.getId());
            e.put("subject", event.getSubject());
        }
        result.set("achievements", web.achievements().toJson());
        return result;
    }

    private static void sendJson(HttpServletResponse response, ObjectNode json) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(json);
        response.setStatus(200);
        response.setContentType("application/json; charset=utf-8");
        response.setHeader("Cache-Control", "no-store");
        response.setContentLength(bytes.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(bytes);
        }
    }

    private static void sendError(HttpServletResponse response, int status, String message) throws IOException {
        ObjectNode json = JSON.createObjectNode();
        json.put("error", message);
        byte[] bytes = JSON.writeValueAsBytes(json);
        response.setStatus(status);
        response.setContentType("application/json; charset=utf-8");
        response.setHeader("Cache-Control", "no-store");
        response.setContentLength(bytes.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(bytes);
        }
    }

    private static String contentType(String name) {
        int dot = name.lastIndexOf('.');
        String type = dot < 0 ? null : CONTENT_TYPES.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
        return type != null ? type : "application/octet-stream";
    }

    private void serveClient(String name, HttpServletResponse response) throws IOException, HttpError {
        if (!name.matches("[A-Za-z0-9_.-]+") || name.startsWith(".")) {
            throw new HttpError(404, "нет такого файла");
        }
        try (InputStream in = PlayerServlet.class.getResourceAsStream(CLIENT + name)) {
            if (in == null) {
                throw new HttpError(404, "нет такого файла");
            }
            byte[] bytes = in.readAllBytes();
            response.setStatus(200);
            response.setContentType(contentType(name));
            response.setHeader("Cache-Control", "no-cache");
            response.setContentLength(bytes.length);
            try (OutputStream out = response.getOutputStream()) {
                out.write(bytes);
            }
        }
    }

    private void serveMedia(String bookId, String name, HttpServletResponse response) throws IOException, HttpError {
        BookMedia media = m_library.media(bookId);
        BookMedia.Media file = media == null ? null : media.resolve(name);
        if (file == null) {
            throw new HttpError(404, "нет такого файла: " + name);
        }
        try (InputStream in = new BufferedInputStream(file.open())) {
            response.setStatus(200);
            response.setContentType(mediaType(file.getName(), in));
            response.setHeader("Cache-Control", "max-age=3600");
            response.setContentLengthLong(file.getLength());
            try (OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
            }
        }
    }

    private void serveTheme(String bookId, String rootName, String path, HttpServletResponse response)
            throws IOException, HttpError {
        if (!m_library.exists(bookId)) {
            throw new HttpError(404, "нет такой книги: " + bookId);
        }
        ScreenTheme.Root[] roots = ScreenTheme.roots(m_library.bookDir(bookId));
        ScreenTheme.Root root = rootName.equals(ScreenTheme.BASE) ? roots[0]
                : rootName.equals(ScreenTheme.GAME) ? roots[1] : null;
        try (InputStream in = root == null ? null : root.open(path)) {
            if (in == null) {
                throw new HttpError(404, "нет такого файла темы: " + path);
            }
            byte[] bytes = in.readAllBytes();
            response.setStatus(200);
            response.setContentType(contentType(path));
            response.setHeader("Cache-Control", "max-age=3600");
            response.setContentLength(bytes.length);
            try (OutputStream out = response.getOutputStream()) {
                out.write(bytes);
            }
        }
    }

    /**
     * Тип картинки — по содержимому: при упаковке книги PNG может стать JPG с прежним именем
     */
    private static String mediaType(String name, InputStream in) throws IOException {
        in.mark(8);
        byte[] head = in.readNBytes(8);
        in.reset();
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (head.length >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "image/png";
        }
        if (head.length >= 4 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
            return "image/gif";
        }
        return contentType(name);
    }

    /**
     * Для тестов: число активных игр
     */
    int sessionCount() {
        return m_sessions.size();
    }
}
