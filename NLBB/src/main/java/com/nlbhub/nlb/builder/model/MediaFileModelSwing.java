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
import com.nlbhub.nlb.domain.MediaExportParameters;
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
    private static final String NC = "<N/C>";
    private NonLinearBookFacade m_nonLinearBookFacade;
    private MediaFile.Type m_mediaType;
    private Map<String, String> m_namesToIdsMap;
    private Set<String> m_usages;

    public MediaFileModelSwing(NonLinearBookFacade nonLinearBookFacade, final MediaFile.Type mediaType) {
        m_nonLinearBookFacade = nonLinearBookFacade;
        m_mediaType = mediaType;
        m_namesToIdsMap = new HashMap<>();
        m_namesToIdsMap.put(NC, Constants.EMPTY_STRING);
        for (final Variable variable : nonLinearBookFacade.getNlb().getVariables()) {
            if (variable.getType() == TAG) {
                // Only first variable with such name is used
                if (!m_namesToIdsMap.containsKey(variable.getValue())) {
                    m_namesToIdsMap.put(variable.getValue(), variable.getId());
                }
            }
        }
        switch (m_mediaType) {
            case Image:
                m_usages = m_nonLinearBookFacade.getNlb().getUsedImages();
                break;
            case Sound:
                m_usages = m_nonLinearBookFacade.getNlb().getUsedSounds();
                break;
            default:
                m_usages = Collections.emptySet();
        }
    }

    public List<String> getRedirectsValues() {
        List<String> result = new ArrayList<>();
        List<MediaFile> mediaFiles = null;
        switch (m_mediaType) {
            case Image:
                mediaFiles = m_nonLinearBookFacade.getNlb().getImageFiles();
                break;
            case Sound:
                mediaFiles = m_nonLinearBookFacade.getNlb().getSoundFiles();
                break;
        }
        if (mediaFiles != null) {
            for (MediaFile mediaFile : mediaFiles) {
                if (StringHelper.isEmpty(mediaFile.getRedirect())) {
                    result.add(mediaFile.getFileName());
                }
            }
        }
        result.add(NC);
        Collections.sort(result);
        return result;
    }

    public List<String> getConstraintsValues() {
        List<String> result = new ArrayList<>();
        for (String value : m_namesToIdsMap.keySet()) {
            result.add(value);
        }
        Collections.sort(result);
        return result;
    }

    public List<String> getPresetsValues() {
        List<String> result = new ArrayList<>();
        // result.add(MediaExportParameters.Preset.CUSTOM.name()); -- uncomment if CUSTOM presets will be actually used
        result.add(MediaExportParameters.Preset.DEFAULT.name());
        result.add(MediaExportParameters.Preset.NOCHANGE.name());
        result.add(MediaExportParameters.Preset.COMPRESSED.name());
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
        return 6;
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
                final boolean flag = (mediaFile != null) && mediaFile.isFlagged();
                if (flag) {
                    return Constants.YES;
                } else {
                    return Constants.NO;
                }
            case 2:
                MediaFile mediaFile0 = getMediaFile(rowIndex);
                final String redirect = (mediaFile0 != null) ? mediaFile0.getRedirect() : null;
                if (StringHelper.notEmpty(redirect)) {
                    return redirect;
                } else {
                    return Constants.EMPTY_STRING;
                }
            case 3:
                MediaFile mediaFile1 = getMediaFile(rowIndex);
                final String constrId = (mediaFile1 != null) ? mediaFile1.getConstrId() : null;
                if (StringHelper.notEmpty(constrId)) {
                    // Constraint is the named boolean variable that should be defined somewhere
                    final Variable constraint = m_nonLinearBookFacade.getNlb().getVariableById(constrId);
                    return constraint.getValue();
                } else {
                    return Constants.EMPTY_STRING;
                }
            case 4:
                MediaFile mediaFile2 = getMediaFile(rowIndex);
                if (mediaFile2 != null) {
                    return mediaFile2.getMediaExportParameters().getPreset().name();
                } else {
                    return MediaExportParameters.Preset.DEFAULT.name();
                }
            case 5:
                MediaFile mediaFile3 = getMediaFile(rowIndex);
                if ((mediaFile3 != null) && m_usages.contains(mediaFile3.getFileName())) {
                    return Constants.EMPTY_STRING;
                } else {
                    return "UNUSED";
                }
            default:
                return null;
        }
    }

    @Override
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
        String value = (String) aValue;
        switch (columnIndex) {
            case 1:
                if (Constants.YES.equalsIgnoreCase(value)) {
                    setMediaFileFlag(rowIndex, true);
                } else {
                    setMediaFileFlag(rowIndex, false);
                }
                break;
            case 2:
                if (NC.equalsIgnoreCase(value)) {
                    setMediaFileRedirect(rowIndex, Constants.EMPTY_STRING);
                } else {
                    setMediaFileRedirect(rowIndex, value);
                }
                break;
            case 3:
                setMediaFileConstrId(rowIndex, m_namesToIdsMap.get(value));
                break;
            case 4:
                setMediaFileExportParameters(rowIndex, value);
                break;
            default:
                super.setValueAt(aValue, rowIndex, columnIndex);
        }
    }

    private void setMediaFileExportParameters(int rowIndex, String value) {
        MediaFile mediaFile = getMediaFile(rowIndex);
        if (mediaFile != null) {
            MediaExportParameters.Preset preset = MediaExportParameters.Preset.valueOf(value);
            m_nonLinearBookFacade.setMediaFileExportParametersPreset(m_mediaType, mediaFile.getFileName(), preset);
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

    private void setMediaFileRedirect(int rowIndex, String redirect) {
        MediaFile mediaFile = getMediaFile(rowIndex);
        if (mediaFile != null) {
            m_nonLinearBookFacade.setMediaFileRedirect(m_mediaType, mediaFile.getFileName(), redirect);
        }
    }

    private void setMediaFileFlag(int rowIndex, boolean flag) {
        MediaFile mediaFile = getMediaFile(rowIndex);
        if (mediaFile != null) {
            m_nonLinearBookFacade.setMediaFileFlag(m_mediaType, mediaFile.getFileName(), flag);
        }
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return "Media file name";
            case 1:
                return (m_mediaType == MediaFile.Type.Image) ? "BG" : "SFX";
            case 2:
                return "Redirect";
            case 3:
                return "Constraint";
            case 4:
                return "Export Parameters";
            case 5:
                return "Usages";
            default:
                return super.getColumnName(column);
        }
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
        return (columnIndex > 0) && (columnIndex <= 4);
    }
}
