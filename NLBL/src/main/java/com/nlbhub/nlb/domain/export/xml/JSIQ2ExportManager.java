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
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.export.*;
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
            article.addScript(createNlbGotoScript());
        }
        boolean hasPageModifications = !StringHelper.isEmpty(pageBlocks.getPageModifications());
        if (hasPageModifications) {
            Script pageModificationsScript = new Script();
            pageModificationsScript.setType("pmod");
            pageModificationsScript.setValue(pageBlocks.getPageModifications());
            article.addScript(pageModificationsScript);
        }
        boolean hasPageVariable = !StringHelper.isEmpty(pageBlocks.getPageVariable());
        if (hasPageVariable) {
            Script pageVariableScript = new Script();
            pageVariableScript.setType("pvar");
            pageVariableScript.setValue(pageBlocks.getPageVariable());
            article.addScript(pageVariableScript);
        }
        boolean hasAutoLinks = false;
        StringBuilder autosStringBuilder = new StringBuilder();
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (int i = 0; i < linksBlocks.size(); i++) {
            final LinkBuildingBlocks linkBlocks = linksBlocks.get(i);
            final boolean hasConstraint = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
            if (linkBlocks.isAuto()) {
                hasAutoLinks = true;
                if (hasConstraint) {
                    autosStringBuilder.append("if (").append(linkBlocks.getLinkConstraint()).append(") {");
                }
                // See decorateLinkGoTo() to understand why -1 is needed.
                // Функции go() в окружении скриптов jsIQ нет: переход — jsIQ.showArticle, после
                // того как плеер закончит показ текущей статьи; return — первая подходящая
                // автоссылка уводит со страницы
                autosStringBuilder.append(showArticleLater("'" + (linkBlocks.getTargetPageNumber() - 1) + "'"))
                        .append("return; ");
                if (hasConstraint) {
                    autosStringBuilder.append("} ");
                }
            } else {
                Action action = new Action();
                if (hasConstraint) {
                    Script actionConditionScript = new Script();
                    actionConditionScript.setType("condition_" + i);
                    actionConditionScript.setValue(
                            //"<![CDATA[return (" + linkBlocks.getLinkConstraint() + ");]]>"
                            "return (" + linkBlocks.getLinkConstraint() + ");"
                    );
                    article.addScript(actionConditionScript);
                    action.setIf("condition_" + i);
                    if (linkBlocks.hasShownAltText()) {
                        // альтернативный текст недоступной ссылки (#135): текст статьи, который
                        // плеер показывает, когда то же условие ложно (jsIQ.createText, ifnot)
                        Text altText = new Text(linkBlocks.getLinkAltText());
                        altText.setIfnot("condition_" + i);
                        article.addText(altText);
                    }
                }
                action.setGoto(linkBlocks.getLinkGoTo());
                action.setValue(linkBlocks.getLinkStart());
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
                boolean hasLinkVisitStateVariable = !StringHelper.isEmpty(linkBlocks.getLinkVisitStateVariable());
                if (hasLinkVisitStateVariable) {
                    Script actionVisitStateVariableScript = new Script();
                    actionVisitStateVariableScript.setType("lvs_var_" + i);
                    actionVisitStateVariableScript.setValue(
                            //"<![CDATA[" + linkBlocks.getLinkVisitStateVariable() + "]]>"
                            linkBlocks.getLinkVisitStateVariable()
                    );
                    article.addScript(actionVisitStateVariableScript);
                }
                if (hasLinkVariable || hasLinkModifications || hasLinkVisitStateVariable) {
                    Script actionDoScript = new Script();
                    actionDoScript.setType("do_" + i);
                    actionDoScript.setValue(
                            //"<![CDATA[" + "var_" + i + "(); " + "mod_" + i + "(); ]]>"
                            (hasLinkModifications ? "mod_" + i + "(); " : Constants.EMPTY_STRING)
                                    + (hasLinkVariable ? "var_" + i + "(); " : Constants.EMPTY_STRING)
                                    + (hasLinkVisitStateVariable ? "lvs_var_" + i + "(); " : Constants.EMPTY_STRING)
                    );
                    article.addScript(actionDoScript);
                    action.setDo("do_" + i);
                }
                article.addAction(action);
            }
        }
        if (hasAutoLinks) {
            Script autoLinkScript = new Script();
            autoLinkScript.setType("autos");
            autoLinkScript.setValue(autosStringBuilder.toString());
            article.addScript(autoLinkScript);
        }
        // Модификации страницы — в preload: плеер выполняет его до вывода текста и действий,
        // поэтому условия ссылок и текстов видят уже изменённые переменные (как в INSTEAD).
        // Переход (GOTO в модификациях, автоссылки) — в onload, после показа статьи: так текст
        // страницы, которую сразу покидают, успевает появиться, и не начинается показ новой
        // статьи посреди показа текущей.
        if (hasPageModifications || hasPageVariable) {
            String preloadValue = (
                    (hasPageModifications ? "pmod(); " : Constants.EMPTY_STRING)
                            + (hasPageVariable ? "pvar(); " : Constants.EMPTY_STRING)
            );
            Script preload = findScript(article, "preload");
            if (preload == null) {
                preload = new Script();
                preload.setType("preload");
                preload.setValue(preloadValue);
                article.addScript(preload);
            } else {
                preload.setValue(preload.getValue() + LINE_SEPARATOR + preloadValue);
            }
        }
        if (hasPageModifications || hasAutoLinks) {
            Script onLoadScript = new Script();
            onLoadScript.setType("onload");
            onLoadScript.setValue(
                    "if (vars." + GOTO_VAR + " != null) { var to = vars." + GOTO_VAR + "; vars." + GOTO_VAR
                            + " = null; " + showArticleLater("to") + "return; } "
                            + (hasAutoLinks ? "autos(); " : Constants.EMPTY_STRING)
            );
            article.addScript(onLoadScript);
        }
        return article;
    }

    /** Переменная jsIQ, в которой модификации страницы оставляют статью для перехода (GOTO) */
    private static final String GOTO_VAR = "nlb_goto";

    /** Показ статьи после того, как плеер закончит показ текущей */
    private static String showArticleLater(String articleIdExpression) {
        return "setTimeout(function() { jsIQ.showArticle(" + articleIdExpression + "); }, 0); ";
    }

    private static Script findScript(Article article, String type) {
        for (Script script : article.getScripts()) {
            if (type.equals(script.getType())) {
                return script;
            }
        }
        return null;
    }

    /** Номер статьи jsIQ для страницы: статьи нумеруются с 0 (см. decoratePageNumber) */
    private String getJsiqArticleId(String pageId) {
        int pageNumber = getExportPageNumber(pageId);
        return pageNumber > 0 ? Integer.toString(pageNumber - 1) : pageId;
    }

    /**
     * Глобальный скрипт nlbGoto({to: 'N'}) — GOTO из модификаций. В действии (ссылке) он меняет
     * цель самого действия (env.from — нажатое действие, плеер переходит по его goto после
     * скрипта do), поэтому GOTO важнее цели ссылки, как в INSTEAD. В модификациях страницы
     * (preload) он запоминает статью, а переходит onload, после показа текущей.
     */
    private static Script createNlbGotoScript() {
        Script nlbGoto = new Script();
        nlbGoto.setInfo("GOTO from NLB modifications");
        nlbGoto.setType("nlbGoto");
        nlbGoto.setIsGlobal("true");
        nlbGoto.setValue(
                "var to = String(arg.to);" + LINE_SEPARATOR +
                        "if (env.from && env.from.goto !== undefined) {" + LINE_SEPARATOR +
                        "    env.from.goto = to;" + LINE_SEPARATOR +
                        "} else {" + LINE_SEPARATOR +
                        "    vars." + GOTO_VAR + " = to;" + LINE_SEPARATOR +
                        "}"
        );
        return nlbGoto;
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

    /**
     * Текст внутри строки JavaScript в апострофах. Подпись предмета может быть многострочной и
     * содержать апострофы — без экранирования скрипт preload не разбирался, и игра не начиналась
     * («Варвар», «Шапочка»)
     */
    private static String jsString(String text) {
        if (text == null) {
            return Constants.EMPTY_STRING;
        }
        return text.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\r", "").replace("\n", "\\n");
    }

    private String createObjsMap(List<ObjBuildingBlocks> objsBlocks) {
        StringBuilder builder = new StringBuilder();
        builder.append("vars.itemNames = {").append(LINE_SEPARATOR);
        for (ObjBuildingBlocks objBlocks : objsBlocks) {
            builder.append("'").append(objBlocks.getObjLabel()).append("': ");
            builder.append("'").append(jsString(objBlocks.getObjDisp())).append("',").append(LINE_SEPARATOR);
        }
        builder.append("'dummy': 'dummy'").append(LINE_SEPARATOR).append("}");
        return builder.toString();
    }

    @Override
    protected String decorateObjDisp(String dispText, boolean imageEnabled, boolean isGraphicalObj) {
        // TODO: resolve imageEnabled case
        // TODO: resolve variables output
        return dispText;
    }

    @Override
    protected String decorateObjLabel(String id) {
        return decorateId(id);
    }

    @Override
    protected String decorateObjName(String name, String id) {
        return name;
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + " = " + variableValue + "; ";
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
        return "while (" + constraint + ") { ";
    }

    @Override
    protected String decorateIf(final String constraint) {
        return "if (" + constraint + ") { ";
    }

    @Override
    protected String decorateIfHave(String objId, String objVar) {
        return "if (checkItem({'name': " + inventoryKey(objId, objVar) + "})) { ";
    }

    @Override
    protected String decorateElse() {
        return "} else { ";
    }

    @Override
    protected String decorateElseIf(final String constraint) {
        return "} else if (" + constraint + ") { ";
    }

    @Override
    protected String decorateEnd() {
        return "} ";
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

    /**
     * Ключ предмета в инвентаре (выражение JavaScript): id объекта, а если объект задан переменной
     * (id при экспорте неизвестен) — значение этой переменной во время игры. Раньше второй случай
     * ронял экспорт (NullPointerException, например на «Фронтире»).
     */
    private String inventoryKey(String objectId, String objectVar) {
        return (objectId != null) ? "'" + decorateId(objectId) + "'" : "String(" + objectVar + ")";
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        String id = inventoryKey(objectId, objectVar);
        return "if (checkItem({'name': " + id + "})) { removeItem({'name': " + id + "}); }; ";
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        String id = inventoryKey(objectId, objectVar);
        return "if (!checkItem({'name': " + id + "})) { addItem({'name': " + id + "}); }; ";
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
        // rnd(n) в NLB — целое от 1 до n
        return variableName + " = Math.floor(Math.random() * (" + maxValue + ")) + 1; ";
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
        // Переход — через глобальный скрипт nlbGoto (см. createNlbGotoScript); return прерывает
        // остальные модификации, как в INSTEAD
        return "nlbGoto({to: '" + getJsiqArticleId(locationId) + "'}); return; ";
    }

    @Override
    protected String decorateSnapshotOperation(String snapshotId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCountGetOperation(String variableName, int statType) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCountResetOperation() {
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
    protected String decorateExistence(final String decoratedVariable) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
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
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, boolean isTechnical, int pageNumber, Theme theme) {
        return linkText;
    }

    @Override
    protected String decorateLinkGoTo(String linkId, String linkText, String linkSource, int sourcePageNumber, String linkTarget, int targetPageNumber, boolean isTechnical, Theme theme) {
        return Integer.toString(targetPageNumber - 1);
    }

    @Override
    protected String decoratePageEnd(boolean isFinish) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return "vars." + variableName + " = true; ";
    }

    @Override
    protected String decorateLinkVisitStateVariable(String linkVisitStateVariable) {
        return "vars." + linkVisitStateVariable + " = true; ";
    }

    @Override
    protected String decoratePageVariable(String variableName) {
        return "vars." + variableName + " = true; ";
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
            return caption;
        } else {
            return Constants.EMPTY_STRING;
        }
    }

    @Override
    protected String decoratePageNotes(String notes) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, final boolean imageBackground, Theme theme) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX, Theme theme) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    protected String expandVariables(List<TextChunk> textChunks, Theme theme) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append(textChunk.getText());
                    break;
                case ACTION_TEXT:
                case VARIABLE:
                    result.append("$").append(textChunk.getText()).append("$");
                    break;
                case NEWLINE:
                    result.append(" <br/>").append(getLineSeparator());
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
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber, Theme theme) {
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
}
