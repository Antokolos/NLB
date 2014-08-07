/**
 * @(#)RootModulePage.java
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

import com.nlbhub.nlb.util.MultiLangString;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The RootModulePage class represents fake module page for entire NLB
 *
 * @author Anton P. Kolosov
 * @version 1.0 2/21/14
 */
public class RootModulePage implements Page {
    private NonLinearBook m_nlb;
    private String m_pageId;

    public RootModulePage(NonLinearBook nlb, String pageId) {
        m_nlb = nlb;
        m_pageId = pageId;
    }

    @Override
    public String getImageFileName() {
        return DEFAULT_IMAGE_FILE_NAME;
    }

    @Override
    public String getText() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public MultiLangString getTexts() {
        return MultiLangString.createCopy(DEFAULT_TEXT);
    }

    @Override
    public String getVarId() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public String getCaption() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public MultiLangString getCaptions() {
        return MultiLangString.createCopy(DEFAULT_CAPTION);
    }

    @Override
    public boolean isUseCaption() {
        return false;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public String getTraverseText() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public MultiLangString getTraverseTexts() {
        return MultiLangString.createEmptyText();
    }

    @Override
    public boolean isAutoTraverse() {
        return true;
    }

    @Override
    public boolean isAutoReturn() {
        return false;
    }

    @Override
    public String getReturnText() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public MultiLangString getReturnTexts() {
        return MultiLangString.createCopy(DEFAULT_RETURN_TEXT);
    }

    @Override
    public String getReturnPageId() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public boolean shouldReturn() {
        return false;
    }

    @Override
    public String getModuleConstrId() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public String getModuleName() {
        return Constants.MAIN_MODULE_NAME;
    }

    @Override
    public NonLinearBook getModule() {
        return m_nlb;
    }

    @Override
    public boolean isAutowire() {
        return false;
    }

    @Override
    public String getAutowireInText() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public MultiLangString getAutowireInTexts() {
        return MultiLangString.createCopy(DEFAULT_AUTOWIRE_IN_TEXT);
    }

    @Override
    public String getAutowireOutText() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public MultiLangString getAutowireOutTexts() {
        return MultiLangString.createCopy(DEFAULT_AUTOWIRE_OUT_TEXT);
    }

    @Override
    public boolean isAutoIn() {
        return DEFAULT_AUTO_IN;
    }

    @Override
    public boolean isAutoOut() {
        return DEFAULT_AUTO_OUT;
    }

    @Override
    public String getAutowireInConstrId() {
        return DEFAULT_AUTOWIRE_IN_CONSTR_ID;
    }

    @Override
    public String getAutowireOutConstrId() {
        return DEFAULT_AUTOWIRE_OUT_CONSTR_ID;
    }

    @Override
    public String getStroke() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public String getFill() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public String getTextColor() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public List<String> getContainedObjIds() {
        return new ArrayList<>();
    }

    @Override
    public Coords getCoords() {
        return new CoordsLw();
    }

    @Override
    public List<Link> getLinks() {
        return new ArrayList<>();
    }

    @Override
    public Link getLinkById(@NotNull String linkId) {
        return null;
    }

    @Override
    public List<Modification> getModifications() {
        return new ArrayList<>();
    }

    @Override
    public Modification getModificationById(@NotNull String modId) {
        return null;
    }

    @Override
    public String getId() {
        return m_pageId;
    }

    @Override
    public String getFullId() {
        return m_pageId;
    }

    @Override
    public boolean isDeleted() {
        return false;
    }

    @Override
    public IdentifiableItem getParent() {
        return null;
    }

    @Override
    public NonLinearBook getCurrentNLB() {
        return m_nlb;
    }

    @Override
    public String addObserver(NLBObserver observer) {
        return Constants.EMPTY_STRING;
    }

    @Override
    public void removeObserver(String observerId) {
    }

    @Override
    public void notifyObservers() {
    }
}
