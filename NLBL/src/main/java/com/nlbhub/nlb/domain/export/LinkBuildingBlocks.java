/**
 * @(#)LinkBuildingBlocks.java
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
package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.api.Theme;

/**
 * The LinkBuildingBlocks class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/5/13
 */
public class LinkBuildingBlocks {
    private String m_linkLabel;
    private String m_linkText;
    private String m_linkAltText;
    private String m_linkComment;
    private String m_linkStart;
    private String m_linkVariable;
    private String m_linkVisitStateVariable;
    private String m_linkConstraint;
    private String m_linkModifications;
    private String m_linkGoTo;
    private int m_targetPageNumber;
    private String m_linkEnd;
    private boolean m_auto;
    private boolean m_needsAction;
    /** Link is considered trivial if it has default text and has no constraints. */
    private boolean m_isTrivial;
    private boolean m_inline;
    private Theme m_theme = Theme.DEFAULT;

    public LinkBuildingBlocks() {
    }

    public boolean isTrivial() {
        return m_isTrivial;
    }

    public void setTrivial(boolean trivial) {
        m_isTrivial = trivial;
    }

    public boolean isInline() {
        return m_inline;
    }

    public void setInline(boolean inline) {
        m_inline = inline;
    }

    public String getLinkLabel() {
        return m_linkLabel;
    }

    public void setLinkLabel(String linkLabel) {
        m_linkLabel = linkLabel;
    }

    public String getLinkText() {
        return m_linkText;
    }

    public void setLinkText(String linkText) {
        m_linkText = linkText;
    }

    public String getLinkAltText() {
        return m_linkAltText;
    }

    public void setLinkAltText(String linkAltText) {
        m_linkAltText = linkAltText;
    }

    public String getLinkComment() {
        return m_linkComment;
    }

    public void setLinkComment(String linkComment) {
        m_linkComment = linkComment;
    }

    public String getLinkStart() {
        return m_linkStart;
    }

    public void setLinkStart(String linkStart) {
        m_linkStart = linkStart;
    }

    public String getLinkVariable() {
        return m_linkVariable;
    }

    public void setLinkVariable(String linkVariable) {
        m_linkVariable = linkVariable;
    }

    public String getLinkVisitStateVariable() {
        return m_linkVisitStateVariable;
    }

    public void setLinkVisitStateVariable(String linkVisitStateVariable) {
        m_linkVisitStateVariable = linkVisitStateVariable;
    }

    public String getLinkConstraint() {
        return m_linkConstraint;
    }

    public void setLinkConstraint(String linkConstraint) {
        m_linkConstraint = linkConstraint;
    }

    public String getLinkModifications() {
        return m_linkModifications;
    }

    public void setLinkModifications(String linkModifications) {
        m_linkModifications = linkModifications;
    }

    public int getTargetPageNumber() {
        return m_targetPageNumber;
    }

    public void setTargetPageNumber(int targetPageNumber) {
        m_targetPageNumber = targetPageNumber;
    }

    public String getLinkGoTo() {
        return m_linkGoTo;
    }

    public void setLinkGoTo(String linkGoTo) {
        m_linkGoTo = linkGoTo;
    }

    public String getLinkEnd() {
        return m_linkEnd;
    }

    public void setLinkEnd(String linkEnd) {
        m_linkEnd = linkEnd;
    }

    public boolean isAuto() {
        return m_auto;
    }

    public void setAuto(boolean auto) {
        m_auto = auto;
    }

    public boolean isNeedsAction() {
        return m_needsAction;
    }

    public void setNeedsAction(boolean needsAction) {
        m_needsAction = needsAction;
    }

    public Theme getTheme() {
        return m_theme;
    }

    public void setTheme(Theme theme) {
        m_theme = theme;
    }
}
