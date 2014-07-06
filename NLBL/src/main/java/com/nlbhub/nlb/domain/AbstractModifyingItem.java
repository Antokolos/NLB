/**
 * @(#)AbstractModifyingItem.java
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
package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.ModifyingItem;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBFileManipulationException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.util.FileManipulator;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The AbstractModifyingItem class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/2/13
 */
@XmlAccessorType(XmlAccessType.NONE)
public abstract class AbstractModifyingItem extends AbstractIdentifiableItem implements ModifyingItem {
    private static final String MODIFICATIONS_DIR_NAME = "modifications";
    private static final String MODORDER_FILE_NAME = "modorder";
    private static final String MODORDER_SEPARATOR = "\n";

    private List<ModificationImpl> m_modifications = new ArrayList<>();

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     * Do not use it for any other purpose!
     */
    public AbstractModifyingItem() {
        super();
    }

    protected AbstractModifyingItem(NonLinearBook currentNLB) {
        super(currentNLB);
    }

    @Override
    public List<Modification> getModifications() {
        List<Modification> result = new ArrayList<>();
        result.addAll(getModificationImpls());
        return result;
    }

    @XmlElement(name = "modification")
    public List<ModificationImpl> getModificationImpls() {
        return m_modifications;
    }

    public void addModification(@NotNull ModificationImpl modification) {
        m_modifications.add(modification);
    }

    @Override
    public ModificationImpl getModificationById(@NotNull String modId) {
        for (final ModificationImpl modification : m_modifications) {
            if (modId.equals(modification.getId())) {
                return modification;
            }
        }
        return null;
    }

    protected void writeModifications(
            FileManipulator fileManipulator,
            File itemDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        final File modificationsDir = new File(itemDir, MODIFICATIONS_DIR_NAME);
        fileManipulator.createDir(
                modificationsDir,
                "Cannot create modifications directory for item with Id = " + getId()
        );
        for (ModificationImpl modification : m_modifications) {
            modification.writeModification(fileManipulator, modificationsDir);
        }
    }

    protected void readModifications(File itemDir) throws NLBIOException, NLBConsistencyException {
        String modOrderString = FileManipulator.getOptionalFileAsString(
                itemDir,
                MODORDER_FILE_NAME,
                DEFAULT_MODORDER
        );
        final File modsDir = new File(itemDir, MODIFICATIONS_DIR_NAME);
        if (!modsDir.exists() && !modOrderString.isEmpty()) {
            throw new NLBIOException(
                    "Invalid NLB structure: modifications directory does not exist for item with Id = "
                            + getId()
                            + ", but some modifications should be specified"
            );
        }
        m_modifications.clear();
        File[] modDirs = modsDir.listFiles();
        if (modDirs == null) {
            if (modOrderString.isEmpty()) {
                modDirs = new File[0];
            } else {
                throw new NLBIOException(
                        "Error when enumerating modifications' directory contents for item with Id = " + getId()
                );
            }
        }
        if (modOrderString.isEmpty()) {
            if (modDirs.length > 0) {
                throw new NLBConsistencyException(
                        "Inconsistent NLB structure: '" + MODIFICATIONS_DIR_NAME + "' directory "
                                + "should be empty for item with id = "
                                + getId()
                );
            }
        } else {
            List<String> modOrderList = Arrays.asList(modOrderString.split(MODORDER_SEPARATOR));
            List<File> modDirsSortedList = createSortedDirList(modDirs, modOrderList);

            for (int i = 0; i < modDirsSortedList.size(); i++) {
                final ModificationImpl modification = new ModificationImpl(this);
                modification.readModification(modDirsSortedList.get(i));
                m_modifications.add(modification);
            }
        }
    }

    protected void writeModOrderFile(
            FileManipulator fileManipulator,
            File itemDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        StringBuilder sb = new StringBuilder();
        final int lastElemIndex = m_modifications.size() - 1;
        if (lastElemIndex >= 0) {
            for (int i = 0; i < lastElemIndex; i++) {
                final ModificationImpl modification = m_modifications.get(i);
                if (!modification.isDeleted()) {
                    sb.append(modification.getId()).append(MODORDER_SEPARATOR);
                }
            }
            if (!m_modifications.get(lastElemIndex).isDeleted()) {
                sb.append(m_modifications.get(lastElemIndex).getId());
            }
            fileManipulator.writeOptionalString(itemDir, MODORDER_FILE_NAME, String.valueOf(sb.toString()), DEFAULT_MODORDER);
        } else {
            fileManipulator.writeOptionalString(itemDir, MODORDER_FILE_NAME, Constants.EMPTY_STRING, DEFAULT_MODORDER);
        }
    }
}
