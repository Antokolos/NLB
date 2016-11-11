/**
 * @(#)MediaFileImpl.java
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
package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.MediaFile;


/**
 * The MediaFileImpl class
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class MediaFileImpl implements MediaFile {
    private String m_fileName;
    private String m_redirect;
    private String m_constrId;
    private boolean m_flagged;
    private MediaExportParameters.Preset m_preset = MediaExportParameters.Preset.DEFAULT;

    public MediaFileImpl(String fileName) {
        m_fileName = fileName;
    }

    @Override
    public String getFileName() {
        return m_fileName;
    }

    public void setFileName(String fileName) {
        m_fileName = fileName;
    }

    @Override
    public String getRedirect() {
        return m_redirect;
    }

    public void setRedirect(String redirect) {
        m_redirect = redirect;
    }

    @Override
    public String getConstrId() {
        return m_constrId;
    }

    public void setConstrId(final String constrId) {
        m_constrId = constrId;
    }

    @Override
    public boolean isFlagged() {
        return m_flagged;
    }

    @Override
    public MediaExportParameters getMediaExportParameters() {
        return MediaExportParameters.fromPreset(m_preset);
    }

    public MediaExportParameters.Preset getPreset() {
        return m_preset;
    }

    public void setPreset(MediaExportParameters.Preset preset) {
        m_preset = preset;
    }

    public void setFlagged(boolean flagged) {
        m_flagged = flagged;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MediaFileImpl mediaFile = (MediaFileImpl) o;

        if (m_flagged != mediaFile.m_flagged) return false;
        if (!m_fileName.equals(mediaFile.m_fileName)) return false;
        if (m_redirect != null ? !m_redirect.equals(mediaFile.m_redirect) : mediaFile.m_redirect != null) return false;
        if (m_constrId != null ? !m_constrId.equals(mediaFile.m_constrId) : mediaFile.m_constrId != null) return false;
        return m_preset == mediaFile.m_preset;

    }

    @Override
    public int hashCode() {
        int result = m_fileName.hashCode();
        result = 31 * result + (m_redirect != null ? m_redirect.hashCode() : 0);
        result = 31 * result + (m_constrId != null ? m_constrId.hashCode() : 0);
        result = 31 * result + (m_flagged ? 1 : 0);
        result = 31 * result + (m_preset != null ? m_preset.hashCode() : 0);
        return result;
    }

    @Override
    public int compareTo(MediaFile o) {
        return m_fileName.compareTo(o.getFileName());
    }
}
