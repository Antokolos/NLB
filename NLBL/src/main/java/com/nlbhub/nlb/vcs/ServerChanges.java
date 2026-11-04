package com.nlbhub.nlb.vcs;

import java.util.Collections;
import java.util.List;

/**
 * Что есть на сервере и ещё нет у автора: сколько коммитов, чьи, последнее сообщение
 */
public final class ServerChanges {
    private final int m_count;
    private final List<String> m_authors;
    private final String m_lastMessage;
    private final long m_lastTime;

    public ServerChanges(int count, List<String> authors, String lastMessage, long lastTime) {
        m_count = count;
        m_authors = Collections.unmodifiableList(authors);
        m_lastMessage = lastMessage;
        m_lastTime = lastTime;
    }

    /**
     * Коммитов на сервере, которых нет у автора; 0 — ничего нового
     */
    public int getCount() {
        return m_count;
    }

    /**
     * Авторы этих коммитов (без повторов, сначала последние)
     */
    public List<String> getAuthors() {
        return m_authors;
    }

    /**
     * Первая строка сообщения последнего из них
     */
    public String getLastMessage() {
        return m_lastMessage;
    }

    /**
     * Время последнего (мс с 1970)
     */
    public long getLastTime() {
        return m_lastTime;
    }
}
