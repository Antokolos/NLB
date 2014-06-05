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
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.export.LinkBuildingBlocks;
import com.nlbhub.nlb.domain.export.NLBBuildingBlocks;
import com.nlbhub.nlb.domain.export.PageBuildingBlocks;
import com.nlbhub.nlb.domain.export.xml.beans.jsiq2.*;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.JaxbMarshaller;
import com.nlbhub.nlb.util.StringHelper;

import java.util.Date;
import java.util.List;

/**
 * The JSIQ2ExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 6/01/14
 */
public class JSIQ2ExportManager extends XMLExportManager {
    public JSIQ2ExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    /*
    * Code to generate CDATA inside tags:
    * http://stackoverflow.com/questions/7536973/jaxb-marshalling-and-unmarshalling-cdata
DocumentBuilderFactory docBuilderFactory =
DocumentBuilderFactory.newInstance();
Document document =
docBuilderFactory.newDocumentBuilder().newDocument();

// Marshall the feed object into the empty document.
jaxbMarshaller.marshal(jaxbObject, document);

// Transform the DOM to the output stream
// TransformerFactory is not thread-safe
StringWriter writer = new StringWriter();
TransformerFactory transformerFactory =
TransformerFactory.newInstance();
Transformer nullTransformer = transformerFactory.newTransformer();
nullTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
nullTransformer.setOutputProperty(
OutputKeys.CDATA_SECTION_ELEMENTS,
 "myElement myOtherElement");
nullTransformer.transform(new DOMSource(document),
 new StreamResult(writer));
    */
    @Override
    protected JaxbMarshaller createMarshaller() {
        return new JaxbMarshaller(Action.class, Article.class, Book.class, Metadata.class, Script.class);
    }

    @Override
    protected Object createRootObject(NLBBuildingBlocks nlbBlocks) {
        Book book = new Book();
        String lastSaveString = (new Date()).toString();
        for (PageBuildingBlocks pageBuildingBlocks : nlbBlocks.getPagesBuildingBlocks()) {
            book.addArticle(createArticle(pageBuildingBlocks, lastSaveString));
        }
        return book;
    }

    private Article createArticle(PageBuildingBlocks pageBlocks, String lastSaveString) {
        Article article = new Article();
        Metadata metadata = new Metadata();
        metadata.setLastSave(lastSaveString);
        article.setId(pageBlocks.getPageNumber());
        article.setMetadata(metadata);
        article.setText(pageBlocks.getPageTextStart());
        Script pageVariableScript = new Script();
        pageVariableScript.setType("pvar");
        pageVariableScript.setValue(pageBlocks.getPageVariable());
        article.addScript(pageVariableScript);
        Script pageModificationsScript = new Script();
        pageModificationsScript.setType("pmod");
        pageModificationsScript.setValue(pageBlocks.getPageModifications());
        article.addScript(pageModificationsScript);
        Script onLoadScript = new Script();
        onLoadScript.setType("onload");
        onLoadScript.setValue(
            //"<![CDATA[pvar(); pmod(); ]]>"
            "pvar(); pmod();"
        );
        article.addScript(onLoadScript);
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (int i = 0; i < linksBlocks.size(); i++) {
            Action action = new Action();
            final LinkBuildingBlocks linkBlocks = linksBlocks.get(i);
            final boolean hasConstraint = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
            if (hasConstraint) {
                Script actionConditionScript = new Script();
                actionConditionScript.setType("condition_" + (i*10));
                actionConditionScript.setValue(
                    //"<![CDATA[return (" + linkBlocks.getLinkConstraint() + ");]]>"
                    "return (" + linkBlocks.getLinkConstraint() + ");"
                );
                article.addScript(actionConditionScript);
                action.setIf("condition_" + (i * 10));
            }
            action.setGoto(linkBlocks.getLinkGoTo());
            action.setValue(linkBlocks.getLinkStart());
            Script actionVariableScript = new Script();
            actionVariableScript.setType("var_" + (i * 10));
            actionVariableScript.setValue(
                //"<![CDATA[" + linkBlocks.getLinkVariable() + "]]>"
                linkBlocks.getLinkVariable()
            );
            article.addScript(actionVariableScript);
            Script actionModificationScript = new Script();
            actionModificationScript.setType("mod_" + (i * 10));
            actionModificationScript.setValue(
                //"<![CDATA[" + linkBlocks.getLinkModifications() + "]]>"
                linkBlocks.getLinkModifications()
            );
            article.addScript(actionModificationScript);
            Script actionDoScript = new Script();
            actionDoScript.setType("do_" + (i * 10));
            actionDoScript.setValue(
                //"<![CDATA[" + "var_" + (i * 10) + "(); " + "mod_" + (i * 10) + "(); ]]>"
                "var_" + (i * 10) + "(); " + "mod_" + (i * 10) + "(); "
            );
            article.addScript(actionDoScript);
            action.setDo("do_" + (i * 10));
            article.addAction(action);
        }
        return article;
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return "vars." + variableName + " = " + variableValue;
    }

    @Override
    protected String decorateDelObj(String objectId, String objectName) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateAddObj(String objectId, String objectName) {
        return Constants.EMPTY_STRING;
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
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageCaption(String caption) {
        return caption;
    }

    @Override
    protected String decoratePageTextStart(String pageText) {
        return pageText;
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
}
