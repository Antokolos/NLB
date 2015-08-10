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
import com.nlbhub.nlb.domain.export.hypertext.TaggedTextExportManager;
import com.nlbhub.nlb.domain.export.xml.JSIQ2ExportManager;
import com.nlbhub.nlb.exception.*;
import com.nlbhub.nlb.util.FileManipulator;
import com.nlbhub.nlb.util.MultiLangString;
import com.nlbhub.nlb.util.StringHelper;
import com.nlbhub.nlb.util.VarFinder;
import com.nlbhub.user.domain.DecisionPoint;
import com.nlbhub.user.domain.History;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The NonLinearBookImpl class represents the main storage for all Non-Linear Book entities.
 *
 * @author Anton P. Kolosov
 * @version 1.0 8/9/12
 */
public class NonLinearBookImpl implements NonLinearBook {
    private static final Pattern AUTOWIRED_OUT_PATTERN = (
            Pattern.compile(LC_VARID_PREFIX + "(.*)" + LC_VARID_SEPARATOR_OUT)
    );
    private static final String MEDIA_FILE_NAME_TEMPLATE = "%s_%d%s";
    private static final String CONSTRID_EXT = ".constrid";
    private static final String FLAG_EXT = ".flag";
    private static final String REDIRECT_EXT = ".redirect";
    private static final FilenameFilter NON_SPECIAL_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(final File dir, final String name) {
            return !name.endsWith(CONSTRID_EXT) && !name.endsWith(REDIRECT_EXT) && !name.endsWith(FLAG_EXT);
        }
    };
    private static final String STARTPOINT_FILE_NAME = "startpoint";
    private static final String LANGUAGE_FILE_NAME = "language";
    private static final String LICENSE_FILE_NAME = "license";
    private static final String FULLAUTO_FILE_NAME = "fullauto";
    private static final String SUPPRESS_MEDIA_FILE_NAME = "suppmed";
    private static final String SUPPRESS_SOUND_FILE_NAME = "suppsou";
    private static final String AUTHOR_FILE_NAME = "author";
    private static final String VERSION_FILE_NAME = "version";
    private static final String PAGES_DIR_NAME = "pages";
    private static final String OBJS_DIR_NAME = "objs";
    private static final String VARS_DIR_NAME = "vars";
    private static final String AUTOWIRED_PAGES_FILE_NAME = "autopgs";
    private static final String AUTOWIRED_SEPARATOR = "\n";
    private static final String DEFAULT_AUTOWIRED_PAGES = Constants.EMPTY_STRING;
    /**
     * Path to the directory on the disk where this book will be stored.
     */
    private File m_rootDir = null;
    /**
     * UUID of the start page in the pages list.
     */
    private String m_startPoint;
    private String m_language;
    private String m_license;
    private String m_author;
    private String m_version;
    private boolean m_fullAutowire;
    private boolean m_suppressMedia;
    private boolean m_suppressSound;
    private Map<String, PageImpl> m_pages;
    private List<String> m_autowiredPages;
    private Map<String, ObjImpl> m_objs;
    private List<VariableImpl> m_variables;
    private Set<MediaFileImpl> m_imageFiles;
    private Set<MediaFileImpl> m_soundFiles;
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

        public boolean existsAndValid() {
            return (
                    m_modifyingItem != null
                            && !m_modifyingItem.isDeleted()
                            && !m_modifyingItem.hasDeletedParent()
                            && m_modification != null
                            && !m_modification.isDeleted()
                            && !m_modification.hasDeletedParent()
            );
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
        private boolean m_autowired;
        private ChangeStartPointCommand m_changeStartPointCommand = null;

        private AddPageCommand(PageImpl page, boolean isAutowired) {
            m_page = page;
            m_autowired = isAutowired;
            // m_page.setDeleted(true);  Not fully exists for now, but don't do this, because it
            // affects page object immediately
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
            if (m_autowired) {
                addAutowiredPageId(m_page.getId());
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
            if (m_autowired) {
                removeAutowiredPageId(m_page.getId());
            }
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
                final NonLinearBook currentNLB,
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
                        currentNLB,
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
        private VariableTracker m_timerVariableTracker;
        private VariableTracker m_moduleConstrIdTracker;
        private VariableTracker m_autowireInConstrIdTracker;
        private VariableTracker m_autowireOutConstrIdTracker;

        private final String m_existingImageFileName;
        private final boolean m_existingImageBackground;
        private final String m_existingSoundFileName;
        private final boolean m_existingSoundSFX;
        private final MultiLangString m_existingPageText;
        private final MultiLangString m_existingPageCaptionText;
        private final boolean m_existingUseCaption;
        private final boolean m_existingUseMPL;
        private final String m_existingModuleName;
        private final MultiLangString m_existingTraverseText;
        private final MultiLangString m_existingReturnText;
        private final boolean m_existingAutoTraverse;
        private final boolean m_existingAutoReturn;
        private final String m_existingReturnPageId;
        private final boolean m_existingAutowire;
        private final MultiLangString m_existingAutowireInText;
        private final MultiLangString m_existingAutowireOutText;
        private final boolean m_existingAutoIn;
        private final boolean m_existingAutoOut;
        private final String m_newImageFileName;
        private final boolean m_newImageBackground;
        private final String m_newSoundFileName;
        private final boolean m_newSoundSFX;
        private final MultiLangString m_newPageText;
        private final MultiLangString m_newPageCaptionText;
        private final boolean m_newUseCaption;
        private final boolean m_newUseMPL;
        private final String m_newModuleName;
        private final MultiLangString m_newTraverseText;
        private final boolean m_newAutoTraverse;
        private final boolean m_newAutoReturn;
        private final MultiLangString m_newReturnText;
        private final String m_newReturnPageId;
        private final boolean m_newAutowire;
        private final MultiLangString m_newAutowireInText;
        private final MultiLangString m_newAutowireOutText;
        private final boolean m_newAutoIn;
        private final boolean m_newAutoOut;
        private AbstractNodeItem.SortLinksCommand m_sortLinkCommand;
        private List<AbstractNodeItem.DeleteLinkCommand> m_deleteLinkCommands = new ArrayList<>();

        private UpdatePageCommand(
                final NonLinearBook currentNLB,
                final Page page,
                final String imageFileName,
                final boolean imageBackground,
                final String soundFileName,
                final boolean soundSFX,
                final String pageVariableName,
                final String pageTimerVariableName,
                final MultiLangString pageText,
                final MultiLangString pageCaptionText,
                final boolean useCaption,
                final boolean useMPL,
                final String moduleName,
                final MultiLangString traverseText,
                final boolean autoTraverse,
                final boolean autoReturn,
                final MultiLangString returnText,
                final String returnPageId,
                final String moduleConsraintVariableBody,
                final boolean autowire,
                final MultiLangString autowireInText,
                final MultiLangString autowireOutText,
                final boolean autoIn,
                final boolean autoOut,
                final String autowireInConstraintVariableBody,
                final String autowireOutConstraintVariableBody,
                final LinksTableModel linksTableModel
        ) {
            this(
                    currentNLB,
                    getPageImplById(page.getId()),
                    imageFileName,
                    imageBackground,
                    soundFileName,
                    soundSFX,
                    pageVariableName,
                    pageTimerVariableName,
                    pageText,
                    pageCaptionText,
                    useCaption,
                    useMPL,
                    moduleName,
                    traverseText,
                    autoTraverse,
                    autoReturn,
                    returnText,
                    returnPageId,
                    moduleConsraintVariableBody,
                    autowire,
                    autowireInText,
                    autowireOutText,
                    autoIn,
                    autoOut,
                    autowireInConstraintVariableBody,
                    autowireOutConstraintVariableBody,
                    linksTableModel
            );
        }

        private UpdatePageCommand(
                final NonLinearBook currentNLB,
                final PageImpl page,
                final String imageFileName,
                final boolean imageBackground,
                final String soundFileName,
                final boolean soundSFX,
                final String pageVariableName,
                final String pageTimerVariableName,
                final MultiLangString pageText,
                final MultiLangString pageCaptionText,
                final boolean useCaption,
                final boolean useMPL,
                final String moduleName,
                final MultiLangString traverseText,
                final boolean autoTraverse,
                final boolean autoReturn,
                final MultiLangString returnText,
                final String returnPageId,
                final String moduleConsraintVariableBody,
                final boolean autowire,
                final MultiLangString autowireInText,
                final MultiLangString autowireOutText,
                final boolean autoIn,
                final boolean autoOut,
                final String autowireInConstraintVariableBody,
                final String autowireOutConstraintVariableBody,
                final LinksTableModel linksTableModel
        ) {
            m_page = page;
            m_variableTracker = new VariableTracker(
                    currentNLB,
                    getVariableImplById(m_page.getVarId()),
                    StringHelper.isEmpty(pageVariableName),
                    Variable.Type.PAGE,
                    Variable.DataType.BOOLEAN,
                    pageVariableName,
                    Variable.DEFAULT_VALUE,
                    m_page.getFullId()
            );
            m_timerVariableTracker = new VariableTracker(
                    currentNLB,
                    getVariableImplById(m_page.getTimerVarId()),
                    StringHelper.isEmpty(pageTimerVariableName),
                    Variable.Type.TIMER,
                    Variable.DataType.NUMBER,
                    pageTimerVariableName,
                    Variable.DEFAULT_VALUE,
                    m_page.getFullId()
            );
            m_moduleConstrIdTracker = new VariableTracker(
                    currentNLB,
                    getVariableImplById(m_page.getModuleConstrId()),
                    StringHelper.isEmpty(moduleConsraintVariableBody),
                    Variable.Type.MODCONSTRAINT,
                    Variable.DataType.BOOLEAN,
                    Variable.DEFAULT_NAME,
                    moduleConsraintVariableBody,
                    m_page.getFullId()
            );
            m_autowireInConstrIdTracker = new VariableTracker(
                    currentNLB,
                    getVariableImplById(m_page.getAutowireInConstrId()),
                    StringHelper.isEmpty(autowireInConstraintVariableBody),
                    Variable.Type.AUTOWIRECONSTRAINT,
                    Variable.DataType.BOOLEAN,
                    Variable.DEFAULT_NAME,
                    autowireInConstraintVariableBody,
                    m_page.getFullId()
            );
            m_autowireOutConstrIdTracker = new VariableTracker(
                    currentNLB,
                    getVariableImplById(m_page.getAutowireOutConstrId()),
                    StringHelper.isEmpty(autowireOutConstraintVariableBody),
                    Variable.Type.AUTOWIRECONSTRAINT,
                    Variable.DataType.BOOLEAN,
                    Variable.DEFAULT_NAME,
                    autowireOutConstraintVariableBody,
                    m_page.getFullId()
            );
            m_existingImageFileName = m_page.getImageFileName();
            m_existingImageBackground = m_page.isImageBackground();
            m_existingSoundFileName = m_page.getSoundFileName();
            m_existingSoundSFX = m_page.isSoundSFX();
            m_existingPageText = m_page.getTexts();
            m_existingPageCaptionText = m_page.getCaptions();
            m_existingUseCaption = m_page.isUseCaption();
            m_existingUseMPL = m_page.isUseMPL();
            m_existingModuleName = m_page.getModuleName();
            m_existingTraverseText = m_page.getTraverseTexts();
            m_existingAutoTraverse = m_page.isAutoTraverse();
            m_existingAutoReturn = m_page.isAutoReturn();
            m_existingReturnText = m_page.getReturnTexts();
            m_existingReturnPageId = m_page.getReturnPageId();
            m_existingAutowire = m_page.isAutowire();
            m_existingAutowireInText = m_page.getAutowireInTexts();
            m_existingAutowireOutText = m_page.getAutowireOutTexts();
            m_existingAutoIn = m_page.isAutoIn();
            m_existingAutoOut = m_page.isAutoOut();
            m_newImageFileName = imageFileName;
            m_newImageBackground = imageBackground;
            m_newSoundFileName = soundFileName;
            m_newSoundSFX = soundSFX;
            m_newPageText = pageText;
            m_newPageCaptionText = pageCaptionText;
            m_newUseCaption = useCaption;
            m_newUseMPL = useMPL;
            m_newModuleName = moduleName;
            m_newTraverseText = traverseText;
            m_newAutoTraverse = autoTraverse;
            m_newAutoReturn = autoReturn;
            m_newReturnText = returnText;
            m_newReturnPageId = returnPageId;
            m_newAutowire = autowire;
            m_newAutowireInText = autowireInText;
            m_newAutowireOutText = autowireOutText;
            m_newAutoIn = autoIn;
            m_newAutoOut = autoOut;
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
            m_page.setImageFileName(m_newImageFileName);
            m_page.setImageBackground(m_newImageBackground);
            m_page.setSoundFileName(m_newSoundFileName);
            m_page.setSoundSFX(m_newSoundSFX);
            m_page.setVarId(m_variableTracker.execute());
            m_page.setTimerVarId(m_timerVariableTracker.execute());
            m_page.setModuleConstrId(m_moduleConstrIdTracker.execute());
            m_page.setAutowireInConstrId(m_autowireInConstrIdTracker.execute());
            m_page.setAutowireOutConstrId(m_autowireOutConstrIdTracker.execute());
            m_page.setTexts(m_newPageText);
            m_page.setCaptions(m_newPageCaptionText);
            m_page.setUseCaption(m_newUseCaption);
            m_page.setUseMPL(m_newUseMPL);
            m_page.setModuleName(m_newModuleName);
            m_page.setTraverseTexts(m_newTraverseText);
            m_page.setAutoTraverse(m_newAutoTraverse);
            m_page.setAutoReturn(m_newAutoReturn);
            m_page.setReturnTexts(m_newReturnText);
            m_page.setReturnPageId(m_newReturnPageId);
            if (m_newAutowire) {
                addAutowiredPageId(m_page.getId());
            } else {
                // just does nothing if page was not in autowired list
                removeAutowiredPageId(m_page.getId());
            }
            m_page.setAutowireInTexts(m_newAutowireInText);
            m_page.setAutowireOutTexts(m_newAutowireOutText);
            m_page.setAutoIn(m_newAutoIn);
            m_page.setAutoOut(m_newAutoOut);
            m_page.notifyObservers();
        }

        @Override
        public void revert() {
            for (AbstractNodeItem.DeleteLinkCommand command : m_deleteLinkCommands) {
                command.revert();
            }
            m_sortLinkCommand.revert();
            m_page.setImageFileName(m_existingImageFileName);
            m_page.setImageBackground(m_existingImageBackground);
            m_page.setSoundFileName(m_existingSoundFileName);
            m_page.setSoundSFX(m_existingSoundSFX);
            m_page.setVarId(m_variableTracker.revert());
            m_page.setTimerVarId(m_timerVariableTracker.revert());
            m_page.setModuleConstrId(m_moduleConstrIdTracker.revert());
            m_page.setAutowireInConstrId(m_autowireInConstrIdTracker.revert());
            m_page.setAutowireOutConstrId(m_autowireOutConstrIdTracker.revert());
            m_page.setTexts(m_existingPageText);
            m_page.setCaptions(m_existingPageCaptionText);
            m_page.setUseCaption(m_existingUseCaption);
            m_page.setUseMPL(m_existingUseMPL);
            m_page.setModuleName(m_existingModuleName);
            m_page.setTraverseTexts(m_existingTraverseText);
            m_page.setAutoTraverse(m_existingAutoTraverse);
            m_page.setAutoReturn(m_existingAutoReturn);
            m_page.setReturnTexts(m_existingReturnText);
            m_page.setReturnPageId(m_existingReturnPageId);
            if (m_existingAutowire) {
                addAutowiredPageId(m_page.getId());
            } else {
                // just does nothing if page was not in autowired list
                removeAutowiredPageId(m_page.getId());
            }
            m_page.setAutowireInTexts(m_existingAutowireInText);
            m_page.setAutowireOutTexts(m_existingAutowireOutText);
            m_page.setAutoIn(m_existingAutoIn);
            m_page.setAutoOut(m_existingAutoOut);
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
        private VariableTracker m_constraintTracker;
        private VariableTracker m_commonToTracker;

        private String m_existingObjName;
        private String m_existingImageFileName;
        private String m_existingSoundFileName;
        private boolean m_existingSoundSFX;
        private boolean m_existingAnimatedImage;
        private boolean m_existingSuppressDsc;
        private MultiLangString m_existingObjDisp;
        private MultiLangString m_existingObjText;
        private MultiLangString m_existingObjActText;
        private boolean m_existingObjIsTakable;
        private boolean m_existingImageInScene;
        private boolean m_existingImageInInventory;
        private String m_newObjName;
        private String m_newImageFileName;
        private String m_newSoundFileName;
        private boolean m_newSoundSFX;
        private boolean m_newAnimatedImage;
        private boolean m_newSuppressDsc;
        private MultiLangString m_newObjDisp;
        private MultiLangString m_newObjText;
        private MultiLangString m_newObjActText;
        private boolean m_newObjIsTakable;
        private boolean m_newImageInScene;
        private boolean m_newImageInInventory;

        private UpdateObjCommand(
                final NonLinearBook currentNLB,
                final Obj obj,
                final String objVariableName,
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
                final boolean objIsTakable,
                final boolean imageInScene,
                final boolean imageInInventory
        ) {
            this(
                    currentNLB,
                    getObjImplById(obj.getId()),
                    objVariableName,
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
                    objIsTakable,
                    imageInScene,
                    imageInInventory
            );
        }

        private UpdateObjCommand(
                final NonLinearBook currentNLB,
                final ObjImpl obj,
                final String objVariableName,
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
                final boolean objIsTakable,
                final boolean imageInScene,
                final boolean imageInInventory
        ) {
            m_obj = obj;
            m_variableTracker = new VariableTracker(
                    currentNLB,
                    getVariableImplById(m_obj.getVarId()),
                    StringHelper.isEmpty(objVariableName),
                    Variable.Type.OBJ,
                    Variable.DataType.BOOLEAN,
                    objVariableName,
                    Variable.DEFAULT_VALUE,
                    m_obj.getFullId()
            );
            m_constraintTracker = new VariableTracker(
                    currentNLB,
                    getVariableImplById(m_obj.getConstrId()),
                    StringHelper.isEmpty(objConstraintValue),
                    Variable.Type.OBJCONSTRAINT,
                    Variable.DataType.BOOLEAN,
                    Variable.DEFAULT_NAME,
                    objConstraintValue,
                    m_obj.getFullId()
            );
            m_commonToTracker = new VariableTracker(
                    currentNLB,
                    getVariableImplById(m_obj.getCommonToId()),
                    StringHelper.isEmpty(objCommonToName),
                    Variable.Type.COMMONTO,
                    Variable.DataType.STRING,
                    objCommonToName,
                    findObjByName(objCommonToName).getId(),
                    m_obj.getFullId()
            );
            m_existingObjName = obj.getName();
            m_existingImageFileName = obj.getImageFileName();
            m_existingSoundFileName = obj.getSoundFileName();
            m_existingSoundSFX = obj.isSoundSFX();
            m_existingAnimatedImage = obj.isAnimatedImage();
            m_existingSuppressDsc = obj.isSuppressDsc();
            m_existingObjDisp = obj.getDisps();
            m_existingObjText = obj.getTexts();
            m_existingObjActText = obj.getActTexts();
            m_existingObjIsTakable = obj.isTakable();
            m_existingImageInScene = obj.isImageInScene();
            m_existingImageInInventory = obj.isImageInInventory();
            m_newObjName = objName;
            m_newImageFileName = imageFileName;
            m_newSoundFileName = soundFileName;
            m_newSoundSFX = soundSFX;
            m_newAnimatedImage = animatedImage;
            m_newSuppressDsc = suppressDsc;
            m_newObjDisp = objDisp;
            m_newObjText = objText;
            m_newObjActText = objActText;
            m_newObjIsTakable = objIsTakable;
            m_newImageInScene = imageInScene;
            m_newImageInInventory = imageInInventory;
        }

        @Override
        public void execute() {
            m_obj.setVarId(m_variableTracker.execute());
            m_obj.setConstrId(m_constraintTracker.execute());
            m_obj.setCommonToId(m_commonToTracker.execute());
            m_obj.setName(m_newObjName);
            m_obj.setImageFileName(m_newImageFileName);
            m_obj.setSoundFileName(m_newSoundFileName);
            m_obj.setSoundSFX(m_newSoundSFX);
            m_obj.setAnimatedImage(m_newAnimatedImage);
            m_obj.setSuppressDsc(m_newSuppressDsc);
            m_obj.setDisps(m_newObjDisp);
            m_obj.setTexts(m_newObjText);
            m_obj.setActTexts(m_newObjActText);
            m_obj.setTakable(m_newObjIsTakable);
            m_obj.setImageInScene(m_newImageInScene);
            m_obj.setImageInInventory(m_newImageInInventory);
            m_obj.notifyObservers();
        }

        @Override
        public void revert() {
            m_obj.setVarId(m_variableTracker.revert());
            m_obj.setConstrId(m_constraintTracker.revert());
            m_obj.setCommonToId(m_commonToTracker.revert());
            m_obj.setName(m_existingObjName);
            m_obj.setImageFileName(m_existingImageFileName);
            m_obj.setSoundFileName(m_existingSoundFileName);
            m_obj.setSoundSFX(m_existingSoundSFX);
            m_obj.setAnimatedImage(m_existingAnimatedImage);
            m_obj.setSuppressDsc(m_existingSuppressDsc);
            m_obj.setDisps(m_existingObjDisp);
            m_obj.setTexts(m_existingObjText);
            m_obj.setActTexts(m_existingObjActText);
            m_obj.setTakable(m_existingObjIsTakable);
            m_obj.setImageInScene(m_existingImageInScene);
            m_obj.setImageInInventory(m_existingImageInInventory);
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
        private LinkImpl m_link;
        private VariableTracker m_variableTracker;
        private VariableTracker m_constraintTracker;
        private MultiLangString m_newLinkText;
        private MultiLangString m_existingLinkText;
        private boolean m_existingAuto;
        private boolean m_newAuto;

        private UpdateLinkCommand(
                final NonLinearBook currentNLB,
                final Link link,
                final String linkVariableName,
                final String linkConstraintValue,
                final MultiLangString linkText,
                final boolean auto
        ) {
            IdentifiableItem parent = link.getParent();
            AbstractNodeItem nodeItem = getPageImplById(parent.getId());
            if (nodeItem == null) {
                nodeItem = getObjImplById(parent.getId());
            }
            init(
                    currentNLB,
                    nodeItem.getLinkById(link.getId()),
                    linkVariableName,
                    linkConstraintValue,
                    linkText,
                    auto
            );
        }

        private UpdateLinkCommand(
                final NonLinearBook currentNLB,
                final LinkImpl link,
                final String linkVariableName,
                final String linkConstraintValue,
                final MultiLangString linkText,
                final boolean auto
        ) {
            init(currentNLB, link, linkVariableName, linkConstraintValue, linkText, auto);
        }

        private void init(
                final NonLinearBook currentNLB,
                final LinkImpl link,
                final String linkVariableName,
                final String linkConstraintValue,
                final MultiLangString linkText,
                final boolean auto
        ) {
            m_link = link;
            m_variableTracker = new VariableTracker(
                    currentNLB,
                    getVariableImplById(m_link.getVarId()),
                    StringHelper.isEmpty(linkVariableName),
                    Variable.Type.LINK,
                    Variable.DataType.BOOLEAN,
                    linkVariableName,
                    Variable.DEFAULT_VALUE,
                    link.getFullId()
            );
            m_constraintTracker = new VariableTracker(
                    currentNLB,
                    getVariableImplById(m_link.getConstrId()),
                    StringHelper.isEmpty(linkConstraintValue),
                    Variable.Type.LINKCONSTRAINT,
                    Variable.DataType.BOOLEAN,
                    Variable.DEFAULT_NAME,
                    linkConstraintValue,
                    link.getFullId()
            );
            m_existingLinkText = link.getTexts();
            m_newLinkText = linkText;
            m_existingAuto = link.isAuto();
            m_newAuto = auto;
        }

        @Override
        public void execute() {
            m_link.setVarId(m_variableTracker.execute());
            m_link.setConstrId(m_constraintTracker.execute());
            m_link.setTexts(m_newLinkText);
            m_link.setAuto(m_newAuto);
            m_link.notifyObservers();
        }

        @Override
        public void revert() {
            m_link.setVarId(m_variableTracker.revert());
            m_link.setConstrId(m_constraintTracker.revert());
            m_link.setTexts(m_existingLinkText);
            m_link.setAuto(m_existingAuto);
            m_link.notifyObservers();
        }
    }

    class UpdateModificationsCommand implements NLBCommand {
        private AbstractModifyingItem m_item;
        private ModificationComparator m_initialComparator;
        private ModificationComparator m_modifiedComparator;
        private Map<String, ModificationImpl> m_modificationsToBeDeleted = new HashMap<>();
        private Map<String, Boolean> m_modificationsDeletionInitState = new HashMap<>();
        private Map<String, ModificationImpl> m_modificationsToBeReplaced = new HashMap<>();
        private Map<String, ModificationImpl> m_modificationsToBeReplacedPrev = new HashMap<>();
        private List<String> m_modificationsToBeAddedIdsInCorrectOrder = new ArrayList<>();
        private Map<String, ModificationImpl> m_modificationsToBeAdded = new HashMap<>();

        private Map<String, VariableImpl> m_variablesToBeReplaced = new HashMap<>();
        private Map<String, VariableImpl> m_variablesToBeReplacedPrev = new HashMap<>();
        private Map<String, VariableImpl> m_variablesToBeAdded = new HashMap<>();

        private class ModificationComparator implements Comparator<ModificationImpl> {
            private Map<String, Integer> m_indicesMap;

            public ModificationComparator(Map<String, Integer> indicesMap) {
                m_indicesMap = indicesMap;
            }

            @Override
            public int compare(ModificationImpl o1, ModificationImpl o2) {
                int idx1 = m_indicesMap.get(o1.getId());
                int idx2 = m_indicesMap.get(o2.getId());
                return idx1 - idx2;
            }
        }

        private UpdateModificationsCommand(
                final ModifyingItem modifyingItem,
                final ModificationsTableModel modificationsTableModel
        ) {
            init(getModifyingItemImpl(modifyingItem), modificationsTableModel);
        }

        private UpdateModificationsCommand(
                final AbstractModifyingItem modifyingItem,
                final ModificationsTableModel modificationsTableModel
        ) {
            init(modifyingItem, modificationsTableModel);
        }

        private void init(
                final AbstractModifyingItem modifyingItem,
                final ModificationsTableModel modificationsTableModel
        ) {
            Map<String, Integer> initialIndicesMap = new HashMap<>();
            Map<String, Integer> modifiedIndicesMap = new HashMap<>();
            // TODO: possibly inefficient code, please refactor
            m_item = modifyingItem;
            if (modifyingItem != null) {
                int initIdx = 0;
                for (ModificationImpl existingModification : m_item.getModificationImpls()) {
                    initialIndicesMap.put(existingModification.getId(), initIdx++);
                }
                int modifiedIdx = 0;
                for (Modification modification : modificationsTableModel.getModifications()) {
                    modifiedIndicesMap.put(modification.getId(), modifiedIdx++);
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
                                        new ModificationImpl(existingModification)
                                );
                            }
                            toBeAdded = false;
                        }
                    }
                    if (toBeAdded) {
                        m_modificationsToBeAddedIdsInCorrectOrder.add(modification.getId());
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
            m_initialComparator = new ModificationComparator(initialIndicesMap);
            m_modifiedComparator = new ModificationComparator(modifiedIndicesMap);
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

                for (String id : m_modificationsToBeAddedIdsInCorrectOrder) {
                    m_item.addModification(m_modificationsToBeAdded.get(id));
                }

                for (Map.Entry<String, VariableImpl> entry : m_variablesToBeReplaced.entrySet()) {
                    final VariableImpl existingVariable = getVariableImplById(entry.getKey());
                    final Variable variable = entry.getValue();
                    existingVariable.copy(variable);
                }

                for (Map.Entry<String, VariableImpl> entry : m_variablesToBeAdded.entrySet()) {
                    addVariable(entry.getValue());
                }
                Collections.sort(m_item.getModificationImpls(), m_modifiedComparator);
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
                Collections.sort(m_item.getModificationImpls(), m_initialComparator);
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
            // m_obj.setDeleted(true);   Not fully exists for now, but don't do this, because it
            // affects obj object immediately
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
        private AbstractNodeItem m_node;
        private AbstractNodeItem m_container;

        protected DeleteNodeCommand(
                final AbstractNodeItem node,
                final String containerId,
                final List<Link> adjacentLinks
        ) {
            m_node = node;
            m_container = getPageImplById(containerId);
            if (m_container == null) {
                m_container = getObjImplById(containerId);
            }
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
            if (m_container != null) {
                m_container.removeContainedObjId(m_node.getId());
            }
            for (String containedObjId : m_node.getContainedObjIds()) {
                ObjImpl objImpl = getObjImplById(containedObjId);
                objImpl.setContainerId(Constants.EMPTY_STRING);
                objImpl.notifyObservers();
            }
        }

        @Override
        public void revert() {
            for (LinkImpl link : m_links) {
                // Please note that link is considered deleted if its parent page is deleted
                link.setDeleted(m_linksDeletionStates.get(link.getId()));
                link.notifyObservers();
            }
            if (m_container != null) {
                m_container.addContainedObjId(m_node.getId());
            }
            for (String containedObjId : m_node.getContainedObjIds()) {
                ObjImpl objImpl = getObjImplById(containedObjId);
                objImpl.setContainerId(m_node.getId());
                objImpl.notifyObservers();
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
        private boolean m_isAutowired;
        private ChangeStartPointCommand m_changeStartPointCommand = null;

        private DeletePageCommand(PageImpl page, final List<Link> adjacentLinks) {
            super(page, Constants.EMPTY_STRING, adjacentLinks);
            m_page = page;
            m_isAutowired = isAutowired(page.getId());
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
            if (m_isAutowired) {
                removeAutowiredPageId(m_page.getId());
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
            if (m_isAutowired) {
                addAutowiredPageId(m_page.getId());
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
            super(obj, obj.getContainerId(), adjacentLinks);
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

    abstract class NotifyingCommand implements NLBCommand {
        protected void notifyAllChildren() {
            for (Page page : m_pages.values()) {
                page.notifyObservers();
                for (Link link : page.getLinks()) {
                    link.notifyObservers();
                }
            }
            for (Obj obj : m_objs.values()) {
                obj.notifyObservers();
                for (Link link : obj.getLinks()) {
                    link.notifyObservers();
                }
            }
        }
    }

    class UpdateBookPropertiesCommand extends NotifyingCommand {

        private final String m_prevLicense;
        private final String m_prevLanguage;
        private final String m_prevAuthor;
        private final String m_prevVersion;
        private final Boolean m_prevFullAutowire;
        private final Boolean m_prevSuppressMedia;
        private final Boolean m_prevSuppressSound;
        private final String m_newLicense;
        private final String m_newLanguage;
        private final String m_newAuthor;
        private final String m_newVersion;
        private final Boolean m_newFullAutowire;
        private final Boolean m_newSuppressMedia;
        private final Boolean m_newSuppressSound;
        private List<UpdateBookPropertiesCommand> m_submodulesCommands = new ArrayList<>();

        UpdateBookPropertiesCommand(
                final String license,
                final String language,
                final String author,
                final String version,
                final Boolean fullAutowire,
                final Boolean suppressMedia,
                final Boolean suppressSound,
                final boolean propagateToSubmodules
        ) {
            m_prevLicense = m_license;
            m_prevLanguage = m_language;
            m_prevAuthor = m_author;
            m_prevVersion = m_version;
            m_prevFullAutowire = m_fullAutowire;
            m_prevSuppressMedia = m_suppressMedia;
            m_prevSuppressSound = m_suppressSound;
            m_newLicense = license;
            m_newLanguage = language;
            m_newAuthor = author;
            m_newVersion = version;
            m_newFullAutowire = fullAutowire;
            m_newSuppressMedia = suppressMedia;
            m_newSuppressSound = suppressSound;
            if (propagateToSubmodules) {
                for (PageImpl page : m_pages.values()) {
                    NonLinearBookImpl moduleImpl = page.getModuleImpl();
                    if (!moduleImpl.isEmpty()) {
                        m_submodulesCommands.add(
                                moduleImpl.createUpdateBookPropertiesCommand(
                                        license,
                                        language,
                                        author,
                                        version,
                                        fullAutowire,
                                        suppressMedia,
                                        suppressSound,
                                        true
                                )
                        );
                    }
                }
            }
        }

        @Override
        public void execute() {
            if (m_newLicense != null) {
                m_license = m_newLicense;
            }
            if (m_newLanguage != null) {
                m_language = m_newLanguage;
            }
            if (m_newAuthor != null) {
                m_author = m_newAuthor;
            }
            if (m_newVersion != null) {
                m_version = m_newVersion;
            }
            if (m_newFullAutowire != null) {
                m_fullAutowire = m_newFullAutowire;
            }
            if (m_newSuppressMedia != null) {
                m_suppressMedia = m_newSuppressMedia;
            }
            if (m_newSuppressSound != null) {
                m_suppressSound = m_newSuppressSound;
            }
            for (UpdateBookPropertiesCommand command : m_submodulesCommands) {
                command.execute();
            }
            notifyAllChildren();
        }

        @Override
        public void revert() {
            m_license = m_prevLicense;
            m_language = m_prevLanguage;
            m_author = m_prevAuthor;
            m_version = m_prevVersion;
            m_fullAutowire = m_prevFullAutowire;
            m_suppressMedia = m_prevSuppressMedia;
            m_suppressSound = m_prevSuppressSound;
            for (UpdateBookPropertiesCommand command : m_submodulesCommands) {
                command.revert();
            }
            notifyAllChildren();
        }
    }

    class PasteCommand extends NotifyingCommand {
        private CommandChainCommand m_commandChain = new CommandChainCommand();

        PasteCommand(NonLinearBookImpl currentNLB, NonLinearBookImpl nlbToPaste) {
            Map<String, String> idsMapping = new HashMap<>();
            Map<String, PageImpl> newPages = new HashMap<>();
            Map<String, ObjImpl> newObjs = new HashMap<>();
            for (Map.Entry<String, PageImpl> entry : nlbToPaste.m_pages.entrySet()) {
                final PageImpl page = entry.getValue();
                Coords coords = page.getCoords();
                PageImpl newPage = new PageImpl(currentNLB, coords.getLeft() + coords.getWidth(), coords.getTop() + coords.getHeight());
                idsMapping.put(entry.getKey(), newPage.getId());
                newPages.put(newPage.getId(), newPage);
                AddPageCommand command = createAddPageCommand(newPage);
                m_commandChain.addCommand(command);
                if (!page.getModule().isEmpty()) {
                    PasteCommand pasteCommand = (
                            newPage.getModuleImpl().createPasteCommand(page.getModuleImpl())
                    );
                    m_commandChain.addCommand(pasteCommand);
                }
            }
            for (Map.Entry<String, ObjImpl> entry : nlbToPaste.m_objs.entrySet()) {
                Coords coords = entry.getValue().getCoords();
                ObjImpl newObj = new ObjImpl(currentNLB, coords.getLeft() + coords.getWidth(), coords.getTop() + coords.getHeight());
                idsMapping.put(entry.getKey(), newObj.getId());
                newObjs.put(newObj.getId(), newObj);
                AddObjCommand command = createAddObjCommand(newObj);
                m_commandChain.addCommand(command);
            }
            for (Map.Entry<String, PageImpl> entry : nlbToPaste.m_pages.entrySet()) {
                PageImpl page = entry.getValue();
                PageImpl newPage = newPages.get(idsMapping.get(entry.getKey()));
                final Variable pageVariable = nlbToPaste.getVariableById(page.getVarId());
                final Variable pageTimerVariable = nlbToPaste.getVariableById(page.getTimerVarId());
                final Variable modConstraint = nlbToPaste.getVariableById(page.getModuleConstrId());
                final Variable autoInConstraint = nlbToPaste.getVariableById(page.getAutowireInConstrId());
                final Variable autoOutConstraint = nlbToPaste.getVariableById(page.getAutowireOutConstrId());
                UpdatePageCommand updatePageCommand = new UpdatePageCommand(
                        currentNLB,
                        newPage,
                        page.getImageFileName(),
                        page.isImageBackground(),
                        page.getSoundFileName(),
                        page.isSoundSFX(),
                        (pageVariable != null) ? pageVariable.getName() : Constants.EMPTY_STRING,
                        (pageTimerVariable != null) ? pageTimerVariable.getName() : Constants.EMPTY_STRING,
                        page.getTexts(),
                        page.getCaptions(),
                        page.isUseCaption(),
                        page.isUseMPL(),
                        page.getModuleName(),
                        page.getTraverseTexts(),
                        page.isAutoTraverse(),
                        page.isAutoReturn(),
                        page.getReturnTexts(),
                        page.getReturnPageId(),
                        (modConstraint != null) ? modConstraint.getName() : Constants.EMPTY_STRING,
                        page.isAutowire(),
                        page.getAutowireInTexts(),
                        page.getAutowireOutTexts(),
                        page.isAutoIn(),
                        page.isAutoOut(),
                        (autoInConstraint != null) ? autoInConstraint.getValue() : Constants.EMPTY_STRING,
                        (autoOutConstraint != null) ? autoOutConstraint.getValue() : Constants.EMPTY_STRING,
                        new LinksTableModel(new ArrayList<Link>())
                );
                m_commandChain.addCommand(updatePageCommand);
                Coords coords = page.getCoords();
                Coords newCoords = newPage.getCoords();
                resizeNode(newPage, coords, newCoords);
                copyModifications(currentNLB, nlbToPaste, page, newPage);
                addLinks(currentNLB, nlbToPaste, idsMapping, page, newPage);
            }
            for (Map.Entry<String, ObjImpl> entry : nlbToPaste.m_objs.entrySet()) {
                ObjImpl obj = entry.getValue();
                ObjImpl newObj = newObjs.get(idsMapping.get(entry.getKey()));
                final Variable objVariable = nlbToPaste.getVariableById(obj.getVarId());
                final Variable objConstraint = nlbToPaste.getVariableById(obj.getConstrId());
                final Variable objCommonTo = nlbToPaste.getVariableById(obj.getCommonToId());
                UpdateObjCommand updateObjCommand = new UpdateObjCommand(
                        currentNLB,
                        newObj,
                        (objVariable != null) ? objVariable.getName() : Constants.EMPTY_STRING,
                        (objConstraint != null) ? objConstraint.getValue() : Constants.EMPTY_STRING,
                        (objCommonTo != null) ? objCommonTo.getName() : Constants.EMPTY_STRING,
                        obj.getName(),
                        obj.getImageFileName(),
                        obj.getSoundFileName(),
                        obj.isSoundSFX(),
                        obj.isAnimatedImage(),
                        obj.isSuppressDsc(),
                        obj.getDisps(),
                        obj.getTexts(),
                        obj.getActTexts(),
                        obj.isTakable(),
                        obj.isImageInScene(),
                        obj.isImageInInventory()
                );
                m_commandChain.addCommand(updateObjCommand);
                Coords coords = obj.getCoords();
                Coords newCoords = newObj.getCoords();
                resizeNode(newObj, coords, newCoords);
                copyModifications(currentNLB, nlbToPaste, obj, newObj);
                addLinks(currentNLB, nlbToPaste, idsMapping, obj, newObj);
            }
        }

        private void resizeNode(AbstractNodeItem nodeItem, Coords coords, Coords newCoords) {
            double deltaX = coords.getWidth() - newCoords.getWidth();
            double deltaY = coords.getHeight() - newCoords.getHeight();
            AbstractNodeItem.ResizeNodeCommand rightResizeCommand = (
                    nodeItem.createResizeNodeCommand(NodeItem.Orientation.RIGHT, deltaX, 0)
            );
            m_commandChain.addCommand(rightResizeCommand);
            AbstractNodeItem.ResizeNodeCommand bottomResizeCommand = (
                    nodeItem.createResizeNodeCommand(NodeItem.Orientation.BOTTOM, 0, deltaY)
            );
            m_commandChain.addCommand(bottomResizeCommand);
        }

        private void copyModifications(
                final NonLinearBookImpl currentNLB,
                final NonLinearBookImpl nlbToPaste,
                final AbstractModifyingItem existingItem,
                final AbstractModifyingItem newItem
        ) {
            ModificationsTableModel existingModel = (
                    new ModificationsTableModel(nlbToPaste, existingItem.getModifications())
            );
            ModificationsTableModel model = (
                    new ModificationsTableModel(currentNLB, new ArrayList<Modification>())
            );
            // Should copy data from existing modifications model
            int maxRow = existingModel.getRowCount();
            int maxCol = existingModel.getColumnCount();
            for (int row = 0; row < maxRow; row++) {
                model.add(newItem);
                // Please note, that we should copy from last column to the first, because
                // in other case type column will not be changed properly
                // (just like when editing manually)
                for (int col = maxCol - 1; col > 0; col--) {
                    final Object value = existingModel.getValueAt(row, col);
                    String text;
                    if (value instanceof Enum) {
                        text = ((Enum) value).name();
                    } else {
                        text = value.toString();
                    }
                    model.setValueAt(text, row, col);
                }
            }
            UpdateModificationsCommand command = new UpdateModificationsCommand(newItem, model);
            m_commandChain.addCommand(command);
        }

        private void addLinks(
                final NonLinearBookImpl currentNLB,
                final NonLinearBookImpl nlbToPaste,
                final Map<String, String> idsMapping,
                final AbstractNodeItem node,
                final AbstractNodeItem newNode
        ) {
            for (LinkImpl link : node.getLinkImpls()) {
                LinkImpl newLink = new LinkImpl(newNode, idsMapping.get(link.getTarget()));
                AbstractNodeItem.AddLinkCommand command = newNode.createAddLinkCommand(newLink);
                m_commandChain.addCommand(command);
                final Variable linkVariable = nlbToPaste.getVariableById(link.getVarId());
                final Variable linkConstraint = nlbToPaste.getVariableById(link.getConstrId());
                UpdateLinkCommand updateLinkCommand = new UpdateLinkCommand(
                        currentNLB,
                        newLink,
                        (linkVariable != null) ? linkVariable.getName() : Constants.EMPTY_STRING,
                        (linkConstraint != null) ? linkConstraint.getValue() : Constants.EMPTY_STRING,
                        link.getTexts(),
                        link.isAuto()
                );
                m_commandChain.addCommand(updateLinkCommand);
                copyModifications(currentNLB, nlbToPaste, link, newLink);
            }
        }

        @Override
        public void execute() {
            m_commandChain.execute();
            notifyAllChildren();
        }

        @Override
        public void revert() {
            m_commandChain.revert();
            notifyAllChildren();
        }
    }

    class CopyCommand implements NLBCommand {
        private NonLinearBookImpl m_prevClipboardData;
        private NonLinearBookImpl m_newClipboardData;

        /**
         * Please note: objIds collection will be processed as is, I.e. if it does not contain ids of some objects
         * contained in the pages or if it does not contain some parent objects, then so be it. In such cases copied
         * info will be corrected (missing object ids will be purged).
         *
         * @param pageIds
         * @param objIds
         */
        CopyCommand(final Collection<String> pageIds, final Collection<String> objIds) {
            m_prevClipboardData = Clipboard.singleton().getNonLinearBook();
            m_newClipboardData = new NonLinearBookImpl();
            processPages(pageIds, objIds);
            processObjs(objIds);
        }

        private void processObjs(Collection<String> objIds) {
            for (String objId : objIds) {
                ObjImpl existingObj = getObjImplById(objId);
                if (existingObj != null && !existingObj.isDeleted()) {
                    ObjImpl obj = new ObjImpl(existingObj);
                    m_newClipboardData.addObj(obj);
                    copyModificationVariables(obj, m_newClipboardData);
                    VariableImpl objVariable = getVariableImplById(obj.getVarId());
                    if (objVariable != null && !objVariable.isDeleted()) {
                        m_newClipboardData.addVariable(objVariable);
                    }
                    VariableImpl objConstraint = getVariableImplById(obj.getConstrId());
                    if (objConstraint != null && !objConstraint.isDeleted()) {
                        m_newClipboardData.addVariable(objConstraint);
                    }
                    VariableImpl objCommonTo = getVariableImplById(obj.getCommonToId());
                    if (objCommonTo != null && !objCommonTo.isDeleted()) {
                        m_newClipboardData.addVariable(objCommonTo);
                    }

                    checkContainedObjects(obj, objIds);
                    copyLinks(obj, objIds, m_newClipboardData);
                    if (!objIds.contains(obj.getContainerId())) {
                        obj.setContainerId(Obj.DEFAULT_CONTAINER_ID);
                    }
                }
            }
        }

        private void processPages(Collection<String> pageIds, Collection<String> objIds) {
            for (String pageId : pageIds) {
                PageImpl existingPage = getPageImplById(pageId);
                if (existingPage != null && !existingPage.isDeleted()) {
                    PageImpl page = new PageImpl(existingPage);
                    m_newClipboardData.addPage(page);
                    copyModificationVariables(page, m_newClipboardData);
                    // also we should move all related variables to the new NLB
                    VariableImpl autowireInConstraint = getVariableImplById(page.getAutowireInConstrId());
                    VariableImpl autowireOutConstraint = getVariableImplById(page.getAutowireOutConstrId());
                    VariableImpl moduleConstraint = getVariableImplById(page.getModuleConstrId());
                    VariableImpl pageVariable = getVariableImplById(page.getVarId());
                    VariableImpl pageTimerVariable = getVariableImplById(page.getTimerVarId());
                    if (autowireInConstraint != null && !autowireInConstraint.isDeleted()) {
                        m_newClipboardData.addVariable(
                                new VariableImpl(autowireInConstraint)
                        );
                    }
                    if (autowireOutConstraint != null && !autowireOutConstraint.isDeleted()) {
                        m_newClipboardData.addVariable(
                                new VariableImpl(autowireOutConstraint)
                        );
                    }
                    if (moduleConstraint != null && !moduleConstraint.isDeleted()) {
                        m_newClipboardData.addVariable(
                                new VariableImpl(moduleConstraint)
                        );
                    }
                    if (pageVariable != null && !pageVariable.isDeleted()) {
                        m_newClipboardData.addVariable(
                                new VariableImpl(pageVariable)
                        );
                    }
                    if (pageTimerVariable != null && !pageTimerVariable.isDeleted()) {
                        m_newClipboardData.addVariable(
                                new VariableImpl(pageTimerVariable)
                        );
                    }
                    checkContainedObjects(page, objIds);
                    copyLinks(page, pageIds, m_newClipboardData);
                }
            }
        }

        private void checkContainedObjects(
                final AbstractNodeItem nodeItem,
                final Collection<String> objIds
        ) {
            // if some containing objects is not listed in objIds, remove them
            List<String> objIdsToRemove = new ArrayList<>();
            for (String containedObjId : nodeItem.getContainedObjIds()) {
                if (!objIds.contains(containedObjId)) {
                    objIdsToRemove.add(containedObjId);
                }
            }
            for (String objIdToRemove : objIdsToRemove) {
                nodeItem.removeContainedObjId(objIdToRemove);
            }
        }

        private void copyLinks(
                final AbstractNodeItem nodeItem,
                final Collection<String> itemIds,
                final NonLinearBookImpl target
        ) {
            // Links will be moved automatically because they are contained inside nodes.
            // One nasty thing: we should move related variables too, including variables inside modifications.
            // Also please note that links pointing to items which is not inside itemIds list will be broken.
            // That's why we purge such malformed links. In this case we can use dangerous removeLinkById() method,
            // because all this links exist only in memory for now.
            List<String> malformedLinksIds = new ArrayList<>();
            for (LinkImpl link : nodeItem.getLinkImpls()) {
                if (!link.isDeleted() && itemIds.contains(link.getTarget())) {
                    copyModificationVariables(link, target);
                    VariableImpl linkVariable = getVariableImplById(link.getVarId());
                    VariableImpl linkConstraint = getVariableImplById(link.getConstrId());
                    if (linkVariable != null && !linkVariable.isDeleted()) {
                        target.addVariable(
                                new VariableImpl(linkVariable)
                        );
                    }
                    if (linkConstraint != null && !linkConstraint.isDeleted()) {
                        target.addVariable(
                                new VariableImpl(linkConstraint)
                        );
                    }
                } else {
                    malformedLinksIds.add(link.getId());
                }
            }
            for (String linkId : malformedLinksIds) {
                nodeItem.removeLinkById(linkId);
            }
        }

        private void copyModificationVariables(
                final AbstractModifyingItem modifyingItem,
                final NonLinearBookImpl target
        ) {
            for (ModificationImpl modification : modifyingItem.getModificationImpls()) {
                if (!modification.isDeleted()) {
                    VariableImpl modificationVariable = getVariableImplById(modification.getVarId());
                    VariableImpl modificationExpression = getVariableImplById(modification.getExprId());
                    if (modificationVariable != null && !modificationVariable.isDeleted()) {
                        target.addVariable(
                                new VariableImpl(modificationVariable)
                        );
                    }
                    if (modificationExpression != null && !modificationExpression.isDeleted()) {
                        target.addVariable(
                                new VariableImpl(modificationExpression)
                        );
                    }
                }
            }
        }

        @Override
        public void execute() {
            Clipboard.singleton().setNonLinearBook(m_newClipboardData);
        }

        @Override
        public void revert() {
            Clipboard.singleton().setNonLinearBook(m_prevClipboardData);
        }
    }

    class DeleteCommand extends NotifyingCommand {
        private CommandChainCommand m_deletionCommandChain = new CommandChainCommand();

        DeleteCommand(final Collection<String> pageIds, final Collection<String> objIds) {
            for (String pageId : pageIds) {
                PageImpl existingPage = getPageImplById(pageId);
                if (existingPage != null && !existingPage.isDeleted()) {
                    List<Link> adjacentLinks = getAssociatedLinks(existingPage);
                    m_deletionCommandChain.addCommand(new DeletePageCommand(existingPage, adjacentLinks));
                }
            }
            for (String objId : objIds) {
                ObjImpl existingObj = getObjImplById(objId);
                if (existingObj != null && !existingObj.isDeleted()) {
                    List<Link> adjacentLinks = getAssociatedLinks(existingObj);
                    m_deletionCommandChain.addCommand(new DeleteObjCommand(existingObj, adjacentLinks));
                }
            }
        }

        @Override
        public void execute() {
            m_deletionCommandChain.execute();
            notifyAllChildren();
        }

        @Override
        public void revert() {
            m_deletionCommandChain.revert();
            notifyAllChildren();
        }
    }

    public NonLinearBookImpl() {
        m_parentNLB = null;
        m_language = DEFAULT_LANGUAGE;
        m_license = DEFAULT_LICENSE;
        m_fullAutowire = DEFAULT_FULL_AUTOWIRE;
        m_suppressMedia = DEFAULT_SUPPRESS_MEDIA;
        m_suppressSound = DEFAULT_SUPPRESS_SOUND;
        m_author = DEFAULT_AUTHOR;
        m_version = DEFAULT_VERSION;
        m_parentPage = null;
        m_pages = new HashMap<>();
        m_autowiredPages = new ArrayList<>();
        m_objs = new HashMap<>();
        m_variables = new ArrayList<>();
        m_imageFiles = new TreeSet<>();
        m_soundFiles = new TreeSet<>();
    }

    public NonLinearBookImpl(NonLinearBook parentNLB, Page parentPage) {
        m_parentNLB = parentNLB;
        m_language = parentNLB.getLanguage();
        m_license = parentNLB.getLicense();
        m_fullAutowire = parentNLB.isFullAutowire();
        m_suppressMedia = parentNLB.isSuppressMedia();
        m_suppressSound = parentNLB.isSuppressSound();
        m_author = parentNLB.getAuthor();
        m_version = parentNLB.getVersion();
        m_parentPage = parentPage;
        m_pages = new HashMap<>();
        m_autowiredPages = new ArrayList<>();
        m_objs = new HashMap<>();
        m_variables = new ArrayList<>();
        m_imageFiles = new TreeSet<>();
        m_soundFiles = new TreeSet<>();
    }

    ChangeStartPointCommand createChangeStartPointCommand(final String startPoint) {
        return new ChangeStartPointCommand(startPoint);
    }

    AddPageCommand createAddPageCommand(final PageImpl page) {
        // In current implementation newly added pages is NOT autowired
        return new AddPageCommand(page, false);
    }

    UpdatePageCommand createUpdatePageCommand(
            final Page page,
            final String imageFileName,
            final boolean imageBackground,
            final String soundFileName,
            final boolean soundSFX,
            final String pageVariableName,
            final String pageTimerVariableName,
            final MultiLangString pageText,
            final MultiLangString pageCaptionText,
            final boolean useCaption,
            final boolean useMPL,
            final String moduleName,
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
            final boolean autoOut,
            final String autowireInConstraint,
            final String autowireOutConstraint,
            final LinksTableModel linksTableModel
    ) {
        return (
                new UpdatePageCommand(
                        this,
                        page,
                        imageFileName,
                        imageBackground,
                        soundFileName,
                        soundSFX,
                        pageVariableName,
                        pageTimerVariableName,
                        pageText,
                        pageCaptionText,
                        useCaption,
                        useMPL,
                        moduleName,
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
                        autoOut,
                        autowireInConstraint,
                        autowireOutConstraint,
                        linksTableModel
                )
        );
    }

    UpdateObjCommand createUpdateObjCommand(
            final Obj obj,
            final String objVariableName,
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
            final boolean objIsTakable,
            final boolean imageInScene,
            final boolean imageInInventory
    ) {
        return (
                new UpdateObjCommand(
                        this,
                        obj,
                        objVariableName,
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
                        objIsTakable,
                        imageInScene,
                        imageInInventory
                )
        );
    }

    UpdateLinkCommand createUpdateLinkCommand(
            final Link link,
            final String linkVariableName,
            final String linkConstraintValue,
            final MultiLangString linkText,
            final boolean auto
    ) {
        return new UpdateLinkCommand(this, link, linkVariableName, linkConstraintValue, linkText, auto);
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

    UpdateBookPropertiesCommand createUpdateBookPropertiesCommand(
            final String license,
            final String language,
            final String author,
            final String version,
            final Boolean fullAutowire,
            final Boolean suppressMedia,
            final Boolean suppressSound,
            final boolean propagateToSubmodules
    ) {
        // We are updating only properties which are actually changed.
        // Thus we can, for example, change language in book and all its modules, leaving
        // other properties intact.
        return new UpdateBookPropertiesCommand(
                m_license.equals(license) ? null : license,
                m_language.equals(language) ? null : language,
                m_author.equals(author) ? null : author,
                m_version.equals(version) ? null : version,
                ((fullAutowire != null) && (m_fullAutowire == fullAutowire)) ? null : fullAutowire,
                ((suppressMedia != null) && (m_suppressMedia == suppressMedia)) ? null : suppressMedia,
                ((suppressSound != null) && (m_suppressSound == suppressSound)) ? null : suppressSound,
                propagateToSubmodules
        );
    }

    CopyCommand createCopyCommand(
            final Collection<String> pageIds,
            final Collection<String> objIds
    ) {
        return new CopyCommand(pageIds, objIds);
    }

    DeleteCommand createDeleteCommand(
            final Collection<String> pageIds,
            final Collection<String> objIds
    ) {
        return new DeleteCommand(pageIds, objIds);
    }

    PasteCommand createPasteCommand(final NonLinearBookImpl nlbToPaste) {
        return new PasteCommand(this, nlbToPaste);
    }

    /**
     * Appends items to this book which are contained in operand (with overwrite if needed)
     *
     * @param operand
     */
    public void append(final NonLinearBookImpl operand) {
        for (Map.Entry<String, PageImpl> entry : operand.m_pages.entrySet()) {
            PageImpl operandPage = entry.getValue();
            PageImpl newPage = new PageImpl(operandPage);
            m_pages.put(entry.getKey(), newPage);
            if (operand.isAutowired(entry.getKey())) {
                addAutowiredPageId(entry.getKey());
            }
        }

        for (Map.Entry<String, ObjImpl> entry : operand.m_objs.entrySet()) {
            m_objs.put(entry.getKey(), new ObjImpl(entry.getValue()));
        }

        // first, remove variables with the same ids
        Iterator<VariableImpl> iterator = m_variables.iterator();
        while (iterator.hasNext()) {
            VariableImpl existingVariable = iterator.next();
            for (Variable variable : operand.getVariables()) {
                if (variable.getId().equals(existingVariable.getId())) {
                    iterator.remove();
                    break;
                }
            }
        }
        // second, add copies of operand variables
        for (VariableImpl variable : operand.m_variables) {
            m_variables.add(new VariableImpl(variable));
        }
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
        m_language = (m_parentNLB != null) ? m_parentNLB.getLanguage() : DEFAULT_LANGUAGE;
        m_license = (m_parentNLB != null) ? m_parentNLB.getLicense() : DEFAULT_LICENSE;
        m_author = (m_parentNLB != null) ? m_parentNLB.getAuthor() : DEFAULT_AUTHOR;
        m_version = (m_parentNLB != null) ? m_parentNLB.getVersion() : DEFAULT_VERSION;
        m_fullAutowire = (m_parentNLB != null) ? m_parentNLB.isFullAutowire() : DEFAULT_FULL_AUTOWIRE;
        m_suppressMedia = (m_parentNLB != null) ? m_parentNLB.isSuppressMedia() : DEFAULT_SUPPRESS_MEDIA;
        m_suppressSound = (m_parentNLB != null) ? m_parentNLB.isSuppressSound() : DEFAULT_SUPPRESS_SOUND;
        m_rootDir = null;
    }

    private Obj findObjByName(String objName) {
        if (objName == null) {
            return NullObj.create();
        }
        for (ObjImpl obj : m_objs.values()) {
            if (objName.equals(obj.getName())) {
                return obj;
            }
        }
        return NullObj.create();
    }

    @Override
    public boolean isEmpty() {
        return m_pages.isEmpty() && m_objs.isEmpty() && m_variables.isEmpty();
    }

    public String getStartPoint() {
        return m_startPoint;
    }

    @Override
    public String getLanguage() {
        return m_language;
    }

    @Override
    public String getLicense() {
        return m_license;
    }

    @Override
    public boolean isFullAutowire() {
        return m_fullAutowire;
    }

    @Override
    public boolean isSuppressMedia() {
        return m_suppressMedia;
    }

    @Override
    public boolean isSuppressSound() {
        return m_suppressSound;
    }

    @Override
    public String getAuthor() {
        return m_author;
    }

    @Override
    public String getVersion() {
        return m_version;
    }

    public void setStartPoint(String startPoint) {
        m_startPoint = startPoint;
    }

    public File getRootDir() {
        return m_rootDir;
    }

    @Override
    public File getImagesDir() {
        return (m_rootDir == null) ? null : new File(m_rootDir, IMAGES_DIR_NAME);
    }

    @Override
    public Set<String> getUsedImages() {
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, PageImpl> pageEntry : m_pages.entrySet()) {
            PageImpl page = pageEntry.getValue();
            result.addAll(getUsedMedia(page.getImageFileName()));
            NonLinearBook module = page.getModule();
            if (!module.isEmpty()) {
                result.addAll(module.getUsedImages());
            }
        }
        for (Map.Entry<String, ObjImpl> objEntry : m_objs.entrySet()) {
            ObjImpl obj = objEntry.getValue();
            result.addAll(getUsedMedia(obj.getImageFileName()));
        }
        return result;
    }

    @Override
    public Set<String> getUsedSounds() {
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, PageImpl> pageEntry : m_pages.entrySet()) {
            PageImpl page = pageEntry.getValue();
            result.addAll(getUsedMedia(page.getSoundFileName()));
            NonLinearBook module = page.getModule();
            if (!module.isEmpty()) {
                result.addAll(module.getUsedSounds());
            }
        }
        for (Map.Entry<String, ObjImpl> objEntry : m_objs.entrySet()) {
            ObjImpl obj = objEntry.getValue();
            result.addAll(getUsedMedia(obj.getSoundFileName()));
        }
        return result;
    }

    private Set<String> getUsedMedia(String fileNamesStr) {
        Set<String> result = new HashSet<>();
        if (StringHelper.notEmpty(fileNamesStr)) {
            String[] fileNames = fileNamesStr.split(Constants.MEDIA_FILE_NAME_SEP);
            Collections.addAll(result, fileNames);
        }
        return result;
    }

    @Override
    public List<MediaFile> getImageFiles() {
        List<MediaFile> imageFiles = new ArrayList<>();
        imageFiles.addAll(m_imageFiles);
        return imageFiles;
    }

    @Override
    public List<MediaFile> getSoundFiles() {
        List<MediaFile> soundFiles = new ArrayList<>();
        soundFiles.addAll(m_soundFiles);
        return soundFiles;
    }

    public void setMediaFileConstrId(final MediaFile.Type mediaType, final String fileName, final String constrId) {
        switch (mediaType) {
            case Image:
                for (MediaFileImpl mediaFile : m_imageFiles) {
                    if (mediaFile.getFileName().equals(fileName)) {
                        mediaFile.setConstrId(constrId);
                        break;
                    }
                }
                break;
            case Sound:
                for (MediaFileImpl mediaFile : m_soundFiles) {
                    if (mediaFile.getFileName().equals(fileName)) {
                        mediaFile.setConstrId(constrId);
                        break;
                    }
                }
                break;
        }
    }

    public void setMediaFileRedirect(final MediaFile.Type mediaType, final String fileName, final String redirect) {
        switch (mediaType) {
            case Image:
                for (MediaFileImpl mediaFile : m_imageFiles) {
                    if (mediaFile.getFileName().equals(fileName)) {
                        mediaFile.setRedirect(redirect);
                        break;
                    }
                }
                break;
            case Sound:
                for (MediaFileImpl mediaFile : m_soundFiles) {
                    if (mediaFile.getFileName().equals(fileName)) {
                        mediaFile.setRedirect(redirect);
                        break;
                    }
                }
                break;
        }
    }

    public void setMediaFileFlag(final MediaFile.Type mediaType, final String fileName, final boolean flag) {
        switch (mediaType) {
            case Image:
                for (MediaFileImpl mediaFile : m_imageFiles) {
                    if (mediaFile.getFileName().equals(fileName)) {
                        mediaFile.setFlagged(flag);
                        break;
                    }
                }
                break;
            case Sound:
                for (MediaFileImpl mediaFile : m_soundFiles) {
                    if (mediaFile.getFileName().equals(fileName)) {
                        mediaFile.setFlagged(flag);
                        break;
                    }
                }
                break;
        }
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

    @Override
    public List<String> getAutowiredPagesIds() {
        return m_autowiredPages;
    }

    public void addPage(@NotNull PageImpl page) {
        m_pages.put(page.getId(), page);
    }

    public void addAutowiredPageId(final String pageId) {
        for (String existingId : m_autowiredPages) {
            if (existingId.equals(pageId)) {
                // already exists
                return;
            }
        }
        m_autowiredPages.add(pageId);
    }

    public void removeAutowiredPageId(final String pageId) {
        Iterator<String> iterator = m_autowiredPages.listIterator();
        while (iterator.hasNext()) {
            String existingId = iterator.next();
            if (existingId.equals(pageId)) {
                iterator.remove();
                return;
            }
        }
    }

    public boolean isAutowired(final String pageId) {
        return m_autowiredPages.contains(pageId);
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
    ) throws ScriptException, NLBConsistencyException {
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
                            source.getTraverseTexts(),
                            source.getModuleConstrId(),
                            Constants.EMPTY_STRING,
                            source.isAutoTraverse(),
                            true,
                            false,
                            false,
                            null
                    )
            );
            if (!determineLinkExcludedStatus(factory, visitedVars, link)) {
                linksToBeAdded.add(link);
            }
        }
        if (m_parentNLB != null && m_parentPage != null && source.shouldReturn()) {
            if (source.isUseMPL()) {
                for (Link link : m_parentPage.getLinks()) {
                    Link linklw = (
                            new LinkLw(
                                    LinkLw.Type.Return,
                                    link.getTarget(),
                                    source,
                                    link.getTexts(),
                                    link.getConstrId(),
                                    link.getVarId(),
                                    link.isAuto(),
                                    link.isPositiveConstraint(),
                                    false,
                                    true,
                                    link.getModifications()
                            )
                    );
                    if (!determineLinkExcludedStatus(factory, visitedVars, linklw)) {
                        linksToBeAdded.add(linklw);
                    }
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
                                StringHelper.isEmpty(source.getReturnPageId())
                                        ? m_parentPage.getId()
                                        : source.getReturnPageId(),
                                source,
                                source.getReturnTexts(),
                                m_parentPage.getModuleConstrId(),
                                Constants.EMPTY_STRING,
                                source.isAutoReturn(),
                                StringHelper.isEmpty(m_parentPage.getModuleConstrId()),
                                false,
                                false,
                                null
                        )
                );
                if (!determineLinkExcludedStatus(factory, visitedVars, link)) {
                    linksToBeAdded.add(link);
                }
            }
        }
        return source.createFilteredCloneWithSubstitutions(new ArrayList<String>(), linkIdsToBeExcluded, linksToBeAdded, visitedVars);
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
    ) throws ScriptException, NLBConsistencyException {

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
                Object evalObject = engine.eval(constraint);
                Boolean evalResult = false;
                if (evalObject != null) {
                    if (evalObject instanceof Boolean) {
                        evalResult = (Boolean) evalObject;
                    } else if (evalObject instanceof Double) {
                        evalResult = ((Double) evalObject) != 0.0;
                    } else if (evalObject instanceof Integer) {
                        evalResult = ((Integer) evalObject) != 0;
                    } else {
                        // Every other non-null result type will be interpreted as true
                        evalResult = true;
                    }
                }
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
    ) throws NLBConsistencyException {
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
        if (decisionPoint.isLinkInfo()) {
            final Page pageFrom = decisionModule.getPageById(decisionPoint.getFromPageId());
            final Link linkToBeFollowedCur = findLink(pageFrom, decisionPoint.getLinkId());

            updateVisitedVars(decisionModule, linkToBeFollowedCur, factory, visitedVars);
            Variable variableLinkCur = decisionModule.getVariableById(linkToBeFollowedCur.getVarId());
            if (variableLinkCur != null && !StringHelper.isEmpty(variableLinkCur.getName())) {
                visitedVars.put(variableLinkCur.getName(), true);
            }

            makeVariableChangesForVisitedPage(decisionModule, linkToBeFollowedCur.getTarget(), factory, visitedVars);
        } else {
            makeVariableChangesForVisitedPage(decisionModule, decisionPoint.getToPageId(), factory, visitedVars);
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
            updateVisitedVars(decisionModule, page, factory, visitedVars);
            Variable variable = decisionModule.getVariableById(page.getVarId());
            // TODO: page timer???
            if (variable != null && !StringHelper.isEmpty(variable.getName())) {
                visitedVars.put(variable.getName(), true);
            }
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
            final String path,
            final ProgressData progressData
    ) throws NLBIOException, NLBConsistencyException, NLBVCSException {
        final File rootDir = new File(path);
        if (!rootDir.exists()) {
            return false;
        }
        m_rootDir = rootDir;
        progressData.setNoteText("Reading autowired pages...");
        readAutowiredPagesFile(rootDir);
        progressData.setProgressValue(20);
        progressData.setNoteText("Reading book properties...");
        readBookProperties(rootDir);
        progressData.setProgressValue(25);
        progressData.setNoteText("Reading objects...");
        readObjs(rootDir);
        progressData.setProgressValue(35);
        progressData.setNoteText("Reading pages and modules...");
        readPages(rootDir);
        progressData.setProgressValue(60);
        progressData.setNoteText("Reading variables...");
        readVariables(rootDir);
        progressData.setProgressValue(65);
        progressData.setNoteText("Reading image files...");
        readImageFiles(rootDir);
        readSoundFiles(rootDir);
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
        readAutowiredPagesFile(rootDir);
        readBookProperties(rootDir);
        readObjs(rootDir);
        readPages(rootDir);
        readVariables(rootDir);
        readImageFiles(rootDir);
        readSoundFiles(rootDir);
        return true;
    }

    private void readBookProperties(File rootDir) throws NLBIOException {
        m_startPoint = (
                FileManipulator.getOptionalFileAsString(
                        rootDir,
                        STARTPOINT_FILE_NAME,
                        DEFAULT_STARTPOINT
                )
        );
        // TODO: Language will be required file in the root NLB, but we make it optional for backward compatibility.
        m_language = (
                FileManipulator.getOptionalFileAsString(
                        rootDir,
                        LANGUAGE_FILE_NAME,
                        (m_parentNLB != null) ? m_parentNLB.getLanguage() : DEFAULT_LANGUAGE
                )
        );
        m_license = (
                FileManipulator.getOptionalFileAsString(
                        rootDir,
                        LICENSE_FILE_NAME,
                        (m_parentNLB != null) ? m_parentNLB.getLicense() : DEFAULT_LICENSE
                )
        );
        m_fullAutowire = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        rootDir,
                        FULLAUTO_FILE_NAME,
                        (m_parentNLB != null) ? String.valueOf(m_parentNLB.isFullAutowire()) : String.valueOf(DEFAULT_FULL_AUTOWIRE)
                )
        );
        m_suppressMedia = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        rootDir,
                        SUPPRESS_MEDIA_FILE_NAME,
                        (m_parentNLB != null) ? String.valueOf(m_parentNLB.isSuppressMedia()) : String.valueOf(DEFAULT_SUPPRESS_MEDIA)
                )
        );
        m_suppressSound = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        rootDir,
                        SUPPRESS_SOUND_FILE_NAME,
                        (m_parentNLB != null) ? String.valueOf(m_parentNLB.isSuppressSound()) : String.valueOf(DEFAULT_SUPPRESS_SOUND)
                )
        );
        m_author = (
                FileManipulator.getOptionalFileAsString(
                        rootDir,
                        AUTHOR_FILE_NAME,
                        (m_parentNLB != null) ? m_parentNLB.getAuthor() : DEFAULT_AUTHOR
                )
        );
        m_version = (
                FileManipulator.getOptionalFileAsString(
                        rootDir,
                        VERSION_FILE_NAME,
                        (m_parentNLB != null) ? m_parentNLB.getVersion() : DEFAULT_VERSION
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
                final ObjImpl obj = new ObjImpl(this);
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
                final VariableImpl var = new VariableImpl(this);
                var.readVariable(varDir);
                m_variables.add(var);
            }
        }
    }

    private void readImageFiles(File rootDir) throws NLBIOException, NLBConsistencyException {
        m_imageFiles.clear();
        final File imagesDir = new File(rootDir, IMAGES_DIR_NAME);
        // imagesDir dir can be nonexistent, in this case there is no images in the book
        if (imagesDir.exists()) {
            File[] listFiles = imagesDir.listFiles(NON_SPECIAL_FILTER);
            if (listFiles == null) {
                throw new NLBIOException("Error when enumerating images' directory contents");
            }
            for (File file : listFiles) {
                final MediaFileImpl imageFile = new MediaFileImpl(file.getName());
                imageFile.setRedirect(
                        FileManipulator.getOptionalFileAsString(
                                imagesDir,
                                file.getName() + REDIRECT_EXT,
                                Constants.EMPTY_STRING
                        )
                );
                imageFile.setConstrId(
                        FileManipulator.getOptionalFileAsString(
                                imagesDir,
                                file.getName() + CONSTRID_EXT,
                                Constants.EMPTY_STRING
                        )
                );
                imageFile.setFlagged(
                        "true".equals(
                                FileManipulator.getOptionalFileAsString(
                                        imagesDir,
                                        file.getName() + FLAG_EXT,
                                        String.valueOf(false)
                                )
                        )
                );
                m_imageFiles.add(imageFile);
            }
        }
    }

    private void readSoundFiles(File rootDir) throws NLBIOException, NLBConsistencyException {
        m_soundFiles.clear();
        final File soundDir = new File(rootDir, SOUND_DIR_NAME);
        // soundDir dir can be nonexistent, in this case there is no sound in the book
        if (soundDir.exists()) {
            File[] listFiles = soundDir.listFiles(NON_SPECIAL_FILTER);
            if (listFiles == null) {
                throw new NLBIOException("Error when enumerating sound' directory contents");
            }
            for (File file : listFiles) {
                final MediaFileImpl soundFile = new MediaFileImpl(file.getName());
                soundFile.setRedirect(
                        FileManipulator.getOptionalFileAsString(
                                soundDir,
                                file.getName() + REDIRECT_EXT,
                                Constants.EMPTY_STRING
                        )
                );
                soundFile.setConstrId(
                        FileManipulator.getOptionalFileAsString(
                                soundDir,
                                file.getName() + CONSTRID_EXT,
                                Constants.EMPTY_STRING
                        )
                );
                soundFile.setFlagged(
                        "true".equals(
                                FileManipulator.getOptionalFileAsString(
                                        soundDir,
                                        file.getName() + FLAG_EXT,
                                        String.valueOf(false)
                                )
                        )
                );
                m_soundFiles.add(soundFile);
            }
        }
    }

    private void writeImageFiles(
            final @NotNull FileManipulator fileManipulator,
            final File rootDir
    ) throws NLBIOException, NLBConsistencyException, NLBFileManipulationException, NLBVCSException {
        final File imagesDir = new File(rootDir, IMAGES_DIR_NAME);
        // imagesDir dir can be nonexistent, in this case there is no images in the book
        if (imagesDir.exists()) {
            writeMediaFiles(m_imageFiles, fileManipulator, imagesDir);
        }
    }

    private void writeSoundFiles(
            final @NotNull FileManipulator fileManipulator,
            final File rootDir
    ) throws NLBIOException, NLBConsistencyException, NLBFileManipulationException, NLBVCSException {
        final File soundDir = new File(rootDir, SOUND_DIR_NAME);
        // soundDir dir can be nonexistent, in this case there is no sound in the book
        if (soundDir.exists()) {
            writeMediaFiles(m_soundFiles, fileManipulator, soundDir);
        }
    }

    private void writeMediaFiles(
            Set<MediaFileImpl> mediaFiles,
            final @NotNull FileManipulator fileManipulator,
            final File mediaDir
    ) throws NLBIOException, NLBConsistencyException, NLBFileManipulationException, NLBVCSException {
        // Please note that all image/sound files were already saved in the images/sound folder
        // (when image/sound is added), only metadata should be saved
        if (mediaDir.exists()) {
            for (MediaFile mediaFile : mediaFiles) {
                File constrIdFile = new File(mediaDir, mediaFile.getFileName() + CONSTRID_EXT);
                if (StringHelper.isEmpty(mediaFile.getConstrId())) {
                    if (constrIdFile.exists()) {
                        fileManipulator.deleteFileOrDir(constrIdFile);
                    }
                } else {
                    fileManipulator.writeOptionalString(
                            mediaDir,
                            constrIdFile.getName(),
                            mediaFile.getConstrId(),
                            Constants.EMPTY_STRING
                    );
                }
                File redirectFile = new File(mediaDir, mediaFile.getFileName() + REDIRECT_EXT);
                if (StringHelper.isEmpty(mediaFile.getRedirect())) {
                    if (redirectFile.exists()) {
                        fileManipulator.deleteFileOrDir(redirectFile);
                    }
                } else {
                    fileManipulator.writeOptionalString(
                            mediaDir,
                            redirectFile.getName(),
                            mediaFile.getRedirect(),
                            Constants.EMPTY_STRING
                    );
                }
                File flagFile = new File(mediaDir, mediaFile.getFileName() + FLAG_EXT);
                if (mediaFile.isFlagged()) {
                    fileManipulator.writeOptionalString(
                            mediaDir,
                            flagFile.getName(),
                            String.valueOf(mediaFile.isFlagged()),
                            String.valueOf(false)
                    );
                } else {
                    if (flagFile.exists()) {
                        fileManipulator.deleteFileOrDir(flagFile);
                    }
                }
            }
        }
    }

    public void save(
            final FileManipulator fileManipulator,
            final ProgressData progressData,
            final PartialProgressData partialProgressData
    )
            throws NLBIOException, NLBConsistencyException, NLBVCSException, NLBFileManipulationException {
        try {
            if (!m_rootDir.exists()) {
                if (!m_rootDir.mkdirs()) {
                    throw new NLBIOException("Cannot create NLB root directory");
                }
            }
            writeSoundFiles(fileManipulator, m_rootDir);
            writeImageFiles(fileManipulator, m_rootDir);
            progressData.setProgressValue(20);
            progressData.setNoteText("Writing variables...");
            writeVariables(fileManipulator, m_rootDir);
            progressData.setProgressValue(partialProgressData.getStartingProgress());
            progressData.setNoteText("Writing pages and modules...");
            writePages(fileManipulator, m_rootDir, partialProgressData);
            progressData.setProgressValue(partialProgressData.getMaximumAllowedProgress());
            progressData.setNoteText("Writing objects...");
            writeObjs(fileManipulator, m_rootDir);
            progressData.setProgressValue(95);
            progressData.setNoteText("Writing book properties...");
            writeBookProperties(fileManipulator, m_rootDir);
            progressData.setNoteText("Writing autowired pages file...");
            writeAutowiredPagesFile(fileManipulator, m_rootDir);
        } catch (IOException e) {
            throw new NLBIOException("IO exception occurred", e);
        }
    }

    private void writePages(
            final FileManipulator fileManipulator,
            final File rootDir,
            final PartialProgressData partialProgressData
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
            partialProgressData.setRealProgressValue();
            page.writePage(fileManipulator, pagesDir, this, partialProgressData);
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

    protected void writeAutowiredPagesFile(
            FileManipulator fileManipulator,
            File rootDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        StringBuilder sb = new StringBuilder();
        final int lastElemIndex = m_autowiredPages.size() - 1;
        if (lastElemIndex >= 0) {
            for (int i = 0; i < lastElemIndex; i++) {
                final String pageId = m_autowiredPages.get(i);
                if (!getPageImplById(pageId).isDeleted()) {
                    sb.append(pageId).append(AUTOWIRED_SEPARATOR);
                }
            }
            String lastPageId = m_autowiredPages.get(lastElemIndex);
            if (!getPageImplById(lastPageId).isDeleted()) {
                sb.append(lastPageId);
            }
            fileManipulator.writeOptionalString(rootDir, AUTOWIRED_PAGES_FILE_NAME, String.valueOf(sb.toString()), DEFAULT_AUTOWIRED_PAGES);
        } else {
            fileManipulator.writeOptionalString(rootDir, AUTOWIRED_PAGES_FILE_NAME, Constants.EMPTY_STRING, DEFAULT_AUTOWIRED_PAGES);
        }
    }

    protected void readAutowiredPagesFile(File rootDir) throws NLBIOException, NLBConsistencyException {
        String autowiredPagesString = FileManipulator.getOptionalFileAsString(
                rootDir,
                AUTOWIRED_PAGES_FILE_NAME,
                DEFAULT_AUTOWIRED_PAGES
        );
        if (autowiredPagesString.isEmpty()) {
            // do nothing
        } else {
            m_autowiredPages.clear();
            List<String> autowiredPages = Arrays.asList(autowiredPagesString.split(AUTOWIRED_SEPARATOR));
            for (String pageId : autowiredPages) {
                m_autowiredPages.add(pageId);
            }
        }
    }

    private void preprocessVariable(final VariableImpl variable) throws NLBConsistencyException {
        final String id = variable.getId();
        if ((variable.getType() == VariableImpl.Type.PAGE) || (variable.getType() == VariableImpl.Type.TIMER)) {
            final PageImpl page = getPageImplById(variable.getTarget());
            if (variable.isDeleted()) {
                // page.getVarId() should be empty or set to another variable's Id
                if (id.equals(page.getVarId())) {
                    throw new NLBConsistencyException(
                            "Page (or timer) variable for page with Id = "
                                    + page.getId()
                                    + " is incorrect, because the corresponding variable with Id = "
                                    + id
                                    + " has been deleted"
                    );
                }
            }
            if (page.isDeleted()) {
                variable.setDeleted(true);
            }
        } else if (
                variable.getType() == VariableImpl.Type.OBJ
                        || variable.getType() == VariableImpl.Type.OBJCONSTRAINT
                        || variable.getType() == VariableImpl.Type.COMMONTO
                ) {
            final Obj obj = getObjById(variable.getTarget());
            if (variable.isDeleted()) {
                // obj.getVarId() should be empty or set to another variable's Id
                if (id.equals(obj.getVarId()) || id.equals(obj.getConstrId()) || id.equals(obj.getCommonToId())) {
                    throw new NLBConsistencyException(
                            "Obj variable for obj with Id = "
                                    + obj.getId()
                                    + " is incorrect, because the corresponding variable with Id = "
                                    + id
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
            if (link.isDeleted() || link.hasDeletedParent()) {
                variable.setDeleted(true);
            }
        } else if (
                variable.getType() == VariableImpl.Type.VAR
                        || variable.getType() == VariableImpl.Type.EXPRESSION
                        || variable.getType() == VariableImpl.Type.TAG
                ) {
            final ModifyingItemAndModification itemAndModification = (
                    getModifyingItemAndModification(variable)
            );
            if (!itemAndModification.existsAndValid()) {
                variable.setDeleted(true);
            }
        } else if (
                variable.getType() == VariableImpl.Type.MODCONSTRAINT
                        || variable.getType() == VariableImpl.Type.AUTOWIRECONSTRAINT
                ) {
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
            } else if (
                    variable.getType() == VariableImpl.Type.EXPRESSION
                            || variable.getType() == VariableImpl.Type.TAG
                    ) {
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

    private void writeBookProperties(
            final FileManipulator fileManipulator,
            final File rootDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        fileManipulator.writeOptionalString(rootDir, STARTPOINT_FILE_NAME, m_startPoint, DEFAULT_STARTPOINT);
        if (m_parentNLB != null) {
            fileManipulator.writeOptionalString(
                    rootDir,
                    LANGUAGE_FILE_NAME,
                    m_language,
                    m_parentNLB.getLanguage()
            );
            fileManipulator.writeOptionalString(rootDir, LICENSE_FILE_NAME, m_license, m_parentNLB.getLicense());
            fileManipulator.writeOptionalString(rootDir, FULLAUTO_FILE_NAME, String.valueOf(m_fullAutowire), String.valueOf(m_parentNLB.isFullAutowire()));
            fileManipulator.writeOptionalString(rootDir, SUPPRESS_MEDIA_FILE_NAME, String.valueOf(m_suppressMedia), String.valueOf(m_parentNLB.isSuppressMedia()));
            fileManipulator.writeOptionalString(rootDir, SUPPRESS_SOUND_FILE_NAME, String.valueOf(m_suppressSound), String.valueOf(m_parentNLB.isSuppressSound()));
            fileManipulator.writeOptionalString(rootDir, AUTHOR_FILE_NAME, m_author, m_parentNLB.getAuthor());
            fileManipulator.writeOptionalString(rootDir, VERSION_FILE_NAME, m_version, m_parentNLB.getVersion());
        } else {
            fileManipulator.writeRequiredString(rootDir, LANGUAGE_FILE_NAME, m_language);
            fileManipulator.writeOptionalString(rootDir, LICENSE_FILE_NAME, m_license, DEFAULT_LICENSE);
            fileManipulator.writeOptionalString(rootDir, FULLAUTO_FILE_NAME, String.valueOf(m_fullAutowire), String.valueOf(DEFAULT_FULL_AUTOWIRE));
            fileManipulator.writeOptionalString(rootDir, SUPPRESS_MEDIA_FILE_NAME, String.valueOf(m_suppressMedia), String.valueOf(DEFAULT_SUPPRESS_MEDIA));
            fileManipulator.writeOptionalString(rootDir, SUPPRESS_SOUND_FILE_NAME, String.valueOf(m_suppressSound), String.valueOf(DEFAULT_SUPPRESS_SOUND));
            fileManipulator.writeOptionalString(rootDir, AUTHOR_FILE_NAME, m_author, DEFAULT_AUTHOR);
            fileManipulator.writeOptionalString(rootDir, VERSION_FILE_NAME, m_version, DEFAULT_VERSION);
        }
    }

    public Variable getVariableById(String varId) {
        Variable result = getVariableImplById(varId);
        if (result != null) {
            return result;
        } else if (m_parentNLB != null) {
            return m_parentNLB.getVariableById(varId);
        } else {
            return getAutowiredVariable(varId);
        }
    }

    @Override
    public List<Variable> getVariables() {
        List<Variable> result = new ArrayList<>();
        result.addAll(m_variables);
        return result;
    }

    private VariableImpl getVariableImplById(String varId) {
        if (!StringHelper.isEmpty(varId)) {
            for (final VariableImpl variable : m_variables) {
                if (variable.getId().equals(varId)) {
                    return variable;
                }
            }
        }
        return getAutowiredVariable(varId);
    }

    private VariableImpl getAutowiredVariable(String varId) {
        if (TRUE_VARID.equals(varId)) {
            VariableImpl variable = new VariableImpl();
            variable.setType(Variable.Type.EXPRESSION);
            variable.setDataType(Variable.DataType.BOOLEAN);
            variable.setValue("true");
            return variable;
        } else if (FALSE_VARID.equals(varId)) {
            VariableImpl variable = new VariableImpl();
            variable.setType(Variable.Type.EXPRESSION);
            variable.setDataType(Variable.DataType.BOOLEAN);
            variable.setValue("false");
            return variable;
        } else {
            String[] ids = parseIds(varId);
            for (PageImpl page : m_pages.values()) {
                if (varId != null && varId.endsWith(page.getId())) {
                    VariableImpl variable = new VariableImpl();
                    boolean isLinkConstraint = varId.startsWith(LC_VARID_PREFIX);
                    variable.setType(
                            isLinkConstraint ? Variable.Type.LINKCONSTRAINT : Variable.Type.VAR
                    );
                    variable.setDataType(Variable.DataType.BOOLEAN);
                    if (isLinkConstraint) {
                        VariableImpl autowiredOutConstraint = null;
                        PageImpl autowiredPage = null;
                        Matcher matcher = AUTOWIRED_OUT_PATTERN.matcher(varId);
                        if (matcher.find()) {
                            String autowiredPageId = matcher.group(1);
                            autowiredPage = getPageImplById(autowiredPageId);
                            autowiredOutConstraint = getVariableImplById(autowiredPage.getAutowireOutConstrId());
                        }
                        // autowiredPage.getId() should be equal to ids[0]...
                        variable.setValue(
                                decorateId(page.getId(), (autowiredPage != null) ? autowiredPage.getId() : ids[0]) +
                                        (
                                                (autowiredOutConstraint != null)
                                                        ? " && " + autowiredOutConstraint.getValue()
                                                        : ""
                                        )
                        );
                    } else {
                        variable.setName(decorateId(page.getId(), ids[0]));
                    }

                    return variable;
                }
            }
        }
        return null;
    }

    private static String[] parseIds(String varId) {
        return varId.split("_");
    }

    public static String decorateId(String id, String autowiredId) {
        return "vl_" + ((autowiredId != null) ? autowiredId.replaceAll("-", "_") + "_" : "") + id.replaceAll("-", "_");
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
    public SearchResultTableModel searchText(SearchContract contract, final String modulePageId) {
        SearchResultTableModel result = new SearchResultTableModel();
        if (contract.isSearchInPages()) {
            for (Map.Entry<String, PageImpl> entry : m_pages.entrySet()) {
                final SearchResult pageResult;
                final PageImpl page = entry.getValue();
                if (!page.isDeleted()) {
                    if ((pageResult = page.searchText(contract)) != null) {
                        pageResult.setModulePageId(modulePageId);
                        result.addSearchResult(pageResult);
                    } else {
                        if (contract.isSearchInVars()) {
                            final VariableImpl variable = getVariableImplById(page.getVarId());
                            final VariableImpl variableTimer = getVariableImplById(page.getTimerVarId());
                            if (variable != null) {
                                final SearchResult varResult = variable.searchText(contract);
                                if (varResult != null) {
                                    varResult.setId(page.getId());
                                    varResult.setModulePageId(modulePageId);
                                    result.addSearchResult(varResult);
                                }
                            }
                            if (variableTimer != null) {
                                final SearchResult varResult = variableTimer.searchText(contract);
                                if (varResult != null) {
                                    varResult.setId(page.getId());
                                    varResult.setModulePageId(modulePageId);
                                    result.addSearchResult(varResult);
                                }
                            }
                            result.addSearchResults(getModificationSearchResults(page, contract));
                        }
                    }

                    final NonLinearBookImpl moduleImpl = page.getModuleImpl();
                    if (!moduleImpl.isEmpty()) {
                        result.addSearchResultTableModel(
                                moduleImpl.searchText(
                                        new SearchContract(contract.getSearchText(), contract.isSearchInIds(), contract.isSearchInPages(), contract.isSearchInObjects(), contract.isSearchInLinks(), contract.isSearchInVars(), contract.isIgnoreCase(), contract.isWholeWords()), page.getId()
                                )
                        );
                    }
                    if (contract.isSearchInLinks()) {
                        searchLinks(modulePageId, page, result, contract);
                    }
                }
            }
        }
        if (contract.isSearchInObjects()) {
            for (Map.Entry<String, ObjImpl> entry : m_objs.entrySet()) {
                final SearchResult objResult;
                final SearchResult varResult;
                final SearchResult constrResult;
                final SearchResult commontoResult;
                final ObjImpl obj = entry.getValue();
                if (!obj.isDeleted()) {
                    if ((objResult = obj.searchText(contract)) != null) {
                        objResult.setModulePageId(modulePageId);
                        result.addSearchResult(objResult);
                    } else {
                        final VariableImpl variable = getVariableImplById(obj.getVarId());
                        final VariableImpl constraint = getVariableImplById(obj.getConstrId());
                        final VariableImpl commonto = getVariableImplById(obj.getCommonToId());
                        if (contract.isSearchInVars()) {
                            if (variable != null) {
                                varResult = variable.searchText(contract);
                                if (varResult != null) {
                                    varResult.setId(obj.getId());
                                    varResult.setModulePageId(modulePageId);
                                    result.addSearchResult(varResult);
                                }
                            }
                            if (constraint != null) {
                                constrResult = constraint.searchText(contract);
                                if (constrResult != null) {
                                    constrResult.setId(obj.getId());
                                    constrResult.setModulePageId(modulePageId);
                                    result.addSearchResult(constrResult);
                                }
                            }
                            if (commonto != null) {
                                commontoResult = commonto.searchText(contract);
                                if (commontoResult != null) {
                                    commontoResult.setId(obj.getId());
                                    commontoResult.setModulePageId(modulePageId);
                                    result.addSearchResult(commontoResult);
                                }
                            }
                            result.addSearchResults(getModificationSearchResults(obj, contract));
                        }
                    }
                    if (contract.isSearchInLinks()) {
                        searchLinks(modulePageId, obj, result, contract);
                    }
                }
            }
        }
        return result;
    }

    private List<SearchResult> getModificationSearchResults(final ModifyingItem item, final SearchContract contract) {
        List<SearchResult> results = new ArrayList<>();
        List<Modification> modifications = item.getModifications();
        for (Modification modification : modifications) {
            final SearchResult modificationResult = modification.searchText(contract);
            if (modificationResult != null) {
                results.add(modificationResult);
            }
        }
        return results;
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
                case TIMER:
                    Page page = getPageById(variable.getTarget());
                    if (!page.isDeleted()) {
                        searchResult.setId(page.getId());
                        result.addSearchResult(searchResult);
                    }
                    break;
                case OBJ:
                case OBJCONSTRAINT:
                case COMMONTO:
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
                case TAG:
                case EXPRESSION:
                    final ModifyingItemAndModification itemAndModification = (
                            getModifyingItemAndModification(variable)
                    );
                    if (itemAndModification.existsAndValid()) {
                        searchResult.setId(itemAndModification.getModifyingItem().getId());
                        result.addSearchResult(searchResult);
                    }
                    break;
                case MODCONSTRAINT:
                case AUTOWIRECONSTRAINT:
                    Page targetPage = getPageById(variable.getTarget());
                    if (!targetPage.isDeleted()) {
                        searchResult.setId(targetPage.getId());
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
                case TAG:
                case EXPRESSION:
                    final ModifyingItemAndModification itemAndModification = (
                            getModifyingItemAndModification(variable)
                    );
                    if (itemAndModification.existsAndValid()) {
                        final String error = checkFormula(variable.getValue().trim());
                        if (error != null) {
                            searchResult.addInformation(error);
                            searchResult.setId(itemAndModification.getModifyingItem().getId());
                            result.addSearchResult(searchResult);
                        }
                    }
                    break;
                case MODCONSTRAINT:
                case AUTOWIRECONSTRAINT:
                    Page targetPage = getPageById(variable.getTarget());
                    if (!targetPage.isDeleted()) {
                        final String error = checkFormula(variable.getValue().trim());
                        if (error != null) {
                            searchResult.addInformation(error);
                            searchResult.setId(targetPage.getId());
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
            result += objEntry.getValue().getActText().length();
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

    /**
     * This method returns overall pages count (even deleted pages are accounted for, but all pages from modules with
     * deleted module page are skipped). This is used during save progress calculation.
     * NB: all pages from modules with deleted module page are skipped, this is because module page directory is
     * removed recursively with its module and pages of this module, therefore such module pages will never be
     * visited during save.
     *
     * @return overall pages count in the book, even in case when these pages were scheduled for deletion, see NB.
     */
    public int getEffectivePagesCountForSave() {
        int pagesCount = 0;
        for (Map.Entry<String, PageImpl> pageEntry : m_pages.entrySet()) {
            pagesCount++;
            final PageImpl page = pageEntry.getValue();
            if (!page.isDeleted() && !page.getModule().isEmpty()) {
                pagesCount += page.getModuleImpl().getEffectivePagesCountForSave();
            }
        }

        return pagesCount;
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
                case TIMER:
                    result.incPageTimerVariablesCount();
                    break;
                case OBJ:
                    result.incObjVariablesCount();
                    break;
                case OBJCONSTRAINT:
                    result.incObjConstraintsCount();
                    break;
                case COMMONTO:
                    result.incObjCommonTosCount();
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
                case TAG:
                case EXPRESSION:
                    result.incExpressionsCount();
                    break;
                case MODCONSTRAINT:
                    result.incModuleConstraintCount();
                    break;
                case AUTOWIRECONSTRAINT:
                    result.incAutowireConstraintCount();
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
    public Page getParentPage() {
        return m_parentPage;
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
                case TIMER:
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
                    if (itemAndModification.existsAndValid()) {
                        Variable modificationVariable = (
                                getVariableById(itemAndModification.getModification().getVarId())
                        );
                        result.put(modificationVariable.getName(), modificationVariable.getDataType());
                    }
                    break;
                case LINKCONSTRAINT:
                case TAG:
                case EXPRESSION:
                case MODCONSTRAINT:
                case AUTOWIRECONSTRAINT:
                case OBJCONSTRAINT:
                case COMMONTO:
                default:
                    // Do nothing
            }
        }
        for (Map.Entry<String, PageImpl> entry : m_pages.entrySet()) {
            // For autowired vars
            for (Map.Entry<String, PageImpl> entryA : m_pages.entrySet()) {
                if (entryA.getValue().isAutowire()) {
                    result.put(decorateId(entry.getKey(), entryA.getKey()), Variable.DataType.BOOLEAN);
                }
            }

            final NonLinearBookImpl moduleImpl = entry.getValue().getModuleImpl();
            if (!moduleImpl.isEmpty()) {
                result.putAll(
                        moduleImpl.getVariableDataTypes()
                );
            }
        }
        return result;
    }

    @Override
    public Map<String, String> getMediaToConstraintMap() {
        Map<String, String> result = new HashMap<>();
        List<MediaFile> imageFiles = getImageFiles();
        for (MediaFile mediaFile : imageFiles) {
            if (StringHelper.notEmpty(mediaFile.getConstrId())) {
                result.put(mediaFile.getFileName(), getVariableById(mediaFile.getConstrId()).getValue());
            }
        }
        List<MediaFile> soundFiles = getSoundFiles();
        for (MediaFile mediaFile : soundFiles) {
            if (StringHelper.notEmpty(mediaFile.getConstrId())) {
                result.put(mediaFile.getFileName(), getVariableById(mediaFile.getConstrId()).getValue());
            }
        }
        return result;
    }

    @Override
    public Map<String, String> getMediaRedirectsMap() {
        Map<String, String> result = new HashMap<>();
        List<MediaFile> imageFiles = getImageFiles();
        for (MediaFile mediaFile : imageFiles) {
            if (StringHelper.notEmpty(mediaFile.getRedirect())) {
                result.put(mediaFile.getFileName(), mediaFile.getRedirect());
            }
        }
        List<MediaFile> soundFiles = getSoundFiles();
        for (MediaFile mediaFile : soundFiles) {
            if (StringHelper.notEmpty(mediaFile.getRedirect())) {
                result.put(mediaFile.getFileName(), mediaFile.getRedirect());
            }
        }
        return result;
    }

    @Override
    public Map<String, Boolean> getMediaFlagsMap() {
        Map<String, Boolean> result = new HashMap<>();
        List<MediaFile> imageFiles = getImageFiles();
        for (MediaFile mediaFile : imageFiles) {
            result.put(mediaFile.getFileName(), mediaFile.isFlagged());
        }
        List<MediaFile> soundFiles = getSoundFiles();
        for (MediaFile mediaFile : soundFiles) {
            result.put(mediaFile.getFileName(), mediaFile.isFlagged());
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
                    case TIMER:
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
                        if (itemAndModification.existsAndValid()) {
                            found = true;
                        } else {
                            continue;
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
            final SearchContract contract
    ) {
        for (LinkImpl link : nodeItem.getLinkImpls()) {
            final SearchResult linkResult;
            final SearchResult varResult;
            final SearchResult constraintsResult;
            if ((linkResult = link.searchText(contract)) != null) {
                linkResult.setModulePageId(module);
                result.addSearchResult(linkResult);
            } else {
                if (contract.isSearchInVars()) {
                    final VariableImpl variable = getVariableImplById(link.getVarId());
                    if (variable != null) {
                        varResult = variable.searchText(contract);
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
                            constraintsResult = constraint.searchText(contract);
                        } else {
                            constraintsResult = null;
                        }
                        if (constraintsResult != null) {
                            constraintsResult.setId(link.getId());
                            constraintsResult.setModulePageId(module);
                            result.addSearchResult(constraintsResult);
                        }
                    }
                    result.addSearchResults(getModificationSearchResults(link, contract));
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

    public void exportToTXTFile(final File targetFile) throws NLBExportException {
        ExportManager manager = new TaggedTextExportManager(this, ExportManager.UTF_8);
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

    public void exportToVNSTEADFile(final File targetFile) throws NLBExportException {
        ExportManager manager = new VNSTEADExportManager(this, ExportManager.UTF_8);
        manager.exportToFile(targetFile);
    }

    public void exportToASMFile(final File targetFile) throws NLBExportException {
        ExportManager manager = new ASMExportManager(this, ExportManager.UTF_8);
        manager.exportToFile(targetFile);
    }

    public void addVariable(@NotNull VariableImpl variable) {
        m_variables.add(variable);
    }

    private MediaFileImpl copyMediaFile(
            final @NotNull FileManipulator fileManipulator,
            final @NotNull File file,
            final @Nullable String fileName,
            final @NotNull String mediaDirName
    ) throws NLBFileManipulationException, NLBIOException, NLBVCSException {
        File localFile = createUniqueMediaFile(fileManipulator, file, fileName, mediaDirName);
        fileManipulator.copyFile(localFile, file, "Cannot copy media file " + localFile.getName());
        MediaFileImpl mediaFile = new MediaFileImpl(localFile.getName());
        return mediaFile;
    }

    public void copyAndAddImageFile(
            final @NotNull FileManipulator fileManipulator,
            final @NotNull File file,
            final @Nullable String fileName
    ) throws NLBFileManipulationException, NLBIOException, NLBVCSException {
        addImageFile(copyMediaFile(fileManipulator, file, fileName, IMAGES_DIR_NAME));
    }

    public void copyAndAddSoundFile(
            final @NotNull FileManipulator fileManipulator,
            final @NotNull File file,
            final @Nullable String fileName
    ) throws NLBFileManipulationException, NLBIOException, NLBVCSException {
        addSoundFile(copyMediaFile(fileManipulator, file, fileName, SOUND_DIR_NAME));
    }

    private File createUniqueMediaFile(
            final @NotNull FileManipulator fileManipulator,
            final @NotNull File newFile,
            final @Nullable String fileName,
            final @NotNull String mediaDirName
    ) throws NLBFileManipulationException, NLBIOException {
        String uniqueFileName = (fileName != null) ? fileName.toLowerCase() : newFile.getName().toLowerCase();
        final File mediaDir = new File(m_rootDir, mediaDirName);
        fileManipulator.createDir(mediaDir, "Cannot create NLB media directory");
        File localFile = new File(mediaDir, uniqueFileName);
        int extIndex = uniqueFileName.lastIndexOf(".");
        String namePart = uniqueFileName.substring(0, extIndex);
        String extPart = uniqueFileName.substring(extIndex);
        int counter = 1;
        while (localFile.exists()) {
            uniqueFileName = String.format(MEDIA_FILE_NAME_TEMPLATE, namePart, counter++, extPart);
            localFile = new File(mediaDir, uniqueFileName);
        }
        return localFile;
    }

    public void removeImageFile(
            final @NotNull FileManipulator fileManipulator,
            final String imageFileName
    ) throws NLBFileManipulationException, NLBIOException, NLBConsistencyException {
        Iterator<MediaFileImpl> imageFileIterator = m_imageFiles.iterator();
        while (imageFileIterator.hasNext()) {
            MediaFileImpl imageFile = imageFileIterator.next();
            if (imageFile.getFileName().equals(imageFileName)) {
                final File imagesDir = new File(m_rootDir, IMAGES_DIR_NAME);
                if (!imagesDir.exists()) {
                    throw new NLBConsistencyException("NLB images dir does not exist");
                }
                fileManipulator.deleteFileOrDir(new File(imagesDir, imageFileName));
                fileManipulator.deleteFileOrDir(new File(imagesDir, imageFileName + REDIRECT_EXT));
                fileManipulator.deleteFileOrDir(new File(imagesDir, imageFileName + CONSTRID_EXT));
                imageFileIterator.remove();
                return;
            }
        }

        throw new NLBConsistencyException("Specified image file does not exist in images dir");
    }

    public void removeSoundFile(
            final @NotNull FileManipulator fileManipulator,
            final String soundFileName
    ) throws NLBFileManipulationException, NLBIOException, NLBConsistencyException {
        Iterator<MediaFileImpl> soundFileIterator = m_soundFiles.iterator();
        while (soundFileIterator.hasNext()) {
            MediaFileImpl soundFile = soundFileIterator.next();
            if (soundFile.getFileName().equals(soundFileName)) {
                final File soundDir = new File(m_rootDir, SOUND_DIR_NAME);
                if (!soundDir.exists()) {
                    throw new NLBConsistencyException("NLB sound dir does not exist");
                }
                fileManipulator.deleteFileOrDir(new File(soundDir, soundFileName));
                fileManipulator.deleteFileOrDir(new File(soundDir, soundFileName + REDIRECT_EXT));
                fileManipulator.deleteFileOrDir(new File(soundDir, soundFileName + CONSTRID_EXT));
                soundFileIterator.remove();
                return;
            }
        }

        throw new NLBConsistencyException("Specified sound file does not exist in sound dir");
    }

    public void addImageFile(@NotNull MediaFileImpl imageFile) {
        m_imageFiles.add(imageFile);
    }

    public void addSoundFile(@NotNull MediaFileImpl soundFile) {
        m_soundFiles.add(soundFile);
    }

    public void exportImages(final boolean isRoot, final File mainExportDir) throws NLBExportException {
        exportMedia(isRoot, mainExportDir, IMAGES_DIR_NAME, getImageFiles(), MediaFile.Type.Image);
    }

    public void exportSound(final boolean isRoot, final File mainExportDir) throws NLBExportException {
        if (!m_suppressSound) {
            exportMedia(isRoot, mainExportDir, SOUND_DIR_NAME, getSoundFiles(), MediaFile.Type.Sound);
        }
    }

    @Override
    public void exportMedia(
            final boolean isRoot,
            final File mainExportDir,
            final String mediaDirName,
            final List<MediaFile> mediaFiles,
            final MediaFile.Type mediaType
    ) throws NLBExportException {
        if (m_suppressMedia) {
            return;
        }
        if (getRootDir() == null) {
            throw new NLBExportException("NLB root dir is undefined");
        }
        File exportDir = (isRoot) ? mainExportDir : new File(mainExportDir, getParentPage().getId());
        if (!exportDir.exists() && !exportDir.mkdir()) {
            if (isRoot) {
                throw new NLBExportException(
                        "Cannot create media export directory for main NLB module"
                );
            } else {
                throw new NLBExportException(
                        "Cannot create media export directory for module with module page id = " + getParentPage().getId()
                );
            }
        }
        try {
            File mediaDir = new File(getRootDir(), mediaDirName);
            if (mediaDir.exists()) {
                for (MediaFile mediaFile : mediaFiles) {
                    String mediaFileName = mediaFile.getFileName();
                    File targetMedia = new File(exportDir, mediaFileName);
                    File sourceMedia = new File(mediaDir, mediaFileName);
                    FileManipulator.transfer(sourceMedia, targetMedia);
                }
            }

            for (Page page : m_pages.values()) {
                final NonLinearBook module = page.getModule();
                if (!module.isEmpty()) {
                    switch (mediaType) {
                        case Image:
                            module.exportMedia(
                                    false,
                                    mainExportDir,
                                    mediaDirName,
                                    module.getImageFiles(),
                                    mediaType
                            );
                            break;
                        case Sound:
                            module.exportMedia(
                                    false,
                                    mainExportDir,
                                    mediaDirName,
                                    module.getSoundFiles(),
                                    mediaType
                            );
                            break;
                        default:
                            throw new NLBExportException("Unknown media type = " + mediaType.name());
                    }

                }
            }
        } catch (IOException e) {
            throw new NLBExportException("IOException when exporting media", e);
        }
    }
}
