package com.nlbhub.nlb.vcs;

/**
 * Поле книги (файл), которое изменено и у автора, и на сервере: три версии — общего предка, своя
 * и с сервера (null — файла на этой стороне нет: элемент или поле удалено), и кто изменил его на
 * сервере. Двоичный файл (картинка, звук) — без текста, только «своя / с сервера»
 */
public final class ConflictField {
    private final String m_path;
    private final String m_base;
    private final String m_mine;
    private final String m_theirs;
    private final boolean m_mineExists;
    private final boolean m_theirsExists;
    private final boolean m_binary;
    private final String m_theirAuthor;
    private final long m_theirTime;
    private final String m_theirMessage;

    public ConflictField(String path, String base, String mine, String theirs, boolean mineExists, boolean theirsExists,
                         boolean binary, String theirAuthor, long theirTime, String theirMessage) {
        m_path = path;
        m_base = base;
        m_mine = mine;
        m_theirs = theirs;
        m_mineExists = mineExists;
        m_theirsExists = theirsExists;
        m_binary = binary;
        m_theirAuthor = theirAuthor;
        m_theirTime = theirTime;
        m_theirMessage = theirMessage;
    }

    /**
     * Путь файла относительно папки книги
     */
    public String getPath() {
        return m_path;
    }

    /**
     * Версия общего предка; null — файла не было или он двоичный
     */
    public String getBase() {
        return m_base;
    }

    public String getMine() {
        return m_mine;
    }

    public String getTheirs() {
        return m_theirs;
    }

    public boolean isMineExists() {
        return m_mineExists;
    }

    public boolean isTheirsExists() {
        return m_theirsExists;
    }

    /**
     * Удалено на одной из сторон
     */
    public boolean isDeletion() {
        return !m_mineExists || !m_theirsExists;
    }

    public boolean isBinary() {
        return m_binary;
    }

    /**
     * Кто изменил поле на сервере (имя автора коммита); null — неизвестно
     */
    public String getTheirAuthor() {
        return m_theirAuthor;
    }

    /**
     * Когда (мс с 1970); 0 — неизвестно
     */
    public long getTheirTime() {
        return m_theirTime;
    }

    public String getTheirMessage() {
        return m_theirMessage;
    }
}
