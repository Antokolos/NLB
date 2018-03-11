/**
 * @(#)VNSTEADExportManager.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2015 Anton P. Kolosov
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
 * Copyright (c) 2015 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The VNSTEADExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 03/25/15
 */
public class VNSTEADExportManager extends STEADExportManager {
    private static final Logger LOG = Logger.getLogger(VNSTEADExportManager.class.getName());
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("((?:^|[^\\.\\?!]+)(?:[\\.\\?!]+(?:\\\\\")?|$))");
    private static final int PARAGRAPH_THRESHOLD = 100;
    private static final int PARAGRAPH_THRESHOLD_WARN = 330;
    private boolean m_technicalInstance = false;

    public VNSTEADExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    /**
     * This one should be used only from STEADExportManager
     * @param nlb
     * @param encoding
     * @param technicalInstance
     * @throws NLBExportException
     */
    protected VNSTEADExportManager(NonLinearBookImpl nlb, String encoding, boolean technicalInstance) throws NLBExportException {
        super(nlb, encoding, technicalInstance);
        m_technicalInstance = technicalInstance;
    }

    protected boolean isVN(Theme theme) {
        return m_technicalInstance || (theme != Theme.STANDARD);
    }

    @Override
    protected String getDefaultThemeSwitchExpression() {
        return "        return 'theme_vn.lua';" + getLineSeparator();
    }

    @Override
    protected String decoratePageTextStart(String labelText, int pageNumber, List<TextChunk> pageTextChunks, Theme theme) {
        if (!isVN(theme)) {
            return super.decoratePageTextStart(labelText, pageNumber, pageTextChunks, theme);
        }
        String lineSep = getLineSeparator();
        StringBuilder pageText = new StringBuilder();
        pageText.append("    dsc = function(s)").append(lineSep);
        if (pageTextChunks.size() > 0) {
            pageText.append("pn(\"");
            pageText.append(expandVariables(pageTextChunks, theme));
            pageText.append("\");").append(lineSep);
            pageText.append("pn();").append(lineSep);
        }
        return pageText.toString();
    }

    @Override
    protected String decoratePageTextEnd(String labelText, int pageNumber, Theme theme, boolean hasChoicesOrLeaf) {
        if (!isVN(theme)) {
            return super.decoratePageTextEnd(labelText, pageNumber, theme, hasChoicesOrLeaf);
        }
        String lineSep = getLineSeparator();
        StringBuilder pageText = new StringBuilder();
        pageText.append("    end,").append(lineSep);
        if (hasChoicesOrLeaf) {
            pageText.append("    walk_to = \"").append(decoratePageName(labelText, pageNumber)).append("_choices\",").append(lineSep);
        }
        return pageText.toString();
    }

    protected boolean isDirectMode(PageBuildingBlocks pageBlocks) {
        return pageBlocks.isDirectMode() && pageBlocks.getTheme() != Theme.STANDARD;
    }

    protected String generateDirectModeStartText(PageBuildingBlocks pageBlocks) {
        String lineSep = getLineSeparator();
        StringBuilder stringBuilder = new StringBuilder();
        if (isDirectMode(pageBlocks)) {
            stringBuilder.append("        vn:request_full_clear();").append(lineSep);
            stringBuilder.append("        vn:lock_direct();").append(lineSep);
        }
        return stringBuilder.toString();
    }

    protected String generateDirectModeStopText(PageBuildingBlocks pageBlocks) {
        String lineSep = getLineSeparator();
        StringBuilder stringBuilder = new StringBuilder();
        if (isDirectMode(pageBlocks)) {
            stringBuilder.append("        vn:request_full_clear();").append(lineSep);
            stringBuilder.append("        vn:unlock_direct();").append(lineSep);
        }
        return stringBuilder.toString();
    }

