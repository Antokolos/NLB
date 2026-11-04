package com.nlbhub.nlb.vcs;

/**
 * Элемент книги, восстановленный при слиянии (pull): на одной стороне его удалили, а другая
 * сторона что-то в него добавила или на него сослалась — без него книга была бы сломана
 * (обрывок страницы, ссылка в никуда), а работа соавтора потеряна
 */
public final class RestoredElement {
    private final String m_path;
    private final boolean m_deletedLocally;

    public RestoredElement(final String path, final boolean deletedLocally) {
        m_path = path;
        m_deletedLocally = deletedLocally;
    }

    /**
     * Папка элемента относительно папки книги: pages/&lt;id&gt;, objs/&lt;id&gt;, vars/&lt;id&gt;, …/links/&lt;id&gt;…
     */
    public String getPath() {
        return m_path;
    }

    /**
     * Удалён у автора (иначе — на сервере, соавтором)
     */
    public boolean isDeletedLocally() {
        return m_deletedLocally;
    }

    @Override
    public String toString() {
        return m_path + (m_deletedLocally ? " (deleted locally)" : " (deleted on the server)");
    }
}
