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

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.domain.ModificationImpl;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;
import com.nlbhub.nlb.util.VarFinder;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;

/**
 * The ExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/4/13
 */
public abstract class ExportManager {
    private static final String EQ_PLACEHOLDER = "000369f3-943a-4696-9c20-f6471b5c131d";
    private static final String NEQ_PLACEHOLDER = "211c47bf-dad2-49d1-9ab0-162082d2664c";
    private static final String GT_PLACEHOLDER = "74ea093d-1918-4e02-b2bd-a929d7db4b0c";
    private static final String GTE_PLACEHOLDER = "94e69065-f584-4d98-a6c9-667e2c6dc3ee";
    private static final String LT_PLACEHOLDER = "c5549d03-b258-4f77-b760-5d13bf981780";
    private static final String LTE_PLACEHOLDER = "17190e04-4537-414f-9c57-25676a99ad6e";
    private static final String NOT_PLACEHOLDER = "2164a414-ba30-45b4-baa3-c32e194304db";
    private static final String OR_PLACEHOLDER = "179ef88a-88b7-4ad2-8dfa-d2040debde73";
    private static final String AND_PLACEHOLDER = "f0e77ec8-a270-4a3f-8b8f-1ade38988f37";

    public static final String EMPTY_STRING = "";
    public static final String UTF_8 = "UTF-8";
    public static final String UTF_16LE = "UTF-16LE";
    public static final String CP1251 = "CP1251";
    private static final String MAIN_DATA_KEY = Constants.MAIN_MODULE_NAME;
    private String m_encoding;
    private Map<String, ExportData> m_exportDataMap;
    private Map<String, Variable.DataType> m_dataTypeMap;

    private class ExportData {
        private NonLinearBook m_nlb;
        private Page m_modulePage;
        private String m_moduleConstraintText;
        private Integer m_modulePageNumber;
        private List<Page> m_pageList = new ArrayList<>();
        private List<Obj> m_objList = new ArrayList<>();
        private Map<String, Integer> m_idToPageNumberMap = new HashMap<>();
        private Map<String, String> m_objNamToIdMap = new HashMap<>();
        private ExportData m_parentED;

        private ExportData(
                NonLinearBook nlb,
                Page modulePage,
                String moduleConstraintText,
                Integer modulePageNumber,
                ExportData parentED
        ) {
            m_nlb = nlb;
            m_modulePage = modulePage;
            m_moduleConstraintText = moduleConstraintText;
            m_modulePageNumber = modulePageNumber;
            m_parentED = parentED;
        }

        private int size() {
            int result = 0;
            for (final Page page : m_pageList) {
                if (page.getModule().isEmpty()) {
                    result++;
                } else {
                    NonLinearBook.BookStatistics stats = page.getModule().getBookStatistics();
                    // Pages in submodule plus module page itself
                    result += stats.getPagesCount() + 1;
                }
            }
            return result;
        }

        private Map<String, ExportData> init() throws NLBConsistencyException {
            Map<String, ExportData> result = new HashMap<>();
            m_idToPageNumberMap.put(m_modulePage.getId(), m_modulePageNumber);
            result.put(m_modulePage.getId(), this);
            String startPoint = m_nlb.getStartPoint();
            final Page startPage = m_nlb.getPageById(startPoint);
            if (startPage == null || startPage.isDeleted()) {
                throw new NLBConsistencyException(
                        "Startpoint error: start page is deleted or missing"
                );
            }
            m_pageList.add(startPage);
            int pageNumber = m_modulePageNumber + 1;
            m_idToPageNumberMap.put(startPage.getId(), pageNumber);
            Variable modConstr = m_nlb.getVariableById(startPage.getModuleConstrId());
            if (startPage.getModule().isEmpty()) {
                pageNumber++;
            } else {
                ExportData moduleED = (
                        new ExportData(
                                startPage.getModule(),
                                startPage,
                                (modConstr != null) ? modConstr.getValue() : Constants.EMPTY_STRING,
                                pageNumber,
                                this
                        )
                );
                result.putAll(moduleED.init());
                m_idToPageNumberMap.put(moduleED.getNlb().getStartPoint(), pageNumber + 1);
                pageNumber += moduleED.size() + 1;
            }
            // Pages auto-shuffling is the interesting side effect of using UUIDs as page ids :)
            // So no page number shuffling is needed.
            // Please note that start page is always go first.
            for (final Page page : m_nlb.getPages().values()) {
                if (!page.isDeleted() && !page.getId().equals(startPoint)) {
                    m_pageList.add(page);
                    m_idToPageNumberMap.put(page.getId(), pageNumber);
                    Variable pageModConstr = m_nlb.getVariableById(page.getModuleConstrId());
                    if (page.getModule().isEmpty()) {
                        pageNumber++;
                    } else {
                        ExportData moduleED = (
                                new ExportData(
                                        page.getModule(),
                                        page,
                                        (pageModConstr != null)
                                                ? pageModConstr.getValue()
                                                : Constants.EMPTY_STRING,
                                        pageNumber,
                                        this
                                )
                        );
                        result.putAll(moduleED.init());
                        m_idToPageNumberMap.put(moduleED.getNlb().getStartPoint(), pageNumber + 1);
                        pageNumber += moduleED.size() + 1;
                    }
                }
            }
            for (final Obj obj : m_nlb.getObjs().values()) {
                if (!obj.isDeleted()) {
                    m_objList.add(obj);
                    m_objNamToIdMap.put(obj.getName(), obj.getId());
                }
            }
            return result;
        }

