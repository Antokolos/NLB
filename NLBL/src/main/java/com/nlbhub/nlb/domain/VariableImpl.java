/**
 * @(#)VariableImpl.java
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

import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBFileManipulationException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.util.FileManipulator;

import java.io.File;

/**
 * The VariableImpl class
 *
 * @author Anton P. Kolosov
 * @version 1.0 11/20/13
 */
public class VariableImpl extends AbstractIdentifiableItem implements Variable {
    private static final String TYPE_FILE_NAME = "type";
    private static final String NAME_FILE_NAME = "name";
    private static final String TARGET_FILE_NAME = "target";
    private static final String VALUE_FILE_NAME = "value";
    private Type m_type = Type.PAGE;
    private String m_name = DEFAULT_NAME;
    private String m_target;
    private String m_value = DEFAULT_VALUE;

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     */
    public VariableImpl() {
        super();
    }

    @Override
    public SearchResult searchText(String searchText, boolean searchInId, boolean ignoreCase, boolean wholeWords) {
        SearchResult result = super.searchText(searchText, searchInId, ignoreCase, wholeWords);
        if (result != null) {
            return result;
        } else {
            result = new SearchResult();
            if (
                !DEFAULT_NAME.equals(m_name)
                && textMatches(m_name, searchText, ignoreCase, wholeWords)
            ) {
                result.setId(getId());
                result.setInformation(m_name);
                return result;
            } else if (
                !DEFAULT_VALUE.equals(m_value)
                && textMatches(m_value, searchText, ignoreCase, wholeWords)
            ) {
                result.setId(getId());
                result.setInformation(m_value);
                return result;
            }
        }
        return null;
    }

    public VariableImpl(Variable variable) {
        super(variable);
        m_type = variable.getType();
        m_name = variable.getName();
        m_target = variable.getTarget();
        m_value = variable.getValue();
    }

    public void copy(Variable variable) {
        super.copy(variable);
        m_type = variable.getType();
        m_name = variable.getName();
        m_target = variable.getTarget();
        m_value = variable.getValue();
    }

    public VariableImpl(Type type, String name, String value, String target) {
        this();
        m_type = type;
        m_name = name;
        m_value = value;
        m_target = target;
    }

    @Override
    public Type getType() {
        return m_type;
    }

    public void setType(Type type) {
        m_type = type;
    }

    @Override
    public String getName() {
        return m_name;
    }

    @Override
    public String getTarget() {
        return m_target;
    }

    public void setTarget(String target) {
        m_target = target;
    }

    public void setName(String name) {
        m_name = name;
    }

    @Override
    public String getValue() {
        return m_value;
    }

    public void setValue(String value) {
        m_value = value;
    }

    public void readVariable(File varDir) throws NLBIOException, NLBConsistencyException {
        setId(varDir.getName());
        String type = FileManipulator.getRequiredFileAsString(
                varDir,
                TYPE_FILE_NAME,
                "Error while reading variable type for variable with Id = " + getId()
        );
        switch (type) {
            case "PAGE":
                m_type = Type.PAGE;
                break;
            case "OBJ":
                m_type = Type.OBJ;
                break;
            case "LINK":
                m_type = Type.LINK;
                break;
            case "LINKCONSTRAINT":
                m_type = Type.LINKCONSTRAINT;
                break;
            case "VAR":
                m_type = Type.VAR;
                break;
            case "EXPRESSION":
                m_type = Type.EXPRESSION;
                break;
            case "MODCONSTRAINT":
                m_type = Type.MODCONSTRAINT;
                break;
            default:
                throw new NLBConsistencyException(
                    "Variable type '" + type
                    + "' cannot be determined for variable with Id = " + getId()
                );
        }
        m_name = FileManipulator.getOptionalFileAsString(
            varDir,
            NAME_FILE_NAME,
            DEFAULT_NAME
        );
        m_value = FileManipulator.getOptionalFileAsString(
            varDir,
            VALUE_FILE_NAME,
            DEFAULT_VALUE
        );
        m_target = FileManipulator.getRequiredFileAsString(
                varDir,
                TARGET_FILE_NAME,
                "Error while reading variable target for variable with Id = " + getId()
        );
    }

    public void writeVariable(
        FileManipulator fileManipulator,
        File varsDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        final File varDir = new File(varsDir, getId());
        if (isDeleted()) {
            // Completely remove variable directory
            fileManipulator.deleteFileOrDir(varDir);
        } else {
            fileManipulator.createDir(
                varDir,
                "Cannot create NLB variable directory for variable with Id = " + getId()
            );
            fileManipulator.writeRequiredString(varDir, TYPE_FILE_NAME, m_type.name());
            fileManipulator.writeOptionalString(varDir, NAME_FILE_NAME, m_name, DEFAULT_NAME);
            fileManipulator.writeOptionalString(varDir, VALUE_FILE_NAME, m_value, DEFAULT_VALUE);
            fileManipulator.writeRequiredString(varDir, TARGET_FILE_NAME, m_target);
        }
    }
}
