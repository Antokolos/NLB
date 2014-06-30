/**
 * @(#)DecisionPoint.java
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
package com.nlbhub.user.domain;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.util.StringHelper;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * The DecisionPoint class
 *
 * @author Anton P. Kolosov
 * @version 1.0 11/15/13
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "decision-point")
public class DecisionPoint {
    private String m_bookId;
    private String m_fromPageId;
    private String m_toPageId;
    private String m_linkId;
    private String m_text;
    private Integer m_visitCount;
    private List<DecisionPoint> m_possibleNextDecisionPoints;

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     */
    public DecisionPoint() {
        m_possibleNextDecisionPoints = new ArrayList<>();
        m_visitCount = 1;
    }

    public DecisionPoint(String bookId, String fromPageId, String linkId) {
        this();
        m_bookId = bookId;
        m_fromPageId = fromPageId;
        m_toPageId = Constants.EMPTY_STRING;
        m_linkId = linkId;
    }

    public DecisionPoint(String bookId, String toPageId) {
        this();
        m_bookId = bookId;
        m_fromPageId = Constants.EMPTY_STRING;
        m_toPageId = toPageId;
        m_linkId = Constants.EMPTY_STRING;
    }


    @XmlElement(name = "bookId")
    public String getBookId() {
        return m_bookId;
    }

    public void setBookId(String bookId) {
        m_bookId = bookId;
    }

    @XmlElement(name = "fromPageId")
    public String getFromPageId() {
        return m_fromPageId;
    }

    public void setFromPageId(String fromPageId) {
        m_fromPageId = fromPageId;
    }

    @XmlElement(name = "toPageId")
    public String getToPageId() {
        return m_toPageId;
    }

    public void setToPageId(String toPageId) {
        m_toPageId = toPageId;
    }

    @XmlElement(name = "linkId")
    public String getLinkId() {
        return m_linkId;
    }

    public void setLinkId(String linkId) {
        m_linkId = linkId;
    }

    public void addPossibleNextDecisionPoint(final DecisionPoint decisionPoint) {
        m_possibleNextDecisionPoints.add(decisionPoint);
    }

    @XmlElement(name = "text")
    public String getText() {
        return m_text;
    }

    public void setText(String text) {
        m_text = text;
    }

    @XmlElement(name = "visit-count")
    public Integer getVisitCount() {
        return m_visitCount;
    }

    public void setVisitCount(Integer visitCount) {
        m_visitCount = visitCount;
    }

    public void incVisitCount() {
        ++m_visitCount;
    }

    public List<DecisionPoint> getPossibleNextDecisionPoints() {
        return m_possibleNextDecisionPoints;
    }

    @XmlElement(name = "is-link-info")
    public boolean isLinkInfo() {
        return !StringHelper.isEmpty(m_linkId);
    }

    public void clearPossibleNextDecisionPoints() {
        m_possibleNextDecisionPoints.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DecisionPoint that = (DecisionPoint) o;

        if (!m_bookId.equals(that.m_bookId)) return false;
        if (!m_fromPageId.equals(that.m_fromPageId)) return false;
        if (!m_linkId.equals(that.m_linkId)) return false;
        if (!m_toPageId.equals(that.m_toPageId)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = m_bookId.hashCode();
        result = 31 * result + m_fromPageId.hashCode();
        result = 31 * result + m_toPageId.hashCode();
        result = 31 * result + m_linkId.hashCode();
        return result;
    }
}