    @Override
    protected String generateOrdinaryLinkTextInsideRoom(PageBuildingBlocks pageBuildingBlocks) {
        if (!isVN(pageBuildingBlocks.getTheme())) {
            return super.generateOrdinaryLinkTextInsideRoom(pageBuildingBlocks);
        }
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String generatePostPageText(PageBuildingBlocks pageBlocks) {
        if (!isVN(pageBlocks.getTheme())) {
            return super.generatePostPageText(pageBlocks);
        }
        List<LinkBuildingBlocks> linksBuildingBlocks = pageBlocks.getLinksBuildingBlocks();
        final boolean theEnd = linksBuildingBlocks.isEmpty();
        if (!hasChoicesOrLeaf(pageBlocks)) {
            return Constants.EMPTY_STRING;
        }
        StringBuilder result = new StringBuilder();
        StringBuilder linksBuilder = new StringBuilder();
        String lineSep = getLineSeparator();
        String roomName = pageBlocks.getPageName();
        result.append(roomName).append("_choices").append(" = vn_choices {").append(lineSep);
        result.append("    nam = \"").append(getNonEmptyTitle(pageBlocks.getModuleTitle())).append("\",").append(lineSep);
        result.append("    disp = true,").append(lineSep);
        result.append("    textbg = true,").append(lineSep);
        result.append("    ignore_preserved_gobjs = true,").append(lineSep);
        if (theEnd) {
            result.append("    dsc = true,").append(lineSep);
        }
        result.append("    var { paginator_state = false; },").append(lineSep);
        result.append("    enter = function(s) ").append(lineSep);
        result.append("        s.paginator_state = paginator.on;").append(lineSep);
        result.append("        objs():zap();").append(lineSep);
        result.append("        paginator:turnoff();").append(lineSep);
        if (pageBlocks.isHasTrivialLinks()) {
            for (LinkBuildingBlocks linkBlock : linksBuildingBlocks) {
                if (!linkBlock.isAuto()) {
                    final boolean constrained = !StringHelper.isEmpty(linkBlock.getLinkConstraint());
                    result.append("if ").append(constrained ? linkBlock.getLinkConstraint() : "true").append(" then").append(lineSep);
                    result.append(linkBlock.getLinkModifications());
                    result.append(linkBlock.getLinkVariable());
                    result.append(linkBlock.getLinkVisitStateVariable());
                    result.append(linkBlock.getLinkGoTo());
                    result.append("    return;").append(lineSep);
                    result.append("end;").append(lineSep);
                }
            }
        } else {
            for (LinkBuildingBlocks linkBlock : linksBuildingBlocks) {
                if (!linkBlock.isAuto()) {
                    final boolean constrained = !StringHelper.isEmpty(linkBlock.getLinkConstraint());
                    if (constrained) {
                        result.append("if ").append(linkBlock.getLinkConstraint()).append(" then").append(lineSep);
                    }
                    result.append("        put(").append(linkBlock.getLinkLabel()).append(");").append(lineSep);
                    if (constrained) {
                        result.append("end;").append(lineSep);
                    }
                    linksBuilder.append(generateOrdinaryLinkCode(linkBlock));
                }
            }
            if (theEnd) {
                result.append("        put(_try_again);").append(lineSep);
            }
            result.append("        s:initf(false);").append(lineSep);
        }
        result.append("    end,").append(lineSep);
        result.append("    initf = function(s, from_vn) ").append(lineSep);
        result.append("        if from_vn then nlb:theme_switch(\"theme_vn.lua\", from_vn); end;").append(lineSep); // TODO: or maybe create theme_vn_choices.lua?
        if (theEnd) {
            result.append("        return vn:auto_geom_end('dissolve');").append(lineSep);
        } else {
            result.append("        return vn:auto_geom_choices('dissolve');").append(lineSep);
        }
        result.append("    end,").append(lineSep);
        result.append("    exit = function(s) ").append(lineSep);
        result.append("        if s.paginator_state then paginator:turnon(); end;").append(lineSep);
        result.append("    end").append(lineSep);
        result.append("}").append(lineSep).append(lineSep);
        result.append(linksBuilder.toString());
        return result.toString();
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber, Theme theme) {
        if (!isVN(theme)) {
            return super.decoratePageLabel(labelText, pageNumber, theme);
        }
        return generatePageBeginningCode(labelText, pageNumber) + "vnr {" + getLineSeparator();
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        if (!isVN(theme)) {
            return super.decorateLinkLabel(linkId, linkText, theme);
        }
        return decorateId(linkId);
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, int pageNumber, Theme theme) {
        if (!isVN(theme)) {
            return super.decorateLinkStart(linkId, linkText, isAuto, isTrivial, pageNumber, theme);
        }
        String lineSep = getLineSeparator();
        StringBuilder result = new StringBuilder();
        result.append(decorateId(linkId)).append(" = menu {").append(lineSep);
        result.append("    nam = \"").append(decorateId(linkId)).append("\",").append(lineSep);
        result.append("    dsc = function(s) ").append("return \"{").append(linkText).append("}^^\" end, ").append(lineSep);
        return result.toString();
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkSource,
            int sourcePageNumber,
            String linkTarget,
            int targetPageNumber,
            Theme theme
    ) {
        if (!isVN(theme)) {
            return super.decorateLinkGoTo(linkId, linkText, linkSource, sourcePageNumber, linkTarget, targetPageNumber, theme);
        }
        return (
                "        nlb:nlbwalk("
                        + (
                        getGoToPageNumbers()
                                ? decorateId(String.valueOf(sourcePageNumber))
                                : decorateId(linkSource)
                )
                        + ", "
                        + (
                        getGoToPageNumbers()
                                ? decorateId(String.valueOf(targetPageNumber))
                                : decorateId(linkTarget)
                )
                        + "); " + getLineSeparator()
        );
    }

    @Override
    protected String decorateLinkEnd(Theme theme) {
        if (!isVN(theme)) {
            return super.decorateLinkEnd(theme);
        }
        return "}" + getLineSeparator();
    }

    @Override
    protected String generateOrdinaryLinkCode(LinkBuildingBlocks linkBlocks) {
        if (!isVN(linkBlocks.getTheme())) {
            return super.generateOrdinaryLinkCode(linkBlocks);
        }
        String lineSep = getLineSeparator();
        StringBuilder result = new StringBuilder();
        if (!linkBlocks.isAuto()) {
            result.append(linkBlocks.getLinkStart());
            result.append("    act = function(s) ").append(lineSep);
            result.append(linkBlocks.getLinkModifications());
            result.append(linkBlocks.getLinkVariable());
            result.append(linkBlocks.getLinkVisitStateVariable());
            result.append(linkBlocks.getLinkGoTo());
            result.append("    end").append(lineSep);
            result.append(linkBlocks.getLinkEnd()).append(lineSep);
        }
        return result.toString();
    }

    @Override
    protected String generateObjsCollection(PageBuildingBlocks pageBlocks, List<LinkBuildingBlocks> linksBlocks) {
        if (!isVN(pageBlocks.getTheme())) {
            return super.generateObjsCollection(pageBlocks, linksBlocks);
        }
        return Constants.EMPTY_STRING;
    }

    /**
     * imageBackground should be true in "The End" pages, if you want to show fancy winner's picture
     * @param pageImagePathDatas
     * @param imageBackground
     * @param theme
     * @return
     */
    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, final boolean imageBackground, Theme theme) {
        if (!isVN(theme)) {
            return super.decoratePageImage(pageImagePathDatas, imageBackground, theme);
        }
        String lineSep = getLineSeparator();
        StringBuilder bgimgBuilder = new StringBuilder("    bgimg = function(s)" + lineSep);
        boolean notFirst = false;
        String bgimgIfTermination = Constants.EMPTY_STRING;
        for (ImagePathData pageImagePathData : pageImagePathDatas) {
            if (pageImagePathData.getMaxFrameNumber() == 0) {
                String pageImagePath = pageImagePathData.getImagePath();
                if (StringHelper.notEmpty(pageImagePath)) {
                    StringBuilder tempBuilder = new StringBuilder();
                    tempBuilder.append("        ").append(notFirst ? "else" : Constants.EMPTY_STRING).append("if (");
                    String constraint = pageImagePathData.getConstraint();
                    tempBuilder.append(StringHelper.notEmpty(constraint) ? "s.tag == '" + constraint + "'" : "true").append(") then");
                    tempBuilder.append(lineSep);
                    bgimgIfTermination = "        end" + lineSep;
                    bgimgBuilder.append(tempBuilder).append("            ");
                    // vn:scene should be called in all cases
                    bgimgBuilder.append("return '").append(pageImagePath).append("';").append(lineSep);
                }
            } else {
                // TODO: support animated images
            }
            notFirst = true;
        }
        bgimgBuilder.append(bgimgIfTermination).append(lineSep);
        bgimgBuilder.append("    end,").append(lineSep);
        return bgimgBuilder.toString();
    }

