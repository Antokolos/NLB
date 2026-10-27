package com.nlbhub.nlb.web.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.LinkImpl;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.PageImpl;
import com.nlbhub.nlb.util.MultiLangString;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Веб-плеер через HTTP: книга в памяти (как из редактора) на двух языках; сервер на свободном порту
 */
public class PlayerServletTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Server s_server;
    private static String s_base;

    private static MultiLangString text(String ru, String en) {
        MultiLangString result = MultiLangString.createEmptyText();
        result.put("ru", ru);
        result.put("en", en);
        return result;
    }

    /**
     * Две комнаты: «Комната 1» -> «Комната 2» и обратно
     */
    private static NonLinearBookImpl book() {
        return book(new NonLinearBookImpl());
    }

    private static NonLinearBookImpl book(NonLinearBookImpl nlb) {
        PageImpl first = new PageImpl(nlb);
        first.setTexts(text("Комната 1", "Room 1"));
        nlb.addPage(first);
        PageImpl second = new PageImpl(nlb);
        second.setTexts(text("Комната 2", "Room 2"));
        nlb.addPage(second);
        nlb.setStartPoint(first.getId());
        LinkImpl forward = new LinkImpl(first, second.getId());
        forward.setTexts(text("Вперёд", "Forward"));
        first.addLink(forward);
        LinkImpl back = new LinkImpl(second, first.getId());
        back.setTexts(text("Назад", "Back"));
        second.addLink(back);
        return nlb;
    }

    @BeforeClass
    public static void start() throws Exception {
        BookLibrary library = new BookLibrary(null);
        library.putEditorBook("rooms", book());
        // книга с темой STANDARD (тему книги меняет только команда редактора — здесь подкласс)
        library.putEditorBook("hall", book(new NonLinearBookImpl() {
            @Override
            public Theme getTheme() {
                return Theme.STANDARD;
            }
        }));
        s_server = PlayerServer.create(library, null, "localhost", 0);
        s_server.start();
        s_base = "http://localhost:" + ((ServerConnector) s_server.getConnectors()[0]).getLocalPort() + "/play/";
    }

    @AfterClass
    public static void stop() throws Exception {
        s_server.stop();
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static Response request(String path, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(s_base + path).openConnection(Proxy.NO_PROXY);
        if (body != null) {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return new Response(status, text);
    }

    private static JsonNode post(String path, String body) throws Exception {
        Response response = request(path, body);
        assertEquals(response.body, 200, response.status);
        return JSON.readTree(response.body);
    }

    private static String text(JsonNode state) {
        return state.path("screen").path("pageText").path(0).path("runs").path(0).path("text").asText();
    }

    private static JsonNode newGame(String language) throws Exception {
        return post("api/sessions", "{\"book\":\"rooms\",\"mode\":\"standard\",\"languages\":[\"" + language + "\"]}");
    }

    @Test
    public void listsBooksAndServesClient() throws Exception {
        JsonNode books = JSON.readTree(request("api/books", null).body).path("books");
        assertEquals(2, books.size());
        assertEquals("hall", books.path(0).path("id").asText());
        assertEquals("standard", books.path(0).path("mode").asText());
        assertEquals("rooms", books.path(1).path("id").asText());
        assertEquals("vn", books.path(1).path("mode").asText());
        assertEquals("[\"en\",\"ru\"]", books.path(1).path("languages").toString());
        Response client = request("", null);
        assertEquals(200, client.status);
        assertTrue(client.body.contains("static/player.js"));
        assertEquals(200, request("static/player.js", null).status);
        assertEquals(404, request("static/missing.js", null).status);
    }

    @Test
    public void servesTheme() throws Exception {
        // у книги из редактора нет своей темы: базовая — стандартная тема INSTEAD, тема игры — тема
        // экспорта NLB (обе зашиты в NLBW); параметры темы игры важнее
        JsonNode theme = JSON.readTree(request("api/books/rooms/theme", null).body);
        JsonNode vn = theme.path("vn");
        assertEquals("game/fonts/STEINEMU.ttf", vn.path("text").path("font").asText());
        assertEquals(32.0, vn.path("text").path("fontSize").asDouble(), 0);
        assertEquals("#00ff00", vn.path("text").path("link").asText());
        assertEquals(1050.0, vn.path("inventory").path("y").asDouble(), 0);
        assertEquals("game/gfx/menubtn_ru.png", vn.path("menuButtonRu").asText());
        assertEquals("game/fonts/STEINEMU.ttf", vn.path("menuFont").asText());
        // подписи графических объектов — шрифтом hudFont vn.lua в обеих темах
        assertEquals("game/fonts/STEINEMU.ttf", vn.path("hudFont").asText());
        assertEquals("game/fonts/STEINEMU.ttf", theme.path("standard").path("hudFont").asText());
        // окно текста VN — из theme_vn.lua темы экспорта
        assertTrue(vn.path("vnTextGeom").asBoolean());
        assertEquals(838.0, vn.path("text").path("y").asDouble(), 0);
        JsonNode standard = theme.path("standard");
        assertEquals("#000000", standard.path("text").path("fg").asText());
        assertEquals("game/fonts/sans-i.ttf", standard.path("text").path("fontItalic").asText());
        assertEquals("game/gfx/aup.png", standard.path("text").path("upImage").asText());
        assertEquals("game/gfx/bg.jpg", standard.path("pageBackground").asText());
        assertEquals(640.0, standard.path("pictureArea").path("h").asDouble(), 0);
        // только в базовой теме: звук щелчка (snd.click) — путь от её корня
        assertEquals("base/click.wav", standard.path("clickSound").asText());
        assertEquals("base/click.wav", vn.path("clickSound").asText());
        assertEquals(200, request("theme/rooms/game/fonts/STEINEMU.ttf", null).status);
        assertEquals(200, request("theme/rooms/game/gfx/menubtn.png", null).status);
        assertEquals(200, request("theme/rooms/base/click.wav", null).status);
        assertEquals(200, request("theme/rooms/base/theme.ini", null).status);
        assertEquals(404, request("theme/rooms/other/theme.ini", null).status);
        assertEquals(404, request("theme/rooms/base/missing.png", null).status);
        // выход из темы: Jetty отвергает такой путь сам (400), сервлет — 404
        assertTrue(request("theme/rooms/base/%2E%2E/theme.ini", null).status >= 400);
        assertEquals(404, request("theme/nobook/base/theme.ini", null).status);
        assertEquals(404, request("api/books/nobook/theme", null).status);
    }

    @Test
    public void modeByBookTheme() throws Exception {
        // без режима в запросе — по теме книги: STANDARD -> standard, иначе (DEFAULT) -> vn
        assertEquals("vn", JSON.readTree(request("api/books/rooms/theme", null).body).path("mode").asText());
        assertEquals("standard", JSON.readTree(request("api/books/hall/theme", null).body).path("mode").asText());
        assertEquals("vn", post("api/sessions", "{\"book\":\"rooms\"}").path("mode").asText());
        JsonNode hall = post("api/sessions", "{\"book\":\"hall\"}");
        assertEquals("standard", hall.path("mode").asText());
        assertEquals("Standard", hall.path("screen").path("layout").asText());
        // заданный режим важнее темы
        assertEquals("vn", post("api/sessions", "{\"book\":\"hall\",\"mode\":\"vn\"}").path("mode").asText());
    }

    @Test
    public void twoPlayersAreIndependent() throws Exception {
        JsonNode first = newGame("ru");
        JsonNode second = newGame("ru");
        assertNotEquals(first.path("session").asText(), second.path("session").asText());
        JsonNode moved = post("api/sessions/" + first.path("session").asText() + "/choose", "{\"index\":0}");
        assertEquals("Комната 2", text(moved));
        JsonNode other = JSON.readTree(request("api/sessions/" + second.path("session").asText(), null).body);
        assertEquals("Комната 1", text(other));
    }

    @Test
    public void languageSwitchKeepsTheGame() throws Exception {
        JsonNode state = newGame("ru");
        String session = state.path("session").asText();
        assertEquals("ru", state.path("language").asText());
        post("api/sessions/" + session + "/choose", "{\"index\":0}");
        JsonNode english = post("api/sessions/" + session + "/language", "{\"language\":\"en\"}");
        assertEquals("en", english.path("language").asText());
        assertEquals("Room 2", text(english));
        assertEquals("Back", english.path("screen").path("choices").path(0).path("text").asText());
        // нет такого языка в книге — английский
        assertEquals("en", newGame("de").path("language").asText());
    }

    @Test
    public void saveAndLoad() throws Exception {
        String session = newGame("ru").path("session").asText();
        post("api/sessions/" + session + "/choose", "{\"index\":0}");
        JsonNode saved = post("api/sessions/" + session + "/save", "{}").path("saved");
        assertEquals("Комната 2", saved.path("label").asText());
        post("api/sessions/" + session + "/choose", "{\"index\":0}");
        // загрузить сохранение в другой игре того же игрока (новая сессия)
        String other = newGame("ru").path("session").asText();
        JsonNode loaded = post("api/sessions/" + other + "/load",
                JSON.createObjectNode().put("save", saved.path("save").asText()).toString());
        assertEquals("Комната 2", text(loaded));
    }

    @Test
    public void errors() throws Exception {
        assertEquals(404, request("api/sessions/missing", null).status);
        assertEquals(404, request("api/sessions", "{\"book\":\"../etc\"}").status);
        String session = newGame("ru").path("session").asText();
        assertEquals(404, request("api/sessions/" + session + "/unknown", "{}").status);
        Response bad = request("api/sessions/" + session + "/load", "{\"save\":\"{}\"}");
        assertEquals(400, bad.status);
        assertTrue(bad.body, JSON.readTree(bad.body).path("error").asText().contains("не сохранение"));
        assertEquals(404, request("media/rooms/x.png", null).status);
    }
}
