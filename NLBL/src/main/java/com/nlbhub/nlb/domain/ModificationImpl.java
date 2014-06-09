/**
 * @(#)ModificationImpl.java
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

import com.nlbhub.nlb.api.IdentifiableItem;
import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.ModifyingItem;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBFileManipulationException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.util.FileManipulator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.File;

/**
 * The ModificationImpl class
 *
 * @author Anton P. Kolosov
 * @version 1.0 11/28/13
 */
@XmlAccessorType(XmlAccessType.NONE)
public class ModificationImpl extends AbstractIdentifiableItem implements Modification {
    private static final String VARID_FILE_NAME = "varid";
    private static final String TYPE_FILE_NAME = "type";
    private static final String EXPRID_FILE_NAME = "exprid";
    private Type m_type = Type.ASSIGN;
    private String m_varId;
    private String m_exprId;

    public ModificationImpl(Modification modification) {
        super(modification);
        m_type = modification.getType();
        m_varId = modification.getVarId();
        m_exprId = modification.getExprId();
    }

    public ModificationImpl(final ModifyingItem parent) {
        super();
        setParent(parent);
    }

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public ModificationImpl() {
    }

    @Override
    public String getVarId() {
        return m_varId;
    }

    public void setVarId(String varId) {
        m_varId = varId;
    }

    @Override
    public String getExprId() {
        return m_exprId;
    }

    public void setExprId(String exprId) {
        m_exprId = exprId;
    }

    @Override
    public Type getType() {
        return m_type;
    }

    @Override
    public boolean isDeleted() {
        final IdentifiableItem parent = getParent();
        final boolean parentDeleted = (parent != null) && parent.isDeleted();
        return super.isDeleted() || parentDeleted;
    }

    @Override
    public SearchResult searchText(String searchText, boolean searchInId, boolean ignoreCase, boolean wholeWords) {
        // TODO: implement search in modifications
        return null;
    }

    public void setType(String type) {
        if (type.equals(Type.ADD.name())) {
            m_type = Type.ADD;
        } else if (type.equals(Type.SUBTRACT.name())) {
            m_type = Type.SUBTRACT;
        } else {
            m_type = Type.ASSIGN;
        }
    }

    public void writeModification(
        FileManipulator fileManipulator,
        File modificationsDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        final File modificationDir = new File(modificationsDir, getId());
        if (isDeleted()) {
            // Completely remove modification directory
            fileManipulator.deleteFileOrDir(modificationDir);
        } else {
            fileManipulator.createDir(
                modificationDir,
                "Cannot create NLB modification directory for modification with Id = " + getId()
            );
            fileManipulator.writeRequiredString(modificationDir, VARID_FILE_NAME, m_varId);
            fileManipulator.writeRequiredString(modificationDir, TYPE_FILE_NAME, m_type.name());
            fileManipulator.writeRequiredString(modificationDir, EXPRID_FILE_NAME, m_exprId);
        }
    }

    public void readModification(
        File modificationDir
    ) throws NLBIOException, NLBConsistencyException {
        setId(modificationDir.getName());
        m_varId = (
            FileManipulator.getFileAsString(
                modificationDir,
                VARID_FILE_NAME,
                "Error while reading modification variable Id for modification with Id = " + getId()
            )
        );
        String type = FileManipulator.getFileAsString(
            modificationDir,
            TYPE_FILE_NAME,
            "Error while reading modification type for modification with Id = " + getId()
        );
        switch (type) {
            case "ASSIGN":
                m_type = Type.ASSIGN;
                break;
            case "ADD":
                m_type = Type.ADD;
                break;
            case "SUBTRACT":
                m_type = Type.SUBTRACT;
                break;
            default:
                throw new NLBConsistencyException(
                    "Modification type '" + type
                    + "' cannot be determined for modification with Id = " + getId()
                );
        }
        m_exprId = (
            FileManipulator.getFileAsString(
                modificationDir,
                EXPRID_FILE_NAME,
                "Error while reading modification expression Id for modification with Id = " + getId()
            )
        );
    }
}
