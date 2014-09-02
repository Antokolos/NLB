/**
 * @(#)QSPExportManager.java
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
 * The QSPExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/4/13
 */
public class QSPExportManager extends TextExportManager {
    // Windows line separator should be used for QSP text format,
    // not System.getProperty("line.separator"), otherwise txt2gam utility processing fails
    private static final String LINE_SEPARATOR = "\r\n";

    public QSPExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String generatePreambleText() {
        return "! Generated with Non-Linear Book Builder, http://nlbhub.com" + LINE_SEPARATOR;
    }

    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        return EMPTY_STRING;
    }

    @Override
    protected String generatePageText(PageBuildingBlocks pageBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
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
            final boolean hasConstraint = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
            stringBuilder.append(linkBlocks.getLinkComment());
            if (hasConstraint) {
                stringBuilder.append("IF ").append(linkBlocks.getLinkConstraint()).append(":");
                stringBuilder.append(LINE_SEPARATOR);
            }
            stringBuilder.append(linkBlocks.getLinkStart());
            stringBuilder.append(linkBlocks.getLinkVariable());
            stringBuilder.append(linkBlocks.getLinkModifications());
            stringBuilder.append(linkBlocks.getLinkGoTo());
            if (hasConstraint) {
                stringBuilder.append("END").append(LINE_SEPARATOR);
            }
        }
        stringBuilder.append(decoratePageEnd());
        return stringBuilder.toString();
    }

    @Override
    protected String generateTrailingText() {
        return "";
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

    @Override
    protected String decorateNot() {
        return "NO";
    }

    @Override
    protected String decorateOr() {
        return "OR";
    }

    @Override
    protected String decorateAnd() {
        return "AND";
    }

    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return "(" + constraintVar + " = 1)";
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return "\\$" + constraintVar;
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return constraintVar;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText) {
        return "#" + linkId + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return "! Link -- " + comment + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, int pageNumber) {
        return "  ACT '" + linkText + "':" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkTarget,
            int targetPageNumber
    ) {
        return "    GOTO '" + linkTarget + "'" + LINE_SEPARATOR + "  END" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageEnd() {
        return "-" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return "    " + variableName + " = 1" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageVariable(final String variableName) {
        return variableName + " = 1" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return modificationsText;
    }

    protected String decoratePageCaption(String caption) {
        if (!StringHelper.isEmpty(caption)) {
            return (
                    "'" + caption.toUpperCase() + "'" + LINE_SEPARATOR
                            + "'" + StringHelper.createRepeatedString(caption.length(), "-") + "'" + LINE_SEPARATOR
                            + "''" + LINE_SEPARATOR
            );
        } else {
            return "";
        }
    }

    @Override
    protected String decoratePageImage(String pageImagePath) {
        if (StringHelper.isEmpty(pageImagePath)) {
            return Constants.EMPTY_STRING;
        } else {
            // TODO: should set USEHTML on first page and do not touch it afterwards
            return (
                    "USEHTML = 1" + LINE_SEPARATOR + "'<img src=\"" + pageImagePath + "\">'" + LINE_SEPARATOR
            );
        }
    }

    @Override
    protected String decoratePageTextStart(List<TextChunk> pageTextChunks) {
        return "'" + super.decoratePageTextStart(pageTextChunks);
    }

    @Override
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTextEnd() {
        return "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber) {
        return "#" + labelText + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return "! Page -- " + comment + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + " = " + variableValue + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDelObj(String objectId, String objectName, String objectDisplayName) {
        return "DELOBJ '" + objectDisplayName + "'" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddObj(String listName, String objectId, String objectName, String objectDisplayName) {
        if (StringHelper.isEmpty(listName)) {
            return "ADDOBJ '" + objectDisplayName + "'" + LINE_SEPARATOR;
        } else {
            // TODO: implement adding to the arbitrary list
            return Constants.EMPTY_STRING;
        }
    }

    @Override
    protected String decoratePopList(String variableName, String listName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateUseOperation(String sourceVariable, String sourceId, String targetVariable, String targetId) {
        // TODO: implement
        return EMPTY_STRING;
    }
}