        private Page getModulePage() {
            return m_modulePage;
        }

        private String getModuleConstraintText() {
            return m_moduleConstraintText;
        }

        private NonLinearBook getNlb() {
            return m_nlb;
        }

        private List<Page> getPageList() {
            return m_pageList;
        }

        private List<Obj> getObjList() {
            return m_objList;
        }

        private Integer getPageNumber(final String pageId) throws NLBConsistencyException {
            if (m_idToPageNumberMap.containsKey(pageId)) {
                return m_idToPageNumberMap.get(pageId);
            } else {
                if (m_parentED != null) {
                    return m_parentED.getPageNumber(pageId);
                } else {
                    throw new NLBConsistencyException(
                            "Page number cannot be determined for pageId = " + pageId
                    );
                }
            }
        }

        private String getObjId(final String objName) throws NLBConsistencyException {
            if (m_objNamToIdMap.containsKey(objName)) {
                return m_objNamToIdMap.get(objName);
            } else {
                if (m_parentED != null) {
                    return m_parentED.getObjId(objName);
                } else {
                    throw new NLBConsistencyException(
                            "Obj Id cannot be determined for objName = " + objName
                    );
                }
            }
        }

        private Map<String, String> getObjNamToIdMap() {
            return m_objNamToIdMap;
        }
    }

