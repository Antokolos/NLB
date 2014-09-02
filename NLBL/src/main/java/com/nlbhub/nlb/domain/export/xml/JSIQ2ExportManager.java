/**
 * @(#)JSIQ2ExportManager.java
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
package com.nlbhub.nlb.domain.export.xml;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.export.LinkBuildingBlocks;
import com.nlbhub.nlb.domain.export.NLBBuildingBlocks;
import com.nlbhub.nlb.domain.export.ObjBuildingBlocks;
import com.nlbhub.nlb.domain.export.PageBuildingBlocks;
import com.nlbhub.nlb.domain.export.xml.beans.jsiq2.*;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.JaxbMarshaller;
import com.nlbhub.nlb.util.StringHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The JSIQ2ExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 6/01/14
 */
public class JSIQ2ExportManager extends XMLExportManager {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    public JSIQ2ExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String getCDataSectionElements() {
        return "script text action cssdata css style";
    }

    @Override
    protected JaxbMarshaller createMarshaller() {
        return new JaxbMarshaller(
                Action.class,
                Article.class,
                Book.class,
                Metadata.class,
                Script.class,
                Css.class,
                CssData.class,
                Style.class
        );
    }

    @Override
    protected Object createRootObject(NLBBuildingBlocks nlbBlocks) {
        Book book = new Book();
        String lastSaveString = (new Date()).toString();
        boolean first = true;
        for (PageBuildingBlocks pageBuildingBlocks : nlbBlocks.getPagesBuildingBlocks()) {
            book.addArticle(
                    createArticle(pageBuildingBlocks, lastSaveString, nlbBlocks.getObjsBuildingBlocks(), first)
            );
            first = false;
        }
        book.addArticle(createCharsheetArticle(lastSaveString));
        CssData cssData = new CssData();
        Css css = new Css();
        css.setName("default");
        css.setValue(".atril-game td{border: 2px solid gray; border-radius: 8px;}");
        Style style = new Style();
        style.setName("default");
        style.setValue("td {border: 2px solid gray; border-radius: 8px;}");
        cssData.setCss(css);
        cssData.setStyle(style);
        book.setCssData(cssData);
        return book;
    }

    private Article createArticle(
            PageBuildingBlocks pageBlocks,
            String lastSaveString,
            List<ObjBuildingBlocks> objsBlocks,
            boolean first
    ) {
        Article article = new Article();
        Metadata metadata = new Metadata();
        metadata.setLastSave(lastSaveString);
        article.setId(pageBlocks.getPageNumber());
        article.setMetadata(metadata);
        article.setText(pageBlocks.getPageTextStart());
        if (first) {
            List<Script> inventoryScripts = createInventoryScripts(objsBlocks);
            for (Script script : inventoryScripts) {
                article.addScript(script);
            }
        }
        boolean hasPageVariable = !StringHelper.isEmpty(pageBlocks.getPageVariable());
        if (hasPageVariable) {
            Script pageVariableScript = new Script();
            pageVariableScript.setType("pvar");
            pageVariableScript.setValue(pageBlocks.getPageVariable());
            article.addScript(pageVariableScript);
        }
        boolean hasPageModifications = !StringHelper.isEmpty(pageBlocks.getPageModifications());
        if (hasPageModifications) {
            Script pageModificationsScript = new Script();
            pageModificationsScript.setType("pmod");
            pageModificationsScript.setValue(pageBlocks.getPageModifications());
            article.addScript(pageModificationsScript);
        }
        if (hasPageVariable || hasPageModifications) {
            Script onLoadScript = new Script();
            onLoadScript.setType("onload");
            onLoadScript.setValue(
                    //"<![CDATA[pvar(); pmod(); ]]>"
                    (hasPageVariable ? "pvar(); " : Constants.EMPTY_STRING)
                            + (hasPageModifications ? "pmod(); " : Constants.EMPTY_STRING)
            );
            article.addScript(onLoadScript);
        }
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (int i = 0; i < linksBlocks.size(); i++) {
            Action action = new Action();
            final LinkBuildingBlocks linkBlocks = linksBlocks.get(i);
            final boolean hasConstraint = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
            if (hasConstraint) {
                Script actionConditionScript = new Script();
                actionConditionScript.setType("condition_" + i);
                actionConditionScript.setValue(
                        //"<![CDATA[return (" + linkBlocks.getLinkConstraint() + ");]]>"
                        "return (" + linkBlocks.getLinkConstraint() + ");"
                );
                article.addScript(actionConditionScript);
                action.setIf("condition_" + i);
            }
            action.setGoto(linkBlocks.getLinkGoTo());
            action.setValue(linkBlocks.getLinkStart());
            boolean hasLinkVariable = !StringHelper.isEmpty(linkBlocks.getLinkVariable());
            if (hasLinkVariable) {
                Script actionVariableScript = new Script();
                actionVariableScript.setType("var_" + i);
                actionVariableScript.setValue(
                        //"<![CDATA[" + linkBlocks.getLinkVariable() + "]]>"
                        linkBlocks.getLinkVariable()
                );
                article.addScript(actionVariableScript);
            }

            boolean hasLinkModifications = !StringHelper.isEmpty(linkBlocks.getLinkModifications());
            if (hasLinkModifications) {
                Script actionModificationScript = new Script();
                actionModificationScript.setType("mod_" + i);
                actionModificationScript.setValue(
                        //"<![CDATA[" + linkBlocks.getLinkModifications() + "]]>"
                        linkBlocks.getLinkModifications()
                );
                article.addScript(actionModificationScript);
            }
            if (hasLinkVariable || hasLinkModifications) {
                Script actionDoScript = new Script();
                actionDoScript.setType("do_" + i);
                actionDoScript.setValue(
                        //"<![CDATA[" + "var_" + i + "(); " + "mod_" + i + "(); ]]>"
                        (hasLinkVariable ? "var_" + i + "(); " : Constants.EMPTY_STRING)
                                + (hasLinkModifications ? "mod_" + i + "(); " : Constants.EMPTY_STRING)
                );
                article.addScript(actionDoScript);
                action.setDo("do_" + i);
            }
            article.addAction(action);
        }
        return article;
    }

