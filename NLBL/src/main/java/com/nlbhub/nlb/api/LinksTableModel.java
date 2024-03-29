/**
 * @(#)LinksTableModel.java
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

import java.util.LinkedList;
import java.util.List;

/**
 * The LinksTableModel class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/16/14
 */
public class LinksTableModel {
    private List<Link> m_links;

    public LinksTableModel(List<Link> links) {
        m_links = new LinkedList<>();
        for (final Link link : links) {
            if (!link.isDeleted()) {
                m_links.add(link);
            }
        }
    }

    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return "Link Id";
            case 1:
                return "Link text";
            default:
                return "N/A";
        }
    }

    public int getRowCount() {
        return m_links.size();
    }

    public int getColumnCount() {
        return 2;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        final Link link = getLinkAt(rowIndex);
        switch (columnIndex) {
            case 0:
                return link.getId();
            case 1:
                return link.getText();
            default:
                return "N/A";

        }
    }

    public Link getLinkAt(int rowIndex) {
        return m_links.get(rowIndex);
    }

    public void deleteLinkAt(int rowIndex) {
        m_links.remove(rowIndex);
    }

    public List<Link> getLinks() {
        return m_links;
    }

    public void moveUp(int rowIndex) {
        if (rowIndex > 0) {
            Link item = m_links.remove(rowIndex - 1);
            m_links.add(rowIndex, item);
        }
    }

    public void moveDown(int rowIndex) {
        if (rowIndex < m_links.size() - 1) {
            Link item = m_links.remove(rowIndex + 1);
            m_links.add(rowIndex, item);
        }
    }
}