    protected ExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        try {
            m_encoding = encoding;
            ExportData mainExportData = (
                    new ExportData(
                            nlb,
                            new RootModulePage(nlb, MAIN_DATA_KEY),
                            Constants.EMPTY_STRING,
                            0,
                            null
                    )
            );
            m_exportDataMap = mainExportData.init();
            m_dataTypeMap = nlb.getVariableDataTypes();
        } catch (NLBConsistencyException e) {
            throw new NLBExportException("Export error", e);
        }
    }

    public String getEncoding() {
        return m_encoding;
    }

    public abstract void exportToFile(final File targetFile) throws NLBExportException;

    /*
    The current version of createNLBBuildingBlocks places pages in continuous order, i.e.
    1, 2, 3, 4, 5, ..., n
    This is useful when creating things like game-books, because in this case module structure
    becomes completely transparent just like as if all NLB was written in one big module.
    If you want instead of it place modules sequentially, use the following code:

    protected NLBBuildingBlocks createNLBBuildingBlocks() throws NLBConsistencyException {
        NLBBuildingBlocks blocks = new NLBBuildingBlocks();
        for (Map.Entry<String, ExportData> entry : m_exportDataMap.entrySet()) {
            for (final Obj obj : entry.getValue().getObjList()) {
                blocks.addObjBuildingBlocks(createObjBuildingBlocks(obj, entry.getValue()));
            }
            for (final Page page : entry.getValue().getPageList()) {
                blocks.addPageBuildingBlocks(createPageBuildingBlocks(page, entry.getValue()));
            }
        }

        return blocks;
    }

    Please note that in this case page numbering will most likely be NOT sequential:
    Example:
    1, 2, 6, 3, 4, 5
    In this example pages 3, 4 and 5 belongs to the module that corresponds to the page number 2,
    pages 1, 2 and 6 belongs to the main NLB module.
     */

    protected NLBBuildingBlocks createNLBBuildingBlocks() throws NLBConsistencyException {
        return createNLBBuildingBlocks(m_exportDataMap.get(MAIN_DATA_KEY));
    }

    private NLBBuildingBlocks createNLBBuildingBlocks(
            final ExportData exportData
    ) throws NLBConsistencyException {
        NLBBuildingBlocks blocks = new NLBBuildingBlocks();
        //stringBuilder.append("#mode quote").append(LINE_SEPARATOR);
        for (final Obj obj : exportData.getObjList()) {
            blocks.addObjBuildingBlocks(createObjBuildingBlocks(obj, exportData));
        }
        for (final Page page : exportData.getPageList()) {
            if (page.getModule().isEmpty()) {
                blocks.addPageBuildingBlocks(createPageBuildingBlocks(page, exportData));
            } else {
                blocks.addPageBuildingBlocks(createPageBuildingBlocks(page, exportData));
                blocks.addNLBBuildingBlocks(
                        createNLBBuildingBlocks(m_exportDataMap.get(page.getId()))
                );
            }
        }

        return blocks;
    }

    private PageBuildingBlocks createPageBuildingBlocks(
            final Page page,
            final ExportData exportData
    ) throws NLBConsistencyException {
        PageBuildingBlocks blocks = new PageBuildingBlocks();
        final Integer pageNumber = exportData.getPageNumber(page.getId());
        blocks.setPageLabel(decoratePageLabel(page.getId(), pageNumber));
        blocks.setPageNumber(decoratePageNumber(pageNumber));
        blocks.setPageComment(decoratePageComment(page.getCaption()));
        blocks.setPageCaption(decoratePageCaption(page.getCaption()));
        Page parentPage = page.getCurrentNLB().getParentPage();
        blocks.setPageImage(
                decoratePageImage(
                        getPageImagePath((parentPage != null) ? parentPage.getId() : null, page.getImageFileName())
                )
        );
        blocks.setUseCaption(page.isUseCaption());
        blocks.setPageTextStart(decoratePageTextStart(page.getText()));
        blocks.setPageTextEnd(decoratePageTextEnd());
        if (!StringHelper.isEmpty(page.getVarId())) {
            Variable variable = exportData.getNlb().getVariableById(page.getVarId());
            // TODO: Add cases with deleted pages/links/variables etc. to the unit test
            if (!variable.isDeleted()) {
                blocks.setPageVariable(decoratePageVariable(variable.getName()));
            } else {
                blocks.setPageVariable(EMPTY_STRING);
            }
        } else {
            blocks.setPageVariable(EMPTY_STRING);
        }
        blocks.setPageModifications(
                decoratePageModifications(
                        buildModificationsText(EMPTY_STRING, page.getModifications(), exportData)
                )
        );
        blocks.setPageEnd(decoratePageEnd());
        List<String> containedObjIds = page.getContainedObjIds();
        if (!containedObjIds.isEmpty()) {
            for (String containedObjId : containedObjIds) {
                blocks.addContainedObjId(decorateContainedObjId(containedObjId));
            }
        }
        List<Link> links = page.getLinks();
        for (final Link link : links) {
            if (!link.isDeleted()) {
                LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(link, exportData);
                blocks.addLinkBuildingBlocks(linkBuildingBlocks);
            }
        }
        if (m_exportDataMap.containsKey(page.getId())) {
            // If this page is module page for someone, create traverse link on the fly
            ExportData targetED = m_exportDataMap.get(page.getId());
            Link link = (
                    new LinkLw(
                            LinkLw.Type.Traverse,
                            targetED.getNlb().getStartPoint(),
                            page,
                            page.getTraverseTexts(),
                            page.getModuleConstrId(),
                            page.isAutoTraverse(),
                            true,
                            false
                    )
            );
            LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(link, exportData);
            blocks.addLinkBuildingBlocks(linkBuildingBlocks);
        }
        if (
                page.isLeaf()
                        && !exportData.getModulePage().getId().equals(MAIN_DATA_KEY)
                        && page.shouldReturn()
                ) {
            // Create return link on the fly. Return links for leafs does not have any constraints,
            // i.e. it is shown always
            Link link = (
                    new LinkLw(
                            LinkLw.Type.Return,
                            StringHelper.isEmpty(page.getReturnPageId())
                                    ? exportData.getModulePage().getId()
                                    : page.getReturnPageId(),
                            page,
                            page.getReturnTexts(),
                            Constants.EMPTY_STRING,
                            page.isAutoReturn(),
                            true,
                            false
                    )
            );
            LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(link, exportData);
            blocks.addLinkBuildingBlocks(linkBuildingBlocks);
        } else if (
                !StringHelper.isEmpty(exportData.getModulePage().getModuleConstrId())
                        && !exportData.getModulePage().getId().equals(MAIN_DATA_KEY)
                        && page.shouldReturn()
                ) {
            // If page has module constraint, than module return links should be added to the
            // each page of the module.
            // These links should have constraints in form of 'NOT (module_constraint)'
            // (i.e. negative constraints)
            Link link = (
                    new LinkLw(
                            LinkLw.Type.Return,
                            StringHelper.isEmpty(page.getReturnPageId())
                                    ? exportData.getModulePage().getId()
                                    : page.getReturnPageId(),
                            page,
                            page.getReturnTexts(),
                            exportData.getModulePage().getModuleConstrId(),
                            page.isAutoReturn(),
                            false,
                            false
                    )
            );
            LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(link, exportData);
            blocks.addLinkBuildingBlocks(linkBuildingBlocks);
        }
        return blocks;
    }

    final ObjBuildingBlocks createObjBuildingBlocks(
            final Obj obj,
            final ExportData exportData
    ) throws NLBConsistencyException {
        ObjBuildingBlocks blocks = new ObjBuildingBlocks();
        blocks.setObjLabel(decorateObjLabel(obj.getId()));
        blocks.setObjComment(decorateObjComment(obj.getName()));
        blocks.setObjStart(decorateObjStart());
        blocks.setObjName(decorateObjName(obj.getName()));
        blocks.setObjDisp(decorateObjDisp(obj.getDisp()));
        blocks.setObjText(decorateObjText(obj.getText()));
        blocks.setTakable(obj.isTakable());
        blocks.setObjTak(decorateObjTak(obj.getName()));
        blocks.setObjInv(decorateObjInv(obj.getName()));
        blocks.setObjActStart(decorateObjActStart());
        if (!StringHelper.isEmpty(obj.getVarId())) {
            Variable variable = exportData.getNlb().getVariableById(obj.getVarId());
            // TODO: Add cases with deleted pages/links/variables etc. to the unit test
            if (!variable.isDeleted()) {
                blocks.setObjVariable(decorateObjVariable(variable.getName()));
            } else {
                blocks.setObjVariable(EMPTY_STRING);
            }
        } else {
            blocks.setObjVariable(EMPTY_STRING);
        }
        blocks.setObjModifications(
                decorateObjModifications(
                        buildModificationsText(EMPTY_STRING, obj.getModifications(), exportData)
                )
        );
        blocks.setObjActEnd(decorateObjActEnd());
        blocks.setObjUseStart(decorateObjUseStart());
        blocks.setObjUseEnd(decorateObjUseEnd());
        blocks.setObjEnd(decorateObjEnd());
        blocks.setObjObjStart(decorateObjObjStart());
        List<String> containedObjIds = obj.getContainedObjIds();
        if (!containedObjIds.isEmpty()) {
            for (String containedObjId : containedObjIds) {
                blocks.addContainedObjId(decorateContainedObjId(containedObjId));
            }
        }
        blocks.setObjObjEnd(decorateObjObjEnd());
        List<Link> links = obj.getLinks();
        for (final Link link : links) {
            if (!link.isDeleted()) {
                UseBuildingBlocks useBuildingBlocks = createUseBuildingBlocks(link, exportData);
                blocks.addUseBuildingBlocks(useBuildingBlocks);
            }
        }
        return blocks;
    }

    protected String decorateObjLabel(String id) {
        return EMPTY_STRING;
    }

    protected String decorateObjComment(String name) {
        return EMPTY_STRING;
    }

    protected String decorateObjStart() {
        return EMPTY_STRING;
    }

    protected String decorateObjName(String name) {
        return EMPTY_STRING;
    }

    protected String decorateObjDisp(String disp) {
        return EMPTY_STRING;
    }

    protected String decorateObjText(String text) {
        return EMPTY_STRING;
    }

    protected String decorateObjTak(String takString) {
        return EMPTY_STRING;
    }

    protected String decorateObjInv(String invString) {
        return EMPTY_STRING;
    }

    protected String decorateObjVariable(String variableName) {
        return EMPTY_STRING;
    }

    protected String decorateObjModifications(String modificationsText) {
        return EMPTY_STRING;
    }

    protected String decorateObjActStart() {
        return EMPTY_STRING;
    }

    protected String decorateObjActEnd() {
        return EMPTY_STRING;
    }

    protected String decorateObjUseStart() {
        return EMPTY_STRING;
    }

    protected String decorateObjUseEnd() {
        return EMPTY_STRING;
    }

    protected String decorateObjObjStart() {
        return EMPTY_STRING;
    }

    protected String decorateObjObjEnd() {
        return EMPTY_STRING;
    }

    protected String decorateUseTarget(String targetId) {
        return EMPTY_STRING;
    }

    protected String decorateUseVariable(String variableName) {
        return EMPTY_STRING;
    }

    protected String decorateUseModifications(String modificationsText) {
        return EMPTY_STRING;
    }

    protected String decorateObjEnd() {
        return EMPTY_STRING;
    }

    protected String decorateContainedObjId(String containedObjId) {
        return EMPTY_STRING;
    }

    private LinkBuildingBlocks createLinkBuildingBlocks(
            final Link link,
            final ExportData exportData
    ) throws NLBConsistencyException {
        LinkBuildingBlocks blocks = new LinkBuildingBlocks();
        blocks.setAuto(link.isAuto());
        blocks.setLinkLabel(decorateLinkLabel(link.getId(), link.getText()));
        blocks.setLinkComment(decorateLinkComment(link.getText()));
        // TODO: exportData.getIdToPageNumberMap().get(link.getTarget()) can produce NPE for return links
        blocks.setLinkStart(
                decorateLinkStart(
                        link.getId(),
                        link.getText(),
                        exportData.getPageNumber(link.getTarget())
                )
        );
        Variable variable = exportData.getNlb().getVariableById(link.getVarId());
        if (variable != null && !variable.isDeleted()) {
            blocks.setLinkVariable(decorateLinkVariable(variable.getName()));
        } else {
            blocks.setLinkVariable(EMPTY_STRING);
        }
        Variable constraint = exportData.getNlb().getVariableById(link.getConstrId());
        if (
                (constraint != null && !constraint.isDeleted())
                        || (
                        link.isObeyToModuleConstraint()
                                && !StringHelper.isEmpty(exportData.getModuleConstraintText())
                )
                ) {
            blocks.setLinkConstraint(
                    translateConstraintBody(
                            (constraint != null) ? constraint.getValue().trim() : Constants.EMPTY_STRING,
                            link.isPositiveConstraint(),
                            link.isObeyToModuleConstraint(),
                            exportData.getModuleConstraintText()
                    )
            );
        } else {
            blocks.setLinkConstraint(EMPTY_STRING);
        }
        blocks.setLinkModifications(
                decorateLinkModifications(
                        buildModificationsText("    ", link.getModifications(), exportData)
                )
        );
        blocks.setLinkGoTo(
                decorateLinkGoTo(
                        link.getId(),
                        link.getText(),
                        link.getTarget(),
                        exportData.getPageNumber(link.getTarget())
                )
        );
        blocks.setLinkEnd(
                decorateLinkEnd()
        );
        return blocks;
    }

    private UseBuildingBlocks createUseBuildingBlocks(
            final Link link,
            final ExportData exportData
    ) throws NLBConsistencyException {
        UseBuildingBlocks blocks = new UseBuildingBlocks();
        blocks.setUseTarget(decorateUseTarget(link.getTarget()));
        Variable variable = exportData.getNlb().getVariableById(link.getVarId());
        if (variable != null && !variable.isDeleted()) {
            blocks.setUseVariable(decorateUseVariable(variable.getName()));
        } else {
            blocks.setUseVariable(EMPTY_STRING);
        }
        Variable constraint = exportData.getNlb().getVariableById(link.getConstrId());
        if (
                (constraint != null && !constraint.isDeleted())
                        || (
                        link.isObeyToModuleConstraint()
                                && !StringHelper.isEmpty(exportData.getModuleConstraintText())
                )
                ) {
            blocks.setUseConstraint(
                    translateConstraintBody(
                            (constraint != null) ? constraint.getValue().trim() : Constants.EMPTY_STRING,
                            link.isPositiveConstraint(),
                            link.isObeyToModuleConstraint(),
                            exportData.getModuleConstraintText()
                    )
            );
        } else {
            blocks.setUseConstraint(EMPTY_STRING);
        }
        blocks.setUseModifications(
                decorateUseModifications(
                        buildModificationsText("    ", link.getModifications(), exportData)
                )
        );
        return blocks;
    }

    private String translateConstraintBody(
            String constraintText,
            boolean isPositiveConstraint,
            boolean shouldObeyToModuleConstraint,
            String moduleConstraintText
    ) {
        String constraintBody = (
                (shouldObeyToModuleConstraint && !StringHelper.isEmpty(moduleConstraintText))
                        ? (
                        (!StringHelper.isEmpty(constraintText))
                                ? moduleConstraintText + "&&" + constraintText
                                : moduleConstraintText
                )
                        : constraintText
        );
        String translatedConstraint = translateExpressionBody(constraintBody);
        return (
                isPositiveConstraint
                        ? translatedConstraint
                        : decorateNot() + " (" + translatedConstraint + ")"
        );
    }

    private String translateExpressionBody(String expressionText) {
        String expression = expressionText;
        final Collection<String> expressionVars = VarFinder.findVariableNames(expression);

        // Crude but effective translation code
        expression = expression.replaceAll("\\s*==\\s*", " " + EQ_PLACEHOLDER + " ");
        expression = expression.replaceAll("\\s*!=\\s*", " " + NEQ_PLACEHOLDER + " ");
        expression = expression.replaceAll("\\s*>=\\s*", " " + GTE_PLACEHOLDER + " ");
        expression = expression.replaceAll("\\s*<=\\s*", " " + LTE_PLACEHOLDER + " ");
        expression = expression.replaceAll("\\s*>\\s*", " " + GT_PLACEHOLDER + " ");
        expression = expression.replaceAll("\\s*<\\s*", " " + LT_PLACEHOLDER + " ");
        expression = expression.replaceAll("\\s*&&\\s*", " " + AND_PLACEHOLDER + " ");
        expression = expression.replaceAll("\\s*\\|\\|\\s*", " " + OR_PLACEHOLDER + " ");
        expression = expression.replaceAll("\\s*!\\s*", " " + NOT_PLACEHOLDER + " ");

        expression = expression.replaceAll(EQ_PLACEHOLDER, decorateEq());
        expression = expression.replaceAll(NEQ_PLACEHOLDER, decorateNEq());
        expression = expression.replaceAll(GT_PLACEHOLDER, decorateGt());
        expression = expression.replaceAll(GTE_PLACEHOLDER, decorateGte());
        expression = expression.replaceAll(LT_PLACEHOLDER, decorateLt());
        expression = expression.replaceAll(LTE_PLACEHOLDER, decorateLte());
        expression = expression.replaceAll(AND_PLACEHOLDER, decorateAnd());
        expression = expression.replaceAll(OR_PLACEHOLDER, decorateOr());
        expression = expression.replaceAll(NOT_PLACEHOLDER, decorateNot());
        for (final String expressionVar : expressionVars) {
            expression = (
                    expression.replaceAll(
                            "\\b" + expressionVar + "\\b",
                            Matcher.quoteReplacement(decorateVariable(expressionVar))
                    )
            );
        }
        return expression;
    }

    private String decorateVariable(String constraintVar) {
        Variable.DataType dataType = m_dataTypeMap.get(constraintVar);
        switch (dataType) {
            case BOOLEAN:
                return decorateBooleanVar(constraintVar);
            case NUMBER:
                return decorateNumberVar(constraintVar);
            case STRING:
                return decorateStringVar(constraintVar);
            case AUTO:
            default:
                return decorateAutoVar(constraintVar);
        }
    }

    private String decorateAutoVar(String constraintVar) {
        return decorateNumberVar(constraintVar);
    }

    private String buildModificationsText(
            final String indentString,
            final List<Modification> modifications,
            final ExportData exportData
    ) throws NLBConsistencyException {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final Modification modification : modifications) {
            if (!modification.isDeleted()) {
                stringBuilder.append(indentString);
                if (modification.getType().equals(ModificationImpl.Type.ADD)) {
                    Variable expression = (
                            exportData.getNlb().getVariableById(modification.getExprId())
                    );
                    if (expression == null || expression.isDeleted()) {
                        throw new NLBConsistencyException(
                                "Expression with id = " + modification.getExprId()
                                        + "cannot be found for modification"
                                        + modification.getFullId()
                        );
                    }
                    final String objId = exportData.getObjId(expression.getValue());
                    stringBuilder.append(
                            decorateAddObj(
                                    objId,
                                    expression.getValue(),
                                    exportData.getNlb().getObjById(objId).getDisp()
                            )
                    );
                } else if (modification.getType().equals(ModificationImpl.Type.REMOVE)) {
                    Variable expression = (
                            exportData.getNlb().getVariableById(modification.getExprId())
                    );
                    if (expression == null || expression.isDeleted()) {
                        throw new NLBConsistencyException(
                                "Expression with id = " + modification.getExprId()
                                        + "cannot be found for modification"
                                        + modification.getFullId()
                        );
                    }
                    final String objId = exportData.getObjId(expression.getValue());
                    stringBuilder.append(
                            decorateDelObj(
                                    objId,
                                    expression.getValue(),
                                    exportData.getNlb().getObjById(objId).getDisp()
                            )
                    );
                } else if (modification.getType().equals(ModificationImpl.Type.ASSIGN)) {
                    Variable variable = (
                            exportData.getNlb().getVariableById(modification.getVarId())
                    );
                    if (variable == null || variable.isDeleted()) {
                        throw new NLBConsistencyException(
                                "Variable with id = " + modification.getVarId()
                                        + "cannot be found for modification"
                                        + modification.getFullId()
                        );
                    }
                    Variable expression = (
                            exportData.getNlb().getVariableById(modification.getExprId())
                    );
                    if (expression == null || expression.isDeleted()) {
                        throw new NLBConsistencyException(
                                "Expression with id = " + modification.getExprId()
                                        + "cannot be found for modification"
                                        + modification.getFullId()
                        );
                    }
                    // Left part of assignment should be decorated as ordinary variable, with the exception of String
                    stringBuilder.append(
                            decorateAssignment(
                                    (variable.getDataType() == Variable.DataType.STRING)
                                            ? decorateStringVar(variable.getName())
                                            : decorateAutoVar(variable.getName()),
                                    translateExpressionBody(expression.getValue())
                            )
                    );
                } else {
                    throw new NLBConsistencyException(
                            "Operation has unknown type for modification with id = "
                                    + modification.getFullId()
                    );
                }
            }
        }
        return stringBuilder.toString();
    }


    protected abstract String decorateAssignment(String variableName, String variableValue);

    protected abstract String decorateDelObj(String objectId, String objectName, String objectDisplayName);

    protected abstract String decorateAddObj(String objectId, String objectName, String objectDisplayName);

    protected abstract String decorateEq();

    protected abstract String decorateNEq();

    protected abstract String decorateGt();

    protected abstract String decorateGte();

    protected abstract String decorateLt();

    protected abstract String decorateLte();

    protected abstract String decorateNot();

    protected abstract String decorateOr();

    protected abstract String decorateAnd();

    protected abstract String decorateBooleanVar(String constraintVar);

    protected abstract String decorateStringVar(String constraintVar);

    protected abstract String decorateNumberVar(String constraintVar);

    protected abstract String decorateLinkLabel(String linkId, String linkText);

    protected abstract String decorateLinkComment(String comment);

    protected abstract String decorateLinkStart(String linkId, String linkText, int pageNumber);

    protected abstract String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkTarget,
            int targetPageNumber
    );

    protected String decorateLinkEnd() {
        return Constants.EMPTY_STRING;
    }

    protected abstract String decoratePageEnd();

    protected abstract String decorateLinkVariable(String variableName);

    protected abstract String decoratePageVariable(final String variableName);

    protected abstract String decoratePageModifications(final String modificationsText);

    protected abstract String decorateLinkModifications(final String modificationsText);

    protected abstract String decoratePageCaption(String caption);

    protected abstract String decoratePageImage(String pageImagePath);

    protected String getPageImagePath(String parentPageId, String imageFileName) {
        if (StringHelper.isEmpty(imageFileName)) {
            return Constants.EMPTY_STRING;
        } else {
            if (parentPageId == null) {
                return "images/" + imageFileName;
            } else {
                return "images/" + parentPageId + "/" + imageFileName;
            }
        }
    }

    protected abstract String decoratePageTextStart(String pageText);

    protected abstract String decoratePageTextEnd();

    protected abstract String decoratePageLabel(String labelText, int pageNumber);

    protected abstract String decoratePageNumber(int pageNumber);

    protected abstract String decoratePageComment(String comment);
}