    private Article createCharsheetArticle(String lastSaveString) {
        Article article = new Article();
        Metadata metadata = new Metadata();
        metadata.setLastSave(lastSaveString);
        article.setId("charsheet");
        article.setMetadata(metadata);
        article.setText("CHARACTER SHEET<br/> <span class=\"linked\" name=\"inventory\"></span>");
        return article;
    }

    private List<Script> createInventoryScripts(List<ObjBuildingBlocks> objsBlocks) {
        List<Script> result = new ArrayList<>();
        Script preload = new Script();
        preload.setType("preload");
        String preloadValue = (
                "vars.inventory = {}; " + LINE_SEPARATOR +
                createObjsMap(objsBlocks) + "; " + LINE_SEPARATOR +
                createInventoryLinkedVariable()
        );
        preload.setValue(preloadValue);
        result.add(preload);
        Script getItemList = new Script();
        getItemList.setInfo("getting the list of items");
        getItemList.setType("getItemList");
        getItemList.setIsGlobal("true");
        getItemList.setValue(
                "var list = [];" + LINE_SEPARATOR +
                        "for (var name in vars.inventory){" + LINE_SEPARATOR +
                        "    if (!vars.inventory.hasOwnProperty(name)){   continue;   }" + LINE_SEPARATOR +
                        "    " + LINE_SEPARATOR +
                        "    if (vars.inventory[name] > 0){" + LINE_SEPARATOR +
                        "        list.push( vars.itemNames[name] );" + LINE_SEPARATOR +
                        "    }" + LINE_SEPARATOR +
                        "}" + LINE_SEPARATOR +
                        "return list;"
        );
        result.add(getItemList);
        Script checkItem = new Script();
        checkItem.setInfo("checking item existence");
        checkItem.setType("checkItem");
        checkItem.setIsGlobal("true");
        checkItem.setValue(
                "var name = arg.name;" + LINE_SEPARATOR +
                        "if (name === undefined && name === '') {" + LINE_SEPARATOR +
                        "    triggerError('checkItem: название предмета не указано',{});" + LINE_SEPARATOR +
                        "    return;" + LINE_SEPARATOR +
                        "}" + LINE_SEPARATOR +
                        "return (vars.inventory[name] == true);"
        );
        result.add(checkItem);
        Script addItem = new Script();
        addItem.setInfo("adding item");
        addItem.setType("addItem");
        addItem.setIsGlobal("true");
        addItem.setValue(
                "var name = arg.name;" + LINE_SEPARATOR +
                        "if (name === undefined && name === '') {" + LINE_SEPARATOR +
                        "    triggerError('checkItem: название предмета не указано',{});" + LINE_SEPARATOR +
                        "    return;" + LINE_SEPARATOR +
                        "}" + LINE_SEPARATOR +
                        "vars.inventory[name] = true;"
        );
        result.add(addItem);
        Script removeItem = new Script();
        removeItem.setInfo("removing item");
        removeItem.setType("removeItem");
        removeItem.setIsGlobal("true");
        removeItem.setValue(
                "var name = arg.name;" + LINE_SEPARATOR +
                        "if (name === undefined && name === '') {" + LINE_SEPARATOR +
                        "    triggerError('removeItem: название предмета не указано',{});" + LINE_SEPARATOR +
                        "    return;" + LINE_SEPARATOR +
                        "}" + LINE_SEPARATOR +
                        "delete vars.inventory[name];"
        );
        result.add(removeItem);
        return result;
    }

