package com.nlbhub.nlb.vcs.secrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Учётные данные для pull/push по https: имя пользователя на сервере запоминается в настройках
 * редактора (это не секрет), пароль или токен — в системном хранилище секретов
 * ({@link SecretStore}), если автор отметил «запомнить». Запись — по серверу (схема, хост и порт
 * адреса книги, например https://github.com) и имени пользователя: внешние модули (подмодули) с
 * того же сервера используют тот же токен.
 */
public final class RemoteCredentials {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCredentials.class);
    private static final String SERVICE_PREFIX = "NLB Builder: ";

    private final SecretStore m_store;
    private final Preferences m_users;

    public RemoteCredentials(final SecretStore store, final Preferences users) {
        m_store = store;
        m_users = users;
    }

    /**
     * Системное хранилище секретов и настройки пользователя ОС
     */
    public static RemoteCredentials system() {
        return new RemoteCredentials(SecretStore.system(), Preferences.userRoot().node("com/nlbhub/nlb/git/users"));
    }

    /**
     * Адрес по ssh (git@host:path, ssh://…) — пароль или токен к нему не подходит
     */
    public static boolean isSsh(final String remoteUrl) {
        if (remoteUrl == null) {
            return false;
        }
        final String url = remoteUrl.trim().toLowerCase(Locale.ROOT);
        if (url.startsWith("ssh://") || url.startsWith("git+ssh://") || url.startsWith("ssh+git://")) {
            return true;
        }
        // scp-подобный адрес: [user@]host:path без схемы (не «C:/путь» Windows: хост — от 2 символов)
        return !url.contains("://") && url.matches("^([^@/\\\\]+@)?[^/\\\\:]{2,}:.+");
    }

    /**
     * Сервер адреса книги: «схема://хост[:порт]» для http(s); иначе null
     */
    public static String server(final String remoteUrl) {
        if (remoteUrl == null) {
            return null;
        }
        try {
            final URI uri = new URI(remoteUrl.trim());
            final String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme) && !"http".equals(scheme) || uri.getHost() == null) {
                return null;
            }
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Имя пользователя из адреса (https://user@host/…), если оно там есть
     */
    public static String userFromUrl(final String remoteUrl) {
        try {
            final String info = new URI(remoteUrl.trim()).getUserInfo();
            if (info == null || info.isEmpty()) {
                return null;
            }
            final int colon = info.indexOf(':');
            return colon >= 0 ? info.substring(0, colon) : info;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Можно запомнить пароль или токен (есть системное хранилище)
     */
    public boolean canRememberSecret() {
        return m_store.isAvailable();
    }

    /**
     * Последнее имя пользователя на этом сервере; null — нет
     */
    public String rememberedUser(final String server) {
        return server == null ? null : m_users.get(key(server), null);
    }

    /**
     * Сохранённый пароль или токен; null — нет (или хранилище недоступно)
     */
    public String rememberedSecret(final String server, final String user) {
        if (server == null || user == null || !m_store.isAvailable()) {
            return null;
        }
        try {
            return m_store.get(SERVICE_PREFIX + server, user);
        } catch (SecretStoreException e) {
            LOGGER.warn("Cannot read the saved password or token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Запомнить имя пользователя на сервере, а пароль или токен — если secret не null
     */
    public void remember(final String server, final String user, final String secret) throws SecretStoreException {
        if (server == null || user == null) {
            return;
        }
        m_users.put(key(server), user);
        if (secret != null && m_store.isAvailable()) {
            m_store.put(SERVICE_PREFIX + server, user, secret);
        }
    }

    /**
     * Забыть сохранённый пароль или токен (сервер его не принял или автор отказался запоминать)
     */
    public void forgetSecret(final String server, final String user) {
        if (server == null || user == null || !m_store.isAvailable()) {
            return;
        }
        try {
            m_store.delete(SERVICE_PREFIX + server, user);
        } catch (SecretStoreException e) {
            LOGGER.warn("Cannot delete the saved password or token: {}", e.getMessage());
        }
    }

    private static final String PASSPHRASE_SERVICE = "NLB Builder: SSH key passphrase";

    /**
     * Сохранённый пароль к ключу ssh; key — что за ключ (текст вопроса JGit с путём к ключу)
     */
    public String rememberedPassphrase(final String key) {
        if (!m_store.isAvailable()) {
            return null;
        }
        try {
            return m_store.get(PASSPHRASE_SERVICE, key);
        } catch (SecretStoreException e) {
            LOGGER.warn("Cannot read the saved passphrase: {}", e.getMessage());
            return null;
        }
    }

    public void rememberPassphrase(final String key, final String passphrase) throws SecretStoreException {
        if (m_store.isAvailable()) {
            m_store.put(PASSPHRASE_SERVICE, key, passphrase);
        }
    }

    public void forgetPassphrase(final String key) {
        if (!m_store.isAvailable()) {
            return;
        }
        try {
            m_store.delete(PASSPHRASE_SERVICE, key);
        } catch (SecretStoreException e) {
            LOGGER.warn("Cannot delete the saved passphrase: {}", e.getMessage());
        }
    }

    /**
     * Ключ настроек — не длиннее Preferences.MAX_KEY_LENGTH (80)
     */
    private static String key(final String server) {
        return server.length() <= Preferences.MAX_KEY_LENGTH ? server : server.substring(0, Preferences.MAX_KEY_LENGTH);
    }
}
