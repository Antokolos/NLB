/**
 * @(#)SearchContract.java
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
package com.nlbhub.nlb.api;

/**
 * The SearchContract class represents contract with text search parameters.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class SearchContract {
    private final String m_searchText;
    private final boolean m_searchInIds;
    private final boolean m_searchInPages;
    private final boolean m_searchInObjects;
    private final boolean m_searchInLinks;
    private final boolean m_searchInVars;
    private final boolean m_ignoreCase;
    private final boolean m_wholeWords;
    private final boolean m_findUnusualQuotes;

    public SearchContract(String searchText, boolean searchInIds, boolean searchInPages, boolean searchInObjects, boolean searchInLinks, boolean searchInVars, boolean ignoreCase, boolean wholeWords, boolean findUnusualQuotes) {
        m_searchText = searchText;
        m_searchInIds = searchInIds;
        m_searchInPages = searchInPages;
        m_searchInObjects = searchInObjects;
        m_searchInLinks = searchInLinks;
        m_searchInVars = searchInVars;
        m_ignoreCase = ignoreCase;
        m_wholeWords = wholeWords;
        m_findUnusualQuotes = findUnusualQuotes;
    }

    public String getSearchText() {
        return m_searchText;
    }

    public boolean isSearchInIds() {
        return m_searchInIds;
    }

    public boolean isSearchInPages() {
        return m_searchInPages;
    }

    public boolean isSearchInObjects() {
        return m_searchInObjects;
    }

    public boolean isSearchInLinks() {
        return m_searchInLinks;
    }

    public boolean isSearchInVars() {
        return m_searchInVars;
    }

    public boolean isIgnoreCase() {
        return m_ignoreCase;
    }

    public boolean isWholeWords() {
        return m_wholeWords;
    }

    public boolean isFindUnusualQuotes() {
        return m_findUnusualQuotes;
    }
}
