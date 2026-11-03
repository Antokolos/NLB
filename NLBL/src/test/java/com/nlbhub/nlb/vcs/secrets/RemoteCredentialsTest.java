package com.nlbhub.nlb.vcs.secrets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Учётные данные для pull/push: сервер из адреса книги, имя пользователя — в настройках, пароль
 * или токен — в хранилище секретов (здесь — подменном, в памяти); ssh-адреса распознаются.
 */
public class RemoteCredentialsTest {
    private Preferences m_users;
    private final Map<String, String> m_secrets = new HashMap<>();

    private final SecretStore m_store = new SecretStore() {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String get(String service, String account) {
            return m_secrets.get(service + "|" + account);
        }

        @Override
        public void put(String service, String account, String secret) {
            m_secrets.put(service + "|" + account, secret);
        }

        @Override
        public void delete(String service, String account) {
            m_secrets.remove(service + "|" + account);
        }
    };

    @Before
    public void setUp() {
        m_users = Preferences.userRoot().node("com/nlbhub/nlb/test/" + System.nanoTime());
    }

    @After
    public void tearDown() throws Exception {
        m_users.removeNode();
    }

    @Test
    public void serverAndSshAreRecognized() {
        assertEquals("https://github.com", RemoteCredentials.server("https://github.com/nlbproject/Frontier"));
        assertEquals("https://github.com", RemoteCredentials.server("https://anna@GitHub.com/nlbproject/Frontier.git"));
        assertEquals("http://git.local:8080", RemoteCredentials.server("http://git.local:8080/book.git"));
        assertNull(RemoteCredentials.server("git@github.com:nlbproject/Frontier.git"));
        assertNull(RemoteCredentials.server(null));
        assertEquals("anna", RemoteCredentials.userFromUrl("https://anna@github.com/x/y.git"));
        assertNull(RemoteCredentials.userFromUrl("https://github.com/x/y.git"));

        assertTrue(RemoteCredentials.isSsh("git@github.com:nlbproject/Frontier.git"));
        assertTrue(RemoteCredentials.isSsh("ssh://git@github.com/nlbproject/Frontier.git"));
        assertTrue(RemoteCredentials.isSsh("github.com:nlbproject/Frontier.git"));
        assertFalse(RemoteCredentials.isSsh("https://github.com/nlbproject/Frontier"));
        assertFalse(RemoteCredentials.isSsh("C:/books/Frontier"));
        assertFalse(RemoteCredentials.isSsh("/home/anna/Frontier"));
        assertFalse(RemoteCredentials.isSsh(null));
    }

    @Test
    public void userAndSecretAreRememberedAndForgotten() throws Exception {
        RemoteCredentials credentials = new RemoteCredentials(m_store, m_users);
        String server = "https://github.com";
        assertNull(credentials.rememberedUser(server));
        assertNull(credentials.rememberedSecret(server, "anna"));

        // без «запомнить» — только имя пользователя
        credentials.remember(server, "anna", null);
        assertEquals("anna", credentials.rememberedUser(server));
        assertNull(credentials.rememberedSecret(server, "anna"));

        credentials.remember(server, "anna", "ghp_token");
        assertEquals("ghp_token", credentials.rememberedSecret(server, "anna"));
        // токен — у своего сервера и пользователя
        assertNull(credentials.rememberedSecret("https://gitlab.com", "anna"));
        assertNull(credentials.rememberedSecret(server, "boris"));
        // в настройках — только имя, не токен
        for (String key : m_users.keys()) {
            assertFalse(m_users.get(key, "").contains("ghp_token"));
        }

        // сервер не принял — забыть токен, имя остаётся
        credentials.forgetSecret(server, "anna");
        assertNull(credentials.rememberedSecret(server, "anna"));
        assertEquals("anna", credentials.rememberedUser(server));
    }
}
