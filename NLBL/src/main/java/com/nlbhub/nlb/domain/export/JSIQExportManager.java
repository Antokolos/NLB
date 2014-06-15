/**
 * @(#)JSIQExportManager.java
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
 * The JSIQExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/12/13
 * @deprecated use JSIQ2ExportManager instead
 */
public class JSIQExportManager extends TextExportManager {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    public JSIQExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String generatePreambleText() {
        return (
                "<html>" + LINE_SEPARATOR
                        + "<head>" + LINE_SEPARATOR
                        + "<title>jsIQ</title>" + LINE_SEPARATOR
                        + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">" + LINE_SEPARATOR
                        + "<link type=\"text/css\" rel=\"stylesheet\" media=\"all\" href=\"game.css\"/>" + LINE_SEPARATOR
                        + "</head>" + LINE_SEPARATOR
                        + LINE_SEPARATOR
                        + "<body style=\"font-family:verdana;font-size:95%\">" + LINE_SEPARATOR
                        + "<script src=\"libs/jquery-1.3.2.min.js\"></script>" + LINE_SEPARATOR
                        + LINE_SEPARATOR
                        + "<script src=\"jsiqapi.js\"></script>" + LINE_SEPARATOR
                        + "<script src=\"std_inventory.js\"></script>" + LINE_SEPARATOR
                        + LINE_SEPARATOR
                        + "<script type=\"text/javascript\">" + LINE_SEPARATOR
                        + "var _globFuncs = new Object();" + LINE_SEPARATOR
                        + "function init() {" + LINE_SEPARATOR
                        + "    insertValue('inv', '');" + LINE_SEPARATOR
                        + "}" + LINE_SEPARATOR
                        + "function getInventory() {" + LINE_SEPARATOR
                        + "    if (_G.inventory === undefined) {" + LINE_SEPARATOR
                        + "        " + "_G.inventory = INVENTORY();" + LINE_SEPARATOR
                        + "        " + "_G.inventory.on_update = function() {" + LINE_SEPARATOR
                        + "            " + "insertValue('inv', _G.inventory.getListAsHTML('<br/>', true));" + LINE_SEPARATOR
                        + "        " + "}" + LINE_SEPARATOR
                        + "        " + "return _G.inventory;" + LINE_SEPARATOR
                        + "    } else {" + LINE_SEPARATOR
                        + "        " + "return _G.inventory;" + LINE_SEPARATOR
                        + "    }" + LINE_SEPARATOR
                        + "}" + LINE_SEPARATOR
                        + "function getGlobVar(variableName) {" + LINE_SEPARATOR
                        + "    if (_G[variableName] === undefined) {" + LINE_SEPARATOR
                        + "        " + "return false;" + LINE_SEPARATOR
                        + "    } else {" + LINE_SEPARATOR
                        + "        " + "return _G[variableName];" + LINE_SEPARATOR
                        + "    }" + LINE_SEPARATOR
                        + "}" + LINE_SEPARATOR
                        + "</script>" + LINE_SEPARATOR
                        + "<div id=\"book\" style=\"display:none\">" + LINE_SEPARATOR
        );
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
        stringBuilder.append(pageBlocks.getPageTextStart());
        stringBuilder.append(pageBlocks.getPageTextEnd());
        stringBuilder.append("        <div class=\"onload\">").append(LINE_SEPARATOR);
        stringBuilder.append(pageBlocks.getPageVariable());
        stringBuilder.append(pageBlocks.getPageModifications());

        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (int i = 0; i < linksBlocks.size(); i++) {
            LinkBuildingBlocks linkBlocks = linksBlocks.get(i);
            final boolean hasConstraint = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
            if (hasConstraint) {
                stringBuilder.append("            if (!(").append(linkBlocks.getLinkConstraint());
                stringBuilder.append(")) {").append(LINE_SEPARATOR);
                stringBuilder.append("                hideAction(").append(i).append(");");
                stringBuilder.append(LINE_SEPARATOR);
                //stringBuilder.append("                setActionVisible(").append(i).append(", false);").append(LINE_SEPARATOR);
                stringBuilder.append("            }").append(LINE_SEPARATOR);
            }
            stringBuilder.append("            _globFuncs.");
            stringBuilder.append(decorateId(linkBlocks.getLinkLabel())).append(" = function() {");
            stringBuilder.append(LINE_SEPARATOR);
            stringBuilder.append(linkBlocks.getLinkVariable());
            stringBuilder.append(linkBlocks.getLinkModifications());
            stringBuilder.append("}").append(LINE_SEPARATOR);
        }
        stringBuilder.append("        </div>").append(LINE_SEPARATOR);

        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            stringBuilder.append(linkBlocks.getLinkComment());
            stringBuilder.append(linkBlocks.getLinkStart());
            stringBuilder.append(linkBlocks.getLinkGoTo());
        }
        stringBuilder.append(decoratePageEnd());
        return stringBuilder.toString();
    }

    @Override
    protected String generateTrailingText() {
        return (
                "<div id=\"char_list_tpl\">" + LINE_SEPARATOR
                        + "\t\t<p class=MsoNormal><b>Charsheet</b></p>" + LINE_SEPARATOR
                        + "\t\t<div id=\"main\" style=\"border: 1px solid #FABF8F\">" + LINE_SEPARATOR
                        + "%%inv" + LINE_SEPARATOR
                        + "\t\t</div>" + LINE_SEPARATOR
                        + "\t</div>" + LINE_SEPARATOR
                        + "\t<div id=\"char_list_update\">" + LINE_SEPARATOR
                        + "\t</div>" + LINE_SEPARATOR
                        + "</div>" + LINE_SEPARATOR
                        + "<table id=\"deco\" border=\"0\" width=\"100%\" style=\"border:0;width: 100%; height: 100%;\" cellspacing=\"0\" cellpadding=\"0\">" + LINE_SEPARATOR
                        + "<tr><td width=\"65%\"  height=\"100%\" valign=\"middle\">" + LINE_SEPARATOR
                        + "\t<div id=\"main\">" + LINE_SEPARATOR
                        + "\t\t<div id=\"title\" style=\"height:40px;display:none\">" + LINE_SEPARATOR
                        + "\t\t</div>" + LINE_SEPARATOR
                        + "\t\t<div id=\"main_text\" style=\"text-align:justify;\">" + LINE_SEPARATOR
                        + "\t\t</div>" + LINE_SEPARATOR
                        + "\t\t<br>" + LINE_SEPARATOR
                        + "\t\t<div id=\"actions\">" + LINE_SEPARATOR
                        + "\t\t</div>" + LINE_SEPARATOR
                        + "\t</div>" + LINE_SEPARATOR
                        + "</td>" + LINE_SEPARATOR
                        + "<td width=\"30px\"><br></td>" + LINE_SEPARATOR
                        + "<td height=\"100%\" valign=\"top\" align=\"right\">" + LINE_SEPARATOR
                        + "\t<div id=\"char_list\" style=\"width: 100%; height: 100%\">" + LINE_SEPARATOR
                        + "\t</div>" + LINE_SEPARATOR
                        + "</td>" + LINE_SEPARATOR
                        + "</tr></table>" + LINE_SEPARATOR
                        + LINE_SEPARATOR
                        + "<script type=\"text/javascript\">" + LINE_SEPARATOR
                        + "\tjsIQ.init();" + LINE_SEPARATOR
                        + "\tjsIQ.startGame(function()" + LINE_SEPARATOR
                        + "\t{" + LINE_SEPARATOR
                        + "\t\t\tshowArticle(1);" + LINE_SEPARATOR
                        + "\t});" + LINE_SEPARATOR
                        + "\tinit();" + LINE_SEPARATOR
                        + "</script>" + LINE_SEPARATOR
                        + LINE_SEPARATOR
                        + "</body>" + LINE_SEPARATOR
                        + "</html>"
        );
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return "_G." + variableName + " = " + variableValue + ";";
    }

    @Override
    protected String decorateDelObj(String objectId, String objectName) {
        // Count parameter should be set to -1 to remove all items from the inventory
        return "getInventory().removeItem('" + objectName + "', -1);" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddObj(String objectId, String objectName) {
        return "getInventory().addItem('" + objectName + "', 1);" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateNot() {
        return "!";
    }

    @Override
    protected String decorateOr() {
        return "||";
    }

    @Override
    protected String decorateAnd() {
        return "&&";
    }

    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return "getGlobVar('" + constraintVar + "')";
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return "getGlobVar('" + constraintVar + "')";
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return "getGlobVar('" + constraintVar + "')";
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText) {
        return linkId;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return "";
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, int pageNumber) {
        return "        <div id=\"" + linkId + "\" class=\"action\" use=\"";
    }

    @Override
    protected String decorateLinkGoTo(String linkId, String linkText, String linkTarget, int targetPageNumber) {
        return (
                "_globFuncs." + decorateId(linkId) + "(); showArticle(" + targetPageNumber + ");\">"
                        + linkText + "</div>" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decoratePageEnd() {
        return (
                "    </div>" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return "                " + decorateAssignment(variableName, "true") + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageVariable(String variableName) {
        return (
                decorateAssignment(variableName, "true") + LINE_SEPARATOR
        );
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return "            " + modificationsText;
    }

    @Override
    protected String decoratePageCaption(String caption) {
        return "<h1>" + caption + "</h1>";
    }

    @Override
    protected String decoratePageTextStart(String pageText) {
        StringBuilder result = new StringBuilder();
        result.append("        <div class=\"text\">").append(LINE_SEPARATOR);
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
            if (!line.isEmpty()) {
                result.append("            ").append(line).append("<br/>").append(LINE_SEPARATOR);
            }
        }
        return result.toString();
    }

    @Override
    protected String decoratePageTextEnd() {
        return "        </div>" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber) {
        return "    <div id=\"p_" + pageNumber + "\">" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return "";
    }

    private String decorateId(String id) {
        return "v_" + id.replaceAll("-", "_");
    }
}
