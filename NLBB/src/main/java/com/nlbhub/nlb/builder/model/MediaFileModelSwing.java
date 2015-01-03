/**
 * @(#)MediaFileModelSwing.java
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
package com.nlbhub.nlb.builder.model;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.MediaFile;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.util.StringHelper;

import javax.swing.table.AbstractTableModel;
import java.util.*;

import static com.nlbhub.nlb.api.Variable.Type.TAG;

/**
 * The MediaFileModelSwing class
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class MediaFileModelSwing extends AbstractTableModel {
    private NonLinearBookFacade m_nonLinearBookFacade;
    private MediaFile.Type m_mediaType;
    private Map<String, String> m_namesToIdsMap;

    public MediaFileModelSwing(NonLinearBookFacade nonLinearBookFacade, final MediaFile.Type mediaType) {
        m_nonLinearBookFacade = nonLinearBookFacade;
        m_mediaType = mediaType;
        m_namesToIdsMap = new HashMap<>();
        m_namesToIdsMap.put("<N/C>", Constants.EMPTY_STRING);
        for (final Variable variable : nonLinearBookFacade.getNlb().getVariables()) {
            if (variable.getType() == TAG) {
                // Only first variable with such name is used
                if (!m_namesToIdsMap.containsKey(variable.getValue())) {
                    m_namesToIdsMap.put(variable.getValue(), variable.getId());
                }
            }
        }
    }

    public List<String> getConstraintsValues() {
        List<String> result = new ArrayList<>();
        for (String value : m_namesToIdsMap.keySet()) {
            result.add(value);
        }
        Collections.sort(result);
        return result;
    }

    @Override
    public int getRowCount() {
        switch (m_mediaType) {
            case Image:
                return m_nonLinearBookFacade.getNlb().getImageFiles().size();
            case Sound:
                return m_nonLinearBookFacade.getNlb().getSoundFiles().size();
            default:
                return -1;
        }
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                switch (m_mediaType) {
                    case Image:
                        return m_nonLinearBookFacade.getNlb().getImageFiles().get(rowIndex).getFileName();
                    case Sound:
                        return m_nonLinearBookFacade.getNlb().getSoundFiles().get(rowIndex).getFileName();
                    default:
                        return null;
                }
            case 1:
                MediaFile mediaFile = getMediaFile(rowIndex);
                final String constrId = (mediaFile != null) ? mediaFile.getConstrId() : null;
                if (StringHelper.notEmpty(constrId)) {
                    // Constraint is the named boolean variable that should be defined somewhere
                    final Variable constraint = m_nonLinearBookFacade.getNlb().getVariableById(constrId);
                    return constraint.getValue();
                } else {
                    return Constants.EMPTY_STRING;
                }
            default:
                return null;
        }
    }

    @Override
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
        switch (columnIndex) {
            case 1:
                String value = (String) aValue;
                setMediaFileConstrId(rowIndex, m_namesToIdsMap.get(value));
                break;
            default:
                super.setValueAt(aValue, rowIndex, columnIndex);
        }
    }

    private MediaFile getMediaFile(int rowIndex) {
        switch (m_mediaType) {
            case Image:
                return m_nonLinearBookFacade.getNlb().getImageFiles().get(rowIndex);
            case Sound:
                return m_nonLinearBookFacade.getNlb().getSoundFiles().get(rowIndex);
            default:
                return null;
        }
    }

    private void setMediaFileConstrId(int rowIndex, String constrId) {
        MediaFile mediaFile = getMediaFile(rowIndex);
        if (mediaFile != null) {
            m_nonLinearBookFacade.setMediaFileConstrId(m_mediaType, mediaFile.getFileName(), constrId);
        }
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return "Media file name";
            case 1:
                return "Constraint";
            default:
                return super.getColumnName(column);
        }
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
        return columnIndex == 1;
    }
}
