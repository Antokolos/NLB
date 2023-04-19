/**
 * @(#)NonLinearBookFacade.java
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
 * Copyright (c) 2014 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.exception.*;
import com.nlbhub.nlb.util.FileManipulator;
import com.nlbhub.nlb.util.MultiLangString;
import com.nlbhub.nlb.util.ResourceManager;
import com.nlbhub.nlb.util.StringHelper;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.GitAdapterWithPathDecoration;
import com.nlbhub.nlb.vcs.VCSAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.nlbhub.nlb.util.ResourceManager.*;

/**
 * The NonLinearBookFacade class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/15/14
 */
public class NonLinearBookFacade implements NLBObservable {
    private NonLinearBookImpl m_nlb;
    private Map<String, PageImpl> m_newPagesPool = new HashMap<>();
    private Map<String, ObjImpl> m_newObjsPool = new HashMap<>();
    private Map<String, LinkImpl> m_newLinksPool = new HashMap<>();
    /**
     * Main undo manager.
     */
    private UndoManager m_undoManager = new UndoManager();
    /**
     * Items' own undo managers.
     */
    private Map<String, UndoManager> m_undoManagersMap = new HashMap<>();
    private ObserverHandler m_observerHandler = new ObserverHandler();
    private VCSAdapter m_vcsAdapter;
    private Author m_author;
    private boolean m_rootFacade;
    private NonLinearBookFacade m_parentFacade;
    private List<NonLinearBookFacade> m_moduleFacades = new ArrayList<>();

    public NonLinearBookFacade(Author author, VCSAdapter vcsAdapter) {
        m_rootFacade = true;
        m_parentFacade = null;
        m_author = author;
        m_vcsAdapter = vcsAdapter;
        // m_nlb should be initialized later
    }

    private NonLinearBookFacade(
            NonLinearBookFacade parentFacade,
            Author author,
            VCSAdapter vcsAdapter,
            NonLinearBookImpl nlb
    ) {
        m_rootFacade = false;
        m_parentFacade = parentFacade;
        m_author = author;
        m_vcsAdapter = vcsAdapter;
        m_nlb = nlb;
    }

    public void createNewBook() {
        DummyNLB parentNLB = DummyNLB.singleton();
        m_nlb = new NonLinearBookImpl(parentNLB, new RootModulePage(parentNLB, Constants.MAIN_MODULE_NAME));
        notifyObservers();
    }

    public NonLinearBookFacade createModuleFacade(final String modulePageId) {
        PageImpl page = m_nlb.getPageImplById(modulePageId);
        NonLinearBookFacade facade = (
                new NonLinearBookFacade(this, m_author, m_vcsAdapter, page.getModuleImpl())
        );
        m_moduleFacades.add(facade);
        notifyObservers();
        return facade;
    }

    public NonLinearBookFacade getMainFacade() {
        NonLinearBookFacade result = this;
        while (result.getParentFacade() != null) {
            result = result.getParentFacade();
        }
        return result;
    }

    public NonLinearBookFacade getParentFacade() {
        return m_parentFacade;
    }

    public NonLinearBook getNlb() {
        return m_nlb;
    }

    public void clear() throws NLBVCSException {
        m_nlb.clear();
        if (m_rootFacade) {
            m_vcsAdapter.closeAdapter();
        }
        clearUndosAndPools();
    }

    public void clearUndosAndPools() throws NLBVCSException {
        clearUndos();
        clearPools();
        for (NonLinearBookFacade facade : m_moduleFacades) {
            facade.clearUndosAndPools();
        }
        notifyObservers();
    }

    private void clearUndos() {
        m_undoManager.clear();
        for (Map.Entry<String, UndoManager> entry : m_undoManagersMap.entrySet()) {
            entry.getValue().clear();
        }
    }

    public void commit(final String commitMessageText) throws NLBVCSException {
        m_vcsAdapter.commit(commitMessageText);
        notifyObservers();
    }

    public void pull(final String userName, final String password, final ProgressData progressData) throws NLBVCSException {
        m_vcsAdapter.pull(userName, password, progressData);
        notifyObservers();
    }

