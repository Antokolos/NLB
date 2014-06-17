/**
 * @(#)NonLinearBookImpl.java
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
 * Copyright (c) 2012 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.domain.export.*;
import com.nlbhub.nlb.domain.export.hypertext.HTMLExportManager;
import com.nlbhub.nlb.domain.export.hypertext.PDFExportManager;
import com.nlbhub.nlb.domain.export.xml.JSIQ2ExportManager;
import com.nlbhub.nlb.exception.*;
import com.nlbhub.nlb.util.FileManipulator;
import com.nlbhub.nlb.util.StringHelper;
import com.nlbhub.nlb.util.VarFinder;
import com.nlbhub.user.domain.DecisionPoint;
import com.nlbhub.user.domain.History;
import org.jetbrains.annotations.NotNull;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * The NonLinearBookImpl class represents the main storage for all Non-Linear Book entities.
 *
 * @author Anton P. Kolosov
 * @version 1.0 8/9/12
 */
public class NonLinearBookImpl implements NonLinearBook {
    private static final String STARTPOINT_FILE_NAME = "startpoint";
    private static final String PAGES_DIR_NAME = "pages";
    private static final String OBJS_DIR_NAME = "objs";
    private static final String VARS_DIR_NAME = "vars";
    /**
     * Path to the directory on the disk where this book will be stored.
     */
    private File m_rootDir = null;
    /**
     * UUID of the start page in the pages list.
     */
    private String m_startPoint;
    private Map<String, PageImpl> m_pages;
    private Map<String, ObjImpl> m_objs;
    private List<VariableImpl> m_variables;
    private NonLinearBook m_parentNLB;
    private Page m_parentPage;

    private class ModifyingItemAndModification {
        AbstractModifyingItem m_modifyingItem;
        Modification m_modification;

        private AbstractModifyingItem getModifyingItem() {
            return m_modifyingItem;
        }

        private void setModifyingItem(AbstractModifyingItem modifyingItem) {
            m_modifyingItem = modifyingItem;
        }

        private Modification getModification() {
            return m_modification;
        }

        private void setModification(Modification modification) {
            m_modification = modification;
        }
    }

    /**
     * The ChangeStartPointCommand class
     * This class has package-level visibility and private constructor. It is aggregated with
     * NonLinearBookImpl.
     *
     * @author Anton P. Kolosov
     * @version 1.0 1/20/14
     */
    class ChangeStartPointCommand implements NLBCommand {
        private String m_newStartPoint;
        private String m_previousStartPoint;

        private ChangeStartPointCommand(String newStartPoint) {
            m_newStartPoint = newStartPoint;
            m_previousStartPoint = getStartPoint();
        }

        @Override
        public void execute() {
            setStartPoint(m_newStartPoint);
            notifyChangedPages();
        }

        @Override
        public void revert() {
            setStartPoint(m_previousStartPoint);
            notifyChangedPages();
        }

        private void notifyChangedPages() {
            if (!StringHelper.isEmpty(m_newStartPoint)) {
                getPageById(m_newStartPoint).notifyObservers();
            }
            if (!StringHelper.isEmpty(m_previousStartPoint)) {
                getPageById(m_previousStartPoint).notifyObservers();
            }
        }
    }

    /**
     * The AddPageCommand class
     * This class has package-level visibility and private constructor. It is aggregated with
     * NonLinearBookImpl.
     *
     * @author Anton P. Kolosov
     * @version 1.0 1/23/14
     */
    class AddPageCommand implements NLBCommand {
        private PageImpl m_page;
        private ChangeStartPointCommand m_changeStartPointCommand = null;

        private AddPageCommand(PageImpl page) {
            m_page = page;
            m_page.setDeleted(true);  // Not fully exists for now
            if (getPages().values().size() == 0) {
                m_changeStartPointCommand = (
                        new ChangeStartPointCommand(page.getId())
                );
            }
        }

        @Override
        public void execute() {
            m_page.setDeleted(false);
            addPage(m_page);
            if (m_changeStartPointCommand != null) {
                m_changeStartPointCommand.execute();
            }
            m_page.notifyObservers();
        }

        @Override
        public void revert() {
            m_page.setDeleted(true);
            if (m_changeStartPointCommand != null) {
                m_changeStartPointCommand.revert();
            }
            m_pages.remove(m_page.getId());
            m_page.notifyObservers();
        }
    }

    /**
     * Class with side effects!
     * Methods execute and revert are modifying existing variable data and NLB content!
     * Use with caution!
     */
    private class VariableTracker {
        private final VariableImpl m_existingVariable;
        private final boolean m_existingVariableDeletionState;
        private final String m_existingVariableName;
        private final String m_existingVariableValue;
        private final Variable.Type m_existingVariableType;
        private final Variable.DataType m_existingVariableDataType;
        private final VariableImpl m_newVariable;
        private final String m_newVariableName;
        private final String m_newVariableValue;
        private final Variable.Type m_newVariableType;
        private final Variable.DataType m_newVariableDataType;
        private final boolean m_deleteFlag;

        private VariableTracker(
                final VariableImpl existingVariable,
                boolean deleteFlag,
                final Variable.Type newVariableType,
                final Variable.DataType newVariableDataType,
                final String newVariableName,
                final String newVariableValue,
                final String newVariableTarget
        ) {
            m_existingVariable = existingVariable;
            m_existingVariableDeletionState = (m_existingVariable != null) && m_existingVariable.isDeleted();
            m_deleteFlag = deleteFlag;
            m_newVariableName = newVariableName;
            m_newVariableValue = newVariableValue;
            m_newVariableType = newVariableType;
            m_newVariableDataType = newVariableDataType;
            m_existingVariableName = (
                    (m_existingVariable != null)
                            ? m_existingVariable.getName()
                            : Variable.DEFAULT_NAME
            );
            m_existingVariableValue = (
                    (m_existingVariable != null)
                            ? m_existingVariable.getValue()
                            : Variable.DEFAULT_VALUE
            );
            m_existingVariableType = (
                    (m_existingVariable != null)
                            ? m_existingVariable.getType()
                            : Variable.Type.VAR
            );
            m_existingVariableDataType = (
                    (m_existingVariable != null)
                            ? m_existingVariable.getDataType()
                            : Variable.DataType.AUTO
            );
            if ((existingVariable == null) && !deleteFlag) {
                m_newVariable = new VariableImpl(
                        newVariableType,
                        newVariableDataType,
                        newVariableName,
                        newVariableValue,
                        newVariableTarget
                );
            } else {
                m_newVariable = null;
            }
        }

        private String execute() {
            String variableId;
            if (m_deleteFlag) {
                variableId = Constants.EMPTY_STRING;
                if (m_existingVariable != null) {
                    m_existingVariable.setDeleted(true);
                }
            } else {
                if (m_existingVariable == null) {
                    assert m_newVariable != null;
                    addVariable(m_newVariable);
                    variableId = m_newVariable.getId();
                } else {
                    m_existingVariable.setName(m_newVariableName);
                    m_existingVariable.setValue(m_newVariableValue);
                    m_existingVariable.setType(m_newVariableType);
                    m_existingVariable.setDataType(m_newVariableDataType);
                    m_existingVariable.setDeleted(false);   // because it can be already deleted; discard this deletion
                    variableId = m_existingVariable.getId();
                }
            }
            return variableId;
        }

        private String revert() {
            String variableId;
            if (m_deleteFlag) {
                if (m_existingVariable != null) {
                    m_existingVariable.setDeleted(false);
                    variableId = m_existingVariable.getId();
                } else {
                    variableId = Constants.EMPTY_STRING;
                }
            } else {
                if (m_existingVariable == null) {
                    assert m_newVariable != null;
                    ListIterator<VariableImpl> iterator = m_variables.listIterator();
                    while (iterator.hasNext()) {
                        VariableImpl variableImpl = iterator.next();
                        if (variableImpl.getId().equals(m_newVariable.getId())) {
                            iterator.remove();
                        }
                    }
                    variableId = Constants.EMPTY_STRING;
                } else {
                    m_existingVariable.setName(m_existingVariableName);
                    m_existingVariable.setValue(m_existingVariableValue);
                    m_existingVariable.setType(m_existingVariableType);
                    m_existingVariable.setDataType(m_existingVariableDataType);
                    m_existingVariable.setDeleted(m_existingVariableDeletionState);
                    variableId = m_existingVariable.getId();
                }
            }
            return variableId;
        }
    }

    /**
     * The UpdatePageCommand class
     * This class has package-level visibility and private constructor. It is aggregated with
     * NonLinearBookImpl.
     *
     * @author Anton P. Kolosov
     * @version 1.0 1/23/14
     */
    class UpdatePageCommand implements NLBCommand {
        private final PageImpl m_page;
        private VariableTracker m_variableTracker;
        private VariableTracker m_moduleConstrIdTracker;

        private final String m_existingPageText;
        private final String m_existingPageCaptionText;
        private final boolean m_existingUseCaption;
        private final String m_existingModuleName;
        private final String m_existingTraverseText;
        private final String m_existingReturnText;
        private final String m_existingReturnPageId;
        private final String m_newPageText;
        private final String m_newPageCaptionText;
        private final boolean m_newUseCaption;
        private final String m_newModuleName;
        private final String m_newTraverseText;
        private final String m_newReturnText;
        private final String m_newReturnPageId;
        private AbstractNodeItem.SortLinksCommand m_sortLinkCommand;
        private List<AbstractNodeItem.DeleteLinkCommand> m_deleteLinkCommands = new ArrayList<>();

