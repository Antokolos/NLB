/**
 * @(#)HypertextExportManager.java
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
package com.nlbhub.nlb.domain.export.hypertext;

import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.export.ExportManager;
import com.nlbhub.nlb.domain.export.LinkBuildingBlocks;
import com.nlbhub.nlb.domain.export.NLBBuildingBlocks;
import com.nlbhub.nlb.domain.export.PageBuildingBlocks;
import com.nlbhub.nlb.domain.export.hypertext.document.HTAnchor;
import com.nlbhub.nlb.domain.export.hypertext.document.HTDocument;
import com.nlbhub.nlb.domain.export.hypertext.document.HTFont;
import com.nlbhub.nlb.domain.export.hypertext.document.HTParagraph;
import com.nlbhub.nlb.exception.HTDocumentException;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.io.File;
import java.io.IOException;

/**
 * The HypertextExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/6/13
 */
public abstract class HypertextExportManager
        <P extends HTParagraph<F, A>, A extends HTAnchor<F>, F extends HTFont> extends ExportManager {

    public HypertextExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    public void exportToFile(File targetFile) throws NLBExportException {
        try {
            NLBBuildingBlocks nlbBlocks = createNLBBuildingBlocks();

            HTDocument<P> document = createDocument(getEncoding(), getLineSeparator());
            document.initWriter(targetFile);
            document.open();

            generateHypertext(
                    document,
                    createParaFont(),
                    createLinkFont(),
                    nlbBlocks
            );

            document.close();
        } catch (
                HTDocumentException | NLBConsistencyException | IOException e
                ) {
            throw new NLBExportException("Error while converting NLB to Hypertext", e);
        }
    }

    protected abstract HTDocument<P> createDocument(String encoding, String lineSeparator);

    protected abstract F createParaFont() throws HTDocumentException;

    protected abstract F createLinkFont() throws HTDocumentException;

    protected abstract P createHTParagraph(
            final String text,
            final F font
    ) throws HTDocumentException;

    protected abstract A createHTAnchor(
            boolean decapitalize,
            final String text,
            final F font
    ) throws HTDocumentException;


    private void generateHypertext(
            HTDocument<P> document,
            F paraFont,
            F linkFont,
            final NLBBuildingBlocks nlbBlocks
    ) throws IOException, HTDocumentException {
        final java.util.List<PageBuildingBlocks> pagesBlocks = nlbBlocks.getPagesBuildingBlocks();
        for (int i = 0; i < pagesBlocks.size(); i++) {
            document.add(createHTParagraph(paraFont, linkFont, pagesBlocks.get(i)));
        }
    }

    protected P createHTParagraph(
            F paraFont,
            F linkFont,
            PageBuildingBlocks pageBlocks
    ) throws IOException, HTDocumentException {
        P page = (
                createHTParagraph(
                        getLineSeparator(),
                        paraFont
                )
        );
        A pageAnchor = createHTAnchor(false, pageBlocks.getPageNumber(), paraFont);
        pageAnchor.setName(pageBlocks.getPageLabel());
        page.add(pageAnchor);
        page.add(getLineSeparator());
        if (pageBlocks.isUseCaption()) {
            page.add(pageBlocks.getPageCaption());
        }
        page.add(pageBlocks.getPageImage());
        page.add(pageBlocks.getPageTextStart());
        page.add(pageBlocks.getPageTextEnd());
        page.add(pageBlocks.getPageVariable());
        page.add(pageBlocks.getPageModifications());
        java.util.List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        boolean first = true;
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (first) {
                first = false;
            } else {
                page.add(getLineSeparator());
            }
            final boolean hasConstraint = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
            //stringBuilder.append(linkBlocks.getLinkComment());
            if (hasConstraint) {
                page.add("Если ");
                page.add(linkBlocks.getLinkConstraint());
                page.add(", то вы можете ");
            }

            A linkAnchor = createHTAnchor(hasConstraint, linkBlocks.getLinkStart(), linkFont);
            linkAnchor.setReference("#" + linkBlocks.getLinkGoTo());
            page.add(linkAnchor);
            page.add(linkBlocks.getLinkVariable());
            page.add(linkBlocks.getLinkModifications());
        }
        return page;
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + "=" + variableValue;
    }

    @Override
    protected String decorateDelObj(String objectId, String objectName, String objectDisplayName) {
        return "Вычеркните из инвентаря " + objectDisplayName;
    }

    @Override
    protected String decorateAddObj(String objectId, String objectName, String objectDisplayName) {
        return (
                "Положите в инвентарь " + objectDisplayName
        );
    }

    @Override
    protected String decorateAddToList(String listName, String objectId, String objectVar) {
        return (
                "Положите в " + listName + " " + objectVar
        );
    }

    @Override
    protected String decoratePopList(String variableName, String listName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listName) {
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

    @Override
    protected String decorateTrue() {
        return "ИСТИНА";
    }

    @Override
    protected String decorateFalse() {
        return "ЛОЖЬ";
    }

    @Override
    protected String decorateEq() {
        return "РАВНО";
    }

    @Override
    protected String decorateNEq() {
        return "НЕ РАВНО";
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
        return "НЕ ";
    }

    @Override
    protected String decorateOr() {
        return "ИЛИ";
    }

    @Override
    protected String decorateAnd() {
        return "И";
    }

    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return constraintVar;
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return constraintVar;
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return constraintVar;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText) {
        return linkId;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return comment;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, int pageNumber) {
        return linkText + " (" + String.valueOf(pageNumber) + ")";
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkTarget,
            int targetPageNumber
    ) {
        return linkTarget;
    }

    @Override
    protected String decoratePageEnd() {
        return "page end";
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        if (StringHelper.isEmpty(variableName)) {
            return "";
        } else {
            return getLineSeparator() + "    " + "Запишите ключевое слово: " + variableName;
        }
    }

    @Override
    protected String decoratePageVariable(String variableName) {
        if (StringHelper.isEmpty(variableName)) {
            return "";
        } else {
            return getLineSeparator() + "Запишите ключевое слово: " + variableName + getLineSeparator();
        }
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        if (StringHelper.isEmpty(modificationsText)) {
            return "";
        } else {
            return (
                    getLineSeparator() + "Сделайте следующие действия: "
                            + getLineSeparator() + modificationsText + getLineSeparator()
            );
        }
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        if (StringHelper.isEmpty(modificationsText)) {
            return "";
        } else {
            return (
                    getLineSeparator() + "    "
                            + "Сделайте следующие действия: "
                            + getLineSeparator() + modificationsText
            );
        }
    }

    @Override
    protected String decoratePageCaption(String caption) {
        return caption + getLineSeparator();
    }

    @Override
    protected String decoratePageTextEnd() {
        return "";
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber) {
        return labelText;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber) + ".";
    }

    @Override
    protected String decoratePageComment(String comment) {
        return comment;
    }
}
