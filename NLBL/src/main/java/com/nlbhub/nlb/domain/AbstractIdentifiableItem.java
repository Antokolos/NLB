/**
 * @(#)AbstractIdentifiableItem.java
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
 * Copyright (c) 2013 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.util.QuotationHelper;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.util.MultiLangString;
import com.nlbhub.nlb.util.StringHelper;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The AbstractIdentifiableItem class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/2/13
 */
@XmlAccessorType(XmlAccessType.NONE)
public abstract class AbstractIdentifiableItem implements IdentifiableItem {
    private String m_id;
    /**
     * Flag indicating that this link has been scheduled for deleting.
     * This means that link directory will be completely removed during save.
     */
    private boolean m_isDeleted = false;
    private NonLinearBook m_currentNLB;
    private IdentifiableItem m_parent = null;

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     * Do not use it for any other purpose!
     */
    public AbstractIdentifiableItem() {
        m_id = UUID.randomUUID().toString();
        m_currentNLB = DummyNLB.singleton();
    }

    public AbstractIdentifiableItem(NonLinearBook currentNLB) {
        this();
        m_currentNLB = currentNLB;
    }

    public AbstractIdentifiableItem(IdentifiableItem identifiableItem, NonLinearBook currentNLB) {
        this(currentNLB);
        m_id = identifiableItem.getId();
        m_isDeleted = identifiableItem.isDeleted();
        m_parent = identifiableItem.getParent();
    }

    public AbstractIdentifiableItem(IdentifiableItem identifiableItem, ModifyingItem parent, NonLinearBook currentNLB) {
        this(currentNLB);
        m_id = identifiableItem.getId();
        m_isDeleted = identifiableItem.isDeleted();
        m_parent = parent;
    }

    public void copy(IdentifiableItem identifiableItem) {
        m_id = identifiableItem.getId();
        m_isDeleted = identifiableItem.isDeleted();
        m_parent = identifiableItem.getParent();
        m_currentNLB = identifiableItem.getCurrentNLB();
    }

    public void setId(String id) {
        m_id = id;
    }

    @Override
    @XmlElement(name = "id")
    public String getId() {
        return m_id;
    }

    public IdentifiableItem getParent() {
        return m_parent;
    }

    public void setParent(IdentifiableItem parent) {
        m_parent = parent;
    }

    @Override
    public boolean hasDeletedParent() {
        IdentifiableItem item = this;
        while ((item = item.getParent()) != null) {
            if (item.isDeleted()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getFullId() {
        List<String> ids = new LinkedList<>();
        IdentifiableItem item = this;
        while (item != null) {
            ids.add(0, item.getId());
            item = item.getParent();
        }
        return StringHelper.formatSequence(ids);
    }

    public void setDeleted(boolean deleted) {
        m_isDeleted = deleted;
    }

    @Override
    public boolean isDeleted() {
        return m_isDeleted;
    }

    @Override
    public NonLinearBook getCurrentNLB() {
        return m_currentNLB;
    }

    @Override
    public SearchResult searchText(SearchContract contract) {
        if (contract.isSearchInIds() && textMatches(m_id, contract)) {
            SearchResult result = new SearchResult();
            result.setId(m_id);
            result.setInformation(m_id);
            return result;
        } else {
            return null;
        }
    }

    protected boolean textMatches(final MultiLangString mlsToTest, final SearchContract contract) {
        for (String text : mlsToTest.values()) {
            if (textMatches(text, contract)) {
                return true;
            }
        }
        return false;
    }

    protected boolean textMatches(final String stringToTest, final SearchContract contract) {
        StringBuilder patternText = new StringBuilder();
        if (contract.isWholeWords()) {
            patternText.append("\\b");
        }
        patternText.append(contract.getSearchText());
        if (contract.isWholeWords()) {
            patternText.append("\\b");
        }
        Pattern pattern = (
                (contract.isIgnoreCase())
                        ? (
                        Pattern.compile(
                                patternText.toString(),
                                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                        )
                )
                        : Pattern.compile(patternText.toString())
        );
        Matcher matcher = pattern.matcher(stringToTest);
        boolean result = matcher.find();
        if (contract.isFindUnusualQuotes()) {
            return result || QuotationHelper.find(stringToTest);
        }
        return result;
    }

    protected List<File> createSortedDirList(File[] dirs, List<String> orderList) throws NLBConsistencyException {
        List<File> dirsList = new LinkedList<File>();
        // Cannot use Arrays.asList(), because this list will be modified later
        //noinspection all
        for (final File file : dirs) {
            dirsList.add(file);
        }

        List<File> result = new ArrayList<>();
        for (final String dirName : orderList) {
            ListIterator<File> iterator = dirsList.listIterator();
            boolean found = false;
            while (iterator.hasNext()) {
                File dir = iterator.next();
                if (dir.getName().equals(dirName)) {
                    found = true;
                    result.add(dir);
                    iterator.remove();
                    break;
                }
            }
            if (!found) {
                throw new NLBConsistencyException(
                        "Inconsistent NLB structure: cannot locate directory with name = "
                                + dirName
                                + " for item with id = "
                                + m_id
                );
            }
        }
        if (!dirsList.isEmpty()) {
            throw new NLBConsistencyException(
                    "Inconsistent NLB structure: directories with names = "
                            + orderList.toString()
                            + " for item with id = "
                            + m_id
                            + " cannot be located in the order file"
            );
        }
        return result;
    }
}