        private UpdatePageCommand(
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
            m_page = getPageImplById(page.getId());
            m_variableTracker = new VariableTracker(
                    getVariableImplById(m_page.getVarId()),
                    StringHelper.isEmpty(pageVariableName),
                    Variable.Type.PAGE,
                    Variable.DataType.BOOLEAN,
                    pageVariableName,
                    Variable.DEFAULT_VALUE,
                    m_page.getFullId()
            );
            m_moduleConstrIdTracker = new VariableTracker(
                    getVariableImplById(m_page.getModuleConstrId()),
                    StringHelper.isEmpty(moduleConsraintVariableName),
                    Variable.Type.MODCONSTRAINT,
                    Variable.DataType.BOOLEAN,
                    Variable.DEFAULT_NAME,
                    moduleConsraintVariableName,
                    m_page.getFullId()
            );
            m_existingPageText = m_page.getText();
            m_existingPageCaptionText = m_page.getCaption();
            m_existingUseCaption = m_page.isUseCaption();
            m_existingModuleName = m_page.getModuleName();
            m_existingTraverseText = m_page.getTraverseText();
            m_existingReturnText = m_page.getReturnText();
            m_existingReturnPageId = m_page.getReturnPageId();
            m_newPageText = pageText;
            m_newPageCaptionText = pageCaptionText;
            m_newUseCaption = useCaption;
            m_newModuleName = moduleName;
            m_newTraverseText = traverseText;
            m_newReturnText = returnText;
            m_newReturnPageId = returnPageId;
            for (final Link link : m_page.getLinks()) {
                boolean absentInModel = true;
                for (final Link modelLink : linksTableModel.getLinks()) {
                    if (modelLink.getId().equals(link.getId())) {
                        absentInModel = false;
                        break;
                    }
                }
                if (absentInModel) {
                    m_deleteLinkCommands.add(m_page.createDeleteLinkCommand(link));
                }
            }
            m_sortLinkCommand = m_page.createSortLinksCommand(linksTableModel.getLinks());
        }

        @Override
        public void execute() {
            m_sortLinkCommand.execute();
            for (AbstractNodeItem.DeleteLinkCommand command : m_deleteLinkCommands) {
                command.execute();
            }
            m_page.setVarId(m_variableTracker.execute());
            m_page.setModuleConstrId(m_moduleConstrIdTracker.execute());
            m_page.setText(m_newPageText);
            m_page.setCaption(m_newPageCaptionText);
            m_page.setUseCaption(m_newUseCaption);
            m_page.setModuleName(m_newModuleName);
            m_page.setTraverseText(m_newTraverseText);
            m_page.setReturnText(m_newReturnText);
            m_page.setReturnPageId(m_newReturnPageId);
            m_page.notifyObservers();
        }

        @Override
        public void revert() {
            for (AbstractNodeItem.DeleteLinkCommand command : m_deleteLinkCommands) {
                command.revert();
            }
            m_sortLinkCommand.revert();
            m_page.setVarId(m_variableTracker.revert());
            m_page.setModuleConstrId(m_moduleConstrIdTracker.revert());
            m_page.setText(m_existingPageText);
            m_page.setCaption(m_existingPageCaptionText);
            m_page.setUseCaption(m_existingUseCaption);
            m_page.setModuleName(m_existingModuleName);
            m_page.setTraverseText(m_existingTraverseText);
            m_page.setReturnText(m_existingReturnText);
            m_page.setReturnPageId(m_existingReturnPageId);
            m_page.notifyObservers();
        }
    }

    /**
     * The UpdateObjCommand class
     * This class has package-level visibility and private constructor. It is aggregated with
     * NonLinearBookImpl.
     *
     * @author Anton P. Kolosov
     * @version 1.0 1/23/14
     */
    class UpdateObjCommand implements NLBCommand {
        private final ObjImpl m_obj;
        private VariableTracker m_variableTracker;

        private String m_existingObjName;
        private String m_existingObjText;
        private boolean m_existingObjIsTakable;
        private String m_newObjName;
        private String m_newObjText;
        private boolean m_newObjIsTakable;

        private UpdateObjCommand(
                final Obj obj,
                final String objVariableName,
                final String objName,
                final String objText,
                final boolean objIsTakable
        ) {
            m_obj = getObjImplById(obj.getId());
            m_variableTracker = new VariableTracker(
                    getVariableImplById(m_obj.getVarId()),
                    StringHelper.isEmpty(objVariableName),
                    Variable.Type.OBJ,
                    Variable.DataType.BOOLEAN,
                    objVariableName,
                    Variable.DEFAULT_VALUE,
                    m_obj.getFullId());
            m_existingObjName = obj.getName();
            m_existingObjText = obj.getText();
            m_existingObjIsTakable = obj.isTakable();
            m_newObjName = objName;
            m_newObjText = objText;
            m_newObjIsTakable = objIsTakable;
        }

        @Override
        public void execute() {
            m_obj.setVarId(m_variableTracker.execute());
            m_obj.setName(m_newObjName);
            m_obj.setText(m_newObjText);
            m_obj.setTakable(m_newObjIsTakable);
            m_obj.notifyObservers();
        }

        @Override
        public void revert() {
            m_obj.setVarId(m_variableTracker.revert());
            m_obj.setName(m_existingObjName);
            m_obj.setText(m_existingObjText);
            m_obj.setTakable(m_existingObjIsTakable);
            m_obj.notifyObservers();
        }
    }

    /**
     * The UpdateLinkCommand class
     * This class has package-level visibility and private constructor. It is aggregated with
     * NonLinearBookImpl.
     *
     * @author Anton P. Kolosov
     * @version 1.0 1/23/14
     */
    class UpdateLinkCommand implements NLBCommand {
        private final LinkImpl m_link;
        private VariableTracker m_variableTracker;
        private VariableTracker m_constraintTracker;
        private final String m_newLinkText;
        private final String m_existingLinkText;

        private UpdateLinkCommand(
                final Link link,
                final String linkVariableName,
                final String linkConstraintName,
                final String linkText
        ) {
            IdentifiableItem parent = link.getParent();
            AbstractNodeItem nodeItem = getPageImplById(parent.getId());
            if (nodeItem == null) {
                nodeItem = getObjImplById(parent.getId());
            }
            m_link = nodeItem.getLinkById(link.getId());
            m_variableTracker = new VariableTracker(
                    getVariableImplById(m_link.getVarId()),
                    StringHelper.isEmpty(linkVariableName),
                    Variable.Type.LINK,
                    Variable.DataType.BOOLEAN,
                    linkVariableName,
                    Variable.DEFAULT_VALUE,
                    link.getFullId()
            );
            m_constraintTracker = new VariableTracker(
                    getVariableImplById(m_link.getConstrId()),
                    StringHelper.isEmpty(linkConstraintName),
                    Variable.Type.LINKCONSTRAINT,
                    Variable.DataType.BOOLEAN,
                    Variable.DEFAULT_NAME,
                    linkConstraintName,
                    link.getFullId()
            );
            m_existingLinkText = link.getText();
            m_newLinkText = linkText;
        }

        @Override
        public void execute() {
            m_link.setVarId(m_variableTracker.execute());
            m_link.setConstrId(m_constraintTracker.execute());
            m_link.setText(m_newLinkText);
            m_link.notifyObservers();
        }

        @Override
        public void revert() {
            m_link.setVarId(m_variableTracker.revert());
            m_link.setConstrId(m_constraintTracker.revert());
            m_link.setText(m_existingLinkText);
            m_link.notifyObservers();
        }
    }

    class UpdateModificationsCommand implements NLBCommand {
        private AbstractModifyingItem m_item;
        private Map<String, ModificationImpl> m_modificationsToBeDeleted = new HashMap<>();
        private Map<String, Boolean> m_modificationsDeletionInitState = new HashMap<>();
        private Map<String, ModificationImpl> m_modificationsToBeReplaced = new HashMap<>();
        private Map<String, ModificationImpl> m_modificationsToBeReplacedPrev = new HashMap<>();
        private Map<String, ModificationImpl> m_modificationsToBeAdded = new HashMap<>();

        private Map<String, VariableImpl> m_variablesToBeReplaced = new HashMap<>();
        private Map<String, VariableImpl> m_variablesToBeReplacedPrev = new HashMap<>();
        private Map<String, VariableImpl> m_variablesToBeAdded = new HashMap<>();

        private UpdateModificationsCommand(
                final ModifyingItem modifyingItem,
                final ModificationsTableModel modificationsTableModel
        ) {
            // TODO: possibly inefficient code, please refactor
            m_item = getModifyingItemImpl(modifyingItem);
            if (modifyingItem != null) {
                for (Modification modification : modificationsTableModel.getModifications()) {
                    boolean toBeAdded = true;
                    for (ModificationImpl existingModification : m_item.getModificationImpls()) {
                        if (existingModification.getId().equals(modification.getId())) {
                            if (modification.isDeleted()) {
                                m_modificationsToBeDeleted.put(
                                        existingModification.getId(),
                                        existingModification
                                );
                                m_modificationsDeletionInitState.put(
                                        existingModification.getId(),
                                        existingModification.isDeleted()
                                );
                            } else {
                                m_modificationsToBeReplaced.put(
                                        modification.getId(),
                                        new ModificationImpl(modification)
                                );
                                m_modificationsToBeReplacedPrev.put(
                                        existingModification.getId(),
                                        existingModification
                                );
                            }
                            toBeAdded = false;
                        }
                    }
                    if (toBeAdded) {
                        m_modificationsToBeAdded.put(
                                modification.getId(),
                                new ModificationImpl(modification)
                        );
                    }
                }

                final Map<String, Variable> variableMap = modificationsTableModel.getVariableMap();
                for (Map.Entry<String, Variable> entry : variableMap.entrySet()) {
                    final VariableImpl existingVariable = getVariableImplById(entry.getKey());
                    final Variable variable = entry.getValue();
                    if (existingVariable != null) {
                        m_variablesToBeReplacedPrev.put(
                                existingVariable.getId(),
                                new VariableImpl(existingVariable)
                        );
                        m_variablesToBeReplaced.put(
                                existingVariable.getId(),
                                new VariableImpl(variable)
                        );
                    } else {
                        if (!variable.isDeleted()) {
                            m_variablesToBeAdded.put(variable.getId(), new VariableImpl(variable));
                        }
                    }
                }
            }
        }

