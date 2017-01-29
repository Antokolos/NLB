/**
 * @(#)PageBuildingBlocks.java
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

import java.util.ArrayList;
import java.util.List;

/**
 * The PageBuildingBlocks class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/5/13
 */
public class PageBuildingBlocks {
    private String m_pageName;
    private String m_pageLabel;
    private String m_pageNumber;
    private String m_pageComment;
    private String m_pageCaption;
    private String m_notes;
    private String m_pageImage;
    private String m_pageSound;
    private String m_pageTextStart;
    private String m_pageTextEnd;
    private String m_pageVariable;
    private String m_pageTimerVariable;
    private String m_pageDefaultTag;
    private String m_pageTimerVariableInit;
    private String m_pageModifications;
    private String m_pageEnd;
    private boolean m_autowired;
    private boolean m_hasObjectsWithAnimatedImages;
    private boolean m_hasAnimatedPageImage;
    private boolean m_imageBackground;
    private boolean m_hasPageTimer;
    private boolean m_hasPageText;
    private List<String> m_containedObjIds;
    private List<String> m_containedGraphicalObjIds;
    private List<LinkBuildingBlocks> m_linksBuildingBlocks;
    /** Page blocks' links are considered trivial if each of them is trivial. */
    private boolean m_hasTrivialLinks;
    private Theme m_theme = Theme.DEFAULT;
    private String m_moduleTitle;

    public PageBuildingBlocks() {
        m_containedObjIds = new ArrayList<>();
        m_containedGraphicalObjIds = new ArrayList<>();
        m_linksBuildingBlocks = new ArrayList<>();
    }

    public String getPageName() {
        return m_pageName;
    }

    public void setPageName(String pageName) {
        m_pageName = pageName;
    }

    public String getPageLabel() {
        return m_pageLabel;
    }

    public void setPageLabel(String pageLabel) {
        m_pageLabel = pageLabel;
    }

    public String getPageNumber() {
        return m_pageNumber;
    }

    public void setPageNumber(String pageNumber) {
        m_pageNumber = pageNumber;
    }

    public String getPageComment() {
        return m_pageComment;
    }

    public void setPageComment(String pageComment) {
        m_pageComment = pageComment;
    }

    public String getPageCaption() {
        return m_pageCaption;
    }

    public void setPageCaption(String pageCaption) {
        m_pageCaption = pageCaption;
    }

    public String getNotes() {
        return m_notes;
    }

    public void setNotes(String notes) {
        m_notes = notes;
    }

    public String getPageImage() {
        return m_pageImage;
    }

    public void setPageImage(String pageImage) {
        m_pageImage = pageImage;
    }

    public String getPageSound() {
        return m_pageSound;
    }

    public void setPageSound(String pageSound) {
        m_pageSound = pageSound;
    }

    public String getPageTextStart() {
        return m_pageTextStart;
    }

    public void setPageTextStart(String pageTextStart) {
        m_pageTextStart = pageTextStart;
    }

    public String getPageTextEnd() {
        return m_pageTextEnd;
    }

    public void setPageTextEnd(String pageTextEnd) {
        m_pageTextEnd = pageTextEnd;
    }

    public boolean isAutowired() {
        return m_autowired;
    }

    public void setAutowired(boolean autowired) {
        m_autowired = autowired;
    }

    public List<String> getContainedGraphicalObjIds() {
        return m_containedGraphicalObjIds;
    }

    public void setContainedGraphicalObjIds(List<String> containedGraphicalObjIds) {
        m_containedGraphicalObjIds = containedGraphicalObjIds;
    }

    public void addContainedGraphicalObjId(String containedGraphicalObjId) {
        m_containedGraphicalObjIds.add(containedGraphicalObjId);
    }

    public boolean isHasGraphicalObjects() {
        return !m_containedGraphicalObjIds.isEmpty();
    }

    public boolean isHasObjectsWithAnimatedImages() {
        return m_hasObjectsWithAnimatedImages;
    }

    public void setHasObjectsWithAnimatedImages(final boolean hasObjectsWithAnimatedImages) {
        m_hasObjectsWithAnimatedImages = hasObjectsWithAnimatedImages;
    }

    public boolean isHasAnimatedPageImage() {
        return m_hasAnimatedPageImage;
    }

    public void setHasAnimatedPageImage(boolean hasAnimatedPageImage) {
        m_hasAnimatedPageImage = hasAnimatedPageImage;
    }

    public boolean isImageBackground() {
        return m_imageBackground;
    }

    public void setImageBackground(boolean imageBackground) {
        m_imageBackground = imageBackground;
    }

    public boolean isHasPageTimer() {
        return m_hasPageTimer;
    }

    public void setHasPageTimer(boolean hasPageTimer) {
        m_hasPageTimer = hasPageTimer;
    }

    public boolean isDirectMode() {
        return !isHasPageText();
    }

    public boolean isHasPageText() {
        return m_hasPageText;
    }

    public void setHasPageText(boolean hasPageText) {
        m_hasPageText = hasPageText;
    }

    public String getPageVariable() {
        return m_pageVariable;
    }

    public void setPageVariable(String pageVariable) {
        m_pageVariable = pageVariable;
    }

    public String getPageTimerVariableInit() {
        return m_pageTimerVariableInit;
    }

    public void setPageTimerVariableInit(String pageTimerVariableInit) {
        m_pageTimerVariableInit = pageTimerVariableInit;
    }

    public String getPageTimerVariable() {
        return m_pageTimerVariable;
    }

    public void setPageTimerVariable(String pageTimerVariable) {
        m_pageTimerVariable = pageTimerVariable;
    }

    public String getPageDefaultTag() {
        return m_pageDefaultTag;
    }

    public void setPageDefaultTag(String pageDefaultTag) {
        m_pageDefaultTag = pageDefaultTag;
    }

    public String getPageModifications() {
        return m_pageModifications;
    }

    public void setPageModifications(String pageModifications) {
        m_pageModifications = pageModifications;
    }

    public List<LinkBuildingBlocks> getLinksBuildingBlocks() {
        return m_linksBuildingBlocks;
    }

    public void setLinksBuildingBlocks(List<LinkBuildingBlocks> linksBuildingBlocks) {
        m_linksBuildingBlocks = linksBuildingBlocks;
    }

    public void addLinkBuildingBlocks(final LinkBuildingBlocks linksBuildingBlocks) {
        m_linksBuildingBlocks.add(linksBuildingBlocks);
    }

    public List<String> getContainedObjIds() {
        return m_containedObjIds;
    }

    public void setContainedObjIds(List<String> containedObjIds) {
        m_containedObjIds = containedObjIds;
    }

    public void addContainedObjId(String containedObjId) {
        m_containedObjIds.add(containedObjId);
    }

    public String getPageEnd() {
        return m_pageEnd;
    }

    public void setPageEnd(String pageEnd) {
        m_pageEnd = pageEnd;
    }

    public boolean isHasTrivialLinks() {
        return m_hasTrivialLinks;
    }

    public void setHasTrivialLinks(boolean hasTrivialLinks) {
        m_hasTrivialLinks = hasTrivialLinks;
    }

    public Theme getTheme() {
        return m_theme;
    }

    public void setTheme(Theme theme) {
        m_theme = theme;
    }

    public String getModuleTitle() {
        return m_moduleTitle;
    }

    public void setModuleTitle(String moduleTitle) {
        m_moduleTitle = moduleTitle;
    }
}
