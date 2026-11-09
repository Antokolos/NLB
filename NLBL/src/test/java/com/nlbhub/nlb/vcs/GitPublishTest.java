package com.nlbhub.nlb.vcs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlbhub.nlb.api.DummyProgressData;
import com.nlbhub.nlb.exception.NLBVCSAuthenticationException;
import com.nlbhub.nlb.exception.NLBVCSException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Публикация книги: создание репозитория через API GitHub (здесь — подменный сервер API, который
 * создаёт пустой репозиторий в папке) и первый push — адрес origin, ветка связана с ним для pull.
 */
public class GitPublishTest {
    private static final String TOKEN = "good-token";
    private Path m_dir;
    private ServerSocket m_server;
    private Thread m_thread;
    private final List<String> m_requests = new ArrayList<>();
    private final List<JsonNode> m_bodies = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        m_dir = Files.createTempDirectory("nlb-publish");
        // простейший HTTP/1.1-сервер: модуль jdk.httpserver тестам на пути модулей недоступен
        m_server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        m_thread = new Thread(() -> {
            while (!m_server.isClosed()) {
                try (Socket socket = m_server.accept()) {
                    serve(socket);
                } catch (IOException e) {
                    // сервер закрыт
                }
            }
        });
        m_thread.setDaemon(true);
        m_thread.start();
    }

    @After
    public void tearDown() throws IOException {
        m_server.close();
        TestDirs.delete(m_dir);
    }

    private String api() {
        return "http://127.0.0.1:" + m_server.getLocalPort();
    }

    /**
     * Подменный GitHub: GET /user, POST /user/repos и /orgs/{org}/repos (организация — только «club»)
     */
    private void serve(Socket socket) throws IOException {
        final InputStream input = new BufferedInputStream(socket.getInputStream());
        final String[] requestLine = readLine(input).split(" ");
        String authorization = null;
        int length = 0;
        for (String line = readLine(input); !line.isEmpty(); line = readLine(input)) {
            final int colon = line.indexOf(':');
            final String name = line.substring(0, colon).trim().toLowerCase();
            final String value = line.substring(colon + 1).trim();
            if (name.equals("authorization")) {
                authorization = value;
            } else if (name.equals("content-length")) {
                length = Integer.parseInt(value);
            }
        }
        final byte[] in = input.readNBytes(length);
        final String path = requestLine[1];
        m_requests.add(requestLine[0] + " " + path);
        int status;
        String body;
        if (!("Bearer " + TOKEN).equals(authorization)) {
            status = 401;
            body = "{\"message\":\"Bad credentials\"}";
        } else if (path.equals("/user")) {
            status = 200;
            body = "{\"login\":\"writer\"}";
        } else if (path.equals("/user/repos") || path.equals("/orgs/club/repos")) {
            final JsonNode json = new ObjectMapper().readTree(in);
            m_bodies.add(json);
            final String owner = path.equals("/user/repos") ? "writer" : "club";
            final File bare = m_dir.resolve("server").resolve(owner).resolve(json.get("name").asText() + ".git").toFile();
            if (bare.exists()) {
                status = 422;
                body = "{\"message\":\"Repository creation failed.\",\"errors\":[{\"message\":\"name already exists on this account\"}]}";
            } else {
                try {
                    Git.init().setBare(true).setDirectory(bare).call().close();
                } catch (Exception e) {
                    throw new IOException(e);
                }
                status = 201;
                body = "{\"full_name\":\"" + owner + "/" + json.get("name").asText() + "\",\"clone_url\":\""
                        + bare.toURI() + "\",\"html_url\":\"https://example.org/" + owner + "/" + json.get("name").asText() + "\"}";
            }
        } else {
            status = 404;
            body = "{\"message\":\"Not Found\"}";
        }
        final byte[] out = body.getBytes(StandardCharsets.UTF_8);
        final OutputStream stream = socket.getOutputStream();
        stream.write(("HTTP/1.1 " + status + " X\r\nContent-Type: application/json\r\nContent-Length: " + out.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        stream.write(out);
        stream.flush();
    }

    private static String readLine(InputStream input) throws IOException {
        final ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int c = input.read(); c >= 0 && c != '\n'; c = input.read()) {
            if (c != '\r') {
                line.write(c);
            }
        }
        return line.toString(StandardCharsets.UTF_8);
    }

    private File book() throws Exception {
        final File dir = m_dir.resolve("work/my book").toFile();
        try (Git git = Git.init().setDirectory(dir).setInitialBranch("master").call()) {
            Files.writeString(new File(dir, "startpoint").toPath(), "page", StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
        }
        return dir;
    }

    private static void commit(File dir) throws Exception {
        try (Git git = Git.open(dir)) {
            git.commit().setAuthor("t", "t@e").setCommitter("t", "t@e").setMessage("first").setSign(false)
                    .setGpgConfig(new GpgConfig(new Config())).call();
        }
    }

    @Test
    public void repositoryIsCreatedAndBookPushed() throws Exception {
        final File dir = book();
        final GitAdapter adapter = new GitAdapter(new Author("t", "t@e"));
        adapter.openRepo(dir.getPath());
        assertFalse("коммитов ещё нет", adapter.hasCommits());
        assertTrue("файл в индексе — не закоммичен", adapter.hasUncommittedChanges());
        commit(dir);
        assertTrue(adapter.hasCommits());
        assertFalse(adapter.hasUncommittedChanges());

        final GitHubPublisher publisher = new GitHubPublisher(api(), TOKEN);
        assertEquals("writer", publisher.login());
        final GitHubPublisher.Repository repo = publisher.createRepository(
                "", GitHubPublisher.repositoryName(dir.getName()), "My book", true
        );
        assertEquals("POST /user/repos", m_requests.get(m_requests.size() - 1));
        final JsonNode request = m_bodies.get(0);
        assertEquals("my-book", request.get("name").asText());
        assertEquals("My book", request.get("description").asText());
        assertTrue(request.get("private").asBoolean());
        assertFalse("пустой репозиторий, без README", request.get("auto_init").asBoolean());
        assertEquals("https://example.org/writer/my-book", repo.getHtmlUrl());

        adapter.setRemoteUrl(repo.getCloneUrl());
        assertEquals(repo.getCloneUrl(), adapter.getRemoteUrl());
        adapter.push(null, null, new DummyProgressData());
        try (Git server = Git.open(m_dir.resolve("server/writer/my-book.git").toFile())) {
            assertNotNull("книга отправлена", server.getRepository().resolve("refs/heads/master"));
        }
        try (Git git = Git.open(dir)) {
            final Repository local = git.getRepository();
            assertEquals("origin", local.getConfig().getString("branch", "master", "remote"));
            assertEquals("refs/heads/master", local.getConfig().getString("branch", "master", "merge"));
        }
        // ветка связана с сервером — pull работает
        adapter.pull(null, null, new DummyProgressData());
        adapter.closeAdapter();
    }

    @Test
    public void organizationAndErrors() throws Exception {
        final GitHubPublisher publisher = new GitHubPublisher(api(), TOKEN);
        publisher.createRepository("club", "book", null, false);
        assertEquals("POST /orgs/club/repos", m_requests.get(m_requests.size() - 1));
        assertFalse(m_bodies.get(0).has("description"));
        try {
            publisher.createRepository("club", "book", null, false);
            fail("имя занято");
        } catch (NLBVCSException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("already exists"));
        }
        try {
            publisher.createRepository("nobody", "book", null, false);
            fail("нет организации");
        } catch (NLBVCSException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("organization is not found"));
        }
        try {
            new GitHubPublisher(api(), "bad").login();
            fail("токен не годен");
        } catch (NLBVCSAuthenticationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Bad credentials"));
        }
    }

    @Test
    public void remoteCanBeRemoved() throws Exception {
        final File dir = book();
        commit(dir);
        final GitAdapter adapter = new GitAdapter(new Author("t", "t@e"));
        adapter.openRepo(dir.getPath());
        adapter.setRemoteUrl("https://example.org/wrong.git");
        adapter.setRemoteUrl(null);
        assertNull(adapter.getRemoteUrl());
        try (Git git = Git.open(dir)) {
            assertNull(git.getRepository().getConfig().getString("branch", "master", "remote"));
        }
        adapter.closeAdapter();
    }

    @Test
    public void repositoryNames() {
        assertEquals("my-book", GitHubPublisher.repositoryName("my book"));
        assertEquals("Frontier", GitHubPublisher.repositoryName("Frontier"));
        assertEquals("book", GitHubPublisher.repositoryName("Книга"));
        assertEquals("wolfman_2", GitHubPublisher.repositoryName("wolfman_2"));
        assertEquals("Book-ru", GitHubPublisher.repositoryName("Book (ru)"));
    }
}