        @Override
        public void execute() {
            if (m_item != null) {
                ListIterator<ModificationImpl> existingModificationsIterator = (
                        m_item.getModificationImpls().listIterator()
                );
                while (existingModificationsIterator.hasNext()) {
                    final ModificationImpl existingModification = existingModificationsIterator.next();
                    if (m_modificationsToBeDeleted.containsKey(existingModification.getId())) {
                        existingModification.setDeleted(true);
                    } else if (m_modificationsToBeReplaced.containsKey(existingModification.getId())) {
                        existingModification.copy(
                                m_modificationsToBeReplaced.get(existingModification.getId())
                        );
                    }
                }

                for (Map.Entry<String, ModificationImpl> entry : m_modificationsToBeAdded.entrySet()) {
                    m_item.addModification(entry.getValue());
                }

                for (Map.Entry<String, VariableImpl> entry : m_variablesToBeReplaced.entrySet()) {
                    final VariableImpl existingVariable = getVariableImplById(entry.getKey());
                    final Variable variable = entry.getValue();
                    existingVariable.copy(variable);
                }

                for (Map.Entry<String, VariableImpl> entry : m_variablesToBeAdded.entrySet()) {
                    addVariable(entry.getValue());
                }
            }
        }

        @Override
        public void revert() {
            if (m_item != null) {
                ListIterator<ModificationImpl> existingModificationsIterator = (
                        m_item.getModificationImpls().listIterator()
                );
                while (existingModificationsIterator.hasNext()) {
                    final ModificationImpl existingModification = existingModificationsIterator.next();
                    if (m_modificationsToBeDeleted.containsKey(existingModification.getId())) {
                        existingModification.setDeleted(
                                m_modificationsDeletionInitState.get(existingModification.getId())
                        );
                    } else if (m_modificationsToBeReplaced.containsKey(existingModification.getId())) {
                        existingModification.copy(
                                m_modificationsToBeReplacedPrev.get(existingModification.getId())
                        );
                    } else if (m_modificationsToBeAdded.containsKey(existingModification.getId())) {
                        existingModificationsIterator.remove();
                    }
                }

                for (Map.Entry<String, VariableImpl> entry : m_variablesToBeReplacedPrev.entrySet()) {
                    final VariableImpl existingVariable = getVariableImplById(entry.getKey());
                    final Variable variable = entry.getValue();
                    existingVariable.copy(variable);
                }

                ListIterator<VariableImpl> iterator = m_variables.listIterator();
                while (iterator.hasNext()) {
                    VariableImpl variableImpl = iterator.next();
                    for (Map.Entry<String, VariableImpl> entry : m_variablesToBeAdded.entrySet()) {
                        if (variableImpl.getId().equals(entry.getValue().getId())) {
                            iterator.remove();
                        }
                    }
                }
            }
        }

        private ModificationImpl getModificationImpl(Modification modification) {
            IdentifiableItem[] parents = new IdentifiableItem[2];
            parents[1] = modification.getParent();
            assert (parents[1] != null);
            parents[0] = parents[1].getParent();
            AbstractModifyingItem modifyingItem = null;
            AbstractNodeItem nodeItem = null;
            for (int i = 0; i < 2; i++) {
                if (parents[i] != null) {
                    if (nodeItem == null) {
                        // This is Page or Object
                        nodeItem = getPageImplById(parents[i].getId());
                        if (nodeItem == null) {
                            nodeItem = getObjImplById(parents[i].getId());
                        }
                    } else {
                        // This is link
                        modifyingItem = nodeItem.getLinkById(parents[1].getId());
                    }
                }
            }
            if (modifyingItem == null) {
                modifyingItem = nodeItem;
            }
            assert (modifyingItem != null);
            return modifyingItem.getModificationById(modification.getId());
        }

        private AbstractModifyingItem getModifyingItemImpl(ModifyingItem modifyingItem) {
            IdentifiableItem[] parents = new IdentifiableItem[2];
            parents[1] = modifyingItem;
            assert (parents[1] != null);
            parents[0] = parents[1].getParent();
            AbstractModifyingItem item = null;
            AbstractNodeItem nodeItem = null;
            for (int i = 0; i < 2; i++) {
                if (parents[i] != null) {
                    if (nodeItem == null) {
                        // This is Page or Object
                        nodeItem = getPageImplById(parents[i].getId());
                        if (nodeItem == null) {
                            nodeItem = getObjImplById(parents[i].getId());
                        }
                    } else {
                        // This is link
                        item = nodeItem.getLinkById(parents[i].getId());
                    }
                }
            }
            if (item == null) {
                item = nodeItem;
            }
            assert (item != null);
            return item;
        }
    }

    /**
     * The AddObjCommand class
     * This class has package-level visibility and private constructor. It is aggregated with
     * NonLinearBookImpl.
     *
     * @author Anton P. Kolosov
     * @version 1.0 1/23/14
     */
    class AddObjCommand implements NLBCommand {
        private ObjImpl m_obj;

        private AddObjCommand(ObjImpl obj) {
            m_obj = obj;
            m_obj.setDeleted(true);  // Not fully exists for now
        }

        @Override
        public void execute() {
            m_obj.setDeleted(false);
            addObj(m_obj);
            m_obj.notifyObservers();
        }

        @Override
        public void revert() {
            m_obj.setDeleted(true);
            m_objs.remove(m_obj.getId());
            m_obj.notifyObservers();
        }
    }

    abstract class DeleteNodeCommand implements NLBCommand {
        private List<LinkImpl> m_links = new ArrayList<>();
        private Map<String, Boolean> m_linksDeletionStates = new HashMap<>();

        protected DeleteNodeCommand(
                final List<Link> adjacentLinks
        ) {
            for (final Link linkToDelete : adjacentLinks) {
                AbstractNodeItem parent = getPageImplById(linkToDelete.getParent().getId());
                if (parent == null) {
                    parent = getObjImplById(linkToDelete.getParent().getId());
                }
                final LinkImpl link = parent.getLinkById(linkToDelete.getId());
                m_links.add(link);
            }
            for (LinkImpl link : m_links) {
                m_linksDeletionStates.put(link.getId(), link.isDeleted());
            }
        }

        @Override
        public void execute() {
            for (LinkImpl link : m_links) {
                link.setDeleted(true);
                link.notifyObservers();
            }
        }

        @Override
        public void revert() {
            for (LinkImpl link : m_links) {
                // Please note that link is considered deleted if its parent page is deleted
                link.setDeleted(m_linksDeletionStates.get(link.getId()));
                link.notifyObservers();
            }
        }
    }

    /**
     * The DeletePageCommand class
     * Does not fully deletes page from map, only sets its deleted flag
     * This class has package-level visibility and private constructor. It is aggregated with
     * NonLinearBookImpl.
     *
     * @author Anton P. Kolosov
     * @version 1.0 1/23/14
     */
    class DeletePageCommand extends DeleteNodeCommand implements NLBCommand {
        private PageImpl m_page;
        private ChangeStartPointCommand m_changeStartPointCommand = null;

        private DeletePageCommand(PageImpl page, final List<Link> adjacentLinks) {
            super(adjacentLinks);
            m_page = page;
            if (page.getId().equals(getStartPoint())) {
                // reset the StartPoint
                m_changeStartPointCommand = new ChangeStartPointCommand("");
            }
        }

        @Override
        public void execute() {
            // deletes adjacent links first...
            super.execute();

            // ...and then delete page
            m_page.setDeleted(true);
            if (m_changeStartPointCommand != null) {
                m_changeStartPointCommand.execute();
            }
            m_page.notifyObservers();
        }

        @Override
        public void revert() {
            // restore page first...
            m_page.setDeleted(false);
            if (m_changeStartPointCommand != null) {
                m_changeStartPointCommand.revert();
            }
            m_page.notifyObservers();

            // ...and then restore adjacent links
            super.revert();
        }
    }

    /**
     * The DeleteObjCommand class
     * Does not fully deletes obj from map, only sets its deleted flag
     * This class has package-level visibility and private constructor. It is aggregated with
     * NonLinearBookImpl.
     *
     * @author Anton P. Kolosov
     * @version 1.0 1/23/14
     */
    class DeleteObjCommand extends DeleteNodeCommand implements NLBCommand {
        private ObjImpl m_obj;

        private DeleteObjCommand(ObjImpl obj, final List<Link> adjacentLinks) {
            super(adjacentLinks);
            m_obj = obj;
        }

        @Override
        public void execute() {
            // deletes adjacent links first...
            super.execute();

            // ...and then delete obj
            m_obj.setDeleted(true);
            m_obj.notifyObservers();
        }

        @Override
        public void revert() {
            // restore obj first...
            m_obj.setDeleted(false);
            m_obj.notifyObservers();

            // ...and then restore adjacent links
            super.revert();
        }
    }

