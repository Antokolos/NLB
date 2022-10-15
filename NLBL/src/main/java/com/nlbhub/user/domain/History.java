/**
 * @(#)History.java
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

import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.exception.DecisionException;

import jakarta.xml.bind.annotation.*;
import java.util.LinkedList;
import java.util.List;

/**
 * The History class
 *
 * @author Anton P. Kolosov
 * @version 1.0 11/15/13
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "history")
public class History {
    public static final int DO_NOT_USE_VISIT_COUNT = -1;
    private List<DecisionPoint> m_decisionPoints = new LinkedList<DecisionPoint>();
    private DecisionPoint m_decisionPointToBeMade = null;
    private String m_decisionPointToBeMadeText;

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     */
    public History() {
    }

    public void clear() {
        m_decisionPoints.clear();
        m_decisionPointToBeMade = null;
    }

    @XmlElement(name = "decision-point")
    public List<DecisionPoint> getDecisionPoints() {
        return m_decisionPoints;
    }

    public void setDecisionPoints(List<DecisionPoint> decisionPoints) {
        m_decisionPoints = decisionPoints;
    }

    public void setDecisionPointToBeMadeText(String decisionPointToBeMadeText) {
        m_decisionPointToBeMadeText = decisionPointToBeMadeText;
    }

    public void makeDecision() {
        if (m_decisionPointToBeMade != null) {
            m_decisionPointToBeMade.setText(m_decisionPointToBeMadeText);
            for (final DecisionPoint decisionPoint : m_decisionPoints) {
                if (decisionPoint.equals(m_decisionPointToBeMade)) {
                    m_decisionPointToBeMade.incVisitCount();
                }
            }
            m_decisionPoints.add(m_decisionPointToBeMade);
            m_decisionPointToBeMade = null;
        }
    }

    public int predictDecisionCount(final DecisionPoint decisionPoint) {
        int count = 1;
        for (final DecisionPoint currentDecisionPoint : m_decisionPoints) {
            if (currentDecisionPoint.equals(decisionPoint)) {
                count++;
            }
        }
        return count;
    }

    public DecisionPoint getDecisionPointToBeMade() {
        return m_decisionPointToBeMade;
    }

    /**
     * set visitCount to DO_NOT_USE_VISIT_COUNT if you are not doing rollback to the specified
     * visit.
     *
     * @param decisionPointToBeMade
     * @param rollback
     * @param visitCount
     * @throws DecisionException
     */
    public void suggestDecisionPointToBeMade(
            DecisionPoint decisionPointToBeMade,
            boolean rollback,
            int visitCount
    ) throws DecisionException {
        if (decisionPointToBeMade != null && !m_decisionPoints.isEmpty()) {
            boolean suggestedOK = false;
            if (!rollback) {
                DecisionPoint tailDecisionPoint = m_decisionPoints.get(m_decisionPoints.size() - 1);
                for (DecisionPoint decisionPoint : tailDecisionPoint.getPossibleNextDecisionPoints()) {
                    if (decisionPoint.equals(decisionPointToBeMade)) {
                        suggestedOK = true;
                    }
                }
            }
            if (!suggestedOK || rollback) {
                // Suggested decision is not possible for current decision list
                // Try to search through the list of existing decisions to locate the last
                // occurence of suggested decision in the past, if any
                // OR locate the decision with specified visit count in the past
                for (int i = m_decisionPoints.size() - 1; i >= 0; i--) {
                    final DecisionPoint curDecisionPoint = m_decisionPoints.get(i);
                    if (
                            curDecisionPoint.equals(decisionPointToBeMade)
                                    && (
                                    visitCount == DO_NOT_USE_VISIT_COUNT
                                            || visitCount == curDecisionPoint.getVisitCount()
                            )
                            ) {
                        m_decisionPointToBeMade = curDecisionPoint;
                        // Possible next decisions will be recreated again
                        m_decisionPointToBeMade.clearPossibleNextDecisionPoints();
                        // Visit count will be incremented in the makeDecision() call
                        m_decisionPointToBeMade.setVisitCount(1);
                        // Throw away unmatched decision tail
                        m_decisionPoints = m_decisionPoints.subList(0, i);
                        return;
                    }
                }
                throw new DecisionException(
                        "Suggested decision cannot be found, please specify correct decision or restart"
                );
            }
        }
        m_decisionPointToBeMade = decisionPointToBeMade;
    }

    public boolean containsLink(Link link) {
        for (DecisionPoint decisionPoint : m_decisionPoints) {
            if (link.getId().equals(decisionPoint.getLinkId())) {
                return true;
            }
        }
        return false;
    }
}
