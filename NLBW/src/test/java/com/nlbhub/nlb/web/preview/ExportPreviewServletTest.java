package com.nlbhub.nlb.web.preview;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Проверка экспортов в браузере (/preview/): страницы ChoiceScript, jsIQ2, QSP, URQ (UrqW), AXMA и
 * INSTEAD (INSTEAD-em) и
 * файлы игр. Движки подложены заглушками — тест не ходит в сеть.
 */
public class ExportPreviewServletTest {
    @Rule
    public TemporaryFolder m_temp = new TemporaryFolder();

    private Server m_server;
    private String m_base;
    private ExportPreview m_preview;

    private static void write(File file, String text) throws Exception {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    @Before
    public void start() throws Exception {
        m_preview = new ExportPreview(m_temp.getRoot());
        m_preview.createDirs();
        File cs = m_preview.getChoiceScriptRuntime();
        write(new File(cs, "LICENSE.txt"), "CHOICESCRIPT LICENSE (CSL) v1.0");
        write(new File(cs, "web/scene.js"), "// scene");
        write(new File(cs, "web/ui.js"), "// ui");
        write(new File(cs, "web/mygame/index.html"), "<script src=\"../ui.js\"></script><script src=\"mygame.js\"></script>");
        write(new File(m_preview.getJsiqRuntime(), "jquery.js"), "// jquery");
        write(new File(m_preview.getJsiqRuntime(), "jsiqapi212.js"), "// jsiq");
        write(new File(m_preview.getChoiceScriptDir(), "startup.txt"), "Начало.\n*finish\n");
        write(new File(m_preview.getChoiceScriptDir(), "chapter2.txt"), "Глава 2.\n");
        write(new File(m_preview.getChoiceScriptDir(), "images/1.png"), "png");
        write(new File(m_preview.getJsiqDir(), "example.xml"), "<book></book>");
        write(new File(m_preview.getQspRuntime(), "wasm-engine/dist/qsp-engine.wasm"), "wasm");
        write(new File(m_preview.getQspRuntime(), "converters/dist/index.js"), "// converters");
        write(new File(m_preview.getUrqwRuntime(), "index-dev.html"), "<html>UrqW</html>");
        write(new File(m_preview.getUrqwRuntime(), "node_modules/jquery/dist/jquery.min.js"), "// jquery 2");
        write(new File(m_preview.getAxmaRuntime(), "index.html"), "<html>AXMA</html>");
        write(new File(m_preview.getAxmaRuntime(), "axmastorymaker.js"), "// axma");
        write(new File(m_preview.getAxmaRuntime(), "axmastorymaker.css"), "/* axma */");
        write(new File(m_preview.getQspDir(), "book.txt"), "QSP");
        write(new File(m_preview.getQspDir(), "images/q.png"), "qpng");
        write(new File(m_preview.getUrqDir(), "book.qst"), ":start");
        write(new File(m_preview.getUrqDir(), "images/u.png"), "upng");
        write(new File(m_preview.getAxmaDir(), "book.sm"), ":: Start");
        write(new File(m_preview.getAxmaDir(), "images/a.png"), "apng");
        write(new File(m_preview.getInsteadRuntime(), "instead.html"), "<html>INSTEAD-em</html>");
        write(new File(m_preview.getInsteadRuntime(), "instead-em.js"), "// instead-em");
        write(new File(m_preview.getInsteadDir(), "main.lua"), "-- main");
        write(new File(m_preview.getInsteadDir(), "modules/nlb.lua"), "-- nlb");
        write(new File(m_preview.getInsteadDir(), "images/i.png"), "ipng");

        m_server = new Server();
        ServerConnector http = new ServerConnector(m_server);
        http.setHost("localhost");
        http.setPort(0);
        m_server.addConnector(http);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        ExportPreviewServer.addTo(context, m_preview);
        m_server.setHandler(context);
        m_server.start();
        m_base = "http://localhost:" + http.getLocalPort() + "/preview/";
    }

    @After
    public void stop() throws Exception {
        m_server.stop();
    }

    private String[] get(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(m_base + path).openConnection(Proxy.NO_PROXY);
        c.setInstanceFollowRedirects(false);
        int status = c.getResponseCode();
        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return new String[]{String.valueOf(status), body, String.valueOf(c.getContentType()), String.valueOf(c.getHeaderField("Location"))};
    }

    @Test
    public void indexListsGames() throws Exception {
        String[] r = get("");
        assertEquals("200", r[0]);
        assertTrue(r[1], r[1].contains("href='choicescript/'"));
        assertTrue(r[1], r[1].contains("jsiq/play.html?book=example.xml"));
        assertTrue(r[1], r[1].contains("qsp/play.html?book=book.txt"));
        assertTrue(r[1], r[1].contains("urq/index-dev.html?encoding=CP1251&id=book"));
        assertTrue(r[1], r[1].contains("axma/play.html?book=book.sm"));
        assertTrue(r[1], r[1].contains("href='instead/'"));
    }

    @Test
    public void insteadGameZip() throws Exception {
        String[] redirect = get("instead/");
        assertEquals("302", redirect[0]);
        assertTrue("адрес игры — в строке запроса (post.js INSTEAD-em): " + redirect[3], redirect[3].endsWith("instead.html?game.zip"));
        assertEquals("<html>INSTEAD-em</html>", get("instead/instead.html")[1]);
        HttpURLConnection c = (HttpURLConnection) new URL(m_base + "instead/game.zip").openConnection(Proxy.NO_PROXY);
        assertEquals(200, c.getResponseCode());
        java.util.List<String> names = new java.util.ArrayList<>();
        String main = null;
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(c.getInputStream())) {
            java.util.zip.ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                names.add(e.getName());
                if (e.getName().endsWith("/main.lua")) {
                    main = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertEquals("одна папка игры — первой (её ищет INSTEAD при установке из zip): " + names, "nlb-preview/", names.get(0));
        for (String name : names) {
            assertTrue(names.toString(), name.startsWith("nlb-preview/"));
        }
        assertTrue(names.toString(), names.contains("nlb-preview/modules/nlb.lua") && names.contains("nlb-preview/images/i.png"));
        assertEquals("-- main", main);
    }

    @Test
    public void qspPage() throws Exception {
        String[] redirect = get("qsp/");
        assertEquals("302", redirect[0]);
        assertTrue(redirect[3], redirect[3].endsWith("play.html?book=book.txt"));
        String page = get("qsp/play.html?book=book.txt")[1];
        assertTrue(page, page.contains("const book = 'book.txt';"));
        assertTrue(page, page.contains("from './runtime/wasm-engine/dist/wasm-engine.modern.js'"));
        assertEquals("application/wasm", get("qsp/runtime/wasm-engine/dist/qsp-engine.wasm")[2]);
        assertEquals("файлы игры", "qpng", get("qsp/images/q.png")[1]);
        assertEquals("QSP", get("qsp/book.txt")[1]);
        assertEquals("неизвестная игра", "404", get("qsp/play.html?book=other.txt")[0]);
    }

    @Test
    public void urqUrqw() throws Exception {
        String[] redirect = get("urq/");
        assertEquals("302", redirect[0]);
        assertTrue(redirect[3], redirect[3].endsWith("index-dev.html?encoding=CP1251&id=book"));
        assertEquals("<html>UrqW</html>", get("urq/index-dev.html")[1]);
        assertEquals("// jquery 2", get("urq/node_modules/jquery/dist/jquery.min.js")[1]);
        String[] main = get("urq/quests/book/main.qst");
        assertEquals("игра — main.qst папки quests/<книга>", ":start", main[1]);
        assertTrue(main[2], main[2].contains("windows-1251"));
        assertEquals("upng", get("urq/quests/book/images/u.png")[1]);
        assertEquals("нет такой игры (UrqW ищет и другие места)", "404", get("urq/quests/other/main.qst")[0]);
    }

    @Test
    public void axmaEditor() throws Exception {
        String[] redirect = get("axma/");
        assertEquals("302", redirect[0]);
        assertTrue(redirect[3], redirect[3].endsWith("play.html?book=book.sm"));
        String page = get("axma/play.html?book=book.sm")[1];
        assertTrue(page, page.contains("src='editor/index.html'") && page.contains("w.Compile(true, -1)"));
        assertTrue("запасной путь — онлайн-редактор: " + page, page.contains("https://hyperbook.ru/editor/"));
        assertEquals("редактор — из runtime", "// axma", get("axma/editor/axmastorymaker.js")[1]);
        assertEquals("картинки игры — относительно страницы редактора", "apng", get("axma/editor/images/a.png")[1]);
        assertEquals(":: Start", get("axma/book.sm")[1]);
    }

    @Test
    public void choiceScriptPageScenesAndInterpreter() throws Exception {
        assertTrue("страница игры — из ChoiceScript", get("choicescript/")[1].contains("mygame.js"));
        assertEquals("сцена — из каталога игры", "Начало.\n*finish\n", get("choicescript/scenes/startup.txt")[1]);
        assertEquals("nav = new SceneNavigator([\"startup\", \"chapter2\"]);\nstats = {};\n", get("choicescript/mygame.js")[1]);
        assertEquals("интерпретатор — из каталога выше страницы", "// ui", get("ui.js")[1]);
        assertEquals("картинки экспорта", "png", get("choicescript/images/1.png")[1]);
        assertTrue(get("license-choicescript")[1].startsWith("CHOICESCRIPT LICENSE"));
    }

    @Test
    public void jsiqPlayer() throws Exception {
        String[] redirect = get("jsiq/");
        assertEquals("одна книга — сразу её страница", "302", redirect[0]);
        assertTrue(redirect[3], redirect[3].endsWith("play.html?book=example.xml"));
        String page = get("jsiq/play.html?book=example.xml")[1];
        assertTrue(page, page.contains("jsIQ.init('example.xml')"));
        assertTrue(page, page.contains("src='runtime/jsiqapi212.js' charset='windows-1251'"));
        assertTrue(get("jsiq/runtime/jsiqapi212.js")[2].contains("windows-1251"));
        assertEquals("<book></book>", get("jsiq/example.xml")[1]);
        assertEquals("неизвестная книга", "404", get("jsiq/play.html?book=other.xml")[0]);
    }

    @Test
    public void noFilesOutsideDirectories() throws Exception {
        // содержимое файла вне каталогов игр (Jetty повторяет адрес в странице 404, поэтому — по содержимому)
        write(new File(m_temp.getRoot().getParentFile(), "secret.txt"), "TOP-SECRET-CONTENT");
        for (String path : new String[]{"choicescript/scenes/../../../secret.txt", "jsiq/..%2F..%2Fsecret.txt",
                "choicescript/..%2F..%2Fsecret.txt", "jsiq/runtime/..%2F..%2F..%2Fsecret.txt",
                "qsp/..%2F..%2Fsecret.txt", "urq/quests/book/..%2F..%2F..%2Fsecret.txt", "axma/editor/..%2F..%2Fsecret.txt",
                "instead/..%2F..%2F..%2Fsecret.txt"}) {
            assertFalse(path, get(path)[1].contains("TOP-SECRET-CONTENT"));
        }
    }

    @Test
    public void clearKeepsRuntime() throws Exception {
        ExportPreview.clear(m_preview.getChoiceScriptDir());
        assertFalse(new File(m_preview.getChoiceScriptDir(), "startup.txt").exists());
        assertTrue(m_preview.getChoiceScriptDir().isDirectory());
        assertTrue(new File(m_preview.getChoiceScriptRuntime(), "web/scene.js").isFile());
    }
}
