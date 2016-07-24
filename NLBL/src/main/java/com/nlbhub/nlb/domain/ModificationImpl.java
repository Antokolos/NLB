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

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBFileManipulationException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.util.FileManipulator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * The ModificationImpl class
 *
 * @author Anton P. Kolosov
 * @version 1.0 11/28/13
 */
@XmlAccessorType(XmlAccessType.NONE)
public class ModificationImpl extends AbstractIdentifiableItem implements Modification {
    private static final String EXTERNAL_FILE_NAME = "external";
    private static final String VARID_FILE_NAME = "varid";
    private static final String TYPE_FILE_NAME = "type";
    private static final String EXPRID_FILE_NAME = "exprid";
    private static final Set<Type> UNPARAMETRIZED_TYPES = new HashSet<Type>() {{
        add(Type.CNTNR);    // Can be without parameters
        add(Type.CLONE);    // Can be without parameters
        add(Type.SSND);
        add(Type.WSND);
        add(Type.GETTAG);
        add(Type.CLEAR);
        add(Type.CLRINV);
        add(Type.ELSE);
        add(Type.END);
        add(Type.RETURN);
    }};
    private boolean m_external;
    private Type m_type = Type.ASSIGN;
    private String m_varId;
    private String m_exprId;

    /**
     * NB: Before using this constructor, please check, whether it is needed to change parent or not.
     * If you want to change modification parent, please use next constructor below.
     *
     * @param modification
     * @param currentNLB
     */
    public ModificationImpl(Modification modification, NonLinearBook currentNLB) {
        super(modification, currentNLB);
        m_external = modification.isExternal();
        m_type = modification.getType();
        m_varId = modification.getVarId();
        m_exprId = modification.getExprId();
    }

    public ModificationImpl(Modification modification, ModifyingItem parent, NonLinearBook currentNLB) {
        super(modification, parent, currentNLB);
        m_external = modification.isExternal();
        m_type = modification.getType();
        m_varId = modification.getVarId();
        m_exprId = modification.getExprId();
    }

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     * Do not use it for any other purpose!
     */
    public ModificationImpl() {
        super();
    }

    public ModificationImpl(final ModifyingItem parent) {
        super(parent.getCurrentNLB());
        setParent(parent);
    }

