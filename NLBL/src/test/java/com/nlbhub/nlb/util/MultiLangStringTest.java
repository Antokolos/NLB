package com.nlbhub.nlb.util;

import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.Page;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Сравнение с текстом по умолчанию (тривиальные ссылки экспорта и плеера): тексты по умолчанию
 * пополнились языком zh, а старые книги хранят «Далее»/«Next» явно только на ru и en
 */
public class MultiLangStringTest {
    private static MultiLangString text(String... pairs) {
        MultiLangString result = MultiLangString.createEmptyText();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(pairs[i], pairs[i + 1]);
        }
        return result;
    }

    @Test
    public void defaultTextMatches() {
        assertTrue(MultiLangString.createCopy(Link.DEFAULT_TEXT).matchesDefault(Link.DEFAULT_TEXT));
        assertTrue(MultiLangString.createEmptyText().matchesDefault(Link.DEFAULT_ALT_TEXT));
    }

    @Test
    public void oldBookTextWithoutChineseMatches() {
        // текст возврата из модуля старой книги
        assertTrue(text("ru", "Далее", "en", "Next").matchesDefault(Link.DEFAULT_TEXT));
        // текст входа в модуль: zh при чтении взят из значения по умолчанию поля («进入»)
        MultiLangString traverse = MultiLangString.createCopy(Page.DEFAULT_TRAVERSE_TEXT);
        traverse.put("ru", "Далее");
        traverse.put("en", "Next");
        assertTrue(traverse.matchesDefault(Link.DEFAULT_TEXT, Page.DEFAULT_TRAVERSE_TEXT));
        assertFalse(traverse.matchesDefault(Link.DEFAULT_TEXT));
    }

    @Test
    public void ownTextDoesNotMatch() {
        // переводы на ru и en обязаны совпадать, как раньше
        assertFalse(text("en", "Next").matchesDefault(Link.DEFAULT_TEXT));
        assertFalse(text("ru", "Далее", "en", "Go").matchesDefault(Link.DEFAULT_TEXT));
        // свой перевод на zh — уже не текст по умолчанию
        assertFalse(text("ru", "Далее", "en", "Next", "zh", "前进").matchesDefault(Link.DEFAULT_TEXT, Page.DEFAULT_TRAVERSE_TEXT));
        // перевод на языке, которого нет в умолчаниях
        assertFalse(text("ru", "Далее", "en", "Next", "de", "Weiter").matchesDefault(Link.DEFAULT_TEXT));
        // непустой альтернативный текст
        assertFalse(text("ru", "Закрыто").matchesDefault(Link.DEFAULT_ALT_TEXT));
    }
}
