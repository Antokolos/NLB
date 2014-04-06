/**
 * @(#)ModificationsTableModel.java
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

import com.nlbhub.nlb.domain.*;
import com.nlbhub.nlb.util.StringHelper;

import java.util.*;

/**
 * The ModificationsTableModel class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/16/14
 */
public class ModificationsTableModel {
    private List<ModificationImpl> m_modifications;
    private Map<String, VariableImpl> m_variableMap;

    public ModificationsTableModel(
        final NonLinearBook nlb,
        final List<Modification> modifications
    ) {
        m_modifications = new ArrayList<>();
        m_variableMap = new HashMap<>();
        // Adds COPIES of original modifications to the list
        for (final Modification modification : modifications) {
            Variable variable = nlb.getVariableById(modification.getVarId());
            if (variable != null) {
                m_variableMap.put(modification.getVarId(), new VariableImpl(variable));
            }

            Variable expression = nlb.getVariableById(modification.getExprId());
            if (expression != null) {
                m_variableMap.put(modification.getExprId(), new VariableImpl(expression));
            }

            m_modifications.add(new ModificationImpl(modification));
        }
    }

    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return "Modification Id";
            case 1:
                return "Variable";
            case 2:
                return "Operation";
            case 3:
                return "Expression";
            default:
                return "N/A";
        }
    }

    public int getRowCount() {
        int i = 0;
        for (Modification modification : m_modifications) {
            if (!modification.isDeleted()) {
                ++i;
            }
        }
        return i;
    }

    public int getColumnCount() {
        return 4;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        final Modification modification = getModificationAt(rowIndex);
        switch (columnIndex) {
            case 0:
                return modification.getId();
            case 1:
                String varId = modification.getVarId();
                final Variable variable = m_variableMap.get(varId);
                return (variable != null) ? variable.getName() : "";
            case 2:
                return modification.getType();
            case 3:
                String exprId = modification.getExprId();
                final Variable expression = m_variableMap.get(exprId);
                return (expression != null) ? expression.getValue() : "";
            default:
                return "N/A";

        }
    }

    public boolean setValueAt(Object aValue, int rowIndex, int columnIndex) {
        ModificationImpl modification = getModificationAt(rowIndex);
        final String cellValue = (String) aValue;
        switch (columnIndex) {
            case 1:
                String prevVarId = modification.getVarId();
                VariableImpl variable = m_variableMap.get(prevVarId);
                if (variable != null) {
                    if (StringHelper.isEmpty(cellValue)) {
                        variable.setDeleted(true);
                        modification.setVarId("");
                    } else {
                        variable.setName(cellValue);
                    }
                } else {
                    if (!StringHelper.isEmpty(cellValue)) {
                        variable = (
                            new VariableImpl(
                                Variable.Type.VAR,
                                cellValue,
                                Variable.NA,
                                modification.getFullId()
                            )
                        );
                        m_variableMap.put(variable.getId(), variable);
                        modification.setVarId(variable.getId());
                    }
                }
                break;
            case 2:
                modification.setType(cellValue);
                break;
            case 3:
                String prevExprId = modification.getExprId();
                VariableImpl expression = m_variableMap.get(prevExprId);
                if (expression != null) {
                    if (StringHelper.isEmpty(cellValue)) {
                        expression.setDeleted(true);
                        modification.setExprId("");
                    } else {
                        expression.setValue(cellValue);
                    }
                } else {
                    if (!StringHelper.isEmpty(cellValue)) {
                        expression = (
                            new VariableImpl(
                                Variable.Type.EXPRESSION,
                                Variable.NA,
                                cellValue,
                                modification.getFullId()
                            )
                        );
                        m_variableMap.put(expression.getId(), expression);
                        modification.setExprId(expression.getId());
                    }
                }
                break;
            default:
                return false;
        }
        return true;
    }

    private ModificationImpl getModificationAt(int rowIndex) {
        int i = 0;
        for (ModificationImpl modification : m_modifications) {
            if (!modification.isDeleted()) {
                if (i == rowIndex) {
                    return modification;
                }
                ++i;
            }
        }
        return null;
    }

    public List<String> getModificationIdsAt(int[] rowIndexArray) {
        List<String> result = new LinkedList<>();
        for (final int rowIndex : rowIndexArray) {
            result.add((getModificationAt(rowIndex)).getId());
        }
        return result;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex > 0;
    }

    public void add(ModifyingItem modifyingItem) {
        m_modifications.add(new ModificationImpl(modifyingItem));
    }

    public void remove(final List<String> modificationIds) {
        for (ModificationImpl modification : m_modifications) {
            if (modificationIds.contains(modification.getId())) {
                modification.setDeleted(true);
            }
        }
    }

    public List<Modification> getModifications() {
        List<Modification> result = new ArrayList<>();
        result.addAll(m_modifications);
        return result;
    }

    public Map<String, Variable> getVariableMap() {
        Map<String, Variable> result = new HashMap<>();
        result.putAll(m_variableMap);
        return result;
    }
}