    public void push(final String userName, final String password, final ProgressData progressData) throws NLBVCSException {
        m_vcsAdapter.push(userName, password, progressData);
        notifyObservers();
    }

    public void exportToChoiceScript(File exportDir) throws NLBExportException {
        m_nlb.exportToChoiceScript(new File(exportDir, "startup.txt"));
        File imagesExportDir = new File(exportDir, NonLinearBook.IMAGES_DIR_NAME);
        m_nlb.exportImages(true, imagesExportDir);
    }

    public void exportToQSPTextFile(File exportDir) throws NLBExportException {
        m_nlb.exportToQSPTextFile(new File(exportDir, "book.txt"));
        File imagesExportDir = new File(exportDir, NonLinearBook.IMAGES_DIR_NAME);
        m_nlb.exportImages(true, imagesExportDir);
    }

    public void exportToURQTextFile(File exportDir) throws NLBExportException {
        m_nlb.exportToURQTextFile(new File(exportDir, "book.qst"));
    }

    public void exportToPDFFile(File exportFile) throws NLBExportException {
        m_nlb.exportToPDFFile(exportFile);
    }

    public void exportToTXTFile(File exportDir) throws NLBExportException {
        m_nlb.exportToTXTFile(new File(exportDir, "book.txt"));
    }

    public void exportToHTMLFile(File exportDir) throws NLBExportException {
        m_nlb.exportToHTMLFile(new File(exportDir, "index.html"));
        File imagesExportDir = new File(exportDir, NonLinearBook.IMAGES_DIR_NAME);
        m_nlb.exportImages(true, imagesExportDir);
    }

    public void exportToJSIQFile(File exportDir) throws NLBExportException {
        m_nlb.exportToJSIQFile(new File(exportDir, "example.xml"));
    }

    public void exportToSTEADFile(File exportDir) throws NLBExportException, NLBIOException {
        m_nlb.exportToSTEADFile(new File(exportDir, "main.lua"));
        exportMedia(exportDir);
        exportAdditionalMedia(exportDir);
    }

    public void exportToVNSTEADFile(File exportDir) throws NLBExportException, NLBIOException {
        m_nlb.exportToVNSTEADFile(new File(exportDir, "main.lua"));
        exportMedia(exportDir);
        exportAdditionalMedia(exportDir);
    }

    private void exportAdditionalMedia(File exportDir) throws NLBIOException {
        ResourceManager.exportBundledFiles(exportDir);
    }

    private void exportMedia(File exportDir) throws NLBExportException {
        File imagesExportDir = new File(exportDir, NonLinearBook.IMAGES_DIR_NAME);
        m_nlb.exportImages(true, imagesExportDir);
        File soundExportDir = new File(exportDir, NonLinearBook.SOUND_DIR_NAME);
        m_nlb.exportSound(true, soundExportDir);
    }

    public void exportToASMFile(File exportDir) throws NLBExportException {
        m_nlb.exportToASMFile(new File(exportDir, "book.sm"));
    }

    public void updateModifications(
            final ModifyingItem modifyingItem,
            final ModificationsTableModel modificationsTableModel
    ) {
        NonLinearBookImpl.UpdateModificationsCommand command = (
                m_nlb.createUpdateModificationsCommand(
                        modifyingItem,
                        modificationsTableModel
                )
        );
        getUndoManagerByItemId(
                modifyingItem.getId() + Constants.MODIFICATIONS_UNDO_ID_POSTFIX
        ).executeAndStore(command);
        notifyObservers();
    }

    /**
     * This method does not have undo and immediately sets constraint id of the media file with the specified
     * type and name.
     *
     * @param mediaType
     * @param fileName
     * @param constrId
     */
    public void setMediaFileConstrId(final MediaFile.Type mediaType, final String fileName, final String constrId) {
        m_nlb.setMediaFileConstrId(mediaType, fileName, constrId);
    }

