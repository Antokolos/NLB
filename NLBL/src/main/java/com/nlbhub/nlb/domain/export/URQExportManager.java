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
import com.nlbhub.nlb.api.Theme;
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
    protected String generatePreambleText(NLBBuildingBlocks nlbBuildingBlocks) {
        return "; Generated with Non-Linear Book Builder, http://nlbhub.com" + LINE_SEPARATOR;
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
        stringBuilder.append(pageBlocks.getPageCaption());
        stringBuilder.append(pageBlocks.getPageImage());
        stringBuilder.append(pageBlocks.getPageTextStart());
        stringBuilder.append(pageBlocks.getPageTextEnd());
        stringBuilder.append(pageBlocks.getPageModifications());
        stringBuilder.append(pageBlocks.getPageVariable());
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (!StringHelper.isEmpty(linkBlocks.getLinkConstraint())) {
                stringBuilder.append("if ").append(linkBlocks.getLinkConstraint()).append(" then ");
            }
            stringBuilder.append(linkBlocks.getLinkStart());
        }
        stringBuilder.append(pageBlocks.getPageEnd());
        return stringBuilder.toString();
    }

    @Override
    protected String generatePostPageText(PageBuildingBlocks pageBlocks) {
        StringBuilder postPage = new StringBuilder();
        for (final LinkBuildingBlocks linkBlocks : pageBlocks.getLinksBuildingBlocks()) {
            postPage.append(linkBlocks.getLinkComment());
            postPage.append(linkBlocks.getLinkLabel());
            postPage.append(linkBlocks.getLinkModifications());
            postPage.append(linkBlocks.getLinkVariable());
            postPage.append(linkBlocks.getLinkVisitStateVariable());
            postPage.append(linkBlocks.getLinkGoTo());
        }
        return postPage.toString();
    }

    @Override
    protected String generateTrailingText() {
        return "";
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, int pageNumber, Theme theme) {
        return "btn " + linkId + ", " + linkText + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkSource,
            int sourcePageNumber,
            String linkTarget,
            int targetPageNumber,
            Theme theme) {
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
        return "not ";
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

    @Override
    protected String decorateExistence(final String decoratedVariable) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
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
     * @param isFinish
     */
    @Override
    protected String decoratePageEnd(boolean isFinish) {
        return "end" + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
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

    @Override
    protected String decorateLinkVisitStateVariable(String linkVisitStateVariable) {
        return "    " + linkVisitStateVariable + "=1" + LINE_SEPARATOR;
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
    protected String decoratePageTimerVariableInit(final String variableName) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageTimerVariable(final String variableName) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decoratePageThemeModifications(String modificationsText) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decoratePageCaption(String caption, boolean useCaption, String moduleTitle, boolean noSave) {
        if (StringHelper.notEmpty(caption) && useCaption) {
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
    protected String decoratePageNotes(String notes) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, final boolean imageBackground, Theme theme) {
        // TODO: support image constraints
        ImagePathData pageImagePathData = pageImagePathDatas.get(0);
        if (pageImagePathData.getMaxFrameNumber() == 0) {
            String pageImagePath = pageImagePathData.getImagePath();
            if (!StringHelper.isEmpty(pageImagePath)) {
                return "image " + pageImagePath + LINE_SEPARATOR;
            } else {
                return Constants.EMPTY_STRING;
            }
        } else {
            // TODO: support animated images
            return Constants.EMPTY_STRING;
        }
    }

    @Override
    protected String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX, Theme theme) {
        return Constants.EMPTY_STRING;
    }

    protected String expandVariables(List<TextChunk> textChunks, Theme theme) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append("pln ").append(textChunk.getText());
                    break;
                case ACTION_TEXT:
                case VARIABLE:
                    result.append("$").append(textChunk.getText()).append("$");
                    break;
                case NEWLINE:
                    result.append(getLineSeparator());
                    break;
            }
        }
        return result.toString();
    }

    @Override
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTextEnd(String labelText, int pageNumber, Theme theme, boolean hasChoicesOrLeaf) {
        return LINE_SEPARATOR;
    }

    /**
     * @param labelText
     * @param pageNumber
     * @param theme
     * @return
     */
    @Override
    protected String decoratePageLabel(String labelText, int pageNumber, Theme theme) {
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

    @Override
    protected String decorateTag(final String variable, final String objId, final String tag) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateGetTagOperation(String resultingVariable, String objId, String objVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWhile(final String constraint) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateIf(final String constraint) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateIfHave(String objId, String objVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateElse() {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateElseIf(final String constraint) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateEnd() {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateReturn(String returnValue) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateHaveOperation(String variableName, String objId, String objVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCloneOperation(final String variableName, final String objId, final String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateContainerOperation(String variableName, String objId, String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateGetIdOperation(final String variableName, final String objId, final String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateDelObj(String destinationId, final String destinationName, String objectId, String objectVar, String objectName, String objectDisplayName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return "Inv- " + objectDisplayName + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return "Inv+ " + objectDisplayName + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String destinationListVariableName, String sourceListVariableName, boolean unique) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateObjsOperation(String listVariableName, String srcObjId, String objectVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSSndOperation() {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWSndOperation() {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSndOperation(String objectId, String objectVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSPushOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWPushOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePushOperation(String listVariableName, String objectId, String objectVar) {
        // TODO: implement adding to the arbitrary list
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePopOperation(String variableName, String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSInjectOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateInjectOperation(String listVariableName, String objectId, String objectVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateEjectOperation(String variableName, String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateClearOperation(String destinationId, String destinationVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateClearInvOperation() {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateRndOperation(String variableName, String maxValue) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAchMaxOperation(String achievementName, int max) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAchieveOperation(String achievementName, String modificationId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAchievedOperation(String variableName, String achievementName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateGoToOperation(String locationId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSnapshotOperation(String snapshotId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateOpenURLOperation(String url) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWinGeomOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateInvGeomOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWinColorOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateInvColorOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateShuffleOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePRNOperation(String variableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePDscOperation(String objVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePDscsOperation(String objId, String objVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateActtOperation(String resultVariableName, String actObjVariable, String actObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateActfOperation(String actingObjVariable, String actingObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateUseOperation(String sourceVariable, String sourceId, String targetVariable, String targetId) {
        // TODO: implement
        return EMPTY_STRING;
    }
}
