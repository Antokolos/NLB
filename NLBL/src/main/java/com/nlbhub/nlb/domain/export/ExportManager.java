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
import com.nlbhub.nlb.domain.MediaExportParameters;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.SearchResult;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.MultiLangString;
import com.nlbhub.nlb.util.StringHelper;
import com.nlbhub.nlb.util.VarFinder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/4/13
 */
public abstract class ExportManager {
    private static final Logger LOG = Logger.getLogger(VNSTEADExportManager.class.getName());

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
    private static final int NONEXISTING_PAGE = -1;

    public static final String NO_CONTAINER = "function() return nil; end";
    public static final String EMPTY_STRING = Constants.EMPTY_STRING;
    public static final String UTF_8 = "UTF-8";
    public static final String UTF_16LE = "UTF-16LE";
    public static final String CP1251 = "CP1251";
    private static final String MAIN_DATA_KEY = Constants.MAIN_MODULE_NAME;
    private String m_encoding;
    private Map<String, ExportData> m_exportDataMap;
    private Map<String, Variable.DataType> m_dataTypeMap;
    private Map<String, String> m_mediaToConstraintMap;
    private Map<String, String> m_mediaRedirectsMap;
    private Map<String, MediaExportParameters> m_mediaExportParametersMap;
    private Map<String, Boolean> m_mediaFlagsMap;

    /**
     * Use page numbers as destinations instead of page IDs.
     */
    private static final boolean GOTO_PAGE_NUMBERS = false;