    private String createInventoryLinkedVariable() {
        return "jsIQ.linkValue('inventory', function(){" + LINE_SEPARATOR +
                "   if (!vars.inventory) return '';" + LINE_SEPARATOR +
                "   var list = $('<ul></ul>');" + LINE_SEPARATOR +
                "   for (var name in vars.inventory){" + LINE_SEPARATOR +
                "       if (!vars.inventory.hasOwnProperty(name)){   continue;   }       " + LINE_SEPARATOR +
                "       if (vars.inventory[name] > 0){" + LINE_SEPARATOR +
                "           list.append('<li>' + vars.itemNames[name] + '</li>');" + LINE_SEPARATOR +
                "       }" + LINE_SEPARATOR +
                "   }" + LINE_SEPARATOR +
                "   return list.html();" + LINE_SEPARATOR +
                "});";
    }

    private String createObjsMap(List<ObjBuildingBlocks> objsBlocks) {
        StringBuilder builder = new StringBuilder();
        builder.append("vars.itemNames = {").append(LINE_SEPARATOR);
        for (ObjBuildingBlocks objBlocks : objsBlocks) {
            builder.append("'").append(objBlocks.getObjLabel()).append("': ");
            builder.append("'").append(objBlocks.getObjDisp()).append("',").append(LINE_SEPARATOR);
        }
        builder.append("'dummy': 'dummy'").append(LINE_SEPARATOR).append("}");
        return builder.toString();
    }

    @Override
    protected String decorateObjLabel(String id) {
        return decorateId(id);
    }

    @Override
    protected String decorateObjName(String name) {
        return name;
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + " = " + variableValue + "; ";
    }

    @Override
    protected String decorateDelObj(String objectId, String objectName, String objectDisplayName) {
        String id = decorateId(objectId);
        return "if (checkItem({'name': '" + id + "'})) { removeItem({'name': '" + id + "'}); }; ";
    }

    @Override
    protected String decorateAddObj(String listName, String objectId, String objectName, String objectDisplayName) {
        if (StringHelper.isEmpty(listName)) {
            String id = decorateId(objectId);
            return "if (!checkItem({'name': '" + id + "'})) { addItem({'name': '" + id + "'}); }; ";
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

    @Override
    protected String decorateTrue() {
        return "true";
    }

    @Override
    protected String decorateFalse() {
        return "false";
    }

    @Override
    protected String decorateEq() {
        return "==";
    }

    @Override
    protected String decorateNEq() {
        return "!=";
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
        return "vars." + constraintVar;
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return "vars." + constraintVar;
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return "vars." + constraintVar;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, int pageNumber) {
        return linkText;
    }

    @Override
    protected String decorateLinkGoTo(String linkId, String linkText, String linkTarget, int targetPageNumber) {
        return Integer.toString(targetPageNumber - 1);
    }

    @Override
    protected String decoratePageEnd() {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return "vars." + variableName + " = true; ";
    }

    @Override
    protected String decoratePageVariable(String variableName) {
        return "vars." + variableName + " = true; ";
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
        return caption;
    }

    @Override
    protected String decoratePageImage(String pageImagePath) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    protected String decoratePageTextStart(List<TextChunk> pageTextChunks) {
        StringBuilder pageText = new StringBuilder();
        for (final TextChunk textChunk : pageTextChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    pageText.append(textChunk.getText());
                    break;
                case VARIABLE:
                    pageText.append("$").append(textChunk.getText()).append("$");
                    break;
                case NEWLINE:
                    pageText.append(" <br/>").append(getLineSeparator());
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
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return Integer.toString(pageNumber - 1);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return Constants.EMPTY_STRING;
    }

    private String decorateId(String id) {
        return "v_" + id.replaceAll("-", "_");
    }
}
