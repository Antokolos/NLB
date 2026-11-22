package com.nlbhub.nlb.vcs;

/**
 * Изменённый файл книги в версии (относительно предыдущей версии): путь, вид изменения, текст
 * «было / стало». Двоичный файл (картинка, звук) и большой файл — без текста; внешний модуль
 * (подмодуль git) — только факт изменения. Группирует файлы по элементам книги {@link BookHistory}
 */
public final class BookFileChange {
    public enum Kind {
        ADDED, DELETED, MODIFIED
    }

    private final String m_path;
    private final Kind m_kind;
    private final String m_before;
    private final String m_after;
    private final boolean m_binary;
    private final boolean m_submodule;
    private final String m_beforeObjectId;
    private final String m_afterObjectId;

    /**
     * @param before         текст до (null — файла не было, он двоичный или слишком большой)
     * @param after          текст после (null — то же)
     * @param beforeObjectId id содержимого до в репозитории (null — файла не было): для картинок
     * @param afterObjectId  id содержимого после (null — файл удалён)
     */
    public BookFileChange(String path, Kind kind, String before, String after, boolean binary, boolean submodule,
                          String beforeObjectId, String afterObjectId) {
        m_path = path;
        m_kind = kind;
        m_before = before;
        m_after = after;
        m_binary = binary;
        m_submodule = submodule;
        m_beforeObjectId = beforeObjectId;
        m_afterObjectId = afterObjectId;
    }

    /**
     * Путь относительно папки книги, через «/»
     */
    public String getPath() {
        return m_path;
    }

    public Kind getKind() {
        return m_kind;
    }

    public String getBefore() {
        return m_before;
    }

    public String getAfter() {
        return m_after;
    }

    /**
     * Двоичный или слишком большой файл: текста нет
     */
    public boolean isBinary() {
        return m_binary;
    }

    /**
     * Внешний модуль (подмодуль git): изменилась версия модуля
     */
    public boolean isSubmodule() {
        return m_submodule;
    }

    public String getBeforeObjectId() {
        return m_beforeObjectId;
    }

    public String getAfterObjectId() {
        return m_afterObjectId;
    }

    @Override
    public String toString() {
        return m_kind + " " + m_path;
    }
}
