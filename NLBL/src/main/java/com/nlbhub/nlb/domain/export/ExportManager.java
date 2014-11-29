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
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.MultiLangString;
import com.nlbhub.nlb.util.StringHelper;
import com.nlbhub.nlb.util.VarFinder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("(^.*\\D|^)(\\d*)(\\..*)$");

    public static final String EMPTY_STRING = Constants.EMPTY_STRING;
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

        private String getObjId(final String objName) {
            if (m_objNamToIdMap.containsKey(objName)) {
                return m_objNamToIdMap.get(objName);
            } else {
                if (m_parentED != null) {
                    return m_parentED.getObjId(objName);
                } else {
                    return null;
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

    protected NLBBuildingBlocks createNLBBuildingBlocks() throws NLBConsistencyException, NLBExportException {
        return createNLBBuildingBlocks(m_exportDataMap.get(MAIN_DATA_KEY));
    }

    private NLBBuildingBlocks createNLBBuildingBlocks(
            final ExportData exportData
    ) throws NLBConsistencyException, NLBExportException {
        NLBBuildingBlocks blocks = new NLBBuildingBlocks();
        //stringBuilder.append("#mode quote").append(LINE_SEPARATOR);
        for (final Obj obj : exportData.getObjList()) {
            blocks.addObjBuildingBlocks(createObjBuildingBlocks(createPreprocessedObj(obj), exportData));
        }
        for (final Page page : exportData.getPageList()) {
            if (page.getModule().isEmpty()) {
                blocks.addPageBuildingBlocks(createPageBuildingBlocks(createPreprocessedPage(page), exportData));
            } else {
                blocks.addPageBuildingBlocks(createPageBuildingBlocks(createPreprocessedPage(page), exportData));
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
    ) throws NLBConsistencyException, NLBExportException {
        NonLinearBook nlb = exportData.getNlb();
        PageBuildingBlocks blocks = new PageBuildingBlocks();
        final Integer pageNumber = exportData.getPageNumber(page.getId());
        blocks.setPageLabel(decoratePageLabel(page.getId(), pageNumber));
        blocks.setPageNumber(decoratePageNumber(pageNumber));
        blocks.setPageComment(decoratePageComment(page.getCaption()));
        blocks.setPageCaption(decoratePageCaption(page.getCaption(), page.isUseCaption()));
        String imageFileName = ((nlb.isSuppressMedia()) ? Page.DEFAULT_IMAGE_FILE_NAME: page.getImageFileName());
        blocks.setPageImage(decoratePageImage(getImagePath(null, imageFileName, false), page.isImageBackground()));
        String soundFileName = ((nlb.isSuppressMedia()) ? Page.DEFAULT_SOUND_FILE_NAME: page.getSoundFileName());
        blocks.setPageSound(decoratePageSound(getSoundPath(null, soundFileName)));
        blocks.setPageTextStart(decoratePageTextStart(StringHelper.getTextChunks(page.getText())));
        blocks.setPageTextEnd(decoratePageTextEnd());
        if (!StringHelper.isEmpty(page.getVarId())) {
            Variable variable = nlb.getVariableById(page.getVarId());
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
        boolean hasAnim = false;
        if (!containedObjIds.isEmpty()) {
            for (String containedObjId : containedObjIds) {
                Obj obj = nlb.getObjById(containedObjId);
                hasAnim = (hasAnim || obj.isAnimatedImage());
                blocks.addContainedObjId(decorateContainedObjId(containedObjId));
            }
        }
        blocks.setHasObjectsWithAnimatedImages(hasAnim);
        List<Link> links = page.getLinks();
        for (final Link link : links) {
            if (!link.isDeleted()) {
                LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(createPreprocessedLink(link), exportData);
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
            LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(createPreprocessedLink(link), exportData);
            blocks.addLinkBuildingBlocks(linkBuildingBlocks);
        }
        if (page.shouldReturn() && !exportData.getModulePage().getId().equals(MAIN_DATA_KEY)) {
            // Create return link on the fly.
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
                            StringHelper.isEmpty(exportData.getModulePage().getModuleConstrId()),
                            false
                    )
            );
            LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(createPreprocessedLink(link), exportData);
            blocks.addLinkBuildingBlocks(linkBuildingBlocks);
        }
        if (page.isAutowire()) {
            // Add return autowired links on the fly
            for (Page nlbPage : nlb.getPages().values()) {
                if (!nlbPage.isAutowire()) {
                    Link link = (
                            new LinkLw(
                                    LinkLw.Type.AutowiredOut,
                                    nlbPage.getId(),
                                    page,
                                    page.getAutowireOutTexts(),
                                    NonLinearBook.LC_VARID_PREFIX
                                            + page.getId() + NonLinearBook.LC_VARID_SEPARATOR_OUT + nlbPage.getId(),
                                    page.isAutoOut(),
                                    true,
                                    false
                            )
                    );
                    LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(createPreprocessedLink(link), exportData);
                    blocks.addLinkBuildingBlocks(linkBuildingBlocks);
                }
            }
        }
        // Please note, that we are adding autowired inward links even from autowired pages itself
        // if book has full autowire property set to true
        if (nlb.isFullAutowire() || !page.isAutowire()) {
            for (String autowiredPageId : nlb.getAutowiredPagesIds()) {
                // Do not creating links to self
                if (!autowiredPageId.equals(page.getId())) {
                    // Add links for autowired pages on the fly
                    final Page autowiredPage = nlb.getPageById(autowiredPageId);
                    Link link = (
                            new LinkLw(
                                    LinkLw.Type.AutowiredIn,
                                    autowiredPageId,
                                    page,
                                    autowiredPage.getAutowireInTexts(),
                                    autowiredPage.getAutowireInConstrId(),
                                    autowiredPage.isAutoIn(),
                                    true,
                                    false
                            )
                    );
                    LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(createPreprocessedLink(link), exportData);
                    blocks.addLinkBuildingBlocks(linkBuildingBlocks);
                }
            }
        }

        return blocks;
    }

    final ObjBuildingBlocks createObjBuildingBlocks(
            final Obj obj,
            final ExportData exportData
    ) throws NLBConsistencyException, NLBExportException {
        NonLinearBook nlb = obj.getCurrentNLB();
        ObjBuildingBlocks blocks = new ObjBuildingBlocks();
        final boolean menuObj = obj.getLinks().size() == 0;
        blocks.setObjLabel(decorateObjLabel(obj.getId()));
        blocks.setObjComment(decorateObjComment(obj.getName()));
        blocks.setObjStart(decorateObjStart(menuObj));
        blocks.setObjName(decorateObjName(obj.getName()));
        String imageFileName = (nlb.isSuppressMedia()) ? Obj.DEFAULT_IMAGE_FILE_NAME : obj.getImageFileName();
        final String objImage = decorateObjImage(getImagePath(null, imageFileName, obj.isAnimatedImage()));
        final boolean hasImage = StringHelper.notEmpty(imageFileName);
        blocks.setObjImage(objImage);
        blocks.setObjDisp(decorateObjDisp(StringHelper.getTextChunks(obj.getDisp()), hasImage && obj.isImageInInventory()));
        blocks.setObjText(decorateObjText(StringHelper.getTextChunks(obj.getText()), hasImage && obj.isImageInScene()));
        blocks.setTakable(obj.isTakable());
        blocks.setObjTak(decorateObjTak(StringHelper.getTextChunks(obj.getName())));
        blocks.setObjInv(decorateObjInv(menuObj));
        blocks.setObjActStart(decorateObjActStart(StringHelper.getTextChunks(obj.getActText())));
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
        if (!StringHelper.isEmpty(obj.getConstrId())) {
            Variable constraint = exportData.getNlb().getVariableById(obj.getConstrId());
            // TODO: Add cases with deleted pages/links/variables etc. to the unit test
            if (!constraint.isDeleted()) {
                blocks.setObjConstraint(decorateObjConstraint(translateConstraintBody(constraint.getValue(), true, false, Constants.EMPTY_STRING)));
            } else {
                blocks.setObjConstraint(EMPTY_STRING);
            }
        } else {
            blocks.setObjConstraint(EMPTY_STRING);
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
                UseBuildingBlocks useBuildingBlocks = createUseBuildingBlocks(createPreprocessedLink(link), exportData);
                blocks.addUseBuildingBlocks(useBuildingBlocks);
            }
        }
        return blocks;
    }

    /**
     * Escapes some characters in text.
     *
     * @param text text to be escaped
     * @return escaped text
     */
    protected String escapeText(String text) {
        return text;
    }

    private MultiLangString escapeMultiLang(MultiLangString multiLangString) {
        MultiLangString result = MultiLangString.createEmptyText();
        Set<String> keySet = multiLangString.keySet();
        for (String key : keySet) {
            result.put(key, escapeText(multiLangString.get(key)));
        }
        return result;
    }

    private Link createPreprocessedLink(final Link link) {
        return new Link() {
            @Override
            public String getVarId() {
                return link.getVarId();
            }

            @Override
            public String getTarget() {
                return link.getTarget();
            }

            @Override
            public String getText() {
                return escapeText(link.getText());
            }

            @Override
            public MultiLangString getTexts() {
                return escapeMultiLang(link.getTexts());
            }

            @Override
            public String getConstrId() {
                return link.getConstrId();
            }

            @Override
            public String getStroke() {
                return link.getStroke();
            }

            @Override
            public Coords getCoords() {
                return link.getCoords();
            }

            @Override
            public boolean isAuto() {
                return link.isAuto();
            }

            @Override
            public boolean isPositiveConstraint() {
                return link.isPositiveConstraint();
            }

            @Override
            public boolean isObeyToModuleConstraint() {
                return link.isObeyToModuleConstraint();
            }

            @Override
            public boolean isTraversalLink() {
                return link.isTraversalLink();
            }

            @Override
            public boolean isReturnLink() {
                return link.isReturnLink();
            }

            @Override
            public List<Modification> getModifications() {
                return link.getModifications();
            }

            @Override
            public Modification getModificationById(@NotNull String modId) {
                return link.getModificationById(modId);
            }

            @Override
            public String getId() {
                return link.getId();
            }

            @Override
            public String getFullId() {
                return link.getFullId();
            }

            @Override
            public boolean isDeleted() {
                return link.isDeleted();
            }

            @Override
            public IdentifiableItem getParent() {
                return link.getParent();
            }

            @Override
            public boolean hasDeletedParent() {
                return link.hasDeletedParent();
            }

            @Override
            public NonLinearBook getCurrentNLB() {
                return link.getCurrentNLB();
            }

            @Override
            public String addObserver(NLBObserver observer) {
                throw new UnsupportedOperationException("Not supported during export");
            }

            @Override
            public void removeObserver(String observerId) {
                throw new UnsupportedOperationException("Not supported during export");
            }

            @Override
            public void notifyObservers() {
                throw new UnsupportedOperationException("Not supported during export");
            }
        };
    }

    private Page createPreprocessedPage(final Page page) {
        return new Page() {
            @Override
            public String getImageFileName() {
                return page.getImageFileName();
            }

            @Override
            public boolean isImageBackground() {
                return page.isImageBackground();
            }

            @Override
            public String getSoundFileName() {
                return page.getSoundFileName();
            }

            @Override
            public String getText() {
                return escapeText(page.getText());
            }

            @Override
            public MultiLangString getTexts() {
                return escapeMultiLang(page.getTexts());
            }

            @Override
            public String getVarId() {
                return page.getVarId();
            }

            @Override
            public String getCaption() {
                return escapeText(page.getCaption());
            }

            @Override
            public MultiLangString getCaptions() {
                return escapeMultiLang(page.getCaptions());
            }

            @Override
            public boolean isUseCaption() {
                return page.isUseCaption();
            }

            @Override
            public boolean isLeaf() {
                return page.isLeaf();
            }

            @Override
            public String getTraverseText() {
                // Not escaping, because this is the text of the autogenerated link, which will be escaped later
                return page.getTraverseText();
            }

            @Override
            public MultiLangString getTraverseTexts() {
                // Not escaping, because this is the text of the autogenerated link, which will be escaped later
                return page.getTraverseTexts();
            }

            @Override
            public boolean isAutoTraverse() {
                return page.isAutoTraverse();
            }

            @Override
            public boolean isAutoReturn() {
                return page.isAutoReturn();
            }

            @Override
            public String getReturnText() {
                // Not escaping, because this is the text of the autogenerated link, which will be escaped later
                return page.getReturnText();
            }

            @Override
            public MultiLangString getReturnTexts() {
                // Not escaping, because this is the text of the autogenerated link, which will be escaped later
                return page.getReturnTexts();
            }

            @Override
            public String getReturnPageId() {
                return page.getReturnPageId();
            }

            @Override
            public boolean shouldReturn() {
                return page.shouldReturn();
            }

            @Override
            public String getModuleConstrId() {
                return page.getModuleConstrId();
            }

            @Override
            public String getModuleName() {
                return page.getModuleName();
            }

            @Override
            public NonLinearBook getModule() {
                return page.getModule();
            }

            @Override
            public boolean isAutowire() {
                return page.isAutowire();
            }

            @Override
            public String getAutowireInText() {
                // Not escaping, because this is the text of the autogenerated link, which will be escaped later
                return page.getAutowireInText();
            }

            @Override
            public MultiLangString getAutowireInTexts() {
                // Not escaping, because this is the text of the autogenerated link, which will be escaped later
                return page.getAutowireInTexts();
            }

            @Override
            public String getAutowireOutText() {
                // Not escaping, because this is the text of the autogenerated link, which will be escaped later
                return page.getAutowireOutText();
            }

            @Override
            public MultiLangString getAutowireOutTexts() {
                // Not escaping, because this is the text of the autogenerated link, which will be escaped later
                return page.getAutowireOutTexts();
            }

            @Override
            public boolean isAutoIn() {
                return page.isAutoIn();
            }

            @Override
            public boolean isAutoOut() {
                return page.isAutoOut();
            }

            @Override
            public String getAutowireInConstrId() {
                return page.getAutowireInConstrId();
            }

            @Override
            public String getAutowireOutConstrId() {
                return page.getAutowireOutConstrId();
            }

            @Override
            public String getStroke() {
                return page.getStroke();
            }

            @Override
            public String getFill() {
                return page.getFill();
            }

            @Override
            public String getTextColor() {
                return page.getTextColor();
            }

            @Override
            public List<String> getContainedObjIds() {
                return page.getContainedObjIds();
            }

            @Override
            public Coords getCoords() {
                return page.getCoords();
            }

            @Override
            public List<Link> getLinks() {
                return page.getLinks();
            }

            @Override
            public Link getLinkById(@NotNull String linkId) {
                return page.getLinkById(linkId);
            }

            @Override
            public List<Modification> getModifications() {
                return page.getModifications();
            }

            @Override
            public Modification getModificationById(@NotNull String modId) {
                return page.getModificationById(modId);
            }

            @Override
            public String getId() {
                return page.getId();
            }

            @Override
            public String getFullId() {
                return page.getFullId();
            }

            @Override
            public boolean isDeleted() {
                return page.isDeleted();
            }

            @Override
            public IdentifiableItem getParent() {
                return page.getParent();
            }

            @Override
            public boolean hasDeletedParent() {
                return page.hasDeletedParent();
            }

            @Override
            public NonLinearBook getCurrentNLB() {
                return page.getCurrentNLB();
            }

            @Override
            public String addObserver(NLBObserver observer) {
                throw new UnsupportedOperationException("Not supported during export");
            }

            @Override
            public void removeObserver(String observerId) {
                throw new UnsupportedOperationException("Not supported during export");
            }

            @Override
            public void notifyObservers() {
                throw new UnsupportedOperationException("Not supported during export");
            }
        };
    }

    private Obj createPreprocessedObj(final Obj obj) {
        return new Obj() {
            @Override
            public String getText() {
                return escapeText(obj.getText());
            }

            @Override
            public String getActText() {
                return escapeText(obj.getActText());
            }

            @Override
            public MultiLangString getTexts() {
                return escapeMultiLang(obj.getTexts());
            }

            @Override
            public MultiLangString getActTexts() {
                return escapeMultiLang(obj.getActTexts());
            }

            @Override
            public String getVarId() {
                return obj.getVarId();
            }

            @Override
            public String getConstrId() {
                return obj.getConstrId();
            }

            @Override
            public String getName() {
                return obj.getName();
            }

            @Override
            public String getImageFileName() {
                return obj.getImageFileName();
            }

            @Override
            public boolean isAnimatedImage() {
                return obj.isAnimatedImage();
            }

            @Override
            public String getDisp() {
                return escapeText(obj.getDisp());
            }

            @Override
            public MultiLangString getDisps() {
                return escapeMultiLang(obj.getDisps());
            }

            @Override
            public boolean isTakable() {
                return obj.isTakable();
            }

            @Override
            public boolean isImageInScene() {
                return obj.isImageInScene();
            }

            @Override
            public boolean isImageInInventory() {
                return obj.isImageInInventory();
            }

            @Override
            public String getContainerId() {
                return obj.getContainerId();
            }

            @Override
            public String getStroke() {
                return obj.getStroke();
            }

            @Override
            public String getFill() {
                return obj.getFill();
            }

            @Override
            public String getTextColor() {
                return obj.getTextColor();
            }

            @Override
            public List<String> getContainedObjIds() {
                return obj.getContainedObjIds();
            }

            @Override
            public Coords getCoords() {
                return obj.getCoords();
            }

            @Override
            public List<Link> getLinks() {
                return obj.getLinks();
            }

            @Override
            public Link getLinkById(@NotNull String linkId) {
                return obj.getLinkById(linkId);
            }

            @Override
            public List<Modification> getModifications() {
                return obj.getModifications();
            }

            @Override
            public Modification getModificationById(@NotNull String modId) {
                return obj.getModificationById(modId);
            }

            @Override
            public String getId() {
                return obj.getId();
            }

            @Override
            public String getFullId() {
                return obj.getFullId();
            }

            @Override
            public boolean isDeleted() {
                return obj.isDeleted();
            }

            @Override
            public IdentifiableItem getParent() {
                return obj.getParent();
            }

            @Override
            public boolean hasDeletedParent() {
                return obj.hasDeletedParent();
            }

            @Override
            public NonLinearBook getCurrentNLB() {
                return obj.getCurrentNLB();
            }

            @Override
            public String addObserver(NLBObserver observer) {
                throw new UnsupportedOperationException("Not supported during export");
            }

            @Override
            public void removeObserver(String observerId) {
                throw new UnsupportedOperationException("Not supported during export");
            }

            @Override
            public void notifyObservers() {
                throw new UnsupportedOperationException("Not supported during export");
            }
        };
    }

    protected String decorateObjLabel(String id) {
        return EMPTY_STRING;
    }

    protected String decorateObjComment(String name) {
        return EMPTY_STRING;
    }

    protected String decorateObjStart(boolean menuObj) {
        return EMPTY_STRING;
    }

    protected String decorateObjName(String name) {
        return EMPTY_STRING;
    }

    protected String decorateObjImage(ImagePathData objImagePathData) {
        return EMPTY_STRING;
    }

    protected String decorateObjDisp(List<TextChunk> dispChunks, boolean imageEnabled) {
        return EMPTY_STRING;
    }

    protected String decorateObjText(List<TextChunk> textChunks, boolean imageEnabled) {
        return EMPTY_STRING;
    }

    protected String decorateObjTak(List<TextChunk> takChunks) {
        return EMPTY_STRING;
    }

    protected String decorateObjInv(boolean menuObj) {
        return EMPTY_STRING;
    }

    protected String decorateObjVariable(String variableName) {
        return EMPTY_STRING;
    }

    protected String decorateObjConstraint(String constraintValue) {
        return EMPTY_STRING;
    }

    protected String decorateObjModifications(String modificationsText) {
        return EMPTY_STRING;
    }

    protected String decorateObjActStart(List<TextChunk> actTextChunks) {
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
        blocks.setUseSuccessText(link.getText());
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
    ) throws NLBConsistencyException {
        String constraintBody = (
                (shouldObeyToModuleConstraint && !StringHelper.isEmpty(moduleConstraintText))
                        ? (
                        (!StringHelper.isEmpty(constraintText))
                                ? moduleConstraintText + "&&" + constraintText
                                : moduleConstraintText
                )
                        : constraintText
        );
        ExpressionData expressionData = translateExpressionBody(constraintBody);
        return (
                isPositiveConstraint
                        ? expressionData.getExistencePart() + "(" + expressionData.getExpressionPart() + ")"
                        : expressionData.getExistencePart() + "(" + decorateNot() + " (" + expressionData.getExpressionPart() + "))"
        );
    }

    private ExpressionData translateExpressionBody(String expressionText) throws NLBConsistencyException {
        StringBuilder existenceBuilder = new StringBuilder();
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
            String decoratedVariable = decorateVariable(expressionVar);
            Variable.DataType dataType = m_dataTypeMap.get(expressionVar);
            if (dataType != Variable.DataType.BOOLEAN) {
                existenceBuilder.append(decorateExistence(decoratedVariable)).append(" ").append(decorateAnd()).append(" ");
            }
            expression = (
                    expression.replaceAll(
                            "\\b" + expressionVar + "\\b",
                            Matcher.quoteReplacement(decoratedVariable)
                    )
            );
        }
        expression = expression.replaceAll("\\b\\s*true\\s*\\b", " " + Matcher.quoteReplacement(decorateTrue()) + " ");
        expression = expression.replaceAll("\\b\\s*false\\s*\\b", " " + Matcher.quoteReplacement(decorateFalse()) + " ");
        return new ExpressionData(existenceBuilder.toString(), expression);
    }

    private String decorateVariable(String constraintVar) throws NLBConsistencyException {
        Variable.DataType dataType = m_dataTypeMap.get(constraintVar);
        if (dataType == null) {
            throw new NLBConsistencyException(
                    "Datatype of the variable " + constraintVar +
                            " cannot be determined. Please verify that this variable is defined."
            );
        }
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
                Variable variable = (
                        StringHelper.isEmpty(modification.getVarId())
                                ? null
                                : exportData.getNlb().getVariableById(modification.getVarId())
                );
                if (modification.returnsValue() && (variable == null || variable.isDeleted())) {
                    throw new NLBConsistencyException(
                            "Variable with id = " + modification.getVarId()
                                    + "cannot be found for modification"
                                    + modification.getFullId()
                    );
                }
                Variable expression = (
                        exportData.getNlb().getVariableById(modification.getExprId())
                );
                if (modification.isParametrized() && (expression == null || expression.isDeleted())) {
                    throw new NLBConsistencyException(
                            "Expression with id = " + modification.getExprId()
                                    + " cannot be found for modification "
                                    + modification.getFullId()
                    );
                }
                switch (modification.getType()) {
                    case ADD:
                        final String addDestinationId = (
                                (variable != null)
                                        ? exportData.getObjId(variable.getName())
                                        : null
                        );
                        final String objIdToAdd = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateAddObj(
                                        addDestinationId,
                                        objIdToAdd,
                                        decorateAutoVar(expression.getValue()),
                                        expression.getValue(),
                                        (objIdToAdd == null)
                                                ? null
                                                : exportData.getNlb().getObjById(objIdToAdd).getDisp())
                        );
                        break;
                    case ADDALL:
                        final String addAllDestinationId = (
                                (variable != null)
                                        ? exportData.getObjId(variable.getName())
                                        : null
                        );
                        stringBuilder.append(
                                decorateAddAllOperation(
                                        addAllDestinationId,
                                        ((addAllDestinationId == null) && (variable != null))
                                                ? variable.getName()
                                                : null,
                                        expression.getValue()
                                )
                        );
                        break;
                    case REMOVE:
                        final String removeDestinationId = (
                                (variable != null)
                                        ? exportData.getObjId(variable.getName())
                                        : null
                        );
                        final String objIdToRemove = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateDelObj(
                                        removeDestinationId,
                                        objIdToRemove,
                                        decorateAutoVar(expression.getValue()),
                                        expression.getValue(),
                                        (objIdToRemove != null)
                                                ? exportData.getNlb().getObjById(objIdToRemove).getDisp()
                                                : null)
                        );
                        break;
                    case CLEAR:
                        final String destinationId = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateClearOperation(
                                        destinationId,
                                        decorateAutoVar(expression.getValue())
                                )
                        );
                        break;
                    case CLRINV:
                        stringBuilder.append(decorateClearOperation(null, null));
                        break;
                    case PUSH:
                        final String objIdToPush = exportData.getObjId(expression.getValue());
                        if (variable != null) {
                            stringBuilder.append(
                                    decoratePushOperation(
                                            variable.getName(),
                                            objIdToPush,
                                            decorateAutoVar(expression.getValue())
                                    )
                            );
                        } else {
                            throw new NLBConsistencyException(
                                    "Destination list name is not specified for push operation"
                            );
                        }
                        break;
                    case POP:
                        assert variable != null;
                        // expression value is the name of the list to pop from
                        stringBuilder.append(
                                decoratePopOperation(
                                        decorateAutoVar(variable.getName()),
                                        expression.getValue()
                                )
                        );
                        break;
                    case SHUFFLE:
                        stringBuilder.append(
                                decorateShuffleOperation(expression.getValue())
                        );
                        break;
                    case ACT:
                        final String actingObjId = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateActOperation(
                                        decorateAutoVar(expression.getValue()),
                                        actingObjId
                                )
                        );
                        break;
                    case USE:
                        assert variable != null;
                        final String sourceId = exportData.getObjId(variable.getName());
                        final String targetId = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateUseOperation(
                                        (sourceId != null) ? variable.getName() : decorateAutoVar(variable.getName()),
                                        sourceId,
                                        (targetId != null) ? expression.getValue() : decorateAutoVar(expression.getValue()),
                                        targetId
                                )
                        );
                        break;
                    case ASSIGN:
                        assert variable != null;
                        // Left part of assignment should be decorated as ordinary variable, with the exception of String
                        stringBuilder.append(
                                decorateAssignment(
                                        (variable.getDataType() == Variable.DataType.STRING)
                                                ? decorateStringVar(variable.getName())
                                                : decorateAutoVar(variable.getName()),
                                        translateExpressionBody(expression.getValue()).getExpressionPart()
                                )
                        );
                        break;
                    case SIZE:
                        assert variable != null;
                        // Left part of assignment should be always number.
                        // TODO: throw exception if its datatype is not number???
                        // Expression value is the name of the list which size will be returned.
                        stringBuilder.append(
                                decorateSizeOperation(
                                        decorateNumberVar(variable.getName()),
                                        expression.getValue()
                                )
                        );
                        break;
                    case RND:
                        assert variable != null;
                        // Left part of assignment should be always number.
                        // TODO: throw exception if its datatype is not number???
                        stringBuilder.append(
                                decorateRndOperation(
                                        decorateAutoVar(variable.getName()),
                                        translateExpressionBody(expression.getValue()).getExpressionPart()
                                )
                        );
                        break;
                    default:
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

    protected abstract String decorateDelObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName);

    protected abstract String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName);

    protected abstract String decorateAddAllOperation(String destinationId, String destinationList, String listName);

    protected abstract String decoratePushOperation(String listName, String objectId, String objectVar);

    protected abstract String decoratePopOperation(String variableName, String listName);

    protected abstract String decorateClearOperation(String destinationId, String destinationVar);

    protected abstract String decorateSizeOperation(String variableName, String listName);

    protected abstract String decorateRndOperation(String variableName, String maxValue);

    protected abstract String decorateShuffleOperation(String listName);

    protected abstract String decorateActOperation(String actingObjVariable, String actingObjId);

    protected abstract String decorateUseOperation(
            String sourceVariable,
            String sourceId,
            String targetVariable,
            String targetId
    );

    protected abstract String decorateTrue();

    protected abstract String decorateFalse();

    protected abstract String decorateEq();

    protected abstract String decorateNEq();

    protected abstract String decorateGt();

    protected abstract String decorateGte();

    protected abstract String decorateLt();

    protected abstract String decorateLte();

    protected abstract String decorateNot();

    protected abstract String decorateOr();

    protected abstract String decorateAnd();

    protected abstract String decorateExistence(String decoratedVariable);

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

    protected abstract String decoratePageCaption(String caption, boolean useCaption);

    protected abstract String decoratePageImage(ImagePathData pageImagePathData, final boolean imageBackground);

    protected abstract String decoratePageSound(String pageSoundPath);

    /**
     * NB: in case of ordinary (inline) NLB modules moduleDir should be <code>null</code>
     *
     * @param moduleDir
     * @param imageFileName
     * @return
     */
    protected ImagePathData getImagePath(
            final String moduleDir,
            final String imageFileName,
            final boolean animatedImage
    ) throws NLBExportException {
        if (StringHelper.isEmpty(imageFileName)) {
            return ImagePathData.EMPTY;
        } else {
            ImagePathData result = new ImagePathData();
            Matcher matcher = FILE_NAME_PATTERN.matcher(imageFileName);
            if (matcher.find()) {
                if (moduleDir == null) {
                    result.setParentFolderPath(NonLinearBook.IMAGES_DIR_NAME);
                } else {
                    result.setParentFolderPath(NonLinearBook.IMAGES_DIR_NAME + "/" + moduleDir);
                }
                if (animatedImage) {
                    result.setFileName(matcher.group(1));
                    result.setMaxFrameNumber(Integer.parseInt(matcher.group(2)));
                } else {
                    result.setFileName(matcher.group(1) + matcher.group(2));
                    result.setMaxFrameNumber(0);
                }
                result.setFileExtension(matcher.group(3));
                return result;
            } else {
                throw new NLBExportException("Filename " + imageFileName + " is bad, please rename");
            }
        }
    }

    protected String getSoundPath(String moduleDir, String soundFileName) {
        if (StringHelper.isEmpty(soundFileName)) {
            return Constants.EMPTY_STRING;
        } else {
            if (Constants.VOID.equals(soundFileName)) {
                return Constants.VOID;
            } else {
                if (moduleDir == null) {
                    return NonLinearBook.SOUND_DIR_NAME + "/" + soundFileName;
                } else {
                    return NonLinearBook.SOUND_DIR_NAME + "/" + moduleDir + "/" + soundFileName;
                }
            }
        }
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
                    pageText.append(getLineSeparator());
                    break;
            }
        }
        return pageText.toString();
    }

    protected abstract String getLineSeparator();

    protected abstract String decoratePageTextEnd();

    protected abstract String decoratePageLabel(String labelText, int pageNumber);

    protected abstract String decoratePageNumber(int pageNumber);

    protected abstract String decoratePageComment(String comment);
}
