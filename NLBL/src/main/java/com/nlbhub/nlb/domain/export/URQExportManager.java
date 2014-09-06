/**
 * @(#)URQExportManager.java
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

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.List;

/**
 * The URQExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/4/13
 */
public class URQExportManager extends TextExportManager {
    // Windows line separator should be used for URQ format
    private static final String LINE_SEPARATOR = "\r\n";

    public URQExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String generatePreambleText() {
        return "; Generated with Non-Linear Book Builder, http://nlbhub.com" + LINE_SEPARATOR;
    }

    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        return EMPTY_STRING;
    }

    @Override
    protected String generatePageText(PageBuildingBlocks pageBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        StringBuilder postPage = new StringBuilder();
        stringBuilder.append(pageBlocks.getPageComment());
        stringBuilder.append(pageBlocks.getPageLabel());
        if (pageBlocks.isUseCaption()) {
            stringBuilder.append(pageBlocks.getPageCaption());
        }
        stringBuilder.append(pageBlocks.getPageImage());
        stringBuilder.append(pageBlocks.getPageTextStart());
        stringBuilder.append(pageBlocks.getPageTextEnd());
        stringBuilder.append(pageBlocks.getPageVariable());
        stringBuilder.append(pageBlocks.getPageModifications());
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (!StringHelper.isEmpty(linkBlocks.getLinkConstraint())) {
                stringBuilder.append("if ").append(linkBlocks.getLinkConstraint()).append(" then ");
            }
            stringBuilder.append(linkBlocks.getLinkStart());
            postPage.append(linkBlocks.getLinkComment());
            postPage.append(linkBlocks.getLinkLabel());
            postPage.append(linkBlocks.getLinkVariable());
            postPage.append(linkBlocks.getLinkModifications());
            postPage.append(linkBlocks.getLinkGoTo());
        }
        stringBuilder.append(decoratePageEnd());
        stringBuilder.append(postPage.toString());
        return stringBuilder.toString();
    }

    @Override
    protected String generateTrailingText() {
        return "";
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, int pageNumber) {
        return "btn " + linkId + ", " + linkText + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkTarget,
            int targetPageNumber
    ) {
        return "goto " + linkTarget + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String decorateTrue() {
        return "1";
    }

    @Override
    protected String decorateFalse() {
        return "0";
    }

    @Override
    protected String decorateEq() {
        return "=";
    }

    @Override
    protected String decorateNEq() {
        return "<>";
    }

    @Override
    protected String decorateGt() {
        return ">";
    }

    @Override
    protected String decorateGte() {
        return ">=";
    }

    @Override
    protected String decorateLt() {
        return "<";
    }

    @Override
    protected String decorateLte() {
        return "<=";
    }

    /**
     * @return
     */
    @Override
    protected String decorateNot() {
        return "not";
    }

    /**
     * @return
     */
    @Override
    protected String decorateOr() {
        return "or";
    }

    /**
     * @return
     */
    @Override
    protected String decorateAnd() {
        return "and";
    }

    /**
     * @param constraintVar
     * @return
     */
    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return "(" + constraintVar + ">0)";//"(" + constraintVar + " == 1)";
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return constraintVar;
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return constraintVar;
    }

    /**
     * @return
     */
    @Override
    protected String decoratePageEnd() {
        return "end" + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText) {
        return ":" + linkId + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return "; Link -- " + comment + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @return
     */
    @Override
    protected String decorateLinkVariable(String variableName) {
        return "    " + variableName + "=1" + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @return
     */
    @Override
    protected String decoratePageVariable(final String variableName) {
        return variableName + "=1" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decoratePageCaption(String caption) {
        if (!StringHelper.isEmpty(caption)) {
            return (
                    "pln " + caption.toUpperCase() + LINE_SEPARATOR
                            + "pln " + StringHelper.createRepeatedString(caption.length(), "-") + LINE_SEPARATOR
                            + "pln" + LINE_SEPARATOR
            );
        } else {
            return "";
        }
    }

    @Override
    protected String decoratePageImage(String pageImagePath) {
        if (!StringHelper.isEmpty(pageImagePath)) {
            return "image " + pageImagePath + LINE_SEPARATOR;
        } else {
            return Constants.EMPTY_STRING;
        }
    }

    protected String decoratePageTextStart(List<TextChunk> pageTextChunks) {
        StringBuilder pageText = new StringBuilder();
        for (final TextChunk textChunk : pageTextChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    pageText.append("pln ").append(textChunk.getText());
                    break;
                case VARIABLE:
                    pageText.append("$").append(textChunk.getText()).append("$");
                    break;
                case NEWLINE:
                    pageText.append(getLineSeparator());
                    break;
            }
        }
        return pageText.toString();
    }

    @Override
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTextEnd() {
        return LINE_SEPARATOR;
    }

    /**
     * @param labelText
     * @param pageNumber
     * @return
     */
    @Override
    protected String decoratePageLabel(String labelText, int pageNumber) {
        return ":" + labelText + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return "; Page -- " + comment + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @param variableValue
     * @return
     */
    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + "=" + variableValue + LINE_SEPARATOR;
    }

    /**
     *
     * @param destinationId
     * @param objectId
     * @param objectVar
     *@param objectName
     * @param objectDisplayName   @return
     */
    @Override
    protected String decorateDelObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName) {
        return "Inv- " + objectDisplayName + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectName, String objectDisplayName) {
        return "Inv+ " + objectDisplayName + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String listName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePushOperation(String listName, String objectId, String objectVar) {
        // TODO: implement adding to the arbitrary list
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePopOperation(String variableName, String listName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateShuffleOperation(String listName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateUseOperation(String sourceVariable, String sourceId, String targetVariable, String targetId) {
        // TODO: implement
        return EMPTY_STRING;
    }
}
