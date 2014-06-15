/**
 * @(#)ASMExportManager.java
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
package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.List;

/**
 * The ASMExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 5/23/14.
 */
public class ASMExportManager extends TextExportManager {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    public ASMExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String generatePreambleText() {
        return "128[::]0[::]0" + LINE_SEPARATOR;
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
        stringBuilder.append(pageBlocks.getPageTextStart());
        stringBuilder.append(pageBlocks.getPageTextEnd());
        stringBuilder.append(pageBlocks.getPageVariable());
        stringBuilder.append(pageBlocks.getPageModifications());
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            final boolean hasConstraint = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
            if (hasConstraint) {
                stringBuilder.append("<<if ").append(linkBlocks.getLinkConstraint()).append(">>").append(LINE_SEPARATOR);
            }
            stringBuilder.append(linkBlocks.getLinkStart());
            if (hasConstraint) {
                stringBuilder.append("<<endif>>").append(LINE_SEPARATOR);
            }
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
    protected String decorateNot() {
        return "not";
    }

    @Override
    protected String decorateOr() {
        return "or";
    }

    @Override
    protected String decorateAnd() {
        return "and";
    }

    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return "(\\$" + constraintVar + " eq 1)";
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return "\\$" + constraintVar;
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return "\\$" + constraintVar;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText) {
        return ":: " + linkId + "[::]" + "10-10-0" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return EMPTY_STRING;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, int pageNumber) {
        return "[[" + linkText + "|" + linkId + "]]" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkTarget,
            int targetPageNumber
    ) {
        return "<<goto '" + linkTarget + "'>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageEnd() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return "<<set $" + variableName + " = 1>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageVariable(final String variableName) {
        return "<<set $" + variableName + " = 1" + ">>" + LINE_SEPARATOR;
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
            return "''" + caption.toUpperCase() + "''" + LINE_SEPARATOR;
        } else {
            return "";
        }
    }

    @Override
    protected String decoratePageTextStart(String pageText) {
        return pageText;
    }

    @Override
    protected String decoratePageTextEnd() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber) {
        if (pageNumber == 1) {
            return (
                    ":: Start[::]0-0-0" + LINE_SEPARATOR
                            + "<<goto '" + labelText + "'>>" + LINE_SEPARATOR + LINE_SEPARATOR
                            + ":: " + labelText + "[::]" + (pageNumber % 10) * 150 + "-" + (pageNumber / 10) * 150 + "-0" + LINE_SEPARATOR
            );
        } else {
            return ":: " + labelText + "[::]" + (pageNumber % 10) * 150 + "-" + (pageNumber / 10) * 150 + "-0" + LINE_SEPARATOR;
        }

    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return "<<set $" + variableName + " = " + variableValue + ">>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDelObj(String objectId, String objectName) {
        // TODO: make use of inventory module
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAddObj(String objectId, String objectName) {
        // TODO: make use of inventory module
        return EMPTY_STRING;
    }
}
