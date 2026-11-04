package com.nlbhub.nlb.vcs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nlbhub.nlb.exception.NLBVCSAuthenticationException;
import com.nlbhub.nlb.exception.NLBVCSException;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Создание пустого репозитория на GitHub (REST API) для публикации книги: без браузера и без
 * установки git, по токену автора (classic — с правом «repo», fine-grained — с правом
 * «Administration: Read and write»). Адрес API — системное свойство {@value #API_PROPERTY}
 * (по умолчанию https://api.github.com; GitHub Enterprise — https://host/api/v3), сервер git для
 * токена — {@value #SERVER_PROPERTY}.
 */
public final class GitHubPublisher {
    public static final String API_PROPERTY = "nlb.github.api";
    public static final String DEFAULT_API = "https://api.github.com";
    public static final String SERVER_PROPERTY = "nlb.github.server";
    /**
     * Сервер git для токена GitHub (тот же, что у адресов https://github.com/…)
     */
    public static final String GITHUB_SERVER = "https://github.com";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String m_api;
    private final String m_token;
    private final HttpClient m_client;

    /**
     * Созданный репозиторий: адрес для git (https) и страница на сайте
     */
    public static final class Repository {
        private final String m_cloneUrl;
        private final String m_htmlUrl;
        private final String m_fullName;

        Repository(String cloneUrl, String htmlUrl, String fullName) {
            m_cloneUrl = cloneUrl;
            m_htmlUrl = htmlUrl;
            m_fullName = fullName;
        }

        public String getCloneUrl() {
            return m_cloneUrl;
        }

        public String getHtmlUrl() {
            return m_htmlUrl;
        }

        public String getFullName() {
            return m_fullName;
        }
    }

    public GitHubPublisher(final String token) {
        this(System.getProperty(API_PROPERTY, DEFAULT_API), token);
    }

    public GitHubPublisher(final String api, final String token) {
        m_api = api.endsWith("/") ? api.substring(0, api.length() - 1) : api;
        m_token = token;
        m_client = HttpClient.newBuilder()
                .proxy(ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Сервер, под которым токен GitHub хранится в системном хранилище (как у pull/push по адресам
     * этого сервера): системное свойство {@value #SERVER_PROPERTY}, по умолчанию https://github.com
     */
    public static String server() {
        return System.getProperty(SERVER_PROPERTY, GITHUB_SERVER);
    }

    /**
     * Имя пользователя, которому принадлежит токен (заодно проверка токена)
     */
    public String login() throws NLBVCSException {
        final JsonNode user = send("GET", "/user", null, "check the token");
        return user.path("login").asText(null);
    }

    /**
     * Создать пустой репозиторий; owner — организация, пусто или null — свой аккаунт
     */
    public Repository createRepository(final String owner, final String name, final String description,
                                       final boolean privateRepo) throws NLBVCSException {
        final ObjectNode body = JSON.createObjectNode();
        body.put("name", name);
        if (description != null && !description.trim().isEmpty()) {
            body.put("description", description.trim());
        }
        body.put("private", privateRepo);
        // без README и .gitignore: первый push книги ляжет в пустой репозиторий
        body.put("auto_init", false);
        final String path = owner == null || owner.trim().isEmpty()
                ? "/user/repos"
                : "/orgs/" + URLEncoder.encode(owner.trim(), StandardCharsets.UTF_8) + "/repos";
        final JsonNode repo = send("POST", path, body, "create the repository");
        final String cloneUrl = repo.path("clone_url").asText(null);
        if (cloneUrl == null) {
            throw new NLBVCSException("GitHub did not return the address of the new repository");
        }
        return new Repository(cloneUrl, repo.path("html_url").asText(cloneUrl), repo.path("full_name").asText(name));
    }

    /**
     * Имя репозитория из имени папки книги: GitHub допускает латиницу, цифры, «-», «_» и «.»
     */
    public static String repositoryName(final String folder) {
        final String name = folder == null ? "" : folder.trim()
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-.]+|-+$", "");
        return name.isEmpty() ? "book" : name;
    }

    private JsonNode send(final String method, final String path, final JsonNode body, final String what)
            throws NLBVCSException {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(m_api + path))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "NLB-Builder")
                .header("Authorization", "Bearer " + m_token);
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json");
            request.method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        }
        final HttpResponse<String> response;
        try {
            response = m_client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new NLBVCSException("Cannot connect to GitHub (" + m_api + ") to " + what + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NLBVCSException("Interrupted while trying to " + what + " on GitHub", e);
        }
        final int status = response.statusCode();
        JsonNode json;
        try {
            json = JSON.readTree(response.body() == null || response.body().isEmpty() ? "{}" : response.body());
        } catch (IOException e) {
            json = JSON.createObjectNode();
        }
        if (status >= 200 && status < 300) {
            return json;
        }
        final String message = errorText(json);
        switch (status) {
            case 401:
                throw new NLBVCSAuthenticationException("GitHub did not accept the token" + message, null);
            case 403:
                throw new NLBVCSException("The token is not allowed to " + what
                        + " (a classic token needs the \"repo\" scope; a fine-grained one needs "
                        + "\"Administration: Read and write\" for the account or organization)" + message);
            case 404:
                throw new NLBVCSException("GitHub cannot " + what + ": the organization is not found, or the token "
                        + "has no access to it" + message);
            case 422:
                throw new NLBVCSException("GitHub cannot " + what + ": a repository with this name probably "
                        + "already exists, or the name is not allowed" + message);
            default:
                throw new NLBVCSException("GitHub cannot " + what + " (HTTP " + status + ")" + message);
        }
    }

    /**
     * Пояснение GitHub к ошибке: «message» и «errors[].message»
     */
    private static String errorText(final JsonNode json) {
        final StringBuilder text = new StringBuilder();
        // «Repository creation failed.» + «name already exists…» — без точки перед «;»
        final String message = json.path("message").asText("").replaceAll("\\.$", "");
        if (!message.isEmpty()) {
            text.append(message);
        }
        for (final JsonNode error : json.path("errors")) {
            final String detail = error.path("message").asText("");
            if (!detail.isEmpty()) {
                text.append(text.length() > 0 ? "; " : "").append(detail);
            }
        }
        return text.length() > 0 ? ": " + text : "";
    }
}
