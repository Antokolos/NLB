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
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.List;

/**
 * The VNSTEADExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 03/25/15
 */
public class VNSTEADExportManager extends STEADExportManager {
    public VNSTEADExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String generatePreambleText() {
        StringBuilder stringBuilder = new StringBuilder();
        String lineSep = getLineSeparator();
        stringBuilder.append("instead_version \"1.9.1\"").append(lineSep);

        stringBuilder.append("require 'modules/fonts'").append(lineSep);
        stringBuilder.append("require 'modules/paginator'").append(lineSep);
        stringBuilder.append("require 'modules/vn'").append(lineSep);
        stringBuilder.append("require 'dash'").append(lineSep);

        stringBuilder.append("game.codepage=\"UTF-8\";").append(lineSep);
        stringBuilder.append("stead.scene_delim = '^';").append(lineSep);
        stringBuilder.append(lineSep);

        stringBuilder.append("function pname(n, c)").append(lineSep);
        stringBuilder.append("    return function()").append(lineSep);
        stringBuilder.append("        pn(img 'blank:8x1',f1:txt(n, c, 1))").append(lineSep);
        stringBuilder.append("    end").append(lineSep);
        stringBuilder.append("end").append(lineSep);

        stringBuilder.append("paginator.delim = '\\n[ \\t]*\\n'").append(lineSep);

        stringBuilder.append("function exec(s)").append(lineSep);
        stringBuilder.append("    p('$'..s:gsub(\"\\n\", \"^\")..'$^^')").append(lineSep);
        stringBuilder.append("end").append(lineSep);

        stringBuilder.append("function init()").append(lineSep);
        stringBuilder.append("    vn.fading = 8").append(lineSep);
        stringBuilder.append("end").append(lineSep);

        stringBuilder.append(generateLibraryMethods());
        return stringBuilder.toString();
    }

    @Override
    protected String decoratePageTextStart(String labelText, int pageNumber, List<TextChunk> pageTextChunks) {
        String roomName = getGoToPageNumbers() ? decorateId(String.valueOf(pageNumber)) : decorateId(labelText);
        String lineSep = getLineSeparator();
        StringBuilder pageText = new StringBuilder();
        pageText.append("    dsc = function(s)").append(lineSep);
        if (pageTextChunks.size() > 0) {
            pageText.append("pn(\"");
            pageText.append(expandVariables(pageTextChunks));
            pageText.append("\");").append(lineSep);
            pageText.append("pn();");
        }
        pageText.append("    end,").append(lineSep);
        pageText.append("    walk_to = dlg {").append(lineSep);
        pageText.append("        nam = \"").append(roomName).append("_choices\",").append(lineSep);
        pageText.append("        entered = function(s) ").append(lineSep);
        pageText.append("            theme.win.geom(320, 320, 1280, 480);").append(lineSep);
        pageText.append("            vn:commit();").append(lineSep);
        pageText.append("        end,").append(lineSep);
        pageText.append("        phr = {").append(lineSep);
        return pageText.toString();
    }

    @Override
    protected String decoratePageTextEnd() {
        String lineSep = getLineSeparator();
        return "        }" + lineSep + "    }," + lineSep;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber) {
        StringBuilder roomBeginning = new StringBuilder();
        String roomName = getGoToPageNumbers() ? decorateId(String.valueOf(pageNumber)) : decorateId(labelText);
        roomBeginning.append(roomName);
        if (pageNumber == 1) {
            roomBeginning.append(", main = room { nam = \"main\", enter = function(s) nlbwalk(main); end }, ");
        } else {
            roomBeginning.append(" = ");
        }
        return roomBeginning.toString() + "vnr {" + getLineSeparator();
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText) {
        return linkText;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, int pageNumber) {
        return "            {";
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkTarget,
            int targetPageNumber
    ) {
        String lineSep = getLineSeparator();
        return (
                "code [["
                        + lineSep
                        + "                nlbwalk("
                        + (
                        getGoToPageNumbers()
                                ? decorateId(String.valueOf(targetPageNumber))
                                : decorateId(linkTarget)
                )
                        + "); "
                        + "]];"
                        + lineSep
        );
    }

    @Override
    protected String decorateLinkEnd() {
        return "            }," + getLineSeparator();
    }

    @Override
    protected String generateOrdinaryLinkCode(LinkBuildingBlocks linkBlocks) {
        String lineSep = getLineSeparator();
        final boolean constrained = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
        StringBuilder result = new StringBuilder();
        result.append(linkBlocks.getLinkStart());
        if (constrained) {
            result.append(linkBlocks.getLinkConstraint()).append(", ");
        }
        result.append("\"").append(linkBlocks.getLinkLabel()).append("\", nil, ").append(lineSep);
        result.append(linkBlocks.getLinkModifications());
        result.append(linkBlocks.getLinkVariable());
        result.append(linkBlocks.getLinkGoTo()).append(lineSep);
        result.append(linkBlocks.getLinkEnd());
        return result.toString();
    }

    @Override
    protected String generateObjsCollection(PageBuildingBlocks pageBlocks, List<LinkBuildingBlocks> linksBlocks) {
        return Constants.EMPTY_STRING;
    }

    /**
     * imageBackground is actually ignored, because in VN every image should be treated as background
     * @param pageImagePathDatas
     * @param imageBackground ignored
     * @return
     */
    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, final boolean imageBackground) {
        String lineSep = getLineSeparator();
        StringBuilder bgimgBuilder = new StringBuilder("    bgimg = function(s)" + lineSep);
        boolean notFirst = false;
        String bgimgIfTermination = Constants.EMPTY_STRING;
        bgimgBuilder.append("theme.win.geom(8, 880, 1904, 200);");
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
                    bgimgBuilder.append("vn:scene ('").append(pageImagePath).append("');").append(lineSep);
                }
            } else {
                // TODO: support animated images
            }
            notFirst = true;
        }
        bgimgBuilder.append(bgimgIfTermination);
        bgimgBuilder.append("vn:start('dissolve');").append(lineSep);
        bgimgBuilder.append("vn:commit();").append(lineSep);
        bgimgBuilder.append("    end,").append(lineSep);
        return bgimgBuilder.toString();
    }
}