    private class ExportData {
        private NonLinearBook m_nlb;
        private Page m_modulePage;
        private String m_moduleConstraintText;
        private Integer m_modulePageNumber;
        private List<Page> m_pageList = new ArrayList<>();
        private List<Obj> m_objList = new ArrayList<>();
        private Map<String, Integer> m_idToPageNumberMap = new HashMap<>();
        private Map<String, String> m_objNamToIdMap = new HashMap<>();
        private Map<String, Boolean> m_inwardLinksMap = new HashMap<>();
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
                for (Link link : obj.getLinks()) {
                    m_inwardLinksMap.put(link.getTarget(), true);
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

        private Boolean hasInwardLinks(final String objId) {
            if (m_inwardLinksMap.containsKey(objId)) {
                return m_inwardLinksMap.get(objId);
            } else {
                if (m_parentED != null) {
                    return m_parentED.hasInwardLinks(objId);
                } else {
                    return false;
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
            m_mediaToConstraintMap = nlb.getMediaToConstraintMap();
            m_mediaRedirectsMap = nlb.getMediaRedirectsMap();
            m_mediaExportParametersMap = nlb.getMediaExportParametersMap();
            m_mediaFlagsMap = nlb.getMediaFlagsMap();
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

    private int getPageNumber(final String pageId) {
        for (ExportData exportData : m_exportDataMap.values()) {
            if (exportData.m_idToPageNumberMap.containsKey(pageId)) {
                return exportData.m_idToPageNumberMap.get(pageId);
            }
        }
        return NONEXISTING_PAGE;
    }

    private int checkedGetPageNumber(final String pageId) throws NLBConsistencyException {
        int pageNumber = getPageNumber(pageId);
        if (pageNumber != NONEXISTING_PAGE) {
            return pageNumber;
        }
        throw new NLBConsistencyException(
                "Page number cannot be determined for pageId = " + pageId
        );
    }

    private int getScreenWidth() {
        return 1920;
    }

    private int getScreenHeight() {
        return 1080;
    }

    private String getRelativeCoords(Obj obj) {
        Coords coords = obj.getRelativeCoords(true);
        double left = Math.floor(coords.getLeft() * getScreenWidth() / coords.getWidth());
        double top = Math.floor(coords.getTop() * getScreenHeight() / coords.getHeight());
        Coords transformedCoords = getTransformedCoords(obj, left, top);
        String x = String.valueOf((int) transformedCoords.getLeft());
        String y = String.valueOf((int) transformedCoords.getTop());
        return x + "," + y;
    }

    private Coords getTransformedCoords(Obj obj, double left, double top) {
        CoordsLw coords = new CoordsLw();
        coords.setLeft((float) left);
        coords.setTop((float) top);
        switch (obj.getCoordsOrigin()) {
            case LeftTop:
                coords.setLeft((float) left);
                coords.setTop((float) top);
                break;
            case MiddleTop:
                coords.setLeft((float) left - getScreenWidth() / 2.0f);
                coords.setTop((float) top);
                break;
            case RightTop:
                coords.setLeft(getScreenWidth() - (float) left);
                coords.setTop(0.0f);
                break;
            case LeftMiddle:
                coords.setLeft((float) left);
                coords.setTop(getScreenHeight() / 2.0f - (float) top);
                break;
            case MiddleMiddle:
                coords.setLeft((float) left - getScreenWidth() / 2.0f);
                coords.setTop(getScreenHeight() / 2.0f - (float) top);
                break;
            case RightMiddle:
                coords.setLeft(getScreenWidth() - (float) left);
                coords.setTop(getScreenHeight() / 2.0f - (float) top);
                break;
            case LeftBottom:
                coords.setLeft((float) left);
                coords.setTop(getScreenHeight() - (float) top);
                break;
            case MiddleBottom:
                coords.setLeft((float) left - getScreenWidth() / 2.0f);
                coords.setTop(getScreenHeight() - (float) top);
                break;
            case RightBottom:
                coords.setLeft(getScreenWidth() - (float) left);
                coords.setTop(getScreenHeight() - (float) top);
                break;
        }
        return coords;
    }

    protected NLBBuildingBlocks createNLBBuildingBlocks() throws NLBConsistencyException, NLBExportException {
        return createNLBBuildingBlocks(m_exportDataMap.get(MAIN_DATA_KEY));
    }

    private NLBBuildingBlocks createNLBBuildingBlocks(
            final ExportData exportData
    ) throws NLBConsistencyException, NLBExportException {
        final NonLinearBook nlb = exportData.getNlb();
        NLBBuildingBlocks blocks = new NLBBuildingBlocks(nlb.getTitle(), nlb.getAuthor(), nlb.getVersion(), nlb.getLanguage(), nlb.getAllAchievementNames(false), nlb.getPerfectGameAchievementName());
        //stringBuilder.append("#mode quote").append(LINE_SEPARATOR);
        for (final Obj obj : exportData.getObjList()) {
            blocks.addObjBuildingBlocks(createObjBuildingBlocks(createPreprocessedObj(obj), exportData));
        }
        for (final Page page : exportData.getPageList()) {
            if (page.getModule().isEmpty()) {
                blocks.addPageBuildingBlocks(createPageBuildingBlocks(createPreprocessedPage(page), exportData));
            } else {
                blocks.addPageBuildingBlocks(createPageBuildingBlocks(createPreprocessedPage(page), exportData));
                NLBBuildingBlocks module = createNLBBuildingBlocks(m_exportDataMap.get(page.getId()));
                blocks.addAchievements(module.getAchievements());
                blocks.addNLBBuildingBlocks(module);
            }
        }

        return blocks;
    }

    /**
     *
     * @param pageBuildingBlocks
     * @return true if links list consists only of trivial links
     */
    private boolean determineTrivialStatus(PageBuildingBlocks pageBuildingBlocks) {
        List<LinkBuildingBlocks> linkBlocks = pageBuildingBlocks.getLinksBuildingBlocks();
        if (linkBlocks.size() == 0) {
            return false;
        }
        for (LinkBuildingBlocks blocks : linkBlocks) {
            if (!blocks.isTrivial()) {
                return false;
            }
        }
        return true;
    }

    private boolean determineTrivialStatus(Link link) {
        return (link.getTexts().equals(Link.DEFAULT_TEXT) && link.getAltTexts().equals(Link.DEFAULT_ALT_TEXT)) || link.isAuto();
    }

    private PageBuildingBlocks createPageBuildingBlocks(
            final Page page,
            final ExportData exportData
    ) throws NLBConsistencyException, NLBExportException {
        NonLinearBook nlb = exportData.getNlb();
        PageBuildingBlocks blocks = new PageBuildingBlocks();
        boolean hasPageText = StringHelper.notEmpty(page.getText());
        blocks.setHasPageText(hasPageText);
        blocks.setTheme(page.getTheme());
        final Integer pageNumber = checkedGetPageNumber(page.getId());
        blocks.setAutowired(page.isAutowire());
        final String pageName = decoratePageName(page.getId(), pageNumber);
        blocks.setPageName(pageName);
        blocks.setPageLabel(decoratePageLabel(page.getId(), pageNumber, page.getTheme()));
        blocks.setPageNumber(decoratePageNumber(pageNumber));
        blocks.setPageComment(decoratePageComment(page.getCaption()));
        final String title = getNonEmptyTitle(nlb);
        blocks.setPageCaption(decoratePageCaption(page.getCaption(), page.isUseCaption(), title, page.isNoSave()));
        blocks.setNotes(decoratePageNotes(page.getNotes()));
        blocks.setModuleTitle(title);
        String imageFileName = ((nlb.isSuppressMedia()) ? Page.DEFAULT_IMAGE_FILE_NAME: page.getImageFileName());
        boolean isAnimatedImage = page.isImageAnimated();
        blocks.setHasAnimatedPageImage(isAnimatedImage);
        blocks.setImageBackground(page.isImageBackground());
        blocks.setPageImage(decoratePageImage(getImagePaths(page.getExternalHierarchy(), imageFileName, isAnimatedImage, false), page.isImageBackground(), page.getTheme()));
        String soundFileName = ((nlb.isSuppressMedia() || nlb.isSuppressSound()) ? Page.DEFAULT_SOUND_FILE_NAME: page.getSoundFileName());
        blocks.setPageSound(decoratePageSound(pageName, getSoundPaths(page.getExternalHierarchy(), soundFileName), page.isSoundSFX(), page.getTheme()));
        blocks.setPageTextStart(decoratePageTextStart(page.getId(), pageNumber, StringHelper.getTextChunks(page.getText()), page.getTheme()));
        boolean hasChoicesOrLeaf = hasChoicesOrLeaf(page);
        blocks.setPageTextEnd(decoratePageTextEnd(page.getId(), pageNumber, page.getTheme(), hasChoicesOrLeaf));
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
        if (!StringHelper.isEmpty(page.getDefaultTagId())) {
            Variable deftag = nlb.getVariableById(page.getDefaultTagId());
            // TODO: Add cases with deleted pages/links/variables etc. to the unit test
            if (!deftag.isDeleted()) {
                blocks.setPageDefaultTag(deftag.getValue());
            } else {
                blocks.setPageDefaultTag(EMPTY_STRING);
            }
        } else {
            blocks.setPageDefaultTag(EMPTY_STRING);
        }
        blocks.setHasPageTimer(false);
        if (!StringHelper.isEmpty(page.getTimerVarId())) {
            Variable timerVariable = nlb.getVariableById(page.getTimerVarId());
            // TODO: Add cases with deleted pages/links/variables etc. to the unit test
            if (!timerVariable.isDeleted()) {
                blocks.setHasPageTimer(true);
                blocks.setPageTimerVariableInit(decoratePageTimerVariableInit(timerVariable.getName()));
                blocks.setPageTimerVariable(decoratePageTimerVariable(timerVariable.getName()));
            } else {
                blocks.setPageTimerVariableInit(decoratePageTimerVariableInit(EMPTY_STRING));
                blocks.setPageTimerVariable(decoratePageTimerVariable(EMPTY_STRING));
            }
        } else {
            blocks.setPageTimerVariableInit(decoratePageTimerVariableInit(EMPTY_STRING));
            blocks.setPageTimerVariable(decoratePageTimerVariable(EMPTY_STRING));
        }
        blocks.setPageModifications(
                decoratePageModifications(
                        buildModificationsText(EMPTY_STRING, page.getModifications(), exportData)
                )
        );
        blocks.setPageEnd(decoratePageEnd(page.isFinish()));
        List<String> containedObjIds = page.getContainedObjIds();
        boolean hasAnim = false;
        boolean hasGraphicalObjs = false;
        if (!containedObjIds.isEmpty()) {
            for (String containedObjId : containedObjIds) {
                Obj obj = nlb.getObjById(containedObjId);
                hasAnim = (hasAnim || obj.isAnimatedImage());
                if (obj.isGraphical()) {
                    hasGraphicalObjs = true;
                    blocks.addContainedGraphicalObjId(decorateId(containedObjId));
                } else {
                    blocks.addContainedObjId(decorateContainedObjId(containedObjId));
                }
            }
        }
        // TODO: NLB-24: workaround is used to determine animated images presence
        blocks.setHasObjectsWithAnimatedImages(hasAnim);
        List<Link> links = page.getLinks();
        for (final Link link : links) {
            if (!link.isDeleted()) {
                LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(page, createPreprocessedLink(link), exportData);
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
                            Link.DEFAULT_ALT_TEXT,
                            page.getModuleConstrId(),
                            Constants.EMPTY_STRING,
                            page.isAutoTraverse(),
                            false,
                            true,
                            false,
                            false,
                            null
                    )
            );
            LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(page, createPreprocessedLink(link), exportData);
            blocks.addLinkBuildingBlocks(linkBuildingBlocks);
        }
        if (page.shouldReturn() && !exportData.getModulePage().getId().equals(MAIN_DATA_KEY)) {
            if (page.isUseMPL()) {
                for (Link link : page.getCurrentNLB().getParentPage().getLinks()) {
                    Link linklw = (
                            new LinkLw(
                                    LinkLw.Type.Return,
                                    link.getTarget(),
                                    page,
                                    link.getTexts(),
                                    link.getAltTexts(),
                                    link.getConstrId(),
                                    link.getVarId(),
                                    link.isAuto(),
                                    link.isOnce(),
                                    link.isPositiveConstraint(),
                                    false,
                                    true,
                                    link.getModifications()
                            )
                    );
                    LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(page, createPreprocessedLink(linklw), exportData);
                    blocks.addLinkBuildingBlocks(linkBuildingBlocks);
                }
            } else {
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
                                Link.DEFAULT_ALT_TEXT,
                                Constants.EMPTY_STRING,
                                Constants.EMPTY_STRING,
                                page.isAutoReturn(),
                                false,
                                StringHelper.isEmpty(exportData.getModulePage().getModuleConstrId()),
                                !page.isLeaf(),
                                false,
                                null)
                );
                LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(page, createPreprocessedLink(link), exportData);
                blocks.addLinkBuildingBlocks(linkBuildingBlocks);
            }
        }
        if (page.isAutowire()) {
            // Add return autowired links on the fly
            for (Page nlbPage : getPagesForAutowiredOutwardLinks(page, nlb)) {
                if (!nlbPage.isAutowire()) {
                    Link link = (
                            new LinkLw(
                                    LinkLw.Type.AutowiredOut,
                                    nlbPage.getId(),
                                    page,
                                    page.getAutowireOutTexts(),
                                    Link.DEFAULT_ALT_TEXT,
                                    NonLinearBook.LC_VARID_PREFIX
                                            + page.getId() + NonLinearBook.LC_VARID_SEPARATOR_OUT + nlbPage.getId(),
                                    Constants.EMPTY_STRING,
                                    page.isAutoOut(),
                                    false,
                                    true,
                                    false,
                                    false,
                                    null)
                    );
                    LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(page, createPreprocessedLink(link), exportData);
                    blocks.addLinkBuildingBlocks(linkBuildingBlocks);
                }
            }
        }
        // Please note, that we are adding autowired inward links even from autowired pages itself
        // if book has full autowire property set to true
        if (nlb.isFullAutowire() || !page.isAutowire()) {
            for (String autowiredPageId : getAllAutowiredPageIds(nlb)) {
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
                                    Link.DEFAULT_ALT_TEXT,
                                    autowiredPage.getAutowireInConstrId(),
                                    Constants.EMPTY_STRING,
                                    autowiredPage.isAutoIn(),
                                    false,
                                    true,
                                    false,
                                    false,
                                    null)
                    );
                    LinkBuildingBlocks linkBuildingBlocks = createLinkBuildingBlocks(page, createPreprocessedLink(link), exportData);
                    blocks.addLinkBuildingBlocks(linkBuildingBlocks);
                }
            }
        }
        blocks.setHasTrivialLinks(determineTrivialStatus(blocks));
        if (!hasPageText && StringHelper.notEmpty(imageFileName) && !hasGraphicalObjs && !page.isLeaf()) {
            LOG.warning("Page " + page.getId() + " has empty text and non-empty image, and it is not leaf and not pure graphical page");
        }
        return blocks;
    }

    private boolean hasChoicesOrLeaf(final Page page) {
        List<Link> links = page.getLinks();
        if (links.isEmpty()) {
            return true;
        }
        for (final Link link : links) {
            if (!link.isAuto()) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasChoicesOrLeaf(final PageBuildingBlocks pageBuildingBlocks) {
        List<LinkBuildingBlocks> linksBuildingBlocks = pageBuildingBlocks.getLinksBuildingBlocks();
        if (linksBuildingBlocks.isEmpty()) {
            return true;
        }
        for (final LinkBuildingBlocks linkBuildingBlocks : linksBuildingBlocks) {
            if (!linkBuildingBlocks.isAuto()) {
                return true;
            }
        }
        return false;
    }

    protected Map<String, String> getInitValuesMap() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Variable.DataType> entry : m_dataTypeMap.entrySet()) {
            String defaultValue = getDefaultValue(entry.getValue());
            result.put(entry.getKey(), defaultValue);
        }
        return result;
    }

    private String getDefaultValue(Variable.DataType datatype) {
        switch (datatype) {
            case BOOLEAN:
                return "false";
            case AUTO:
            case NUMBER:
                return "0";
            case STRING:
                return "\"\"";
            default:
                return "";
        }
    }

    private List<String> getAllAutowiredPageIds(NonLinearBook nlb) {
        List<String> result = new ArrayList<>();
        result.addAll(nlb.getAutowiredPagesIds());
        result.addAll(nlb.getParentGlobalAutowiredPagesIds());
        return result;
    }

    private List<Page> getPagesForAutowiredOutwardLinks(Page autowiredSourcePage, NonLinearBook nlb) {
        List<Page> result = new ArrayList<>();
        if (autowiredSourcePage.isGlobalAutowire()) {
            result.addAll(nlb.getDownwardPagesHeirarchy().values());
        } else {
            result.addAll(nlb.getPages().values());
        }
        return result;
    }

    private String getContainerRef(Obj obj, ExportData exportData) {
        String containerId = obj.getContainerId();
        if (Obj.DEFAULT_CONTAINER_ID.equals(containerId)) {
            return NO_CONTAINER;
        }
        int pageNumber = getPageNumber(containerId);
        if (pageNumber != NONEXISTING_PAGE) {
            return "function() return " + decoratePageName(containerId, pageNumber) + "; end";
        } else {
            // This is obj, just decorate its id
            return "function() return " + decorateId(containerId) + "; end";
        }
    }

    private List<String> getDecoratedContainedObjIds(Obj obj) {
        List<String> result = new ArrayList<>();
        if (obj != null) {
            List<String> containedObjIds = obj.getContainedObjIds();
            if (!containedObjIds.isEmpty()) {
                for (String containedObjId : containedObjIds) {
                    result.add(decorateContainedObjId(containedObjId));
                }
            }
        }
        return result;
    }

    private ObjBuildingBlocks createObjBuildingBlocks(
            final Obj obj,
            final ExportData exportData
    ) throws NLBConsistencyException, NLBExportException {
        NonLinearBook nlb = obj.getCurrentNLB();
        ObjBuildingBlocks blocks = new ObjBuildingBlocks();
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
        if (!StringHelper.isEmpty(obj.getDefaultTagId())) {
            Variable deftag = exportData.getNlb().getVariableById(obj.getDefaultTagId());
            // TODO: Add cases with deleted pages/links/variables etc. to the unit test
            if (!deftag.isDeleted()) {
                blocks.setObjDefaultTagVariable(deftag.getValue());
            } else {
                blocks.setObjDefaultTagVariable(EMPTY_STRING);
            }
        } else {
            blocks.setObjDefaultTagVariable(EMPTY_STRING);
        }
        if (!StringHelper.isEmpty(obj.getConstrId())) {
            Variable constraint = exportData.getNlb().getVariableById(obj.getConstrId());
            // TODO: Add cases with deleted pages/links/variables etc. to the unit test
            if (!constraint.isDeleted()) {
                blocks.setObjConstraint(decorateObjConstraint(translateConstraintBody(constraint.getValue(), true, false, Constants.EMPTY_STRING, Constants.EMPTY_STRING)));
            } else {
                blocks.setObjConstraint(EMPTY_STRING);
            }
        } else {
            blocks.setObjConstraint(EMPTY_STRING);
        }
        if (!StringHelper.isEmpty(obj.getCommonToId())) {
            Variable commonTo = exportData.getNlb().getVariableById(obj.getCommonToId());
            // TODO: Add cases with deleted pages/links/variables etc. to the unit test
            if (!commonTo.isDeleted()) {
                blocks.setObjCommonTo(decorateObjCommonTo(commonTo.getValue()));
                Obj commonToObj = exportData.getNlb().getObjById(commonTo.getValue());
                List<String> decoratedContainedObjIds = getDecoratedContainedObjIds(commonToObj);
                for (String containedObjId : decoratedContainedObjIds) {
                    blocks.addContainedObjId(containedObjId);
                }
            } else {
                blocks.setObjCommonTo(decorateObjCommonTo(EMPTY_STRING));
            }
        } else {
            blocks.setObjCommonTo(decorateObjCommonTo(EMPTY_STRING));
        }
        blocks.setObjModifications(
                decorateObjModifications(
                        buildModificationsText(EMPTY_STRING, obj.getModifications(), exportData)
                )
        );
        final ObjType objType = getObjType(obj, exportData);
        blocks.setObjLabel(decorateObjLabel(obj.getId()));
        blocks.setObjComment(decorateObjComment(obj.getName()));
        // blocks obj default tag variable was set earlier
        blocks.setObjStart(decorateObjStart(obj.getId(), getContainerRef(obj, exportData), objType, obj.isPreserved(), obj.isLoadOnce(), obj.isClearUnderTooltip(), obj.isActOnKey(), obj.isCacheText(), obj.isLooped(), blocks.getObjDefaultTagVariable()));
        blocks.setObjName(decorateObjName(obj.getName(), obj.getId()));
        blocks.setObjAlias(StringHelper.notEmpty(obj.getName()) ? decorateAutoVar(obj.getName()) : Constants.EMPTY_STRING);
        String imageFileName = (nlb.isSuppressMedia()) ? Obj.DEFAULT_IMAGE_FILE_NAME : obj.getImageFileName();
        List<ImagePathData> imagePaths = getImagePaths(obj.getExternalHierarchy(), imageFileName, obj.isAnimatedImage(), obj.isAnimatedImage() && obj.isGraphical());
        int maxStep = (imagePaths.size() > 0 && imagePaths.get(0).getMaxFrameNumber() > 0) ? imagePaths.get(0).getMaxFrameNumber() : obj.getMaxFrame();
        blocks.setObjPreload(decorateObjPreload(obj.getStartFrame(), maxStep, obj.getPreloadFrames()));
        final String objImage = decorateObjImage(imagePaths, obj.isGraphical());
        boolean hasParentObj = (obj.getContainerType() == Obj.ContainerType.Obj);
        int curStep = obj.getStartFrame() > 0 ? obj.getStartFrame() : getCurStep(obj.getEffect());
        final String objEffect = decorateObjEffect(obj.getOffset(), (hasParentObj) ? "0,0" : getRelativeCoords(obj), obj.isGraphical(), hasParentObj, obj.getMovementDirection(), obj.getEffect(), obj.getCoordsOrigin(), obj.getStartFrame(), curStep, maxStep);
        blocks.setObjEffect(objEffect);
        Coords coords = getRelativeCoordsOrOffset(obj);
        blocks.setObjArm(obj.isGraphical() && hasParentObj ? decorateObjArm(coords.getLeft(), coords.getTop()) : "");
        Obj morphOverObj = obj.getMorphOverObj();
        blocks.setMorphOver(decorateMorphOver((morphOverObj != null) ? morphOverObj.getId() : EMPTY_STRING, obj.isGraphical()));
        Obj morphOutObj = obj.getMorphOutObj();
        blocks.setMorphOut(decorateMorphOut((morphOutObj != null) ? morphOutObj.getId() : EMPTY_STRING, obj.isGraphical()));
        final boolean hasImage = StringHelper.notEmpty(imageFileName);
        blocks.setObjImage(objImage);
        blocks.setObjDisp(decorateObjDisp(expandVariables(StringHelper.getTextChunks(obj.getDisp())), hasImage && obj.isImageInInventory(), obj.isGraphical()));
        blocks.setObjText(decorateObjText(obj.getId(), obj.getName(), obj.isSuppressDsc(), expandVariables(StringHelper.getTextChunks(obj.getText())), hasImage && obj.isImageInScene(), obj.isGraphical()));
        blocks.setGraphical(obj.isGraphical());
        blocks.setTakable(obj.isTakable());
        blocks.setObjTak(decorateObjTak(obj.getName()));
        blocks.setObjInv(decorateObjInv(objType));
        blocks.setObjActStart(decorateObjActStart(expandVariables(StringHelper.getTextChunks(obj.getActText()))));
        blocks.setObjActEnd(decorateObjActEnd(obj.isCollapsable()));
        blocks.setObjUseStart(decorateObjUseStart());
        blocks.setObjUseEnd(decorateObjUseEnd());
        blocks.setObjEnd(decorateObjEnd());
        blocks.setObjObjStart(decorateObjObjStart());
        List<String> decoratedContainedObjIds = getDecoratedContainedObjIds(obj);
        for (String containedObjId : decoratedContainedObjIds) {
            blocks.addContainedObjId(containedObjId);
        }
        blocks.setObjObjEnd(decorateObjObjEnd());
        String soundFileName = ((nlb.isSuppressMedia() || nlb.isSuppressSound()) ? Obj.DEFAULT_SOUND_FILE_NAME: obj.getSoundFileName());
        blocks.setObjSound(decorateObjSound(getSoundPaths(obj.getExternalHierarchy(), soundFileName), obj.isSoundSFX()));
        List<Link> links = obj.getLinks();
        for (final Link link : links) {
            if (!link.isDeleted()) {
                UseBuildingBlocks useBuildingBlocks = createUseBuildingBlocks(obj, createPreprocessedLink(link), exportData);
                blocks.addUseBuildingBlocks(useBuildingBlocks);
            }
        }
        return blocks;
    }

    private Coords getRelativeCoordsOrOffset(Obj obj) {
        String offset = obj.getOffset();
        if (StringHelper.isEmpty(offset)) {
            return obj.getRelativeCoords(true);
        } else {
            String[] offsets = offset.split(",");
            if (offsets.length == 2) {
                CoordsLw coords = new CoordsLw();
                coords.setLeft(Integer.parseInt(offsets[0].trim()));
                coords.setTop(Integer.parseInt(offsets[1].trim()));
                return coords;
            } else {
                return CoordsLw.ZERO_COORDS;
            }
        }
    }

    private int getCurStep(Obj.Effect effect) {
        switch (effect) {
            case FadeIn:
            case FadeOut:
            case None:
                return 0;
            default:
                return 2;
        }
    }

    private ObjType getObjType(final Obj obj, final ExportData exportData) {
        if ((obj.getLinks().size() == 0) && !exportData.hasInwardLinks(obj.getId())) {
            if (obj.isGraphical()) {
                return ObjType.GMENU;
            } else {
                if (
                        !obj.getDisps().isEmpty()
                                && obj.getTexts().isEmpty()
                                && obj.getActTexts().isEmpty()
                                && obj.isTakable()
                                && obj.hasNoModifications()
                                && StringHelper.isEmpty(obj.getCommonToId())
                        ) {
                    return ObjType.STAT;
                } else {
                    return ObjType.MENU;
                }
            }
        } else {
            if (obj.isGraphical()) {
                return ObjType.GOBJ;
            } else {
                return ObjType.OBJ;
            }
        }
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
            public String getAltText() {
                return escapeText(link.getAltText());
            }

            @Override
            public MultiLangString getAltTexts() {
                return escapeMultiLang(link.getAltTexts());
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
            public boolean isOnce() {
                return link.isOnce();
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
            public boolean hasNoModifications() {
                return link.hasNoModifications();
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
            public SearchResult searchText(SearchContract contract) {
                return link.searchText(contract);
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
            public boolean isImageAnimated() {
                return page.isImageAnimated();
            }

            @Override
            public String getSoundFileName() {
                return page.getSoundFileName();
            }

            @Override
            public boolean isSoundSFX() {
                return page.isSoundSFX();
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
            public Theme getTheme() {
                return page.getTheme();
            }

            @Override
            public String getVarId() {
                return page.getVarId();
            }

            @Override
            public String getTimerVarId() {
                return page.getTimerVarId();
            }

            @Override
            public String getCaption() {
                return escapeText(page.getCaption());
            }

            @Override
            public String getNotes() {
                return escapeText(page.getNotes());
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
            public boolean isUseMPL() {
                return page.isUseMPL();
            }

            @Override
            public boolean isLeaf() {
                return page.isLeaf();
            }

            @Override
            public boolean isFinish() {
                return page.isFinish();
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
            public boolean isModuleExternal() {
                return page.isModuleExternal();
            }

            @Override
            public String getExternalHierarchy() {
                return page.getExternalHierarchy();
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
            public boolean isGlobalAutowire() {
                return page.isGlobalAutowire();
            }

            @Override
            public boolean isNoSave() {
                return page.isNoSave();
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
            public String getDefaultTagId() {
                return page.getDefaultTagId();
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
            public boolean hasNoModifications() {
                return page.hasNoModifications();
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
            public SearchResult searchText(SearchContract contract) {
                return page.searchText(contract);
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
            public Theme getTheme() {
                return obj.getTheme();
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
            public String getCommonToId() {
                return obj.getCommonToId();
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
            public String getSoundFileName() {
                return obj.getSoundFileName();
            }

            @Override
            public boolean isSoundSFX() {
                return obj.isSoundSFX();
            }

            @Override
            public boolean isAnimatedImage() {
                return obj.isAnimatedImage();
            }

            @Override
            public boolean isSuppressDsc() {
                return obj.isSuppressDsc();
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
            public boolean isGraphical() {
                return obj.isGraphical();
            }

            @Override
            public boolean isPreserved() {
                return obj.isPreserved();
            }

            @Override
            public boolean isLoadOnce() {
                return obj.isLoadOnce();
            }

            @Override
            public boolean isCollapsable() {
                return obj.isCollapsable();
            }

            @Override
            public String getOffset() {
                return obj.getOffset();
            }

            @Override
            public MovementDirection getMovementDirection() {
                return obj.getMovementDirection();
            }

            @Override
            public Effect getEffect() {
                return obj.getEffect();
            }

            @Override
            public int getStartFrame() {
                return obj.getStartFrame();
            }

            @Override
            public int getMaxFrame() {
                return obj.getMaxFrame();
            }

            @Override
            public int getPreloadFrames() {
                return obj.getPreloadFrames();
            }

            @Override
            public CoordsOrigin getCoordsOrigin() {
                return obj.getCoordsOrigin();
            }

            @Override
            public boolean isClearUnderTooltip() {
                return obj.isClearUnderTooltip();
            }

            @Override
            public boolean isActOnKey() {
                return obj.isActOnKey();
            }

            @Override
            public boolean isCacheText() {
                return obj.isCacheText();
            }

            @Override
            public boolean isLooped() {
                return obj.isLooped();
            }

            public String getMorphOverId() {
                return obj.getMorphOverId();
            }

            @Override
            public Obj getMorphOverObj() {
                return obj.getMorphOverObj();
            }

            public String getMorphOutId() {
                return obj.getMorphOutId();
            }

            @Override
            public Obj getMorphOutObj() {
                return obj.getMorphOutObj();
            }

            @Override
            public Coords getRelativeCoords(final boolean lookInMorphs) {
                return obj.getRelativeCoords(lookInMorphs);
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
            public ContainerType getContainerType() {
                return obj.getContainerType();
            }

            @Override
            public String getCumulativeText(List<String> objIdsToBeExcluded, Map<String, Object> visitedVars) {
                return obj.getCumulativeText(objIdsToBeExcluded, visitedVars);
            }

            @Override
            public String getDefaultTagId() {
                return obj.getDefaultTagId();
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
            public String getExternalHierarchy() {
                return obj.getExternalHierarchy();
            }

            @Override
            public List<Modification> getModifications() {
                return obj.getModifications();
            }

            @Override
            public boolean hasNoModifications() {
                return obj.hasNoModifications();
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
            public SearchResult searchText(SearchContract contract) {
                return obj.searchText(contract);
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

    protected String decorateObjStart(final String id, String containerRef, ObjType objType, boolean preserved, boolean loadOnce, boolean clearUnderTooltip, boolean actOnKey, boolean cacheText, boolean looped, String objDefaultTag) {
        return EMPTY_STRING;
    }

    protected String decorateObjName(String name, String id) {
        return EMPTY_STRING;
    }

    protected String decorateObjEffect(String offsetString, String coordString, boolean graphicalObj, boolean hasParentObj, Obj.MovementDirection movementDirection, Obj.Effect effect, Obj.CoordsOrigin coordsOrigin, int startFrame, int curStep, int maxStep) {
        return EMPTY_STRING;
    }

    protected String decorateObjPreload(int startFrame, int maxFrames, int preloadFrames) {
        return EMPTY_STRING;
    }

    protected String decorateMorphOver(String morphOverId, boolean graphicalObj) {
        return EMPTY_STRING;
    }

    protected String decorateMorphOut(String morphOutId, boolean graphicalObj) {
        return EMPTY_STRING;
    }

    protected String decorateObjImage(List<ImagePathData> objImagePathDatas, boolean graphicalObj) {
        return EMPTY_STRING;
    }

    protected String decorateObjDisp(String dispText, boolean imageEnabled, boolean isGraphicalObj) {
        return EMPTY_STRING;
    }

    protected String decorateObjText(String objId, String objName, boolean suppressDsc, String objText, boolean imageEnabled, boolean isGraphicalObj) {
        return EMPTY_STRING;
    }

    protected String decorateObjTak(final String objName) {
        return EMPTY_STRING;
    }

    protected String decorateObjInv(ObjType objType) {
        return EMPTY_STRING;
    }

    protected String decorateObjVariable(String variableName) {
        return EMPTY_STRING;
    }

    protected String decorateObjConstraint(String constraintValue) {
        return EMPTY_STRING;
    }

    protected String decorateObjCommonTo(String commonObjId) {
        return EMPTY_STRING;
    }

    protected String decorateObjModifications(String modificationsText) {
        return EMPTY_STRING;
    }

    protected String decorateObjActStart(String actTextExpanded) {
        return EMPTY_STRING;
    }

    protected String decorateObjActEnd(boolean collapsable) {
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

    protected String decorateObjSound(List<SoundPathData> objSoundPathDatas, boolean soundSFX) {
        return EMPTY_STRING;
    }

    protected String decorateObjArm(float left, float top) {
        return EMPTY_STRING;
    }

    private LinkBuildingBlocks createLinkBuildingBlocks(
            final Page page,
            final Link link,
            final ExportData exportData
    ) throws NLBConsistencyException {
        LinkBuildingBlocks blocks = new LinkBuildingBlocks();
        blocks.setTheme(page.getTheme());
        final boolean trivial = determineTrivialStatus(link);
        blocks.setAuto(link.isAuto());
        String expandedLinkText = expandVariablesForLinks(StringHelper.getTextChunks(link.getText()), page.getTheme());
        blocks.setLinkAltText(decorateLinkAltText(expandVariablesForLinks(StringHelper.getTextChunks(link.getAltText()), page.getTheme())));
        blocks.setTrivial(trivial);
        blocks.setLinkLabel(decorateLinkLabel(link.getId(), expandedLinkText, page.getTheme()));
        blocks.setLinkComment(decorateLinkComment(link.getText()));
        // TODO: exportData.getIdToPageNumberMap().get(link.getTarget()) can produce NPE for return links
        blocks.setLinkStart(
                decorateLinkStart(
                        link.getId(),
                        expandedLinkText,
                        link.isAuto(),
                        trivial,
                        checkedGetPageNumber(link.getTarget()),
                        page.getTheme()
                )
        );
        Variable variable = exportData.getNlb().getVariableById(link.getVarId());
        String linkVisitStateVariable = link.isOnce() ? SpecialVariablesNameHelper.decorateLinkVisitStateVar(link.getId()) : Constants.EMPTY_STRING;
        boolean variableExists = (variable != null && !variable.isDeleted());
        String variableName = variableExists ? variable.getName() : Constants.EMPTY_STRING;
        if (variableExists) {
            blocks.setLinkVariable(decorateLinkVariable(variableName));
        } else {
            blocks.setLinkVariable(EMPTY_STRING);
        }
        if (link.isOnce()) {
            blocks.setLinkVisitStateVariable(decorateLinkVisitStateVariable(linkVisitStateVariable));
        } else {
            blocks.setLinkVisitStateVariable(EMPTY_STRING);
        }
        Variable constraint = exportData.getNlb().getVariableById(link.getConstrId());
        String additionalConstraintText = link.isOnce() ? "!" + SpecialVariablesNameHelper.decorateLinkVisitStateVar(link.getId()) : Constants.EMPTY_STRING;
        if (
                StringHelper.notEmpty(additionalConstraintText) ||
                        (constraint != null && !constraint.isDeleted())
                        || (
                        link.isObeyToModuleConstraint()
                                && !StringHelper.isEmpty(exportData.getModuleConstraintText())
                )
                ) {
            // If isOnce(), then additional constraint is '!lvs_xxxxx'
            blocks.setLinkConstraint(
                    translateConstraintBody(
                            (constraint != null) ? constraint.getValue().trim() : Constants.EMPTY_STRING,
                            link.isPositiveConstraint(),
                            link.isObeyToModuleConstraint(),
                            exportData.getModuleConstraintText(),
                            additionalConstraintText
                    )
            );
        } else {
            blocks.setLinkConstraint(EMPTY_STRING);
        }
        blocks.setLinkModifications(
                decorateLinkModifications(
                        buildModificationsText(getIndentString(), link.getModifications(), exportData)
                )
        );
        int targetPageNumber = checkedGetPageNumber(link.getTarget());
        blocks.setTargetPageNumber(targetPageNumber);
        blocks.setLinkGoTo(
                decorateLinkGoTo(
                        link.getId(),
                        expandedLinkText,
                        link.getTarget(),
                        targetPageNumber,
                        page.getTheme()
                )
        );
        blocks.setLinkEnd(
                decorateLinkEnd(page.getTheme())
        );
        return blocks;
    }

    protected String getIndentString() {
        return "    ";
    }

    private UseBuildingBlocks createUseBuildingBlocks(
            final Obj obj,
            final Link link,
            final ExportData exportData
    ) throws NLBConsistencyException {
        UseBuildingBlocks blocks = new UseBuildingBlocks();
        blocks.setUseTarget(decorateUseTarget(link.getTarget()));
        blocks.setUseSuccessText(expandVariablesForLinks(StringHelper.getTextChunks(link.getText()), obj.getTheme()));
        blocks.setUseFailureText(expandVariablesForLinks(StringHelper.getTextChunks(link.getAltText()), obj.getTheme()));
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
                            exportData.getModuleConstraintText(),
                            Constants.EMPTY_STRING
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
            String moduleConstraintText,
            String additionalConstraintText
    ) throws NLBConsistencyException {
        String constraintBody = (
                (shouldObeyToModuleConstraint && !StringHelper.isEmpty(moduleConstraintText))
                        ? (
                        (StringHelper.notEmpty(constraintText))
                                ? "(" + moduleConstraintText + ")&&" + "(" + constraintText + ")"
                                : moduleConstraintText
                )
                        : constraintText
        );
        if (StringHelper.notEmpty(additionalConstraintText)) {
            if (StringHelper.isEmpty(constraintBody)) {
                constraintBody = additionalConstraintText;
            } else {
                constraintBody = "(" + additionalConstraintText + ")&&" + "(" + constraintBody + ")";
            }
        }
        ExpressionData expressionData = translateExpressionBody(constraintBody);
        return (
                isPositiveConstraint
                        ? expressionData.getExistencePart() + "(" + expressionData.getExpressionPart() + ")"
                        : expressionData.getExistencePart() + "(" + decorateNot() + "(" + expressionData.getExpressionPart() + "))"
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
        for (final String expressionVar : expressionVars) {
            String decoratedVariable = decorateVariable(expressionVar);
            Variable.DataType dataType = m_dataTypeMap.get(expressionVar);
            if (dataType != Variable.DataType.BOOLEAN) {
                String existenceExpression = decorateExistence(decoratedVariable);
                if (!StringHelper.isEmpty(existenceExpression)) {
                    existenceBuilder.append(existenceExpression).append(" ").append(decorateAnd()).append(" ");
                }
            }
            expression = (
                    expression.replaceAll(
                            "\\b" + expressionVar + "\\b",
                            Matcher.quoteReplacement(decoratedVariable)
                    )
            );
        }
        expression = expression.replaceAll(NOT_PLACEHOLDER + " ", decorateNot());  // decorateNot() should append whitespace in the end, if needed!
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
                return additionalDecorationForVariableInExpression(decorateBooleanVar(constraintVar));
            case NUMBER:
                return decorateNumberVar(constraintVar);
            case STRING:
                return decorateStringVar(constraintVar);
            case AUTO:
            default:
                return additionalDecorationForVariableInExpression(decorateAutoVar(constraintVar));
        }
    }

    /**
     * Possible additional decoration, should be applied to all BOOLEAN or AUTO variables.
     * It was introduced in order to provide mandatory ()'s in ChoiceScript's not().
     * By default, no additional modifications are done.
     * @param variable
     * @return
     */
    protected String additionalDecorationForVariableInExpression(String variable) {
        return variable;
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
        final Page modulePage = exportData.getModulePage();
        for (final Modification modification : modifications) {
            final boolean shouldUse = !modification.isExternal() || ((modulePage != null) && modulePage.isModuleExternal());
            if (!modification.isDeleted() && shouldUse) {
                stringBuilder.append(indentString);
                Variable variable = (
                        StringHelper.isEmpty(modification.getVarId())
                                ? null
                                : exportData.getNlb().getVariableById(modification.getVarId())
                );
                if (modification.returnsValue() && (variable == null || variable.isDeleted())) {
                    throw new NLBConsistencyException(
                            "Variable with id = " + modification.getVarId()
                                    + " cannot be found for modification "
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
                boolean unique = false;
                switch (modification.getType()) {
                    case TAG:
                        // TODO: is obj id really necessary for tag and gettag operations???
                        boolean hasName = variable != null;
                        String varName = (variable != null) ? variable.getName() : Constants.EMPTY_STRING;
                        final String objIdToTag = (hasName) ? exportData.getObjId(varName) : null;
                        stringBuilder.append(
                                decorateTag(
                                        hasName ? decorateAutoVar(varName) : Constants.EMPTY_STRING,
                                        objIdToTag,
                                        expression.getValue()
                                )
                        );
                        break;
                    case GETTAG:
                        // Actually this is double check, because empty variable was already checked
                        String resName = (variable != null) ?  variable.getName() : Constants.EMPTY_STRING;
                        final String gettagObjId = (expression != null) ? exportData.getObjId(expression.getValue()) : null;
                        stringBuilder.append(
                                decorateGetTagOperation(
                                        decorateStringVar(resName),
                                        gettagObjId,
                                        (expression != null && StringHelper.notEmpty(expression.getValue()))
                                                ? decorateAutoVar(expression.getValue())
                                                : Constants.EMPTY_STRING
                                )
                        );
                        break;
                    case WHILE:
                        stringBuilder.append(
                                decorateWhile(
                                        translateConstraintBody(
                                                expression.getValue(),
                                                true,
                                                false,
                                                Constants.EMPTY_STRING,
                                                Constants.EMPTY_STRING
                                        )
                                )
                        );
                        break;
                    case IF:
                        stringBuilder.append(
                                decorateIf(
                                        translateConstraintBody(
                                                expression.getValue(),
                                                true,
                                                false,
                                                Constants.EMPTY_STRING,
                                                Constants.EMPTY_STRING
                                        )
                                )
                        );
                        break;
                    case IFHAVE:
                        assert variable != null;
                        final String objIdForIfHave = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateIfHave(
                                        objIdForIfHave,
                                        decorateAutoVar(expression.getValue())
                                )
                        );
                        break;
                    case ELSE:
                        stringBuilder.append(decorateElse());
                        break;
                    case ELSEIF:
                        stringBuilder.append(
                                decorateElseIf(
                                        translateConstraintBody(
                                                expression.getValue(),
                                                true,
                                                false,
                                                Constants.EMPTY_STRING,
                                                Constants.EMPTY_STRING
                                        )
                                )
                        );
                        break;
                    case END:
                        stringBuilder.append(decorateEnd());
                        break;
                    case RETURN:
                        stringBuilder.append(decorateReturn());
                        break;
                    case HAVE:
                        assert variable != null;
                        final String objIdToCheck = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateHaveOperation(
                                        decorateBooleanVar(variable.getName()),
                                        objIdToCheck,
                                        decorateAutoVar(expression.getValue())
                                )
                        );
                        break;
                    case CLONE:
                        assert variable != null;
                        final String cloneArg = (expression != null) ? decorateAutoVar(expression.getValue()) : null;
                        final String objIdToClone = (expression != null) ? exportData.getObjId(expression.getValue()) : null;
                        stringBuilder.append(
                                decorateCloneOperation(
                                        decorateAutoVar(variable.getName()),
                                        objIdToClone,
                                        cloneArg
                                )
                        );
                        break;
                    case CNTNR:
                        assert variable != null;
                        final String containerArg = (expression != null) ? decorateAutoVar(expression.getValue()) : null;
                        final String objIdToGetContainer = (expression != null) ? exportData.getObjId(expression.getValue()) : null;
                        stringBuilder.append(
                                decorateContainerOperation(
                                        decorateAutoVar(variable.getName()),
                                        objIdToGetContainer,
                                        containerArg
                                )
                        );
                        break;
                    case ID:
                        assert variable != null;
                        final String objIdToGetId = exportData.getObjId(expression.getValue());
                        // Datatype should be String
                        stringBuilder.append(
                                decorateGetIdOperation(
                                        (variable.getDataType() == Variable.DataType.STRING)
                                                ? decorateStringVar(variable.getName())
                                                : decorateAutoVar(variable.getName()),
                                        objIdToGetId,
                                        decorateAutoVar(expression.getValue())
                                )
                        );
                        break;
                    case ADDU:
                        unique = true;
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
                                                : exportData.getNlb().getObjById(objIdToAdd).getDisp(),
                                        unique
                                )
                        );
                        break;
                    case ADDINV:
                        final String objIdToAddInv = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateAddInvObj(
                                        objIdToAddInv,
                                        decorateAutoVar(expression.getValue()),
                                        expression.getValue(),
                                        (objIdToAddInv == null)
                                                ? null
                                                : exportData.getNlb().getObjById(objIdToAddInv).getDisp()
                                )
                        );
                        break;
                    case ADDALLU:
                        unique = true;
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
                                                ? decorateAutoVar(variable.getName())
                                                : null,
                                        decorateAutoVar(expression.getValue()),
                                        unique
                                )
                        );
                        break;
                    case REMOVE:
                        final String removeDestinationId = (
                                (variable != null)
                                        ? exportData.getObjId(variable.getName())
                                        : null
                        );
                        final String removeDestinationName = (
                                (variable != null && removeDestinationId == null)
                                        ? decorateAutoVar(variable.getName())
                                        : null
                        );
                        final String objIdToRemove = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateDelObj(
                                        removeDestinationId,
                                        removeDestinationName,
                                        objIdToRemove,
                                        decorateAutoVar(expression.getValue()),
                                        expression.getValue(),
                                        (objIdToRemove != null)
                                                ? exportData.getNlb().getObjById(objIdToRemove).getDisp()
                                                : null)
                        );
                        break;
                    case RMINV:
                        final String objIdToRmInv = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateDelInvObj(
                                        objIdToRmInv,
                                        decorateAutoVar(expression.getValue()),
                                        expression.getValue(),
                                        (objIdToRmInv != null)
                                                ? exportData.getNlb().getObjById(objIdToRmInv).getDisp()
                                                : null
                                )
                        );
                        break;
                    case CLEAR:
                        final String destinationId = (expression != null) ? exportData.getObjId(expression.getValue()) : null;
                        stringBuilder.append(
                                decorateClearOperation(
                                        destinationId,
                                        (expression != null) ? decorateAutoVar(expression.getValue()) : null
                                )
                        );
                        break;
                    case CLRINV:
                        stringBuilder.append(decorateClearInvOperation());
                        break;
                    case OBJS:
                        final String srcObjId = exportData.getObjId(expression.getValue());
                        if (variable != null) {
                            stringBuilder.append(
                                    decorateObjsOperation(
                                            decorateAutoVar(variable.getName()),
                                            srcObjId,
                                            decorateAutoVar(expression.getValue())
                                    )
                            );
                        } else {
                            throw new NLBConsistencyException(
                                    "Destination list variable name is not specified for objs operation"
                            );
                        }
                        break;
                    case SSND:
                        stringBuilder.append(decorateSSndOperation());
                        break;
                    case WSND:
                        stringBuilder.append(decorateWSndOperation());
                        break;
                    case SND:
                        final String sndArgObjId = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateSndOperation(sndArgObjId, decorateAutoVar(expression.getValue()))
                        );
                        break;
                    case SPUSH:
                        stringBuilder.append(decorateSPushOperation(decorateAutoVar(expression.getValue())));
                        break;
                    case WPUSH:
                        stringBuilder.append(decorateWPushOperation(decorateAutoVar(expression.getValue())));
                        break;
                    case PUSH:
                        final String objIdToPush = exportData.getObjId(expression.getValue());
                        if (variable != null) {
                            stringBuilder.append(
                                    decoratePushOperation(
                                            decorateAutoVar(variable.getName()),
                                            objIdToPush,
                                            decorateAutoVar(expression.getValue())
                                    )
                            );
                        } else {
                            throw new NLBConsistencyException(
                                    "Destination list variable name is not specified for push operation"
                            );
                        }
                        break;
                    case POP:
                        assert variable != null;
                        // expression value is the name of the list to pop from
                        stringBuilder.append(
                                decoratePopOperation(
                                        decorateAutoVar(variable.getName()),
                                        decorateAutoVar(expression.getValue())
                                )
                        );
                        break;
                    case SINJECT:
                        stringBuilder.append(decorateSInjectOperation(decorateAutoVar(expression.getValue())));
                        break;
                    case INJECT:
                        final String objIdToInject = exportData.getObjId(expression.getValue());
                        if (variable != null) {
                            stringBuilder.append(
                                    decorateInjectOperation(
                                            decorateAutoVar(variable.getName()),
                                            objIdToInject,
                                            decorateAutoVar(expression.getValue())
                                    )
                            );
                        } else {
                            throw new NLBConsistencyException(
                                    "Destination list variable name is not specified for inject operation"
                            );
                        }
                        break;
                    case EJECT:
                        assert variable != null;
                        // expression value is the name of the list to eject from
                        stringBuilder.append(
                                decorateEjectOperation(
                                        decorateAutoVar(variable.getName()),
                                        decorateAutoVar(expression.getValue())
                                )
                        );
                        break;
                    case SHUFFLE:
                        stringBuilder.append(decorateShuffleOperation(decorateAutoVar(expression.getValue())));
                        break;
                    case PRN:
                        String prnArg;
                        switch (expression.getDataType()) {
                            case STRING:
                                prnArg = decorateStringVar(expression.getValue());
                                break;
                            case BOOLEAN:
                                prnArg = decorateBooleanVar(expression.getValue());
                                break;
                            case NUMBER:
                                prnArg = decorateNumberVar(expression.getValue());
                                break;
                            default:
                                prnArg = decorateAutoVar(expression.getValue());
                        }
                        stringBuilder.append(decoratePRNOperation(prnArg));
                        break;
                    case DSC:
                        assert variable != null;
                        final String dscObjId = exportData.getObjId(expression.getValue());
                        // Left part of assignment should be string variable
                        stringBuilder.append(
                                decorateDSCOperation(
                                        decorateStringVar(variable.getName()),
                                        decorateAutoVar(expression.getValue()),
                                        dscObjId
                                )
                        );
                        break;
                    case PDSC:
                        stringBuilder.append(decoratePDscOperation(decorateAutoVar(expression.getValue())));
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
                    case ACTT:
                        assert variable != null;
                        final String actObjId = exportData.getObjId(expression.getValue());
                        // Left part of assignment should be string variable
                        stringBuilder.append(
                                decorateActtOperation(
                                        decorateStringVar(variable.getName()),
                                        decorateAutoVar(expression.getValue()),
                                        actObjId
                                )
                        );
                        break;
                    case ACTF:
                        final String actfingObjId = exportData.getObjId(expression.getValue());
                        stringBuilder.append(
                                decorateActfOperation(
                                        decorateAutoVar(expression.getValue()),
                                        actfingObjId
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
                                        decorateAutoVar(expression.getValue())
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
                    case ACHIEVE:
                        stringBuilder.append(decorateAchieveOperation(expression.getValue()));
                        break;
                    case ACHIEVED:
                        assert variable != null;
                        // Achieved always returns number of achievements
                        stringBuilder.append(decorateAchievedOperation(decorateAutoVar(variable.getName()), expression.getValue()));
                        break;
                    case GOTO:
                        stringBuilder.append(decorateGoToOperation(expression.getValue()));
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

    protected abstract String decorateTag(String variable, final String objId, String tag);

    protected abstract String decorateGetTagOperation(String resultingVariable, final String objId, String objVariableName);

    protected abstract String decorateWhile(String constraint);

    protected abstract String decorateIf(String constraint);

    protected abstract String decorateIfHave(String objId, String objVar);

    protected abstract String decorateElse();

    protected abstract String decorateElseIf(String constraint);

    protected abstract String decorateEnd();

    protected abstract String decorateReturn();

    protected abstract String decorateHaveOperation(String variableName, String objId, String objVar);

    protected abstract String decorateCloneOperation(String variableName, String objId, String objVar);

    protected abstract String decorateContainerOperation(String variableName, String objId, String objVar);

    protected abstract String decorateGetIdOperation(String variableName, String objId, String objVar);

    protected abstract String decorateDelObj(String destinationId, final String destinationName, String objectId, String objectVar, String objectName, String objectDisplayName);

    protected abstract String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName);

    protected abstract String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique);

    protected abstract String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName);

    protected abstract String decorateAddAllOperation(String destinationId, String destinationListVariableName, String sourceListVariableName, boolean unique);

    protected abstract String decorateObjsOperation(String listVariableName, String srcObjId, String objectVar);

    protected abstract String decorateSSndOperation();

    protected abstract String decorateWSndOperation();

    protected abstract String decorateSndOperation(String objectId, String objectVar);

    protected abstract String decorateSPushOperation(String listVariableName);

    protected abstract String decorateWPushOperation(String listVariableName);

    protected abstract String decoratePushOperation(String listVariableName, String objectId, String objectVar);

    protected abstract String decoratePopOperation(String variableName, String listVariableName);

    protected abstract String decorateSInjectOperation(String listVariableName);

    protected abstract String decorateInjectOperation(String listVariableName, String objectId, String objectVar);

    protected abstract String decorateEjectOperation(String variableName, String listVariableName);

    protected abstract String decorateClearOperation(String destinationId, String destinationVar);

    protected abstract String decorateClearInvOperation();

    protected abstract String decorateSizeOperation(String variableName, String listVariableName);

    protected abstract String decorateRndOperation(String variableName, String maxValue);

    protected abstract String decorateAchieveOperation(String achievementName);

    protected abstract String decorateAchievedOperation(String variableName, String achievementName);

    protected abstract String decorateGoToOperation(String locationId);

    protected abstract String decorateShuffleOperation(String listVariableName);

    protected abstract String decoratePRNOperation(String variableName);

    protected abstract String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId);

    protected abstract String decoratePDscOperation(String objVariableName);

    protected abstract String decorateActOperation(String actingObjVariable, String actingObjId);

    protected abstract String decorateActtOperation(String resultVariableName, String actObjVariable, String actObjId);

    protected abstract String decorateActfOperation(String actingObjVariable, String actingObjId);

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

    /**
     * This method should append whitespace in the end, if needed!
     * @return
     */
    protected abstract String decorateNot();

    protected abstract String decorateOr();

    protected abstract String decorateAnd();

    protected abstract String decorateExistence(String decoratedVariable);

    protected abstract String decorateBooleanVar(String constraintVar);

    protected abstract String decorateStringVar(String constraintVar);

    protected abstract String decorateNumberVar(String constraintVar);

    protected String decorateLinkAltText(String text) {
        return text;
    }

    protected abstract String decorateLinkLabel(String linkId, String linkText, Theme theme);

    protected abstract String decorateLinkComment(String comment);

    protected abstract String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, int pageNumber, Theme theme);

    protected abstract String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkTarget,
            int targetPageNumber,
            Theme theme);

    protected String decorateLinkEnd(Theme theme) {
        return Constants.EMPTY_STRING;
    }

    protected abstract String decoratePageEnd(boolean isFinish);

    protected abstract String decorateLinkVariable(String variableName);

    protected abstract String decorateLinkVisitStateVariable(String linkVisitStateVariable);

    protected abstract String decoratePageVariable(final String variableName);

    protected abstract String decoratePageTimerVariableInit(final String variableName);

    protected abstract String decoratePageTimerVariable(final String variableName);

    protected abstract String decoratePageModifications(final String modificationsText);

    protected abstract String decorateLinkModifications(final String modificationsText);

    protected abstract String decoratePageCaption(String caption, boolean useCaption, String moduleTitle, boolean noSave);

    protected abstract String decoratePageNotes(String notes);

    @NotNull
    protected String getNonEmptyTitle(String moduleTitle) {
        return StringHelper.notEmpty(moduleTitle) ? moduleTitle : "...";
    }

    private String getNonEmptyTitle(NonLinearBook module) {
        String title = module.getTitle();
        if (StringHelper.isEmpty(title) && module.getParentNLB() != null && !module.getParentNLB().isDummy()) {
            return getNonEmptyTitle(module.getParentNLB());
        }
        return getNonEmptyTitle(title);
    }

    protected abstract String decoratePageImage(List<ImagePathData> pageImagePathDatas, final boolean imageBackground, Theme theme);

    protected abstract String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX, Theme theme);

    /**
     * Returns the name of the media file to which mediaFileName is redirected, or mediaFileName
     * if no redirect was found.
     * @param mediaFileName media file name
     * @return the name of the media file to which mediaFileName is redirected, or mediaFileName
     * if no redirect was found.
     */
    private String getRedirectMediaOrSelf(final String mediaFileName) {
        if (m_mediaRedirectsMap.containsKey(mediaFileName)) {
            return m_mediaRedirectsMap.get(mediaFileName);
        }
        return mediaFileName;
    }

    /**
     * Expands variables from text chunks.
     * By default the text is copied as is.
     * Override this method if you really want to expand variable values.
     *
     * @param textChunks
     * @param theme
     * @return
     */
    protected String expandVariables(List<TextChunk> textChunks, Theme theme) {
        return expandVariables(textChunks);
    }

    /**
     * Expands variables from text chunks for links.
     * By default it is the same as expandVariables()
     *
     * @param textChunks
     * @param theme
     * @return
     */
    protected String expandVariablesForLinks(List<TextChunk> textChunks, Theme theme) {
        return expandVariables(textChunks, theme);
    }

    /**
     * Expands variables from text chunks.
     * By default the text is copied as is.
     * Override this method if you really want to expand variable values.
     *
     * @param textChunks
     * @return
     */
    protected String expandVariables(List<TextChunk> textChunks) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append(textChunk.getText());
                    break;
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

    /**
     * NB: in case of ordinary (inline) NLB modules externalHierarchy should be <code>null</code>
     * (if this inline module is inside the main book and not inside of external module)
     *
     * @param externalHierarchy
     * @param imageFileNames
     * @param removeFrameNumber
     * @return
     */
    protected List<ImagePathData> getImagePaths(
            final String externalHierarchy,
            final String imageFileNames,
            final boolean animatedImage,
            final boolean removeFrameNumber
    ) throws NLBExportException {
        if (StringHelper.isEmpty(imageFileNames)) {
            return new ArrayList<ImagePathData>() {{
                add(ImagePathData.EMPTY);
            }};
        } else {
            List<ImagePathData> result = new ArrayList<>();
            String[] fileNamesArr = imageFileNames.split(Constants.MEDIA_FILE_NAME_SEP);
            for (String fileName : fileNamesArr) {
                MediaExportParameters mediaExportParameters = getMediaExportParameters(fileName);
                result.add(getImagePath(externalHierarchy, fileName, animatedImage, removeFrameNumber, mediaExportParameters));
            }
            return result;
        }
    }

    private MediaExportParameters getMediaExportParameters(String fileName) {
        MediaExportParameters result = m_mediaExportParametersMap.get(fileName);
        if (result != null) {
            return result;
        } else {
            return MediaExportParameters.getDefault();
        }
    }

    /**
     * NB: in case of ordinary (inline) NLB modules externalHierarchy should be <code>null</code>
     * (if this inline module is inside the main book and not inside of external module)
     *
     * @param externalHierarchy
     * @param imageFileName
     * @param removeFrameNumber
     *@param mediaExportParameters  @return
     */
    protected ImagePathData getImagePath(
            final String externalHierarchy,
            final String imageFileName,
            final boolean animatedImage,
            final boolean removeFrameNumber,
            final MediaExportParameters mediaExportParameters
    ) throws NLBExportException {
        ImagePathData result = new ImagePathData();
        result.setRemoveFrameNumber(removeFrameNumber);
        // Please note that here we use redirected file name.
        Matcher matcher = FILE_NAME_PATTERN.matcher(getRedirectMediaOrSelf(imageFileName));
        if (matcher.find()) {
            if (StringHelper.isEmpty(externalHierarchy)) {
                result.setParentFolderPath(NonLinearBook.IMAGES_DIR_NAME);
            } else {
                result.setParentFolderPath(NonLinearBook.IMAGES_DIR_NAME + "/" + externalHierarchy);
            }
            if (animatedImage) {
                String fileName = matcher.group(1);
                int len = fileName.length();
                if (removeFrameNumber && fileName.endsWith(".") && (len > 0)) {
                    result.setFileName(fileName.substring(0, len - 1));
                } else {
                    result.setFileName(fileName);
                }
                result.setMaxFrameNumber(Integer.parseInt(matcher.group(2)));
            } else {
                result.setFileName(matcher.group(1) + matcher.group(2));
                result.setMaxFrameNumber(0);
            }
            if (mediaExportParameters.isConvertPNG2JPG()) {
                result.setFileExtension(".jpg");
            } else {
                result.setFileExtension(matcher.group(3));
            }
            // Please note that here we use initial file name, not redirected.
            // Thus we can use constraint for this initial file name.
            if (m_mediaToConstraintMap.containsKey(imageFileName)) {
                result.setConstraint(m_mediaToConstraintMap.get(imageFileName));
            } else {
                result.setConstraint(Constants.EMPTY_STRING);
            }
            return result;
        } else {
            throw new NLBExportException("Filename " + imageFileName + " is bad, please rename");
        }
    }

    protected List<SoundPathData> getSoundPaths(String externalHierarchy, String soundFileNames) {
        if (StringHelper.isEmpty(soundFileNames)) {
            return new ArrayList<SoundPathData>() {{
                add(SoundPathData.EMPTY);
            }};
        } else {
            List<SoundPathData> result = new ArrayList<>();
            String[] fileNamesArr = soundFileNames.split(Constants.MEDIA_FILE_NAME_SEP);
            for (String fileName : fileNamesArr) {
                result.add(getSoundPath(externalHierarchy, fileName));
            }
            return result;
        }
    }

    protected SoundPathData getSoundPath(String externalHierarchy, String soundFileName) {
        if (StringHelper.isEmpty(soundFileName)) {
            return SoundPathData.EMPTY;
        } else {
            if (Constants.VOID.equals(soundFileName)) {
                return SoundPathData.VOID;
            } else {
                SoundPathData result = new SoundPathData();
                if (StringHelper.isEmpty(externalHierarchy)) {
                    result.setParentFolderPath(NonLinearBook.SOUND_DIR_NAME);
                } else {
                    result.setParentFolderPath(NonLinearBook.SOUND_DIR_NAME + "/" + externalHierarchy);
                }

                // Please note that here we use redirected file name.
                result.setFileName(getRedirectMediaOrSelf(soundFileName));

                // Please note that here we use initial file name, not redirected.
                // Thus we can use constraint for this initial file name.
                if (m_mediaToConstraintMap.containsKey(soundFileName)) {
                    result.setConstraint(m_mediaToConstraintMap.get(soundFileName));
                } else {
                    result.setConstraint(Constants.EMPTY_STRING);
                }
                if (StringHelper.isEmpty(externalHierarchy)) {
                    result.setSfx(m_mediaFlagsMap.get(soundFileName));
                } else {
                    result.setSfx(m_mediaFlagsMap.get(externalHierarchy + "/" + soundFileName));
                }
                return result;
            }
        }
    }

    protected String decoratePageTextStart(String labelText, int pageNumber, List<TextChunk> pageTextChunks, Theme theme) {
        return expandVariables(pageTextChunks, theme);
    }

    protected abstract String getLineSeparator();

    protected abstract String decoratePageTextEnd(String labelText, int pageNumber, Theme theme, boolean hasChoicesOrLeaf);

    protected boolean getGoToPageNumbers() {
        return GOTO_PAGE_NUMBERS;
    }

    protected String decorateId(String id) {
        return "v_" + SpecialVariablesNameHelper.decorateId(id);
    }

    protected String decoratePageName(String labelText, int pageNumber) {
        return getGoToPageNumbers() ? decorateId(String.valueOf(pageNumber)) : decorateId(labelText);
    }

    protected abstract String decoratePageLabel(String labelText, int pageNumber, Theme theme);

    protected abstract String decoratePageNumber(int pageNumber);

    protected abstract String decoratePageComment(String comment);
}
