/**
 * @(#)SearchResultTableModel.java
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

import com.nlbhub.nlb.domain.SearchResult;

import java.util.*;

/**
 * The SearchResultTableModel class
 *
 * @author Anton P. Kolosov
 * @version 1.0 2/5/14
 */
public class SearchResultTableModel {
    private static String NA = "N/A";
    private Map<String, List<SearchResult>> m_searchResultMap = new HashMap<>();
    /**
     * Column index zero is always "Id",
     * column index one is always "Module Page Id",
     * these are the names of the other columns.
     */
    private List<String> m_columnNames = new ArrayList<>();
    private int m_columnCount;

    public SearchResultTableModel() {
        m_columnNames.add("Information");
        m_columnCount = 3;
    }

    public SearchResultTableModel(final List<String> columnNames) {
        m_columnNames.addAll(columnNames);
        m_columnCount = columnNames.size() + 2;
    }

    public SearchResultTableModel(String... columnNames) {
        this(Arrays.asList(columnNames));
    }

    public void addSearchResult(final SearchResult searchResult) {
        if (searchResult != null) {
            List<SearchResult> searchResults = m_searchResultMap.get(searchResult.getId());
            if (searchResults == null) {
                searchResults = new ArrayList<>();
                m_searchResultMap.put(searchResult.getId(), searchResults);
            }
            searchResults.add(searchResult);
        }
    }

    public void addSearchResults(final List<SearchResult> searchResults) {
        for (SearchResult searchResult : searchResults) {
            addSearchResult(searchResult);
        }
    }

    /**
     * @param searchResultTableModel should have same column set or at least the same column count
     */
    public void addSearchResultTableModel(final SearchResultTableModel searchResultTableModel) {
        if (
                searchResultTableModel != null
                        && searchResultTableModel.m_columnCount == m_columnCount
                ) {
            m_searchResultMap.putAll(searchResultTableModel.m_searchResultMap);
        }
    }

    public int getRowCount() {
        int result = 0;
        for (Map.Entry<String, List<SearchResult>> entry : m_searchResultMap.entrySet()) {
            result += entry.getValue().size();
        }
        return result;
    }

    public int getColumnCount() {
        return m_columnCount;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        int i = 0;
        for (Map.Entry<String, List<SearchResult>> entry : m_searchResultMap.entrySet()) {
            for (SearchResult searchResult : entry.getValue()) {
                if (i == rowIndex) {
                    if (columnIndex == 0) {
                        return entry.getKey();
                    } else if (columnIndex == 1) {
                        return searchResult.getModulePageId();
                    } else if (columnIndex >= m_columnCount) {
                        return NA;
                    } else {
                        return searchResult.getInformationByPosition(columnIndex - 2);
                    }
                }
                i++;
            }
        }
        return NA;
    }

    public String getColumnName(int column) {
        if (column == 0) {
            return "Id";
        } else if (column == 1) {
            return "Module Page Id";
        } else if (column >= m_columnCount) {
            return NA;
        } else {
            return m_columnNames.get(column - 2);
        }
    }
}
