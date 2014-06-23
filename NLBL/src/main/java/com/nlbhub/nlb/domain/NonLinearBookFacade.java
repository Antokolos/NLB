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
import com.nlbhub.nlb.util.StringHelper;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.GitAdapterWithPathDecoration;
import com.nlbhub.nlb.vcs.VCSAdapter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private List<NonLinearBookFacade> m_moduleFacades = new ArrayList<>();

    public NonLinearBookFacade() {
        m_rootFacade = true;
        m_author = new Author("author", "author@example.com");
        m_vcsAdapter = new GitAdapterWithPathDecoration(m_author);
        // m_nlb should be initialized later
    }

    private NonLinearBookFacade(Author author, VCSAdapter vcsAdapter, NonLinearBookImpl nlb) {
        m_rootFacade = false;
        m_author = author;
        m_vcsAdapter = vcsAdapter;
        m_nlb = nlb;
    }

    public void createNewBook() {
        m_nlb = new NonLinearBookImpl();
        notifyObservers();
    }

    public NonLinearBookFacade createModuleFacade(final String modulePageId) {
        PageImpl page = m_nlb.getPageImplById(modulePageId);
        NonLinearBookFacade facade = (
                new NonLinearBookFacade(m_author, m_vcsAdapter, page.getModuleImpl())
        );
        m_moduleFacades.add(facade);
        notifyObservers();
        return facade;
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

    public void commit(String commitMessageText) throws NLBVCSException {
        m_vcsAdapter.commit(commitMessageText);
        notifyObservers();
    }

    public void exportToQSPTextFile(File exportFile) throws NLBExportException {
        m_nlb.exportToQSPTextFile(exportFile);
    }

    public void exportToURQTextFile(File exportFile) throws NLBExportException {
        m_nlb.exportToURQTextFile(exportFile);
    }

    public void exportToPDFFile(File exportFile) throws NLBExportException {
        m_nlb.exportToPDFFile(exportFile);
    }

    public void exportToHTMLFile(File exportFile) throws NLBExportException {
        m_nlb.exportToHTMLFile(exportFile);
    }

    public void exportToJSIQFile(File exportFile) throws NLBExportException {
        m_nlb.exportToJSIQFile(exportFile);
    }

    public void exportToSTEADFile(File exportFile) throws NLBExportException {
        m_nlb.exportToSTEADFile(exportFile);
    }

    public void exportToASMFile(File exportFile) throws NLBExportException {
        m_nlb.exportToASMFile(exportFile);
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

    public void updatePage(
            final Page page,
            final String pageVariableName,
            final String pageText,
            final String pageCaptionText,
            final boolean useCaption,
            final String moduleName,
            final String traverseText,
            final String returnText,
            final String returnPageId,
            final String moduleConsraintVariableName,
            final LinksTableModel linksTableModel
    ) {
        NonLinearBookImpl.UpdatePageCommand command = (
                m_nlb.createUpdatePageCommand(
                        page,
                        pageVariableName,
                        pageText,
                        pageCaptionText,
                        useCaption,
                        moduleName,
                        traverseText,
                        returnText,
                        returnPageId,
                        moduleConsraintVariableName,
                        linksTableModel
                )
        );
        getUndoManagerByItemId(page.getId()).executeAndStore(command);
        notifyObservers();
    }

    public void updateLink(
            final Link link,
            final String linkVariableName,
            final String linkConstraintName,
            final String linkText
    ) {
        NonLinearBookImpl.UpdateLinkCommand command = (
                m_nlb.createUpdateLinkCommand(
                        link,
                        linkVariableName,
                        linkConstraintName,
                        linkText
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
            final String objName,
            final String objText,
            final boolean objIsTakable
    ) {
        NonLinearBookImpl.UpdateObjCommand command = (
                m_nlb.createUpdateObjCommand(
                        obj,
                        objVariableName,
                        objName,
                        objText,
                        objIsTakable
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
            final float left,
            final float top,
            final float deltaX,
            final float deltaY
    ) {
        final CommandChainCommand commandChain = new CommandChainCommand();
        updateNodeCoords(commandChain, nodeItem, left, top, deltaX, deltaY);
        m_undoManager.executeAndStore(commandChain);
        notifyObservers();
    }

    private void updateNodeCoords(
            final CommandChainCommand commandChain,
            final NodeItem nodeItem,
            final float left,
            final float top,
            final float deltaX,
            final float deltaY
    ) {
        final NLBCommand command = new UpdateNodeCoordsCommand(m_nlb, nodeItem, left, top);
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
                    nodeCoords.getLeft() + deltaX,
                    nodeCoords.getTop() + deltaY,
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
        ObjImpl obj = new ObjImpl(left, top);
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

    public void save(
            boolean create
    ) throws NLBVCSException, NLBConsistencyException, NLBFileManipulationException, NLBIOException {
        saveNLB(create);
        clearUndosAndPools();
    }

    private void saveNLB(
            boolean create
    ) throws NLBVCSException, NLBConsistencyException, NLBFileManipulationException, NLBIOException {
        try {
            final File rootDir = m_nlb.getRootDir();
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
            FileManipulator fileManipulator = new FileManipulator(m_vcsAdapter, rootDir);
            m_nlb.save(fileManipulator);
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

    public void saveAs(File nlbFolder) throws NLBVCSException, NLBConsistencyException, NLBFileManipulationException, NLBIOException {
        m_nlb.setRootDir(nlbFolder);
        saveNLB(true);
        clearUndos();
        clearPools();
        notifyObservers();
    }

    public void load(String path, ProgressData progressData) throws NLBIOException, NLBConsistencyException, NLBVCSException {
        try {
            final File rootDir = new File(path);
            if (!rootDir.exists()) {
                // We need to be sure that main root dir exists before trying to open VCS repo
                throw new NLBIOException("Specified NLB root directory " + path + " does not exist");
            }
            m_vcsAdapter.openRepo(rootDir.getCanonicalPath());
            m_nlb.load(path, progressData);
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