    public void copy(Modification modification) {
        super.copy(modification);
        m_external = modification.isExternal();
        m_type = modification.getType();
        m_varId = modification.getVarId();
        m_exprId = modification.getExprId();
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
    public boolean isExternal() {
        return m_external;
    }

    public void setExternal(boolean external) {
        m_external = external;
    }

    @Override
    public boolean returnsValue() {
        switch (m_type) {
            case CNTNR:
            case CLONE:
            case GETTAG:
            case ID:
            case ASSIGN:
            case HAVE:
            case POP:
            case EJECT:
            case SIZE:
            case RND:
            case DSC:
            case ACTT:
            case OBJS:
                return true;
            default:
                // TAG, WHILE, IF, ELSE, ELSEIF, END, RETURN, IFHAVE, ADD, ADDU, ADDINV, ADDALL, ADDALLU,
                // REMOVE, RMINV, CLEAR, CLRINV, SPUSH, WPUSH, PUSH, SINJECT, INJECT, SHUFFLE, PDSC, ACT, USE
                // SSND, WSND, SND, ACHIEVE
                // It is funny, but RETURN operation currently does not actually return anything :)
                return false;
        }
    }

    @Override
    public boolean isParametrized() {
        return !UNPARAMETRIZED_TYPES.contains(m_type);
    }

    @Override
    public SearchResult searchText(SearchContract contract) {
        SearchResult result = super.searchText(contract);
        if (result != null) {
            return result;
        } else {
            if (!isDeleted()) {
                IdentifiableItem parent = getParent();
                Variable variable = getCurrentNLB().getVariableById(m_varId);
                SearchResult resultV = (variable != null) ? variable.searchText(contract) : null;
                if (resultV != null) {
                    if (parent != null) {
                        resultV.setId(parent.getId());
                        resultV.setModulePageId(parent.getCurrentNLB().getParentPage().getId());
                    }
                    return resultV;
                }
                Variable expression = getCurrentNLB().getVariableById(m_exprId);
                SearchResult resultE = (expression != null) ? expression.searchText(contract) : null;
                if (resultE != null) {
                    if (parent != null) {
                        resultE.setId(parent.getId());
                        resultE.setModulePageId(parent.getCurrentNLB().getParentPage().getId());
                    }
                    return resultE;
                }
            }
        }
        return null;
    }

    public void setType(String type) {
        if (type.equals(Type.WHILE.name())) {
            m_type = Type.WHILE;
        } else if (type.equals(Type.TAG.name())) {
            m_type = Type.TAG;
        } else if (type.equals(Type.GETTAG.name())) {
            m_type = Type.GETTAG;
        } else if (type.equals(Type.IF.name())) {
            m_type = Type.IF;
        } else if (type.equals(Type.IFHAVE.name())) {
            m_type = Type.IFHAVE;
        } else if (type.equals(Type.ELSE.name())) {
            m_type = Type.ELSE;
        } else if (type.equals(Type.ELSEIF.name())) {
            m_type = Type.ELSEIF;
        } else if (type.equals(Type.END.name())) {
            m_type = Type.END;
        } else if (type.equals(Type.RETURN.name())) {
            m_type = Type.RETURN;
        } else if (type.equals(Type.HAVE.name())) {
            m_type = Type.HAVE;
        } else if (type.equals(Type.CLONE.name())) {
            m_type = Type.CLONE;
        } else if (type.equals(Type.CNTNR.name())) {
            m_type = Type.CNTNR;
        } else if (type.equals(Type.ID.name())) {
            m_type = Type.ID;
        } else if (type.equals(Type.ADD.name())) {
            m_type = Type.ADD;
        } else if (type.equals(Type.ADDU.name())) {
            m_type = Type.ADDU;
        } else if (type.equals(Type.ADDINV.name())) {
            m_type = Type.ADDINV;
        } else if (type.equals(Type.ADDALL.name())) {
            m_type = Type.ADDALL;
        } else if (type.equals(Type.ADDALLU.name())) {
            m_type = Type.ADDALLU;
        } else if (type.equals(Type.REMOVE.name())) {
            m_type = Type.REMOVE;
        } else if (type.equals(Type.RMINV.name())) {
            m_type = Type.RMINV;
        } else if (type.equals(Type.CLEAR.name())) {
            m_type = Type.CLEAR;
        } else if (type.equals(Type.CLRINV.name())) {
            m_type = Type.CLRINV;
        } else if (type.equals(Type.OBJS.name())) {
            m_type = Type.OBJS;
        } else if (type.equals(Type.SSND.name())) {
            m_type = Type.SSND;
        } else if (type.equals(Type.WSND.name())) {
            m_type = Type.WSND;
        } else if (type.equals(Type.SND.name())) {
            m_type = Type.SND;
        } else if (type.equals(Type.SPUSH.name())) {
            m_type = Type.SPUSH;
        } else if (type.equals(Type.WPUSH.name())) {
            m_type = Type.WPUSH;
        } else if (type.equals(Type.PUSH.name())) {
            m_type = Type.PUSH;
        } else if (type.equals(Type.POP.name())) {
            m_type = Type.POP;
        } else if (type.equals(Type.SINJECT.name())) {
            m_type = Type.SINJECT;
        } else if (type.equals(Type.INJECT.name())) {
            m_type = Type.INJECT;
        } else if (type.equals(Type.EJECT.name())) {
            m_type = Type.EJECT;
        } else if (type.equals(Type.SHUFFLE.name())) {
            m_type = Type.SHUFFLE;
        } else if (type.equals(Type.PRN.name())) {
            m_type = Type.PRN;
        } else if (type.equals(Type.DSC.name())) {
            m_type = Type.DSC;
        } else if (type.equals(Type.PDSC.name())) {
            m_type = Type.PDSC;
        } else if (type.equals(Type.ACT.name())) {
            m_type = Type.ACT;
        } else if (type.equals(Type.ACTT.name())) {
            m_type = Type.ACTT;
        } else if (type.equals(Type.ACTF.name())) {
            m_type = Type.ACTF;
        } else if (type.equals(Type.USE.name())) {
            m_type = Type.USE;
        } else if (type.equals(Type.SIZE.name())) {
            m_type = Type.SIZE;
        } else if (type.equals(Type.RND.name())) {
            m_type = Type.RND;
        }  else if (type.equals(Type.ACHIEVE.name())) {
            m_type = Type.ACHIEVE;
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
            fileManipulator.writeOptionalString(modificationDir, EXTERNAL_FILE_NAME, String.valueOf(m_external), String.valueOf(DEFAULT_EXTERNAL));
            fileManipulator.writeRequiredString(modificationDir, VARID_FILE_NAME, m_varId);
            fileManipulator.writeRequiredString(modificationDir, TYPE_FILE_NAME, m_type.name());
            fileManipulator.writeRequiredString(modificationDir, EXPRID_FILE_NAME, m_exprId);
        }
    }

    public void readModification(
            File modificationDir
    ) throws NLBIOException, NLBConsistencyException {
        setId(modificationDir.getName());
        m_external = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        modificationDir,
                        EXTERNAL_FILE_NAME,
                        String.valueOf(DEFAULT_EXTERNAL)
                )
        );
        m_varId = (
                FileManipulator.getRequiredFileAsString(
                        modificationDir,
                        VARID_FILE_NAME,
                        "Error while reading modification variable Id for modification with Id = " + getId()
                )
        );
        String type = FileManipulator.getRequiredFileAsString(
                modificationDir,
                TYPE_FILE_NAME,
                "Error while reading modification type for modification with Id = " + getId()
        );
        switch (type) {
            case "ASSIGN":
                m_type = Type.ASSIGN;
                break;
            case "TAG":
                m_type = Type.TAG;
                break;
            case "GETTAG":
                m_type = Type.GETTAG;
                break;
            case "WHILE":
                m_type = Type.WHILE;
                break;
            case "IF":
                m_type = Type.IF;
                break;
            case "IFHAVE":
                m_type = Type.IFHAVE;
                break;
            case "ELSE":
                m_type = Type.ELSE;
                break;
            case "ELSEIF":
                m_type = Type.ELSEIF;
                break;
            case "END":
                m_type = Type.END;
                break;
            case "RETURN":
                m_type = Type.RETURN;
                break;
            case "HAVE":
                m_type = Type.HAVE;
                break;
            case "CLONE":
                m_type = Type.CLONE;
                break;
            case "CNTNR":
                m_type = Type.CNTNR;
                break;
            case "ID":
                m_type = Type.ID;
                break;
            case "ADD":
                m_type = Type.ADD;
                break;
            case "ADDU":
                m_type = Type.ADDU;
                break;
            case "ADDINV":
                m_type = Type.ADDINV;
                break;
            case "ADDALL":
                m_type = Type.ADDALL;
                break;
            case "ADDALLU":
                m_type = Type.ADDALLU;
                break;
            case "REMOVE":
                m_type = Type.REMOVE;
                break;
            case "RMINV":
                m_type = Type.RMINV;
                break;
            case "CLEAR":
                m_type = Type.CLEAR;
                break;
            case "CLRINV":
                m_type = Type.CLRINV;
                break;
            case "OBJS":
                m_type = Type.OBJS;
                break;
            case "SSND":
                m_type = Type.SSND;
                break;
            case "WSND":
                m_type = Type.WSND;
                break;
            case "SND":
                m_type = Type.SND;
                break;
            case "SPUSH":
                m_type = Type.SPUSH;
                break;
            case "WPUSH":
                m_type = Type.WPUSH;
                break;
            case "PUSH":
                m_type = Type.PUSH;
                break;
            case "POP":
                m_type = Type.POP;
                break;
            case "SINJECT":
                m_type = Type.SINJECT;
                break;
            case "INJECT":
                m_type = Type.INJECT;
                break;
            case "EJECT":
                m_type = Type.EJECT;
                break;
            case "SHUFFLE":
                m_type = Type.SHUFFLE;
                break;
            case "PRN":
                m_type = Type.PRN;
                break;
            case "DSC":
                m_type = Type.DSC;
                break;
            case "PDSC":
                m_type = Type.PDSC;
                break;
            case "ACT":
                m_type = Type.ACT;
                break;
            case "ACTT":
                m_type = Type.ACTT;
                break;
            case "ACTF":
                m_type = Type.ACTF;
                break;
            case "USE":
                m_type = Type.USE;
                break;
            case "SIZE":
                m_type = Type.SIZE;
                break;
            case "RND":
                m_type = Type.RND;
                break;
            case "ACHIEVE":
                m_type = Type.ACHIEVE;
                break;
            default:
                throw new NLBConsistencyException(
                        "Modification type '" + type
                                + "' cannot be determined for modification with Id = " + getId()
                );
        }
        m_exprId = (
                FileManipulator.getRequiredFileAsString(
                        modificationDir,
                        EXPRID_FILE_NAME,
                        "Error while reading modification expression Id for modification with Id = " + getId()
                )
        );
    }
}