    public NonLinearBookImpl() {
        m_parentNLB = null;
        m_parentPage = null;
        m_pages = new HashMap<>();
        m_objs = new HashMap<>();
        m_variables = new ArrayList<>();
    }

    public NonLinearBookImpl(NonLinearBook parentNLB, Page parentPage) {
        m_parentNLB = parentNLB;
        m_parentPage = parentPage;
        m_pages = new HashMap<>();
        m_objs = new HashMap<>();
        m_variables = new ArrayList<>();
    }

    ChangeStartPointCommand createChangeStartPointCommand(final String startPoint) {
        return new ChangeStartPointCommand(startPoint);
    }

    AddPageCommand createAddPageCommand(final PageImpl page) {
        return new AddPageCommand(page);
    }

    UpdatePageCommand createUpdatePageCommand(
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
        return (
                new UpdatePageCommand(
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
    }

    UpdateObjCommand createUpdateObjCommand(
            final Obj obj,
            final String objVariableName,
            final String objName,
            final String objText,
            final boolean objIsTakable
    ) {
        return new UpdateObjCommand(obj, objVariableName, objName, objText, objIsTakable);
    }

    UpdateLinkCommand createUpdateLinkCommand(
            final Link link,
            final String linkVariableName,
            final String linkConstraintName,
            final String linkText
    ) {
        return new UpdateLinkCommand(link, linkVariableName, linkConstraintName, linkText);
    }

    UpdateModificationsCommand createUpdateModificationsCommand(
            final ModifyingItem modifyingItem,
            final ModificationsTableModel modificationsTableModel
    ) {
        return new UpdateModificationsCommand(modifyingItem, modificationsTableModel);
    }

    AddObjCommand createAddObjCommand(final ObjImpl obj) {
        return new AddObjCommand(obj);
    }

    DeletePageCommand createDeletePageCommand(final PageImpl page, final List<Link> adjacentLinks) {
        return new DeletePageCommand(page, adjacentLinks);
    }

    DeleteObjCommand createDeleteObjCommand(final ObjImpl obj, final List<Link> adjacentLinks) {
        return new DeleteObjCommand(obj, adjacentLinks);
    }

    public List<Link> getAssociatedLinks(NodeItem nodeItem) {
        List<Link> result = new ArrayList<>();
        NodeItem node = getPageById(nodeItem.getId());
        boolean isPage = true;
        if (node == null) {
            node = getObjById(nodeItem.getId());
            isPage = false;
        }
        result.addAll(node.getLinks());
        if (isPage) {
            for (Map.Entry<String, PageImpl> entry : m_pages.entrySet()) {
                List<Link> links = entry.getValue().getLinks();
                for (Link link : links) {
                    if (nodeItem.getId().equals(link.getTarget())) {
                        result.add(link);
                    }
                }
            }
        } else {
            for (Map.Entry<String, ObjImpl> entry : m_objs.entrySet()) {
                List<Link> links = entry.getValue().getLinks();
                for (Link link : links) {
                    if (nodeItem.getId().equals(link.getTarget())) {
                        result.add(link);
                    }
                }
            }
        }
        return result;
    }

    public void clear() throws NLBVCSException {
        m_pages.clear();
        m_objs.clear();
        m_variables.clear();
        m_startPoint = null;
        m_rootDir = null;
    }

    @Override
    public boolean isEmpty() {
        return m_pages.isEmpty() && m_objs.isEmpty() && m_variables.isEmpty();
    }

    public String getStartPoint() {
        return m_startPoint;
    }

    public void setStartPoint(String startPoint) {
        m_startPoint = startPoint;
    }

    public File getRootDir() {
        return m_rootDir;
    }

    public void setRootDir(final File rootDir) {
        m_rootDir = rootDir;
    }

    @Override
    public Map<String, Page> getPages() {
        Map<String, Page> result = new HashMap<>();
        result.putAll(m_pages);
        return result;
    }

    public void addPage(@NotNull PageImpl page) {
        m_pages.put(page.getId(), page);
    }

    public Page getPageById(String id) {
        Page result = getPageImplById(id);
        if (result != null) {
            return result;
        } else if (m_parentNLB != null) {
            return m_parentNLB.getPageById(id);
        } else {
            return null;
        }
    }

    public PageImpl getPageImplById(String id) {
        return m_pages.get(id);
    }

    @Override
    public Map<String, Obj> getObjs() {
        Map<String, Obj> result = new HashMap<>();
        result.putAll(m_objs);
        return result;
    }

    public void addObj(@NotNull ObjImpl obj) {
        m_objs.put(obj.getId(), obj);
    }

    public Obj getObjById(String objId) {
        Obj result = getObjImplById(objId);
        if (result != null) {
            return result;
        } else if (m_parentNLB != null) {
            return m_parentNLB.getObjById(objId);
        } else {
            return null;
        }
    }

    public ObjImpl getObjImplById(String objId) {
        return m_objs.get(objId);
    }

    /**
     * For internal use only
     *
     * @param sourceId
     * @param history
     * @return
     * @throws ScriptException
     */
    public PageImpl createFilteredPage(
            final String sourceId,
            final History history
    ) throws ScriptException {
        final PageImpl source = getPageImplById(sourceId);
        ScriptEngineManager factory = new ScriptEngineManager();
        List<DecisionPoint> decisionsList = history.getDecisionPoints();
        final Map<String, Object> visitedVars = new HashMap<>();

        for (final DecisionPoint decisionPoint : decisionsList) {
            makeDecisionVariableChange(factory, visitedVars, decisionPoint);
        }

        final DecisionPoint decisionPointToBeMade = history.getDecisionPointToBeMade();
        makeDecisionVariableChange(factory, visitedVars, decisionPointToBeMade);

        List<String> linkIdsToBeExcluded = new ArrayList<>();
        // TODO: ineffective search, please refactor!
        for (final Link link : source.getLinks()) {
            if (determineLinkExcludedStatus(factory, visitedVars, link)) {
                linkIdsToBeExcluded.add(link.getId());
            }
        }
        List<Link> linksToBeAdded = new ArrayList<>();
        if (!source.getModule().isEmpty()) {
            // If this page is module page for someone, create traverse link on the fly
            Link link = (
                    new LinkLw(
                            LinkLw.Type.Traverse,
                            source.getModule().getStartPoint(),
                            source,
                            source.getTraverseText(),
                            source.getModuleConstrId(),
                            true,
                            false
                    )
            );
            if (!determineLinkExcludedStatus(factory, visitedVars, link)) {
                linksToBeAdded.add(link);
            }
        }
        if (m_parentNLB != null && m_parentPage != null && source.isLeaf()) {
            // Create return link on the fly. Return links for leafs does not have any constraints,
            // i.e. it is shown always
            Link link = (
                    new LinkLw(
                            LinkLw.Type.Return,
                            StringHelper.isEmpty(source.getReturnPageId())
                                    ? m_parentPage.getId()
                                    : source.getReturnPageId(),
                            source,
                            source.getReturnText(),
                            Constants.EMPTY_STRING,
                            true,
                            false
                    )
            );
            if (!determineLinkExcludedStatus(factory, visitedVars, link)) {
                linksToBeAdded.add(link);
            }
        } else if (
                m_parentNLB != null
                        && m_parentPage != null
                        && !StringHelper.isEmpty(m_parentPage.getModuleConstrId())
                ) {
            // If page has module constraint, than module return links should be added to the
            // each page of the module.
            // These links should have constraints in form of 'NOT (module_constraint)'
            // (i.e. negative constraints)
            Link link = (
                    new LinkLw(
                            LinkLw.Type.Return,
                            StringHelper.isEmpty(source.getReturnPageId())
                                    ? m_parentPage.getId()
                                    : source.getReturnPageId(),
                            source,
                            source.getReturnText(),
                            m_parentPage.getModuleConstrId(),
                            false,
                            false
                    )
            );
            if (!determineLinkExcludedStatus(factory, visitedVars, link)) {
                linksToBeAdded.add(link);
            }
        }
        return PageImpl.createFilteredClone(source, linkIdsToBeExcluded, linksToBeAdded);
    }

    private void updateVisitedVars(
            final NonLinearBook decisionModule,
            final ModifyingItem modifyingItem,
            final ScriptEngineManager factory,
            final Map<String, Object> visitedVars
    ) throws ScriptException {
        for (final Modification modification : modifyingItem.getModifications()) {
            if (modification.getType() == Modification.Type.ASSIGN) {
                // create a JavaScript engine
                ScriptEngine engine = factory.getEngineByName("JavaScript");
                for (Map.Entry<String, Object> entry : visitedVars.entrySet()) {
                    engine.put(entry.getKey(), entry.getValue());
                }
                Variable modVariable = decisionModule.getVariableById(modification.getVarId());
                Variable modExpression = decisionModule.getVariableById(modification.getExprId());
                // evaluate JavaScript code from String
                visitedVars.put(modVariable.getName(), engine.eval(modExpression.getValue()));
            }
        }
    }

    private NonLinearBook getModuleByBookId(final String bookId) {
        NonLinearBook result = getMainNLB();
        Map<Integer, String> modulePageMap = new HashMap<>();
        String[] idParts = bookId.split(";");
        int maxModuleIdx = 0;
        for (String idPart : idParts) {
            String[] modulePageParts = idPart.split("=");
            if (modulePageParts.length > 1) {
                final int curModuleIdx = Integer.parseInt(modulePageParts[0]);
                modulePageMap.put(curModuleIdx, modulePageParts[1]);
                if (curModuleIdx > maxModuleIdx) {
                    maxModuleIdx = curModuleIdx;
                }
            }
        }
        for (int i = 1; i <= maxModuleIdx; i++) {
            result = result.getPageById(modulePageMap.get(i)).getModule();
        }
        return result;
    }

    private boolean determineLinkExcludedStatus(
            final ScriptEngineManager factory,
            final Map<String, Object> visitedVars,
            final Link link
    ) throws ScriptException {

        final Variable constraintVar = getVariableById(link.getConstrId());
        final Variable moduleConstraint = (
                m_parentPage != null
                        ? getVariableById(m_parentPage.getModuleConstrId())
                        : null
        );

        if (constraintVar != null || moduleConstraint != null) {
            if (moduleConstraint != null && link.isObeyToModuleConstraint()) {
                final String constraint = moduleConstraint.getValue().trim();
                ScriptEngine engine = prepareEngine(factory, constraint, visitedVars);
                // TODO: Should check that result can be casted to Boolean, get exception otherwise
                Boolean evalModule = (Boolean) engine.eval(constraint);
                if (
                        (link.isPositiveConstraint() && !evalModule)
                                || (!link.isPositiveConstraint() && evalModule)
                        ) {
                    // Link should be excluded, because it fails to comply module constraint
                    return true;
                }
            }

            // Otherwise, check main constraint
            if (constraintVar != null) {
                final String constraint = constraintVar.getValue().trim();
                ScriptEngine engine = prepareEngine(factory, constraint, visitedVars);
                // TODO: Should check that result can be casted to Boolean, get exception otherwise
                Boolean evalResult = (Boolean) engine.eval(constraint);
                return (
                        (link.isPositiveConstraint() && !evalResult)
                                || (!link.isPositiveConstraint() && evalResult)
                );
            }
        }
        return false;
    }

    private ScriptEngine prepareEngine(
            ScriptEngineManager factory,
            final String constraint,
            final Map<String, Object> visitedVars
    ) {
        final Collection<String> constraintVars = VarFinder.findVariableNames(constraint);
        final Map<String, Object> varMapping = new HashMap<>();
        for (final String var : constraintVars) {
            if (visitedVars.containsKey(var)) {
                varMapping.put(var, visitedVars.get(var));
            } else {
                varMapping.put(var, false);
            }
        }
        // create a JavaScript engine
        ScriptEngine engine = factory.getEngineByName("JavaScript");
        for (Map.Entry<String, Object> entry : varMapping.entrySet()) {
            engine.put(entry.getKey(), entry.getValue());
        }
        return engine;
    }

    private void makeDecisionVariableChange(
            final ScriptEngineManager factory,
            final Map<String, Object> visitedVars,
            final DecisionPoint decisionPoint
    ) throws ScriptException {
        final NonLinearBook decisionModule = getModuleByBookId(decisionPoint.getBookId());
        final NonLinearBook fromModule = getModuleByBookId(decisionPoint.getFromBookId());
        makeVariableChangesForVisitedPage(fromModule, decisionPoint.getFromPageId(), factory, visitedVars);
        makeVariableChangesForVisitedPage(decisionModule, decisionPoint.getToPageId(), factory, visitedVars);
        if (decisionPoint.isLinkInfo()) {
            final Page pageFrom = decisionModule.getPageById(decisionPoint.getFromPageId());
            final Link linkToBeFollowedCur = findLink(pageFrom, decisionPoint.getLinkId());
            Variable variableLinkCur = (
                    decisionModule.getVariableById(linkToBeFollowedCur.getVarId())
            );
            if (
                    variableLinkCur != null
                            && !StringHelper.isEmpty(variableLinkCur.getName())
                    ) {
                visitedVars.put(variableLinkCur.getName(), true);
            }
            updateVisitedVars(decisionModule, linkToBeFollowedCur, factory, visitedVars);
        }
    }

    private void makeVariableChangesForVisitedPage(
            final NonLinearBook decisionModule,
            final String pageId,
            final ScriptEngineManager factory,
            final Map<String, Object> visitedVars
    ) throws ScriptException {
        if (!StringHelper.isEmpty(pageId)) {
            final Page page = decisionModule.getPageById(pageId);
            Variable variable = decisionModule.getVariableById(page.getVarId());
            if (variable != null && !StringHelper.isEmpty(variable.getName())) {
                visitedVars.put(variable.getName(), true);
            }
            updateVisitedVars(decisionModule, page, factory, visitedVars);
        }
    }

    private Link findLink(final Page page, final String linkId) {
        final List<Link> links = page.getLinks();
        for (final Link link : links) {
            if (link.getId().equals(linkId)) {
                return link;
            }
        }
        return null;
    }

    public boolean load(
            final String path
    ) throws NLBIOException, NLBConsistencyException, NLBVCSException {
        final File rootDir = new File(path);
        if (!rootDir.exists()) {
            return false;
        }
        m_rootDir = rootDir;
        readStartPoint(rootDir);
        readObjs(rootDir);
        readPages(rootDir);
        readVariables(rootDir);
        return true;
    }

    public boolean loadAndSetParent(
            final String path,
            final NonLinearBook parentNLB,
            final Page parentPage
    ) throws NLBIOException, NLBConsistencyException, NLBVCSException {
        final File rootDir = new File(path);
        if (!rootDir.exists()) {
            return false;
        }
        m_rootDir = rootDir;
        m_parentNLB = parentNLB;
        m_parentPage = parentPage;
        readStartPoint(rootDir);
        readObjs(rootDir);
        readPages(rootDir);
        readVariables(rootDir);
        return true;
    }

    private void readStartPoint(File rootDir) throws NLBIOException {
        m_startPoint = (
                FileManipulator.getOptionalFileAsString(
                        rootDir,
                        STARTPOINT_FILE_NAME,
                        DEFAULT_STARTPOINT
                )
        );
    }

    private void readPages(
            final File rootDir
    ) throws NLBIOException, NLBConsistencyException, NLBVCSException {
        m_pages.clear();
        final File pagesDir = new File(rootDir, PAGES_DIR_NAME);
        // pages dir can be nonexistent, in this case there is no pages in the book
        if (pagesDir.exists()) {
            File[] pageDirs = pagesDir.listFiles();
            if (pageDirs == null) {
                throw new NLBIOException("Error when enumerating pages' directory contents");
            }
            for (File pageDir : pageDirs) {
                final PageImpl page = new PageImpl(this);
                page.readPage(pageDir);
                m_pages.put(pageDir.getName(), page);
            }
        }
    }

    private void readObjs(File rootDir) throws NLBIOException, NLBConsistencyException {
        m_objs.clear();
        final File objsDir = new File(rootDir, OBJS_DIR_NAME);
        // objs dir can be nonexistent, in this case there is no objects in the book
        if (objsDir.exists()) {
            File[] objDirs = objsDir.listFiles();
            if (objDirs == null) {
                throw new NLBIOException("Error when enumerating objs' directory contents");
            }
            for (File objDir : objDirs) {
                final ObjImpl obj = new ObjImpl();
                obj.readObj(objDir);
                m_objs.put(objDir.getName(), obj);
            }
        }
    }

    private void readVariables(File rootDir) throws NLBIOException, NLBConsistencyException {
        m_variables.clear();
        final File varsDir = new File(rootDir, VARS_DIR_NAME);
        // vars dir can be nonexistent, in this case there is no variables in the book
        if (varsDir.exists()) {
            File[] varDirs = varsDir.listFiles();
            if (varDirs == null) {
                throw new NLBIOException("Error when enumerating vars' directory contents");
            }
            for (File varDir : varDirs) {
                final VariableImpl var = new VariableImpl();
                var.readVariable(varDir);
                m_variables.add(var);
            }
        }
    }

    public void save(FileManipulator fileManipulator)
            throws NLBIOException, NLBConsistencyException, NLBVCSException, NLBFileManipulationException {
        try {
            if (!m_rootDir.exists()) {
                if (!m_rootDir.mkdirs()) {
                    throw new NLBIOException("Cannot create NLB root directory");
                }
            }
            writeVariables(fileManipulator, m_rootDir);
            writePages(fileManipulator, m_rootDir);
            writeObjs(fileManipulator, m_rootDir);
            writeStartPoint(fileManipulator, m_rootDir);
        } catch (IOException e) {
            throw new NLBIOException("IO exception occurred", e);
        }
    }

    private void writePages(
            FileManipulator fileManipulator,
            File rootDir
    ) throws
            IOException,
            NLBIOException,
            NLBFileManipulationException,
            NLBVCSException,
            NLBConsistencyException {
        final File pagesDir = new File(rootDir, PAGES_DIR_NAME);
        fileManipulator.createDir(pagesDir, "Cannot create NLB pages directory");
        final List<String> deletedPagesIds = new ArrayList<>();
        for (PageImpl page : m_pages.values()) {
            page.writePage(fileManipulator, pagesDir, this);
            if (page.isDeleted()) {
                deletedPagesIds.add(page.getId());
            }
        }
        removeDeletedPages(deletedPagesIds);
    }

    private void writeObjs(
            FileManipulator fileManipulator,
            File rootDir
    ) throws IOException, NLBIOException, NLBFileManipulationException, NLBVCSException {
        final File objsDir = new File(rootDir, OBJS_DIR_NAME);
        fileManipulator.createDir(objsDir, "Cannot create NLB objs directory");
        final List<String> deletedObjsIds = new ArrayList<>();
        for (ObjImpl obj : m_objs.values()) {
            obj.writeObj(fileManipulator, objsDir, this);
            if (obj.isDeleted()) {
                deletedObjsIds.add(obj.getId());
            }
        }
        removeDeletedObjs(deletedObjsIds);
    }

    private void writeVariables(
            FileManipulator fileManipulator,
            File rootDir
    ) throws IOException, NLBIOException, NLBConsistencyException, NLBFileManipulationException, NLBVCSException {
        final File varsDir = new File(rootDir, VARS_DIR_NAME);
        fileManipulator.createDir(varsDir, "Cannot create NLB vars directory");
        final List<String> deletedVarsIds = new ArrayList<>();
        for (VariableImpl variable : m_variables) {
            preprocessVariable(variable);
            variable.writeVariable(fileManipulator, varsDir);
            if (variable.isDeleted()) {
                deletedVarsIds.add(variable.getId());
            }
        }
        removeDeletedVariables(deletedVarsIds);
    }

    private void preprocessVariable(final VariableImpl variable) throws NLBConsistencyException {
        if (variable.getType() == VariableImpl.Type.PAGE) {
            final PageImpl page = getPageImplById(variable.getTarget());
            if (variable.isDeleted()) {
                // page.getVarId() should be empty or set to another variable's Id
                if (variable.getId().equals(page.getVarId())) {
                    throw new NLBConsistencyException(
                            "Page variable for page with Id = "
                                    + page.getId()
                                    + " is incorrect, because the corresponding variable with Id = "
                                    + variable.getId()
                                    + " has been deleted"
                    );
                }
            }
            if (page.isDeleted()) {
                variable.setDeleted(true);
            }
        } else if (variable.getType() == VariableImpl.Type.OBJ) {
            final Obj obj = getObjById(variable.getTarget());
            if (variable.isDeleted()) {
                // obj.getVarId() should be empty or set to another variable's Id
                if (variable.getId().equals(obj.getVarId())) {
                    throw new NLBConsistencyException(
                            "Obj variable for obj with Id = "
                                    + obj.getId()
                                    + " is incorrect, because the corresponding variable with Id = "
                                    + variable.getId()
                                    + " has been deleted"
                    );
                }
            }
            if (obj.isDeleted()) {
                variable.setDeleted(true);
            }
        } else if (
                variable.getType() == VariableImpl.Type.LINK
                        || variable.getType() == VariableImpl.Type.LINKCONSTRAINT
                ) {
            // TODO: check in what circumstances getLinkWithCheck() can return null
            final Link link = getLinkWithCheck(variable);
            assert link != null;
            if (link.isDeleted()) {
                variable.setDeleted(true);
            }
        } else if (
                variable.getType() == VariableImpl.Type.VAR
                        || variable.getType() == VariableImpl.Type.EXPRESSION
                ) {
            final ModifyingItemAndModification itemAndModification = (
                    getModifyingItemAndModification(variable)
            );
            if (
                    itemAndModification.getModifyingItem().isDeleted()
                            || itemAndModification.getModification().isDeleted()
                    ) {
                variable.setDeleted(true);
            }
        } else if (variable.getType() == VariableImpl.Type.MODCONSTRAINT) {
            final Page page = getPageById(variable.getTarget());
            assert page != null;
            if (page.isDeleted()) {
                variable.setDeleted(true);
            }
        }
    }

    private Link getLinkWithCheck(VariableImpl variable) throws NLBConsistencyException {
        String[] ids = StringHelper.getItems(variable.getTarget());
        NodeItem nodeItem = getPageById(ids[0]);
        if (nodeItem == null) {
            nodeItem = getObjById(ids[0]);
        }
        final Link link = nodeItem.getLinkById(ids[1]);
        if (variable.isDeleted()) {
            if (variable.getType() == VariableImpl.Type.LINK) {
                // link.getVarId() should be empty or set to another variable's Id
                if (variable.getId().equals(link.getVarId())) {
                    throw new NLBConsistencyException(
                            "Link variable for link with full Id = " + variable.getTarget()
                                    + " is incorrect, because the corresponding variable with Id = "
                                    + variable.getId()
                                    + " has been deleted"
                    );
                }
            } else if (variable.getType() == VariableImpl.Type.LINKCONSTRAINT) {
                // link.getConstrId() should be empty or set to another variable's Id
                if (variable.getId().equals(link.getConstrId())) {
                    throw new NLBConsistencyException(
                            "Link constraint for link with full Id = " + variable.getTarget()
                                    + " is incorrect, because the corresponding variable with Id = "
                                    + variable.getId()
                                    + " has been deleted"
                    );
                }
            }
        }
        return link;
    }

    private ModifyingItemAndModification getModifyingItemAndModification(
            VariableImpl variable
    ) throws NLBConsistencyException {
        ModifyingItemAndModification result = new ModifyingItemAndModification();
        String[] ids = StringHelper.getItems(variable.getTarget());
        AbstractNodeItem nodeItem = getPageImplById(ids[0]);
        if (nodeItem == null) {
            nodeItem = getObjImplById(ids[0]);
        }
        if (nodeItem == null) {
            throw new NLBConsistencyException(
                    "Cannot find target page or obj with id = "
                            + ids[0]
                            + " for variable with id = "
                            + variable.getId()
            );
        }
        LinkImpl link;

        Modification modification;
        if (ids.length > 2) {
            if (nodeItem.isDeleted()) {
                throw new NLBConsistencyException(
                        "Node item with id = "
                                + ids[0]
                                + " is deleted and cannot be origin for link with id = "
                                + ids[1]
                                + " when checking variable with id = "
                                + variable.getId()
                );
            }
            link = nodeItem.getLinkById(ids[1]);
            if (link == null) {
                throw new NLBConsistencyException(
                        "Cannot find target link with id = "
                                + ids[1]
                                + " in page with id = "
                                + ids[0]
                                + " for variable with id = "
                                + variable.getId()
                );
            }
            modification = link.getModificationById(ids[2]);
            if (modification == null) {
                throw new NLBConsistencyException(
                        "Cannot find target modification with id = " + ids[2]
                                + " in page with id = " + ids[0]
                                + " and link with id = " + ids[1]
                                + " for variable with id = "
                                + variable.getId()
                );
            }

        } else {
            link = null;
            modification = nodeItem.getModificationById(ids[1]);
            if (modification == null) {
                throw new NLBConsistencyException(
                        "Cannot find target modification with id = " + ids[1]
                                + " in page with id = " + ids[0]
                                + " for variable with id = "
                                + variable.getId()
                );
            }
        }

        if (variable.isDeleted()) {
            if (variable.getType() == VariableImpl.Type.VAR) {
                // modification.getVarId() should be empty or set to another variable's Id
                if (variable.getId().equals(modification.getVarId())) {
                    throw new NLBConsistencyException(
                            "Modification variable for modification with full Id = " + variable.getTarget()
                                    + " is incorrect, because the corresponding variable with Id = "
                                    + variable.getId()
                                    + " has been deleted"
                    );
                }
            } else if (variable.getType() == VariableImpl.Type.EXPRESSION) {
                // modification.getExprId() should be empty or set to another variable's Id
                if (variable.getId().equals(modification.getExprId())) {
                    throw new NLBConsistencyException(
                            "Modification expression for modification with full Id = " + variable.getTarget()
                                    + " is incorrect, because the corresponding variable with Id = "
                                    + variable.getId()
                                    + " has been deleted"
                    );
                }
            }
        }
        result.setModifyingItem((link != null) ? link : nodeItem);
        result.setModification(modification);
        return result;
    }

    private void removeDeletedPages(List<String> pagesIds) {
        for (final String pageId : pagesIds) {
            m_pages.remove(pageId);
        }
    }

    private void removeDeletedObjs(List<String> objsIds) {
        for (final String objId : objsIds) {
            m_pages.remove(objId);
        }
    }

    private void removeDeletedVariables(List<String> varIds) {
        ListIterator<VariableImpl> variablesIterator = m_variables.listIterator();
        while (variablesIterator.hasNext()) {
            VariableImpl variable = variablesIterator.next();
            for (final String varId : varIds) {
                if (variable.getId().equals(varId)) {
                    variablesIterator.remove();
                }
            }
        }
    }

    private void writeStartPoint(
            final FileManipulator fileManipulator,
            final File rootDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        fileManipulator.writeOptionalString(rootDir, STARTPOINT_FILE_NAME, m_startPoint, DEFAULT_STARTPOINT);
    }

    public Variable getVariableById(String varId) {
        Variable result = getVariableImplById(varId);
        if (result != null) {
            return result;
        } else if (m_parentNLB != null) {
            return m_parentNLB.getVariableById(varId);
        } else {
            return null;
        }
    }

    private VariableImpl getVariableImplById(String varId) {
        if (!StringHelper.isEmpty(varId)) {
            for (final VariableImpl variable : m_variables) {
                if (variable.getId().equals(varId)) {
                    return variable;
                }
            }
        }
        return null;
    }

    @Override
    public SearchResultTableModel getLeafs(final String modulePageId) {
        SearchResultTableModel result = new SearchResultTableModel();
        for (Map.Entry<String, PageImpl> entry : m_pages.entrySet()) {
            final PageImpl page = entry.getValue();
            if (page.isLeaf()) {
                result.addSearchResult(
                        new SearchResult(page.getId(), modulePageId, page.getCaption())
                );
            }

            if (!page.getModule().isEmpty()) {
                result.addSearchResultTableModel(page.getModuleImpl().getLeafs(page.getId()));
            }
        }
        return result;
    }

    @Override
    public SearchResultTableModel searchText(
            final String modulePageId,
            final String searchText,
            final boolean searchInIds,
            final boolean searchInPages,
            final boolean searchInObjects,
            final boolean searchInLinks,
            final boolean searchInVars,
            final boolean ignoreCase,
            final boolean wholeWords
    ) {
        SearchResultTableModel result = new SearchResultTableModel();
        if (searchInPages) {
            for (Map.Entry<String, PageImpl> entry : m_pages.entrySet()) {
                final SearchResult pageResult;
                final SearchResult varResult;
                final PageImpl page = entry.getValue();
                if ((pageResult = page.searchText(searchText, searchInIds, ignoreCase, wholeWords)) != null) {
                    pageResult.setModulePageId(modulePageId);
                    result.addSearchResult(pageResult);
                } else {
                    final VariableImpl variable;
                    if (searchInVars && ((variable = getVariableImplById(page.getVarId())) != null)) {
                        varResult = variable.searchText(searchText, searchInIds, ignoreCase, wholeWords);
                        if (varResult != null) {
                            varResult.setId(page.getId());
                            varResult.setModulePageId(modulePageId);
                            result.addSearchResult(varResult);
                        }
                    }
                }
                final NonLinearBookImpl moduleImpl = page.getModuleImpl();
                if (!moduleImpl.isEmpty()) {
                    result.addSearchResultTableModel(
                            moduleImpl.searchText(
                                    page.getId(),
                                    searchText,
                                    searchInIds,
                                    searchInPages,
                                    searchInObjects,
                                    searchInLinks,
                                    searchInVars,
                                    ignoreCase,
                                    wholeWords
                            )
                    );
                }
                if (searchInLinks) {
                    searchLinks(
                            modulePageId,
                            page,
                            result,
                            searchText,
                            searchInIds,
                            searchInVars,
                            ignoreCase,
                            wholeWords
                    );
                }
            }
        }
        if (searchInObjects) {
            for (Map.Entry<String, ObjImpl> entry : m_objs.entrySet()) {
                final SearchResult objResult;
                final SearchResult varResult;
                final ObjImpl obj = entry.getValue();
                if ((objResult = obj.searchText(searchText, searchInIds, ignoreCase, wholeWords)) != null) {
                    objResult.setModulePageId(modulePageId);
                    result.addSearchResult(objResult);
                } else {
                    final VariableImpl variable;
                    if (searchInVars && ((variable = getVariableImplById(obj.getVarId())) != null)) {
                        varResult = variable.searchText(searchText, searchInIds, ignoreCase, wholeWords);
                        if (varResult != null) {
                            varResult.setId(obj.getId());
                            varResult.setModulePageId(modulePageId);
                            result.addSearchResult(varResult);
                        }
                    }
                }
                if (searchInLinks) {
                    searchLinks(
                            modulePageId,
                            obj,
                            result,
                            searchText,
                            searchInIds,
                            searchInVars,
                            ignoreCase,
                            wholeWords
                    );
                }
            }
        }
        return result;
    }

    @Override
    public SearchResultTableModel getVariables(
            final String modulePageId
    ) throws NLBConsistencyException {
        SearchResultTableModel result = new SearchResultTableModel("Type", "Name", "Value");
        for (VariableImpl variable : m_variables) {
            if (variable.isDeleted()) {
                continue;
            }
            SearchResult searchResult = new SearchResult();
            searchResult.setModulePageId(modulePageId);
            Variable.Type type = variable.getType();
            searchResult.addInformation(type.name());
            searchResult.addInformation(variable.getName());
            searchResult.addInformation(variable.getValue());
            switch (type) {
                case PAGE:
                    Page page = getPageById(variable.getTarget());
                    if (!page.isDeleted()) {
                        searchResult.setId(page.getId());
                        result.addSearchResult(searchResult);
                    }
                    break;
                case OBJ:
                    Obj obj = getObjById(variable.getTarget());
                    if (!obj.isDeleted()) {
                        searchResult.setId(obj.getId());
                        result.addSearchResult(searchResult);
                    }
                    break;
                case LINK:
                case LINKCONSTRAINT:
                    final Link link = getLinkWithCheck(variable);
                    if (!link.isDeleted()) {
                        searchResult.setId(link.getId());
                        result.addSearchResult(searchResult);
                    }
                    break;
                case VAR:
                case EXPRESSION:
                    final ModifyingItemAndModification itemAndModification = (
                            getModifyingItemAndModification(variable)
                    );
                    if (
                            !itemAndModification.getModifyingItem().isDeleted()
                                    && !itemAndModification.getModification().isDeleted()
                            ) {
                        searchResult.setId(itemAndModification.getModifyingItem().getId());
                        result.addSearchResult(searchResult);
                    }
                    break;
                case MODCONSTRAINT:
                    Page modulePage = getPageById(variable.getTarget());
                    if (!modulePage.isDeleted()) {
                        searchResult.setId(modulePage.getId());
                        result.addSearchResult(searchResult);
                    }
                    break;
                default:
                    // Do nothing
            }
        }
        for (Map.Entry<String, PageImpl> entry : m_pages.entrySet()) {
            final NonLinearBookImpl moduleImpl = entry.getValue().getModuleImpl();
            if (!moduleImpl.isEmpty()) {
                result.addSearchResultTableModel(
                        moduleImpl.getVariables(entry.getValue().getId())
                );
            }
        }
        return result;
    }

    @Override
    public SearchResultTableModel checkBook(
            final String modulePageId
    ) throws NLBConsistencyException {
        SearchResultTableModel result = new SearchResultTableModel("Type", "Value", "Problem");
        for (Map.Entry<String, PageImpl> entry : m_pages.entrySet()) {
            PageImpl page = entry.getValue();
            if (!StringHelper.isEmpty(page.getReturnPageId())) {
                SearchResult searchResult = new SearchResult();
                searchResult.setModulePageId(modulePageId);
                searchResult.addInformation("Return page");
                searchResult.addInformation(page.getReturnPageId());
                searchResult.setId(page.getId());
                if (m_parentNLB != null) {
                    // Do not using getPageById() here, because return page should be located
                    // in parent book ONLY
                    Page targetPage = m_parentNLB.getPages().get(page.getReturnPageId());
                    if (targetPage == null || targetPage.isDeleted()) {
                        searchResult.addInformation(
                                "Referenced page cannot be found in the parent book"
                        );
                        result.addSearchResult(searchResult);
                    }
                } else {
                    searchResult.addInformation("No parent book exists to return to");
                    result.addSearchResult(searchResult);
                }
            }
        }
        for (VariableImpl variable : m_variables) {
            if (variable.isDeleted()) {
                continue;
            }
            SearchResult searchResult = new SearchResult();
            searchResult.setModulePageId(modulePageId);
            Variable.Type type = variable.getType();
            searchResult.addInformation(type.name());
            searchResult.addInformation(variable.getValue());
            switch (type) {
                case LINKCONSTRAINT:
                    final Link link = getLinkWithCheck(variable);
                    if (!link.isDeleted()) {
                        final String error = checkFormula(variable.getValue().trim());
                        if (error != null) {
                            searchResult.addInformation(error);
                            searchResult.setId(link.getId());
                            result.addSearchResult(searchResult);
                        }
                    }
                    break;
                case EXPRESSION:
                    final ModifyingItemAndModification itemAndModification = (
                            getModifyingItemAndModification(variable)
                    );
                    if (
                            !itemAndModification.getModifyingItem().isDeleted()
                                    && !itemAndModification.getModification().isDeleted()
                            ) {
                        final String error = checkFormula(variable.getValue().trim());
                        if (error != null) {
                            searchResult.addInformation(error);
                            searchResult.setId(itemAndModification.getModifyingItem().getId());
                            result.addSearchResult(searchResult);
                        }
                    }
                    break;
                case MODCONSTRAINT:
                    Page modulePage = getPageById(variable.getTarget());
                    if (!modulePage.isDeleted()) {
                        final String error = checkFormula(variable.getValue().trim());
                        if (error != null) {
                            searchResult.addInformation(error);
                            searchResult.setId(modulePage.getId());
                            result.addSearchResult(searchResult);
                        }
                    }
                    break;
                default:
                    // Do nothing
            }
        }
        for (Map.Entry<String, PageImpl> entry : m_pages.entrySet()) {
            final NonLinearBookImpl moduleImpl = entry.getValue().getModuleImpl();
            if (!moduleImpl.isEmpty()) {
                result.addSearchResultTableModel(
                        moduleImpl.checkBook(entry.getValue().getId())
                );
            }
        }
        return result;
    }

    private int getCharacterCount() {
        int result = 0;
        for (Map.Entry<String, PageImpl> pageEntry : m_pages.entrySet()) {
            result += pageEntry.getValue().getText().length();
            for (LinkImpl link : pageEntry.getValue().getLinkImpls()) {
                result += link.getText().length();
            }
        }
        for (Map.Entry<String, ObjImpl> objEntry : m_objs.entrySet()) {
            result += objEntry.getValue().getText().length();
            for (LinkImpl link : objEntry.getValue().getLinkImpls()) {
                result += link.getText().length();
            }
        }
        return result;
    }

    @Override
    public BookStatistics getBookStatistics() {
        return getBookStatistics(0, false);
    }

    private BookStatistics getBookStatistics(int depth, boolean deletedModule) {
        BookStatistics result = new BookStatistics();
        result.incCharactersCount(getCharacterCount());
        int objsCount = 0;
        int pagesCount = 0;
        int uniqueEndingsCount = 0;
        for (Map.Entry<String, ObjImpl> objEntry : m_objs.entrySet()) {
            if (!objEntry.getValue().isDeleted()) {
                objsCount++;
            }
        }
        result.incObjsCount(objsCount);
        for (Map.Entry<String, PageImpl> pageEntry : m_pages.entrySet()) {
            final PageImpl page = pageEntry.getValue();
            if (!page.isDeleted() && !deletedModule) {
                pagesCount++;
                if (page.isLeaf()) {
                    uniqueEndingsCount++;
                }
                if (!page.getModule().isEmpty()) {
                    result.addModuleInfo(
                            new ModuleInfo(page.getId(), page.getModuleName(), depth)
                    );
                    result.addBookStatistics(
                            page.getModuleImpl().getBookStatistics(depth + 1, false)
                    );
                }
            } else {
                if (!page.getModule().isEmpty()) {
                    result.addModuleToBeDeletedInfo(
                            new ModuleInfo(page.getId(), page.getModuleName(), depth)
                    );
                    result.addDeletedModulesFromBookStatistics(
                            page.getModuleImpl().getBookStatistics(depth + 1, true)
                    );
                }
            }
        }
        result.incPagesCount(pagesCount);
        result.incUniqueEndings(uniqueEndingsCount);
        return result;
    }

    @Override
    public VariableStatistics getVariableStatistics() {
        VariableStatistics result = new VariableStatistics();
        for (VariableImpl variable : m_variables) {
            if (variable.isDeleted()) {
                continue;
            }
            Variable.Type type = variable.getType();
            switch (type) {
                case PAGE:
                    result.incPageVariablesCount();
                    break;
                case OBJ:
                    result.incObjVariablesCount();
                    break;
                case LINK:
                    result.incLinkVariablesCount();
                    break;
                case LINKCONSTRAINT:
                    result.incLinkConstraintVariablesCount();
                    break;
                case VAR:
                    result.incPlainVariablesCount();
                    break;
                case EXPRESSION:
                    result.incExpressionsCount();
                    break;
                case MODCONSTRAINT:
                    result.incModuleConstraintCount();
                    break;
                default:
                    // Do nothing
            }
        }
        return result;
    }

    @Override
    public NonLinearBook getParentNLB() {
        return m_parentNLB;
    }

    @Override
    public Map<String, Variable.DataType> getVariableDataTypes() throws NLBConsistencyException {
        Map<String, Variable.DataType> result = new HashMap<>();
        for (VariableImpl variable : m_variables) {
            if (variable.isDeleted()) {
                continue;
            }
            Variable.Type type = variable.getType();
            switch (type) {
                case PAGE:
                    Page page = getPageById(variable.getTarget());
                    if (!page.isDeleted()) {
                        result.put(variable.getName(), variable.getDataType());
                    }
                    break;
                case OBJ:
                    Obj obj = getObjById(variable.getTarget());
                    if (!obj.isDeleted()) {
                        result.put(variable.getName(), variable.getDataType());
                    }
                    break;
                case LINK:
                    final Link link = getLinkWithCheck(variable);
                    if (!link.isDeleted()) {
                        result.put(variable.getName(), variable.getDataType());
                    }
                    break;
                case VAR:
                    final ModifyingItemAndModification itemAndModification = (
                            getModifyingItemAndModification(variable)
                    );
                    if (
                            !itemAndModification.getModifyingItem().isDeleted()
                                    && !itemAndModification.getModification().isDeleted()
                            ) {
                        Variable modificationVariable = (
                                getVariableById(itemAndModification.getModification().getVarId())
                        );
                        result.put(modificationVariable.getName(), modificationVariable.getDataType());
                    }
                    break;
                case LINKCONSTRAINT:
                case EXPRESSION:
                case MODCONSTRAINT:
                default:
                    // Do nothing
            }
        }
        for (Map.Entry<String, PageImpl> entry : m_pages.entrySet()) {
            final NonLinearBookImpl moduleImpl = entry.getValue().getModuleImpl();
            if (!moduleImpl.isEmpty()) {
                result.putAll(
                        moduleImpl.getVariableDataTypes()
                );
            }
        }
        return result;
    }

    private NonLinearBook getMainNLB() {
        NonLinearBook result = this;
        while (result.getParentNLB() != null) {
            result = result.getParentNLB();
        }
        return result;
    }

    private String checkFormula(final String formula) throws NLBConsistencyException {
        final Collection<String> constraintVars;
        try {
            constraintVars = (
                    VarFinder.findVariableNames(formula)
            );
        } catch (Exception e) {
            return "Variable names cannot be extracted: " + e.getMessage();
        }
        for (String variableName : constraintVars) {
            boolean found = findVariable(variableName);
            if (found) {
                try {
                    // Finally try to evaluate the formula. Most probably syntax errors
                    // already has been handled via VarFinder.findVariableNames() call,
                    // so this is just a double check
                    ScriptEngineManager factory = new ScriptEngineManager();
                    // create a JavaScript engine
                    ScriptEngine engine = factory.getEngineByName("JavaScript");
                    for (String variableNameCur : constraintVars) {
                        engine.put(variableNameCur, false);
                    }
                    // evaluate JavaScript code from String
                    engine.eval(formula);
                } catch (ScriptException e) {
                    return "Formula cannot be evaluated: " + e.getMessage();
                }
            } else {
                return "Variable '" + variableName + "' was not found in NLB variables";
            }
        }
        return null;
    }

    @Override
    public boolean findVariable(String variableNameToFind) throws NLBConsistencyException {
        boolean found = false;
        for (VariableImpl variable : m_variables) {
            if (variable.isDeleted()) {
                continue;
            }
            if (variableNameToFind.equals(variable.getName())) {
                Variable.Type type = variable.getType();
                switch (type) {
                    case PAGE:
                        Page page = getPageById(variable.getTarget());
                        if (page.isDeleted()) {
                            continue;
                        } else {
                            found = true;
                        }
                        break;
                    case OBJ:
                        Obj obj = getObjById(variable.getTarget());
                        if (obj.isDeleted()) {
                            continue;
                        } else {
                            found = true;
                        }
                        break;
                    case LINK:
                        final Link link = getLinkWithCheck(variable);
                        if (link.isDeleted()) {
                            continue;
                        } else {
                            found = true;
                        }
                        break;
                    case VAR:
                        final ModifyingItemAndModification itemAndModification = (
                                getModifyingItemAndModification(variable)
                        );
                        if (
                                itemAndModification.getModifyingItem().isDeleted()
                                        || itemAndModification.getModification().isDeleted()
                                ) {
                            continue;
                        } else {
                            found = true;
                        }
                        break;
                    default:
                        // Do nothing
                }
            }
        }
        if (!found && m_parentNLB != null) {
            found = m_parentNLB.findVariable(variableNameToFind);
        }
        return found;
    }

    private void searchLinks(
            final String module,
            AbstractNodeItem nodeItem,
            SearchResultTableModel result,
            final String searchText,
            final boolean searchInIds,
            final boolean searchInVars,
            final boolean ignoreCase,
            final boolean wholeWords
    ) {
        for (LinkImpl link : nodeItem.getLinkImpls()) {
            final SearchResult linkResult;
            final SearchResult varResult;
            final SearchResult constraintsResult;
            if ((linkResult = link.searchText(searchText, searchInIds, ignoreCase, wholeWords)) != null) {
                linkResult.setModulePageId(module);
                result.addSearchResult(linkResult);
            } else {
                if (searchInVars) {
                    final VariableImpl variable = getVariableImplById(link.getVarId());
                    if (variable != null) {
                        varResult = variable.searchText(searchText, searchInIds, ignoreCase, wholeWords);
                    } else {
                        varResult = null;
                    }
                    if (varResult != null) {
                        varResult.setId(link.getId());
                        varResult.setModulePageId(module);
                        result.addSearchResult(varResult);
                    } else {
                        final VariableImpl constraint = getVariableImplById(link.getConstrId());
                        if (constraint != null) {
                            constraintsResult = (
                                    constraint.searchText(searchText, searchInIds, ignoreCase, wholeWords)
                            );
                        } else {
                            constraintsResult = null;
                        }
                        if (constraintsResult != null) {
                            constraintsResult.setId(link.getId());
                            constraintsResult.setModulePageId(module);
                            result.addSearchResult(constraintsResult);
                        }
                    }
                }
            }
        }
    }

    public void exportToQSPTextFile(final File targetFile) throws NLBExportException {
        ExportManager manager = new QSPExportManager(this, ExportManager.UTF_16LE);
        manager.exportToFile(targetFile);
    }

    public void exportToURQTextFile(final File targetFile) throws NLBExportException {
        ExportManager manager = new URQExportManager(this, ExportManager.CP1251);
        manager.exportToFile(targetFile);
    }

    public void exportToPDFFile(final File targetFile) throws NLBExportException {
        ExportManager manager = new PDFExportManager(this, ExportManager.CP1251);
        manager.exportToFile(targetFile);
    }

    public void exportToHTMLFile(final File targetFile) throws NLBExportException {
        ExportManager manager = new HTMLExportManager(this, ExportManager.UTF_8);
        manager.exportToFile(targetFile);
    }

    public void exportToJSIQFile(final File targetFile) throws NLBExportException {
        ExportManager manager = new JSIQ2ExportManager(this, ExportManager.UTF_8);
        manager.exportToFile(targetFile);
    }

    public void exportToSTEADFile(final File targetFile) throws NLBExportException {
        ExportManager manager = new STEADExportManager(this, ExportManager.UTF_8);
        manager.exportToFile(targetFile);
    }

    public void exportToASMFile(final File targetFile) throws NLBExportException {
        ExportManager manager = new ASMExportManager(this, ExportManager.UTF_8);
        manager.exportToFile(targetFile);
    }

    public void addVariable(@NotNull VariableImpl variable) {
        m_variables.add(variable);
    }
}
