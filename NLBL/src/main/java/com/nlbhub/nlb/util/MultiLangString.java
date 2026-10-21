/**
 * @(#)MultiLangString.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2014 Anton P. Kolosov
 * Authors: Anton P. Kolosov, et al.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * ANTON P. KOLOSOV. ANTON P. KOLOSOV DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the Non-Linear Book software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Anton P. Kolosov at this
 * address: antokolos@gmail.com
 *
 * Copyright (c) 2014 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.util;

import com.nlbhub.nlb.api.Constants;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The MultiLangString class
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class MultiLangString {
    private Map<String, String> m_content;

    public MultiLangString(final MultiLangString source) {
        this();
        m_content.putAll(source.m_content);
    }

    public MultiLangString() {
        m_content = new HashMap<>();
    }

    public static MultiLangString createEmptyText() {
        return new MultiLangString();
    }

    public static MultiLangString createDefaultLinkText() {
        MultiLangString linkText = new MultiLangString();
        linkText.put(Constants.RU, "Далее");
        linkText.put(Constants.EN, "Next");
        linkText.put(Constants.ZH, "继续");
        return linkText;
    }

    public static MultiLangString createDefaultTraverseText() {
        MultiLangString linkText = new MultiLangString();
        linkText.put(Constants.RU, "Зайти внутрь");
        linkText.put(Constants.EN, "Go inside");
        linkText.put(Constants.ZH, "进入");
        return linkText;
    }

    public static MultiLangString createCopy(final MultiLangString source) {
        return new MultiLangString(source);
    }

    public Set<String> keySet() {
        return m_content.keySet();
    }

    public Collection<String> values() {
        return m_content.values();
    }

    public void put(final String langKey, final String value) {
        m_content.put(langKey, value);
    }

    public String get(final String langKey) {
        String result = m_content.get(langKey);
        return result != null ? result : Constants.EMPTY_STRING;
    }

    public boolean equalsAs(final String langKey, final MultiLangString mlsToCompare) {
        String contentText = m_content.get(langKey);
        String contentTextToCompare = mlsToCompare.m_content.get(langKey);
        if (StringHelper.isEmpty(contentText)) {
            return StringHelper.isEmpty(contentTextToCompare);
        } else {
            return contentText.equals(contentTextToCompare);
        }
    }

    public boolean isSubsetOf(final MultiLangString mlsToCompare) {
        for (Map.Entry<String, String> entry : m_content.entrySet()) {
            final String valueToCompare = mlsToCompare.get(entry.getKey());
            if (StringHelper.isEmpty(valueToCompare) && !StringHelper.isEmpty(entry.getValue())) {
                return false;
            }
            if (!valueToCompare.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Языки, на которых тексты по умолчанию были с самого начала. Книги, сохранённые до того,
     * как к текстам по умолчанию добавились другие языки (zh), хранят явный текст только на них
     */
    private static final Set<String> BASE_LANGUAGES = Set.of(Constants.RU, Constants.EN);

    /**
     * Текст совпадает с текстом по умолчанию defaults — как equals, но с поправкой на языки,
     * добавленные к текстам по умолчанию позже исходных ru и en (zh). Старые книги хранят такие
     * тексты явно только на ru и en; перевод на новом языке у них либо отсутствует, либо при чтении
     * берётся из значения по умолчанию самого поля (у текста входа в модуль — «进入»). Поэтому на
     * новом языке перевод может отсутствовать или совпадать с одним из unsetValues — это значит,
     * что автор его не задавал. Переводы на ru и en обязаны совпадать точно
     */
    public boolean matchesDefault(final MultiLangString defaults, final MultiLangString... unsetValues) {
        Set<String> languages = new HashSet<>(m_content.keySet());
        languages.addAll(defaults.m_content.keySet());
        for (String language : languages) {
            String value = get(language);
            if (value.equals(defaults.get(language))) {
                continue;
            }
            if (!BASE_LANGUAGES.contains(language) && (value.isEmpty() || isUnset(language, value, unsetValues))) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isUnset(String language, String value, MultiLangString[] unsetValues) {
        for (MultiLangString unset : unsetValues) {
            if (value.equals(unset.get(language))) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        for (Map.Entry<String, String> entry : m_content.entrySet()) {
            if (StringHelper.notEmpty(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MultiLangString that = (MultiLangString) o;

        if (m_content != null ? !m_content.equals(that.m_content) : that.m_content != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return m_content != null ? m_content.hashCode() : 0;
    }
}
