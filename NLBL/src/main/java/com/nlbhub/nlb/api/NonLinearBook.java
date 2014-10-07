/**
 * @(#)NonLinearBook.java
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
package com.nlbhub.nlb.api;

import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.user.domain.History;

import javax.script.ScriptException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The NonLinearBook class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/15/14
 */
public interface NonLinearBook {
    public static final String TRUE_VARID = "TRUE";
    public static final String FALSE_VARID = "FALSE";
    public static final String LC_VARID_PREFIX = "LC_";
    public static final String LC_VARID_SEPARATOR_OUT = "_OUT_";

    public static final String SOUND_DIR_NAME = "sound";
    public static final String IMAGES_DIR_NAME = "images";
    public static final String DEFAULT_STARTPOINT = Constants.EMPTY_STRING;
    public static final String DEFAULT_LANGUAGE = Constants.RU;
    public static final String DEFAULT_LICENSE = Constants.EMPTY_STRING;
    public static final boolean DEFAULT_FULL_AUTOWIRE = false;
    public static final String DEFAULT_AUTHOR = Constants.EMPTY_STRING;
    public static final String DEFAULT_VERSION = Constants.EMPTY_STRING;

    public class ModuleInfo {
        private String m_modulePageId;
        private String m_moduleName;
        private int m_depth;

        public ModuleInfo(String modulePageId, String moduleName, int depth) {
            m_modulePageId = modulePageId;
            m_moduleName = moduleName;
            m_depth = depth;
        }

        public String getModulePageId() {
            return m_modulePageId;
        }

