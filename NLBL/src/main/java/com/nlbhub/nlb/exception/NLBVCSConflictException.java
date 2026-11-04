package com.nlbhub.nlb.exception;

import java.util.Collections;
import java.util.List;

/**
 * Pull не слил правки с сервера: они пересекаются с нашими (одни и те же файлы книги изменены и
 * там, и здесь). Книга оставлена такой, какой была до pull, — ничего не потеряно. Пути — файлы
 * книги относительно её папки (pages/&lt;id&gt;/text/ru и т. п.); редактор называет их по-человечески.
 * uncommitted — пересеклись не закоммиченные (только сохранённые) правки: их сначала нужно закоммитить
 */
public class NLBVCSConflictException extends NLBVCSException {
    private final List<String> m_paths;
    private final boolean m_uncommitted;

    public NLBVCSConflictException(String message, List<String> paths, boolean uncommitted) {
        super(message);
        m_paths = Collections.unmodifiableList(paths);
        m_uncommitted = uncommitted;
    }

    public List<String> getPaths() {
        return m_paths;
    }

    public boolean isUncommitted() {
        return m_uncommitted;
    }
}
