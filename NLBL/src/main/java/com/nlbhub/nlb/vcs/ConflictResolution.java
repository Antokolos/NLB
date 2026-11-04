package com.nlbhub.nlb.vcs;

/**
 * Решение автора для одного пересёкшегося поля книги (файла) при pull: своя версия, версия с
 * сервера или свой текст (например, оба варианта, сведённые вручную). Удалено на одной стороне
 * — «своя» или «с сервера» решают для элемента книги целиком: оставить его или удалить
 */
public final class ConflictResolution {
    public enum Kind {MINE, THEIRS, TEXT}

    private static final ConflictResolution MINE = new ConflictResolution(Kind.MINE, null);
    private static final ConflictResolution THEIRS = new ConflictResolution(Kind.THEIRS, null);

    private final Kind m_kind;
    private final String m_text;

    private ConflictResolution(final Kind kind, final String text) {
        m_kind = kind;
        m_text = text;
    }

    public static ConflictResolution mine() {
        return MINE;
    }

    public static ConflictResolution theirs() {
        return THEIRS;
    }

    public static ConflictResolution text(final String text) {
        return new ConflictResolution(Kind.TEXT, text);
    }

    public Kind getKind() {
        return m_kind;
    }

    /**
     * Текст поля для {@link Kind#TEXT}
     */
    public String getText() {
        return m_text;
    }
}
