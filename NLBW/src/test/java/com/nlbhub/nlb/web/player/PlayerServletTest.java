package com.nlbhub.nlb.web.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        NonLinearBookImpl nlb = new NonLinearBookImpl();
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
        JsonNode books = JSON.readTree(request("api/books", null).body);
        assertEquals("rooms", books.path("books").path(0).path("id").asText());
        assertEquals("[\"en\",\"ru\"]", books.path("books").path(0).path("languages").toString());
        Response client = request("", null);
        assertEquals(200, client.status);
        assertTrue(client.body.contains("static/player.js"));
        assertEquals(200, request("static/player.js", null).status);
        assertEquals(404, request("static/missing.js", null).status);
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
