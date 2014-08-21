/**
 * @(#)DummyNLB.java
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The DummyNLB class
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class DummyNLB implements NonLinearBook {
    private static final DummyNLB S_SINGLETON = new DummyNLB();

    public static DummyNLB singleton() {
        return S_SINGLETON;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public String getStartPoint() {
        return DEFAULT_STARTPOINT;
    }

    @Override
    public String getLanguage() {
        return DEFAULT_LANGUAGE;
    }

    @Override
    public String getLicense() {
        return DEFAULT_LICENSE;
    }

    @Override
    public boolean isFullAutowire() {
        return DEFAULT_FULL_AUTOWIRE;
    }

    @Override
    public String getAuthor() {
        return DEFAULT_AUTHOR;
    }

    @Override
    public String getVersion() {
        return DEFAULT_VERSION;
    }

    @Override
    public File getRootDir() {
        return null;
    }

    @Override
    public File getImagesDir() {
        return null;
    }

    @Override
    public List<ImageFile> getImageFiles() {
        return Collections.emptyList();
    }

    @Override
    public void exportImages(boolean isRoot, File mainExportDir) throws NLBExportException {
        // do nothing
    }

    @Override
    public Map<String, Page> getPages() {
        return Collections.emptyMap();
    }

    @Override
    public List<String> getAutowiredPagesIds() {
        return Collections.emptyList();
    }

    @Override
    public boolean isAutowired(String pageId) {
        return false;
    }

    @Override
    public Page getPageById(String id) {
        return null;
    }

    @Override
    public Map<String, Obj> getObjs() {
        return Collections.emptyMap();
    }

    @Override
    public Obj getObjById(String objId) {
        return null;
    }

    @Override
    public Page createFilteredPage(String sourceId, History history) throws ScriptException {
        throw new UnsupportedOperationException("This operation is unsupported!");
    }

    @Override
    public boolean load(
            String path,
            ProgressData progressData
    ) throws NLBIOException, NLBConsistencyException, NLBVCSException {
        throw new UnsupportedOperationException("This operation is unsupported!");
    }

    @Override
    public Variable getVariableById(String varId) {
        return null;
    }

    @Override
    public List<Variable> getVariables() {
        return Collections.emptyList();
    }

    @Override
    public SearchResultTableModel getLeafs(String modulePageId) {
        return new SearchResultTableModel();
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
        return new SearchResultTableModel();
    }

    @Override
    public SearchResultTableModel getVariables(String modulePageId) throws NLBConsistencyException {
        return new SearchResultTableModel();
    }

    @Override
    public boolean findVariable(String variableNameToFind) throws NLBConsistencyException {
        return false;
    }

    @Override
    public SearchResultTableModel checkBook(String modulePageId) throws NLBConsistencyException {
        return new SearchResultTableModel();
    }

    @Override
    public BookStatistics getBookStatistics() {
        return new BookStatistics();
    }

    @Override
    public VariableStatistics getVariableStatistics() {
        return new VariableStatistics();
    }

    @Override
    public NonLinearBook getParentNLB() {
        return null;
    }

    @Override
    public Page getParentPage() {
        return null;
    }

    @Override
    public Map<String, Variable.DataType> getVariableDataTypes() throws NLBConsistencyException {
        return Collections.emptyMap();
    }
}