        public String getModuleName() {
            return m_moduleName;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < m_depth - 1; i++) {
                result.append("  ");
            }
            if (m_depth >= 1) {
                result.append("\u2570");
            }
            result.append(m_moduleName);
            return result.toString();
        }
    }

    public class BookStatistics {
        private int m_pagesCount = 0;
        private int m_objsCount = 0;
        private int m_uniqueEndings = 0;
        private int m_charactersCount = 0;
        private List<ModuleInfo> m_moduleInfos = new ArrayList<>();
        private List<ModuleInfo> m_modulesToBeDeletedInfos = new ArrayList<>();

        public List<ModuleInfo> getModuleInfos() {
            return m_moduleInfos;
        }

        public void addModuleInfo(final ModuleInfo moduleInfo) {
            m_moduleInfos.add(moduleInfo);
        }

        public List<ModuleInfo> getModulesToBeDeletedInfos() {
            return m_modulesToBeDeletedInfos;
        }

        public void addModuleToBeDeletedInfo(final ModuleInfo moduleInfo) {
            m_modulesToBeDeletedInfos.add(moduleInfo);
        }

        public int getPagesCount() {
            return m_pagesCount;
        }

        public void incPagesCount(int pagesCount) {
            m_pagesCount += pagesCount;
        }

        public int getObjsCount() {
            return m_objsCount;
        }

        public void incObjsCount(int objsCount) {
            m_objsCount += objsCount;
        }

        public int getUniqueEndings() {
            return m_uniqueEndings;
        }

        public void incUniqueEndings(int uniqueEndings) {
            m_uniqueEndings += uniqueEndings;
        }

        public int getCharactersCount() {
            return m_charactersCount;
        }

        public void incCharactersCount(int charactersCount) {
            m_charactersCount += charactersCount;
        }

        public void addBookStatistics(final BookStatistics bookStatistics) {
            m_pagesCount += bookStatistics.m_pagesCount;
            m_objsCount += bookStatistics.m_objsCount;
            m_uniqueEndings += bookStatistics.m_uniqueEndings;
            m_charactersCount += bookStatistics.m_charactersCount;
            m_moduleInfos.addAll(bookStatistics.m_moduleInfos);
            addDeletedModulesFromBookStatistics(bookStatistics);
        }

        public void addDeletedModulesFromBookStatistics(final BookStatistics bookStatistics) {
            m_modulesToBeDeletedInfos.addAll(bookStatistics.m_modulesToBeDeletedInfos);
        }

        public int getModulesCount() {
            return m_moduleInfos.size();
        }
    }

    public class VariableStatistics {
        private int m_pageVariablesCount = 0;
        private int m_objVariablesCount = 0;
        private int m_objConstraintsCount = 0;
        private int m_linkVariablesCount = 0;
        private int m_linkConstraintVariablesCount = 0;
        private int m_plainVariablesCount = 0;
        private int m_expressionsCount = 0;
        private int m_moduleConstraintCount = 0;
        private int m_autowireConstraintCount = 0;

        public int getPageVariablesCount() {
            return m_pageVariablesCount;
        }

        public void incPageVariablesCount() {
            m_pageVariablesCount++;
        }

        public int getObjVariablesCount() {
            return m_objVariablesCount;
        }

        public void incObjVariablesCount() {
            m_objVariablesCount++;
        }

        public int getObjConstraintsCount() {
            return m_objConstraintsCount;
        }

        public void incObjConstraintsCount() {
            m_objConstraintsCount++;
        }

        public int getLinkVariablesCount() {
            return m_linkVariablesCount;
        }

        public void incLinkVariablesCount() {
            m_linkVariablesCount++;
        }

        public int getLinkConstraintVariablesCount() {
            return m_linkConstraintVariablesCount;
        }

        public void incLinkConstraintVariablesCount() {
            m_linkConstraintVariablesCount++;
        }

        public int getPlainVariablesCount() {
            return m_plainVariablesCount;
        }

        public void incPlainVariablesCount() {
            m_plainVariablesCount++;
        }

        public int getExpressionsCount() {
            return m_expressionsCount;
        }

        public void incExpressionsCount() {
            m_expressionsCount++;
        }

        public int getModuleConstraintCount() {
            return m_moduleConstraintCount;
        }

        public void incModuleConstraintCount() {
            m_moduleConstraintCount++;
        }

        public int getAutowireConstraintCount() {
            return m_autowireConstraintCount;
        }

        public void incAutowireConstraintCount() {
            m_autowireConstraintCount++;
        }
    }

    public boolean isEmpty();

    public String getStartPoint();

    public String getLanguage();

    public String getLicense();

    public boolean isFullAutowire();

    public String getAuthor();

    public String getVersion();

    public File getRootDir();

    public File getImagesDir();

    public List<MediaFile> getImageFiles();

    public List<MediaFile> getSoundFiles();

    public void exportMedia(
            boolean isRoot,
            File mainExportDir,
            String mediaDirName,
            List<MediaFile> mediaFiles,
            final MediaFile.Type mediaType
    ) throws NLBExportException;

    public Map<String, Page> getPages();

    public List<String> getAutowiredPagesIds();

    public boolean isAutowired(final String pageId);

    public Page getPageById(String id);

    public Map<String, Obj> getObjs();

    public Obj getObjById(String objId);

    public Page createFilteredPage(final String sourceId, final History history) throws ScriptException;

    public boolean load(final String path, final ProgressData progressData) throws NLBIOException, NLBConsistencyException, NLBVCSException;

    public Variable getVariableById(String varId);

    public List<Variable> getVariables();

    public SearchResultTableModel getLeafs(String modulePageId);

    public SearchResultTableModel searchText(
            SearchContract searchContract, String modulePageId
    );

    public SearchResultTableModel getVariables(String modulePageId) throws NLBConsistencyException;

    public boolean findVariable(String variableNameToFind) throws NLBConsistencyException;

    public SearchResultTableModel checkBook(String modulePageId) throws NLBConsistencyException;

    public BookStatistics getBookStatistics();

    public VariableStatistics getVariableStatistics();

    public NonLinearBook getParentNLB();

    public Page getParentPage();

    public Map<String, Variable.DataType> getVariableDataTypes() throws NLBConsistencyException;
}
