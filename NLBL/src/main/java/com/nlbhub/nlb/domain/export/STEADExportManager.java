/**
 * @(#)STEADExportManager.java
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

import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The STEADExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/10/13
 */
public class STEADExportManager extends TextExportManager {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    /**
     * Use page numbers as destinations instead of page IDs.
     */
    private static final boolean GOTO_PAGE_NUMBERS = true;
    /**
     * Enable comments in the generated text
     */
    private static final boolean ENABLE_COMMENTS = true;

    public STEADExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String generatePreambleText() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("instead_version \"1.9.1\"").append(LINE_SEPARATOR);

        stringBuilder.append("require \"xact\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"hideinv\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"para\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"dash\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"quotes\" ").append(LINE_SEPARATOR);
        stringBuilder.append("game.codepage=\"UTF-8\";").append(LINE_SEPARATOR);
        stringBuilder.append(LINE_SEPARATOR);

        stringBuilder.append("game.act = 'Nothing happens.';").append(LINE_SEPARATOR);
        stringBuilder.append("game.inv = 'Hm.. This is strange thing..';").append(LINE_SEPARATOR);
        stringBuilder.append("game.use = 'Does not work...';").append(LINE_SEPARATOR);
        stringBuilder.append(LINE_SEPARATOR);
        return stringBuilder.toString();
    }

    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        if (ENABLE_COMMENTS) {
            stringBuilder.append(objBlocks.getObjComment());
        }
        stringBuilder.append(objBlocks.getObjLabel()).append(objBlocks.getObjStart());
        stringBuilder.append(objBlocks.getObjName());
        stringBuilder.append(objBlocks.getObjText());
        if (objBlocks.isTakable()) {
            stringBuilder.append(objBlocks.getObjTak());
            stringBuilder.append(objBlocks.getObjInv());
        }
        stringBuilder.append(objBlocks.getObjActStart());
        boolean varsOrModsPresent = (
                !StringHelper.isEmpty(objBlocks.getObjVariable())
                        || !StringHelper.isEmpty(objBlocks.getObjModifications())
        );
        if (varsOrModsPresent) {
            stringBuilder.append(objBlocks.getObjVariable());
            stringBuilder.append(objBlocks.getObjModifications());
        }
        stringBuilder.append(objBlocks.getObjActEnd());
        List<UseBuildingBlocks> usesBuildingBlocks = objBlocks.getUseBuildingBlocks();
        if (usesBuildingBlocks.size() != 0) {
            if (!objBlocks.isTakable()) {
                // Not takable but usable => scene_use should be specified
                stringBuilder.append("    scene_use = true,").append(LINE_SEPARATOR);
            }
            stringBuilder.append(objBlocks.getObjUseStart());
            for (int i = 0; i < usesBuildingBlocks.size(); i++) {
                UseBuildingBlocks useBuildingBlocks = usesBuildingBlocks.get(i);
                String padding = "        ";
                if (i == 0) {
                    stringBuilder.append(padding).append("if ");
                    stringBuilder.append(useBuildingBlocks.getUseTarget());
                    stringBuilder.append(" then").append(LINE_SEPARATOR);
                } else {
                    stringBuilder.append(padding).append("elseif ");
                    stringBuilder.append(useBuildingBlocks.getUseTarget());
                    stringBuilder.append(" then").append(LINE_SEPARATOR);
                }
                final boolean constrained = !StringHelper.isEmpty(useBuildingBlocks.getUseConstraint());
                String extraPadding = constrained ? "    " : "";
                if (constrained) {
                    stringBuilder.append(padding).append("    if ").append(useBuildingBlocks.getUseConstraint());
                    stringBuilder.append(" then").append(LINE_SEPARATOR);
                }
                stringBuilder.append(padding).append(extraPadding);
                stringBuilder.append(useBuildingBlocks.getUseVariable()).append(LINE_SEPARATOR);
                //stringBuilder.append(padding).append(extraPadding);
                stringBuilder.append(useBuildingBlocks.getUseModifications()).append(LINE_SEPARATOR);
                /*
                The following code does not needed. INSTEAD handles changes in variables
                automatically. But I will keep the code to show my intention :)
                stringBuilder.append(padding).append(extraPadding);
                if (ENABLE_COMMENTS) {
                    stringBuilder.append(
                        "    -- walk to the current room again in order to "
                        + "reflect changes in the variables"
                    ).append(LINE_SEPARATOR);
                }
                stringBuilder.append(padding).append(extraPadding);
                stringBuilder.append("    walk(here());").append(LINE_SEPARATOR);
                */
                if (constrained) {
                    stringBuilder.append(padding).append("    end;").append(LINE_SEPARATOR);
                }
            }
            stringBuilder.append("        end;").append(LINE_SEPARATOR);
            stringBuilder.append(objBlocks.getObjUseEnd());
        }
        List<String> containedObjIds = objBlocks.getContainedObjIds();
        if (containedObjIds.size() != 0) {
            stringBuilder.append(objBlocks.getObjObjStart());
            for (final String objString : containedObjIds) {
                stringBuilder.append(objString);
            }
            stringBuilder.append(objBlocks.getObjObjEnd());
        }
        stringBuilder.append(objBlocks.getObjEnd());
        return stringBuilder.toString();
    }

    @Override
    protected String generatePageText(PageBuildingBlocks pageBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        StringBuilder linksBuilder = new StringBuilder();
        if (ENABLE_COMMENTS) {
            stringBuilder.append(pageBlocks.getPageComment());
        }
        stringBuilder.append(pageBlocks.getPageLabel());
        stringBuilder.append("    forcedsc = true,").append(LINE_SEPARATOR);
        // Do not check pageBlocks.isUseCaption() here, because in INSTEAD all rooms must have name
        stringBuilder.append(pageBlocks.getPageCaption());
        stringBuilder.append(pageBlocks.getPageTextStart());
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            final boolean constrained = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
            stringBuilder.append("        p ");
            if (constrained) {
                stringBuilder.append("((").append(linkBlocks.getLinkConstraint()).append(") and ");
            }
            stringBuilder.append("\"").append(linkBlocks.getLinkLabel()).append("\"");
            if (constrained) {
                stringBuilder.append(" or ");
                stringBuilder.append("\"\")");
            }
            stringBuilder.append(";").append(LINE_SEPARATOR);
        }

        boolean varsOrModsPresent = (
                !StringHelper.isEmpty(pageBlocks.getPageVariable())
                        || !StringHelper.isEmpty(pageBlocks.getPageModifications())
        );
        stringBuilder.append(pageBlocks.getPageTextEnd());

        if (varsOrModsPresent) {
            stringBuilder.append("    entered = function(s)").append(LINE_SEPARATOR);
            stringBuilder.append(pageBlocks.getPageVariable());
            stringBuilder.append(pageBlocks.getPageModifications());
            stringBuilder.append("    end,").append(LINE_SEPARATOR);
        }

        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            linksBuilder.append(linkBlocks.getLinkStart());
            if (ENABLE_COMMENTS) {
                linksBuilder.append(linkBlocks.getLinkComment());
            }
            linksBuilder.append(linkBlocks.getLinkVariable());
            linksBuilder.append(linkBlocks.getLinkModifications());
            linksBuilder.append(linkBlocks.getLinkGoTo());
        }
        String linksText = linksBuilder.toString();
        final boolean containedObjIdsIsEmpty = pageBlocks.getContainedObjIds().isEmpty();
        final boolean linksTextIsEmpty = StringHelper.isEmpty(linksText);
        if (!linksTextIsEmpty || !containedObjIdsIsEmpty) {
            stringBuilder.append("    obj = { ").append(LINE_SEPARATOR);
            if (!containedObjIdsIsEmpty) {
                for (String containedObjId : pageBlocks.getContainedObjIds()) {
                    stringBuilder.append(containedObjId);
                }
            }
            stringBuilder.append("        xdsc(),").append(LINE_SEPARATOR);
            if (!linksTextIsEmpty) {
                stringBuilder.append(linksText).append(LINE_SEPARATOR);
            }
            stringBuilder.append("    },").append(LINE_SEPARATOR);
        }
        stringBuilder.append(decoratePageEnd());
        return stringBuilder.toString();
    }

    @Override
    protected String decorateObjLabel(String id) {
        return decorateId(id);
    }

    @Override
    protected String decorateObjComment(String name) {
        return "-- " + name + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjStart() {
        return " = obj {" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjName(String name) {
        return "    nam = \"" + name + "\"," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjText(String text) {
        return "    dsc = [[" + text + "]]," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjTak(String takString) {
        return "    tak = \"" + takString + "\"," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjInv(String invString) {
        return "    inv = \"" + invString + "\"," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjActStart() {
        return "    act = function(s)" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjActEnd() {
        return "    end," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjUseStart() {
        return "    use = function(s, w)" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjUseEnd() {
        return "    end," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjObjStart() {
        return "    obj = {" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjObjEnd() {
        return "    }," + LINE_SEPARATOR;
    }

    protected String decorateUseTarget(String targetId) {
        return "w == " + decorateId(targetId);
    }

    protected String decorateUseVariable(String variableName) {
        return (
                "    if not (" + variableName + ") then stead.add_var { "
                        + variableName
                        + " = true }; end;"
                        + LINE_SEPARATOR
        );
    }

    protected String decorateUseModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateObjVariable(String variableName) {
        return (
                "        if not (" + variableName + ") then stead.add_var { "
                        + variableName
                        + " = true }; end;"
                        + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateObjModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateObjEnd() {
        return "};" + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String decorateContainedObjId(String containedObjId) {
        return "        '" + decorateId(containedObjId) + "'," + LINE_SEPARATOR;
    }

    @Override
    protected String generateTrailingText() {
        return "";
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + "=" + variableValue + ";" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDelObj(String objectId, String objectName) {
        return (
                "            if have(\"" + objectName + "\") then remove(\""
                        + objectName + "\", " + "inv()); end;" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateAddObj(String objectId, String objectName) {
        return (
                "            if not have(\""
                        + objectName
                        + "\") then take('"
                        + decorateId(objectId)
                        + "'); end;" + LINE_SEPARATOR
        );
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
        return "{" + decorateId(linkId) + "|" + linkText + "}^";
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return "                --" + comment + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, int pageNumber) {
        return (
                "        xact(" + LINE_SEPARATOR
                        + "            '" + decorateId(linkId) + "'," + LINE_SEPARATOR
                        + "            function(s) " + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkTarget,
            int targetPageNumber
    ) {
        return (
                "                walk("
                        + (
                        GOTO_PAGE_NUMBERS
                                ? decorateId(String.valueOf(targetPageNumber))
                                : decorateId(linkTarget)
                )
                        + ");" + LINE_SEPARATOR
                        + "            end" + LINE_SEPARATOR
                        + "        )," + LINE_SEPARATOR
        );
    }

    @Override
    protected String decoratePageEnd() {
        return "};" + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return (
                "                if not (" + variableName + ") then stead.add_var { "
                        + variableName
                        + " = true }; end;"
                        + LINE_SEPARATOR
        );
    }

    @Override
    protected String decoratePageVariable(String variableName) {
        return (
                "        if not (" + variableName + ") then stead.add_var { "
                        + variableName
                        + " = true }; end;"
                        + LINE_SEPARATOR
        );
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
            return "    nam = \"" + caption + "\"," + LINE_SEPARATOR;
        } else {
            return "    nam = \"...\"," + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decoratePageTextStart(String pageText) {
        StringBuilder result = new StringBuilder();
        result.append("    dsc = function(s)").append(LINE_SEPARATOR).append("p [[");
        result.append(LINE_SEPARATOR);
        /*
         * This RegExp is used to extract multiple lines of text, separated by CR+LF or LF.
         * By default, ^ and $ match the start- and end-of-input respectively.
         * You'll need to enable MULTI-LINE mode with (?m), which causes ^ and $ to match the
         * start- and end-of-line
         */
        Pattern pattern = Pattern.compile("(?m)^.*$");
        Matcher matcher = pattern.matcher(pageText);
        while (matcher.find()) {
            final String line = matcher.group().trim();
            if (line.isEmpty()) {
                result.append("^");
            } else {
                result.append(line);
            }
            result.append(LINE_SEPARATOR);
        }
        result.append("]];").append(LINE_SEPARATOR);
        result.append("    end,").append(LINE_SEPARATOR);
        result.append("    xdsc = function(s)").append(LINE_SEPARATOR);
        result.append("        p \"^^\";").append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String decoratePageTextEnd() {
        return "    end," + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber) {
        String roomName = (
                (pageNumber == 1)
                        ? "main"
                        : (
                        GOTO_PAGE_NUMBERS
                                ? decorateId(String.valueOf(pageNumber))
                                : decorateId(labelText)
                )
        );
        return roomName + " = room {" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return "-- PageNo. " + String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return "-- " + comment + LINE_SEPARATOR;
    }

    private String decorateId(String id) {
        return "v_" + id.replaceAll("-", "_");
    }
}