    /**
     * Expands variables from text chunks.
     *
     * @param textChunks
     * @param theme
     * @return
     */
    protected String expandVariables(List<TextChunk> textChunks, Theme theme) {
        if (!isVN(theme)) {
            return super.expandVariables(textChunks, theme);
        }
        StringBuilder result = new StringBuilder();
        String lineSep = getLineSeparator();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append(preprocessText(textChunk.getText()));
                    break;
                case ACTION_TEXT:
                    result.append("\"..nlb:lasttext()..\"");
                    break;
                case VARIABLE:
                    result.append("\"..");
                    result.append("tostring(").append(getGlobalVarPrefix()).append(textChunk.getText()).append(")");
                    result.append("..\"");
                    break;
                case NEWLINE:
                    result.append("\");").append(lineSep);
                    result.append("pn();").append(lineSep);
                    result.append("pn(\"");
                    break;
            }
        }
        return result.toString();
    }

    protected String preprocessText(String text) {
        StringBuilder result = new StringBuilder();
        List<String> preprocessedChunks = getPreprocessedChunks(text);
        if (preprocessedChunks.size() == 1) {
            return preprocessedChunks.get(0);
        }
        preprocessedChunks.stream().map(this::getChunkText).forEach(result::append);
        return result.toString();
    }

    private String getChunkText(String chunk) {
        String lineSep = getLineSeparator();
        return chunk + "\");" + lineSep + "pn();" + lineSep + "pn(\"";
    }

    private List<String> getPreprocessedChunks(String text) {
        String prevText = Constants.EMPTY_STRING;
        String intermediateText = Constants.EMPTY_STRING;
        List<String> result = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        while (matcher.find()) {
            intermediateText = intermediateText + matcher.group(1);
            if (intermediateText.length() > PARAGRAPH_THRESHOLD) {
                if (intermediateText.length() > PARAGRAPH_THRESHOLD_WARN) {
                    LOG.warning("Length of the line during VN export is " + intermediateText.length() + ": " + intermediateText);
                }
                if (StringHelper.notEmpty(prevText)) {
                    result.add(prevText);
                }
                prevText = intermediateText;
                intermediateText = Constants.EMPTY_STRING;
            }
        }
        String tmpText = prevText + intermediateText;
        if (tmpText.length() <= PARAGRAPH_THRESHOLD_WARN) {
            if (StringHelper.notEmpty(tmpText)) {
                result.add(tmpText);
            }
        } else {
            if (StringHelper.notEmpty(prevText)) {
                result.add(prevText);
            }
            if (StringHelper.notEmpty(intermediateText)) {
                if (intermediateText.length() > PARAGRAPH_THRESHOLD_WARN) {
                    LOG.warning("Length of the line during VN export is " + intermediateText.length() + ": " + intermediateText);
                }
                result.add(intermediateText);
            }
        }
        return result;
    }
}
