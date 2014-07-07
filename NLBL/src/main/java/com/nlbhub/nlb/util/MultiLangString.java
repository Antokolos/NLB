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
        return linkText;
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
}
