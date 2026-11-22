package com.nlbhub.nlb.vcs;

import java.util.Collections;
import java.util.List;

/**
 * Версия книги в истории (коммит первой линии истории: правки этого компьютера и полученные с
 * сервера). Слияние при pull — одна версия «правки с сервера»: {@link #isFromServer()}, кто и
 * сколько версий принёс — {@link #getServerAuthors()}, {@link #getServerVersions()}
 */
public final class BookVersion {
    /**
     * Есть ли версия на сервере — по последнему известному состоянию сервера (origin/&lt;ветка&gt;
     * после прошлых pull или push; в сеть ради этого не ходим)
     */
    public enum ServerState {
        /**
         * Сервера нет (книга не опубликована) — ничего не показывать
         */
        NO_SERVER,
        /**
         * Только на этом компьютере: на сервер уйдёт после Push
         */
        LOCAL_ONLY,
        /**
         * Есть на сервере
         */
        ON_SERVER
    }

    private final String m_id;
    private final String m_message;
    private final String m_authorName;
    private final String m_authorEmail;
    private final long m_time;
    private final ServerState m_serverState;
    private final boolean m_fromServer;
    private final List<String> m_serverAuthors;
    private final int m_serverVersions;

    public BookVersion(String id, String message, String authorName, String authorEmail, long time,
                       ServerState serverState, boolean fromServer, List<String> serverAuthors, int serverVersions) {
        m_id = id;
        m_message = message == null ? "" : message;
        m_authorName = authorName == null ? "" : authorName;
        m_authorEmail = authorEmail == null ? "" : authorEmail;
        m_time = time;
        m_serverState = serverState;
        m_fromServer = fromServer;
        m_serverAuthors = serverAuthors == null ? Collections.emptyList() : Collections.unmodifiableList(serverAuthors);
        m_serverVersions = serverVersions;
    }

    /**
     * Полный id коммита (для {@link VCSAdapter#getVersionChanges(String)})
     */
    public String getId() {
        return m_id;
    }

    /**
     * Сообщение коммита целиком
     */
    public String getMessage() {
        return m_message;
    }

    /**
     * Первая строка сообщения
     */
    public String getShortMessage() {
        final int end = m_message.indexOf('\n');
        return (end < 0 ? m_message : m_message.substring(0, end)).trim();
    }

    public String getAuthorName() {
        return m_authorName;
    }

    public String getAuthorEmail() {
        return m_authorEmail;
    }

    /**
     * Когда сделана (мс с 1970): у правки — время автора, у слияния — время слияния
     */
    public long getTime() {
        return m_time;
    }

    public ServerState getServerState() {
        return m_serverState;
    }

    /**
     * Слияние при pull: правки, полученные с сервера (изменения — относительно нашей прежней версии)
     */
    public boolean isFromServer() {
        return m_fromServer;
    }

    /**
     * Авторы версий, принесённых слиянием (без повторов, от новых к старым)
     */
    public List<String> getServerAuthors() {
        return m_serverAuthors;
    }

    /**
     * Сколько версий принесло слияние (коммиты слияния не считаются)
     */
    public int getServerVersions() {
        return m_serverVersions;
    }

    @Override
    public String toString() {
        return m_id.substring(0, Math.min(8, m_id.length())) + " " + getShortMessage();
    }
}