    /**
     * This method does not have undo and immediately sets redirect of the media file with the specified
     * type and name.
     *
     * @param mediaType
     * @param fileName
     * @param redirect
     */
    public void setMediaFileRedirect(final MediaFile.Type mediaType, final String fileName, final String redirect) {
        m_nlb.setMediaFileRedirect(mediaType, fileName, redirect);
    }

    /**
     * This method does not have undo and immediately sets flagged state of the media file with the specified
     * type and name.
     *
     * @param mediaType
     * @param fileName
     * @param flag
     */
    public void setMediaFileFlag(final MediaFile.Type mediaType, final String fileName, final boolean flag) {
        m_nlb.setMediaFileFlag(mediaType, fileName, flag);
    }

    /**
     * This method does not have undo and immediately sets export parameters preset of the media file with the specified
     * type and name.
     *
     * @param mediaType
     * @param fileName
     * @param preset
     */
    public void setMediaFileExportParametersPreset(final MediaFile.Type mediaType, final String fileName, final MediaExportParameters.Preset preset) {
        m_nlb.setMediaFileExportParametersPreset(mediaType, fileName, preset);
    }

    public void updateBookProperties(
            final String license,
            final Theme theme,
            final String language,
            final String title,
            final String author,
            final String version,
            final String perfectGameAchievementName,
            final boolean fullAutowire,
            final boolean suppressMedia,
            final boolean suppressSound,
            final boolean propagateToSubmodules
    ) {
        NonLinearBookImpl.UpdateBookPropertiesCommand command = (
                m_nlb.createUpdateBookPropertiesCommand(license, theme, language, title, author, version, perfectGameAchievementName, fullAutowire, suppressMedia, suppressSound, propagateToSubmodules)
        );
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void updatePage(
            final Page page,
            final String imageFileName,
            final boolean imageBackground,
            final boolean imageAnimated,
            final String soundFileName,
            final boolean soundSFX,
            final String pageVariableName,
            final String pageTimerVariableName,
            final String pageDefTagVariableValue,
            final MultiLangString pageText,
            final MultiLangString pageCaptionText,
            final Theme theme,
            final boolean useCaption,
            final boolean useMPL,
            final String moduleName,
            final boolean moduleExternal,
            final MultiLangString traverseText,
            final boolean autoTraverse,
            final boolean autoReturn,
            final MultiLangString returnText,
            final String returnPageId,
            final String moduleConsraintVariableName,
            final boolean autowire,
            final MultiLangString autowireInText,
            final MultiLangString autowireOutText,
            final boolean autoIn,
            final boolean needsAction,
            final boolean autoOut,
            final String autowireInConstraint,
            final String autowireOutConstraint,
            final boolean globalAutowire,
            final boolean noSave,
            final boolean autosFirst,
            final LinksTableModel linksTableModel
    ) {
        NonLinearBookImpl.UpdatePageCommand command = (
                m_nlb.createUpdatePageCommand(
                        page,
                        imageFileName,
                        imageBackground,
                        imageAnimated,
                        soundFileName,
                        soundSFX,
                        pageVariableName,
                        pageTimerVariableName,
                        pageDefTagVariableValue,
                        pageText,
                        pageCaptionText,
                        theme,
                        useCaption,
                        useMPL,
                        moduleName,
                        moduleExternal,
                        traverseText,
                        autoTraverse,
                        autoReturn,
                        returnText,
                        returnPageId,
                        moduleConsraintVariableName,
                        autowire,
                        autowireInText,
                        autowireOutText,
                        autoIn,
                        needsAction,
                        autoOut,
                        autowireInConstraint,
                        autowireOutConstraint,
                        globalAutowire,
                        noSave,
                        autosFirst,
                        linksTableModel
                )
        );
        getUndoManagerByItemId(page.getId()).executeAndStore(command);
        notifyObservers();
    }

    public void updateLink(
            final Link link,
            final String linkVariableName,
            final String linkConstraintValue,
            final MultiLangString linkText,
            final MultiLangString linkAltText,
            final boolean auto,
            final boolean once
    ) {
        NonLinearBookImpl.UpdateLinkCommand command = (
                m_nlb.createUpdateLinkCommand(
                        link,
                        linkVariableName,
                        linkConstraintValue,
                        linkText,
                        linkAltText,
                        auto,
                        once
                )
        );
        getUndoManagerByItemId(link.getId()).executeAndStore(command);
        notifyObservers();
    }

    public void updateNode(final NodeItem nodeToUpdate) {
        nodeToUpdate.notifyObservers();
        // TODO: maybe not update links?
        final List<Link> adjacentLinks = m_nlb.getAssociatedLinks(nodeToUpdate);
        for (Link link : adjacentLinks) {
            link.notifyObservers();
        }
    }

    public void updateLink(final Link linkToUpdate) {
        linkToUpdate.notifyObservers();
    }

    public void updateObj(
            final Obj obj,
            final String objVariableName,
            final String objDefTagVariableValue,
            final String objConstraintValue,
            final String objCommonToName,
            final String objName,
            final String imageFileName,
            final String soundFileName,
            final boolean soundSFX,
            final boolean animatedImage,
            final boolean suppressDsc,
            final MultiLangString objDisp,
            final MultiLangString objText,
            final MultiLangString objActText,
            final MultiLangString objNouseText,
            final boolean objIsGraphical,
            final boolean objIsShowOnCursor,
            final boolean objIsPreserved,
            final boolean objIsLoadOnce,
            final boolean objIsCollapsable,
            final String offset,
            final Obj.MovementDirection movementDirection,
            final Obj.Effect effect,
            final int startFrame,
            final int maxFrame,
            final int preloadFrames,
            final int pauseFrames,
            final Obj.CoordsOrigin coordsOrigin,
            final boolean objIsClearUnderTooltip,
            final boolean objIsActOnKey,
            final boolean objIsCacheText,
            final boolean objIsLooped,
            final boolean objIsNoRedrawOnAct,
            final String objMorphOver,
            final String objMorphOut,
            final boolean objIsTakable,
            final boolean imageInScene,
            final boolean imageInInventory
    ) {
        NonLinearBookImpl.UpdateObjCommand command = (
                m_nlb.createUpdateObjCommand(
                        obj,
                        objVariableName,
                        objDefTagVariableValue,
                        objConstraintValue,
                        objCommonToName,
                        objName,
                        imageFileName,
                        soundFileName,
                        soundSFX,
                        animatedImage,
                        suppressDsc,
                        objDisp,
                        objText,
                        objActText,
                        objNouseText,
                        objIsGraphical,
                        objIsShowOnCursor,
                        objIsPreserved,
                        objIsLoadOnce,
                        objIsCollapsable,
                        offset,
                        movementDirection,
                        effect,
                        startFrame,
                        maxFrame,
                        preloadFrames,
                        pauseFrames,
                        coordsOrigin,
                        objIsClearUnderTooltip,
                        objIsActOnKey,
                        objIsCacheText,
                        objIsLooped,
                        objIsNoRedrawOnAct,
                        objMorphOver,
                        objMorphOut,
                        objIsTakable,
                        imageInScene,
                        imageInInventory
                )
        );
        getUndoManagerByItemId(obj.getId()).executeAndStore(command);
        notifyObservers();
    }

    public void updateLinkCoords(
            final Link link,
            final float left,
            final float top
    ) {
        final NLBCommand command = new UpdateLinkCoordsCommand(m_nlb, link, left, top);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void updateLinkCoords(
            final Link link,
            final float height
    ) {
        final NLBCommand command = new UpdateLinkCoordsCommand(m_nlb, link, height);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    /**
     * If imageFileName is null, then imageFile.getName() is used.
     *
     * @param imageFile
     * @param imageFileName
     * @throws NLBFileManipulationException
     * @throws NLBIOException
     * @throws NLBConsistencyException
     * @throws NLBVCSException
     */
    public void addImageFile(
            final @NotNull File imageFile,
            final @Nullable String imageFileName
    ) throws NLBFileManipulationException, NLBIOException, NLBConsistencyException, NLBVCSException {
        File rootDir = m_nlb.getRootDir();
        if (rootDir != null) {
            FileManipulator fileManipulator = new FileManipulator(m_vcsAdapter, rootDir);
            m_nlb.copyAndAddImageFile(fileManipulator, imageFile, imageFileName);
        } else {
            throw new NLBConsistencyException("NLB root dir is undefined");
        }
    }

    /**
     * If soundFileName is null, then soundFile.getName() is used.
     *
     * @param soundFile
     * @param soundFileName
     * @throws NLBFileManipulationException
     * @throws NLBIOException
     * @throws NLBConsistencyException
     * @throws NLBVCSException
     */
    public void addSoundFile(
            final @NotNull File soundFile,
            final @Nullable String soundFileName
    ) throws NLBFileManipulationException, NLBIOException, NLBConsistencyException, NLBVCSException {
        File rootDir = m_nlb.getRootDir();
        if (rootDir != null) {
            FileManipulator fileManipulator = new FileManipulator(m_vcsAdapter, rootDir);
            m_nlb.copyAndAddSoundFile(fileManipulator, soundFile, soundFileName);
        } else {
            throw new NLBConsistencyException("NLB root dir is undefined");
        }
    }

    public void removeImageFile(
            final String imageFileName
    ) throws NLBFileManipulationException, NLBIOException, NLBConsistencyException {
        File rootDir = m_nlb.getRootDir();
        if (rootDir != null) {
            FileManipulator fileManipulator = new FileManipulator(m_vcsAdapter, rootDir);
            m_nlb.removeImageFile(fileManipulator, imageFileName);
        } else {
            throw new NLBConsistencyException("NLB root dir is undefined");
        }
    }

    public void removeSoundFile(
            final String soundFileName
    ) throws NLBFileManipulationException, NLBIOException, NLBConsistencyException {
        File rootDir = m_nlb.getRootDir();
        if (rootDir != null) {
            FileManipulator fileManipulator = new FileManipulator(m_vcsAdapter, rootDir);
            m_nlb.removeSoundFile(fileManipulator, soundFileName);
        } else {
            throw new NLBConsistencyException("NLB root dir is undefined");
        }
    }

    public void resizeNode(
            NodeItem nodeItem,
            NodeItem.Orientation orientation,
            double deltaX,
            double deltaY
    ) {
        AbstractNodeItem node = m_nlb.getPageImplById(nodeItem.getId());
        if (node == null) {
            node = m_nlb.getObjImplById(nodeItem.getId());
        }
        final List<Link> adjacentLinks = m_nlb.getAssociatedLinks(nodeItem);
        AbstractNodeItem.ResizeNodeCommand command = (
                node.createResizeNodeCommand(orientation, deltaX, deltaY, adjacentLinks)
        );
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void updateNodeCoords(
            final NodeItem nodeItem,
            final Set<NodeItem> additionallyMovedItems,
            final float deltaX,
            final float deltaY
    ) {
        final CommandChainCommand commandChain = new CommandChainCommand();
        updateNodeCoords(commandChain, nodeItem, deltaX, deltaY);
        for (NodeItem additionalNodeItem : additionallyMovedItems) {
            updateNodeCoords(commandChain, additionalNodeItem, deltaX, deltaY);
        }
        m_undoManager.executeAndStore(commandChain);
        notifyObservers();
    }

    private void updateNodeCoords(
            final CommandChainCommand commandChain,
            final NodeItem nodeItem,
            final float deltaX,
            final float deltaY
    ) {
        final NLBCommand command = new UpdateNodeCoordsCommand(m_nlb, nodeItem, deltaX, deltaY);
        commandChain.addCommand(command);
        offsetContainedObjects(commandChain, nodeItem, deltaX, deltaY);
    }

    private void offsetContainedObjects(
            final CommandChainCommand commandChain,
            NodeItem container,
            float deltaX,
            float deltaY
    ) {
        for (String nodeId : container.getContainedObjIds()) {
            NodeItem node = m_nlb.getObjById(nodeId);
            Coords nodeCoords = node.getCoords();
            updateNodeCoords(
                    commandChain,
                    node,
                    deltaX,
                    deltaY
            );

            offsetContainedObjects(commandChain, node, deltaX, deltaY);
        }
    }

    public void changeContainer(
            final String previousContainerId,
            final String newContainerId,
            final String objId
    ) {
        AbstractNodeItem prevContainer = null;
        if (!StringHelper.isEmpty(previousContainerId)) {
            prevContainer = m_nlb.getPageImplById(previousContainerId);
            if (prevContainer == null) {
                prevContainer = m_nlb.getObjImplById(previousContainerId);
            }
        }
        AbstractNodeItem newContainer = null;
        if (!StringHelper.isEmpty(newContainerId)) {
            newContainer = m_nlb.getPageImplById(newContainerId);
            if (newContainer == null) {
                newContainer = m_nlb.getObjImplById(newContainerId);
            }
        }
        if (
                (prevContainer == null && newContainer != null)           // from nowhere to something
                        || (prevContainer != null && newContainer == null)        // from something to nowhere
                        || (
                        prevContainer != null                                 // from something...
                                && !prevContainer.getId().equals(newContainer.getId()) // ...to something new
                )
                ) {
            ObjImpl obj = m_nlb.getObjImplById(objId);
            ChangeContainerCommand command = (
                    new ChangeContainerCommand(
                            prevContainer,
                            newContainer,
                            obj
                    )
            );
            m_undoManager.executeAndStore(command);
            notifyObservers();
        }
    }

    public void changeStartPoint(final String startPoint) {
        NonLinearBookImpl.ChangeStartPointCommand command = (
                m_nlb.createChangeStartPointCommand(startPoint)
        );
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void cut(final Collection<String> pageIds, final Collection<String> objIds) {
        CommandChainCommand command = new CommandChainCommand();
        command.addCommand(m_nlb.createCopyCommand(pageIds, objIds));
        command.addCommand(m_nlb.createDeleteCommand(pageIds, objIds));
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void copy(final Collection<String> pageIds, final Collection<String> objIds) {
        NonLinearBookImpl.CopyCommand command = m_nlb.createCopyCommand(pageIds, objIds);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void paste() {
        final NonLinearBookImpl nlbToPaste = Clipboard.singleton().getNonLinearBook();
        if (nlbToPaste != null) {
            NonLinearBookImpl.PasteCommand command = m_nlb.createPasteCommand(nlbToPaste);
            m_undoManager.executeAndStore(command);
            notifyObservers();
        }
    }

    /**
     * This method is not participating in undo. It pre-creates page.
     */
    public Page createPage(final float left, final float top) {
        PageImpl page = new PageImpl(m_nlb, left, top);
        m_newPagesPool.put(page.getId(), page);
        return page;
    }

    public void addPage(final Page page) {
        final PageImpl pageImpl = m_newPagesPool.get(page.getId());
        // m_newPagesPool.remove(page.getId()); -- do not remove here, instead of it this map
        // will be entirely cleared on save
        NonLinearBookImpl.AddPageCommand command = m_nlb.createAddPageCommand(pageImpl);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    /**
     * This method is not participating in undo. It pre-creates obj.
     */
    public Obj createObj(final float left, final float top) {
        ObjImpl obj = new ObjImpl(m_nlb, left, top);
        m_newObjsPool.put(obj.getId(), obj);
        return obj;
    }

    public void addObj(final Obj obj) {
        final ObjImpl objImpl = m_newObjsPool.get(obj.getId());
        // m_newObjsPool.remove(obj.getId()); -- do not remove here, instead of it this map
        // will be entirely cleared on save
        NonLinearBookImpl.AddObjCommand command = m_nlb.createAddObjCommand(objImpl);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    /**
     * This method is not participating in undo. It pre-creates link.
     */
    public Link createLink(final NodeItem item, final NodeItem target) {
        NodeItem origin = m_nlb.getPageById(item.getId());
        if (origin == null) {
            origin = m_nlb.getObjById(item.getId());
        }
        LinkImpl link = new LinkImpl(origin, target.getId());
        m_newLinksPool.put(link.getId(), link);
        return link;
    }

    public void addLink(final Link link) {
        AbstractNodeItem origin = m_nlb.getPageImplById(link.getParent().getId());
        if (origin == null) {
            origin = m_nlb.getObjImplById(link.getParent().getId());
        }
        LinkImpl linkImpl = m_newLinksPool.get(link.getId());
        // m_newLinksPool.remove(link.getId()); -- do not remove here, instead of it this map
        // will be entirely cleared on save
        AbstractNodeItem.AddLinkCommand command = origin.createAddLinkCommand(linkImpl);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public boolean hasChanges() {
        if (canUndo() || canRedo()) {
            return true;
        }
        for (UndoManager undoManager : m_undoManagersMap.values()) {
            if (undoManager.canUndo() || undoManager.canRedo()) {
                return true;
            }
        }
        for (NonLinearBookFacade facade : m_moduleFacades) {
            if (facade.hasChanges()) {
                return true;
            }
        }
        return false;
    }

    public void save(
            final boolean create,
            final ProgressData progressData
    ) throws NLBVCSException, NLBConsistencyException, NLBFileManipulationException, NLBIOException {
        saveNLB(create, progressData);
        clearUndosAndPools();
    }

    private void saveNLB(
            final boolean create,
            final ProgressData progressData
    ) throws NLBVCSException, NLBConsistencyException, NLBFileManipulationException, NLBIOException {
        try {
            final File rootDir = m_nlb.getRootDir();
            progressData.setProgressValue(5);
            progressData.setNoteText("Opening VCS repository...");
            // Here we actually duplicating code from NonLinearBookImpl.save(), because
            // we need to be sure that main root dir exists before trying to init or open VCS repo
            if (!rootDir.exists()) {
                if (!rootDir.mkdirs()) {
                    throw new NLBIOException("Cannot create NLB root directory");
                }
                m_vcsAdapter.initRepo(rootDir.getCanonicalPath());
            } else {
                if (create) {
                    m_vcsAdapter.initRepo(rootDir.getCanonicalPath());
                } else {
                    m_vcsAdapter.openRepo(rootDir.getCanonicalPath());
                }
            }
            progressData.setProgressValue(15);
            progressData.setNoteText("Saving Non-Linear Book...");
            FileManipulator fileManipulator = new FileManipulator(m_vcsAdapter, rootDir);
            int effectivePagesCount = m_nlb.getEffectivePagesCountForSave();
            int startProgress = 25; // it will become 25 when pages saving will be started, see the code in m_nlb.save()
            int maxProgress = 85; // it will become 85 when pages saving will be finished, see the code in m_nlb.save()
            Double itemsCountPerIncrement = (
                    Math.ceil(((double) effectivePagesCount) / ((double) (maxProgress - startProgress)))
            );
            // if pages count is less than (max - start), then max will never be reached, but we don't care,
            // because it will become max rightly after pages' saving.
            // All this mess is about to support progress handling in case when pages count is much more than 60
            PartialProgressData partialProgressData = (
                    new PartialProgressData(progressData, startProgress, maxProgress, itemsCountPerIncrement.intValue())
            );
            m_nlb.save(fileManipulator, progressData, partialProgressData);
        } catch (IOException e) {
            throw new NLBIOException("Error while obtaining canonical path during save");
        }
    }

    public boolean canUndo() {
        return m_undoManager.canUndo();
    }

    public void undo() {
        m_undoManager.undo();
        notifyObservers();
    }

    public boolean canUndo(final String id) {
        return getUndoManagerByItemId(id).canUndo();
    }

    public void undo(final String id) {
        getUndoManagerByItemId(id).undo();
        notifyObservers();
    }

    public boolean canRedo() {
        return m_undoManager.canRedo();
    }

    public void redo() {
        m_undoManager.redo();
        notifyObservers();
    }

    public boolean canRedo(final String id) {
        return getUndoManagerByItemId(id).canRedo();
    }

    public void redo(final String id) {
        getUndoManagerByItemId(id).redo();
        notifyObservers();
    }

    public void redoAll(final String id) {
        getUndoManagerByItemId(id).redoAll();
        notifyObservers();
    }

    private void clearPools() {
        m_newPagesPool.clear();
        m_newObjsPool.clear();
        m_newLinksPool.clear();
    }

    public void saveAs(
            final File nlbFolder,
            final ProgressData progressData
    ) throws NLBVCSException, NLBConsistencyException, NLBFileManipulationException, NLBIOException {
        m_nlb.setRootDir(nlbFolder);
        saveNLB(true, progressData);
        clearUndosAndPools();
    }

    public void load(
            final String path,
            final ProgressData progressData
    ) throws NLBIOException, NLBConsistencyException, NLBVCSException {
        try {
            final File rootDir = new File(path);
            if (!rootDir.exists()) {
                // We need to be sure that main root dir exists before trying to open VCS repo
                throw new NLBIOException("Specified NLB root directory " + path + " does not exist");
            }
            progressData.setNoteText("Opening VCS repository...");
            progressData.setProgressValue(5);
            m_vcsAdapter.openRepo(rootDir.getCanonicalPath());
            progressData.setNoteText("Loading book contents...");
            progressData.setProgressValue(15);
            m_nlb.load(path, progressData);
            progressData.setProgressValue(70);
            progressData.setNoteText("Prepare to drawing...");
            notifyObservers();
        } catch (IOException e) {
            throw new NLBIOException("Error while obtaining canonical path for path = " + path);
        }
    }

    public void deleteNode(final NodeItem nodeToDelete) {
        final List<Link> adjacentLinks = m_nlb.getAssociatedLinks(nodeToDelete);
        PageImpl page = m_nlb.getPageImplById(nodeToDelete.getId());
        NonLinearBookImpl.DeleteNodeCommand command = null;
        if (page != null) {
            command = m_nlb.createDeletePageCommand(page, adjacentLinks);
        } else {
            command = m_nlb.createDeleteObjCommand(
                    m_nlb.getObjImplById(nodeToDelete.getId()),
                    adjacentLinks
            );
        }
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void deleteLink(final Link link) {
        final IdentifiableItem parent = link.getParent();
        AbstractNodeItem nodeItem = m_nlb.getPageImplById(parent.getId());
        if (nodeItem == null) {
            nodeItem = m_nlb.getObjImplById(parent.getId());
        }
        AbstractNodeItem.DeleteLinkCommand command = nodeItem.createDeleteLinkCommand(link);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void invalidateAssociatedLinks(NodeItem nodeItem) {
        List<Link> associatedLinks = m_nlb.getAssociatedLinks(nodeItem);
        for (Link link : associatedLinks) {
            link.notifyObservers();
        }
    }

    public void updateAllViews() {
        for (Map.Entry<String, Page> pageEntry : m_nlb.getPages().entrySet()) {
            pageEntry.getValue().notifyObservers();
            List<Link> links = pageEntry.getValue().getLinks();
            for (Link link : links) {
                link.notifyObservers();
            }
        }
        for (Map.Entry<String, Obj> objEntry : m_nlb.getObjs().entrySet()) {
            objEntry.getValue().notifyObservers();
            List<Link> links = objEntry.getValue().getLinks();
            for (Link link : links) {
                link.notifyObservers();
            }
        }
    }

    private UndoManager getUndoManagerByItemId(final String id) {
        UndoManager undoManager = m_undoManagersMap.get(id);
        if (undoManager == null) {
            undoManager = new UndoManager();
            m_undoManagersMap.put(id, undoManager);
        }
        return undoManager;
    }

    @Override
    public String addObserver(NLBObserver observer) {
        return m_observerHandler.addObserver(observer);
    }

    @Override
    public void removeObserver(String observerId) {
        m_observerHandler.removeObserver(observerId);
    }

    @Override
    public void notifyObservers() {
        m_observerHandler.notifyObservers();
    }
}
