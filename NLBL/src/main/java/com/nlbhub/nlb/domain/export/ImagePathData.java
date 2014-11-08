/**
 * @(#)ImagePathData.java
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
package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.api.Constants;

/**
 * The ImagePathData class
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class ImagePathData {
    public static final ImagePathData EMPTY = new ImagePathData() {{
        setParentFolderPath(Constants.EMPTY_STRING);
        setFileName(Constants.EMPTY_STRING);
        setMaxFrameNumber(0);
        setFileExtension(Constants.EMPTY_STRING);
    }};
    private String m_parentFolderPath;
    private String m_fileName;
    private int m_maxFrameNumber;
    private String m_fileExtension;

    public String getParentFolderPath() {
        return m_parentFolderPath;
    }

    public void setParentFolderPath(final String parentFolderPath) {
        m_parentFolderPath = parentFolderPath;
    }

    public String getFileName() {
        return m_fileName;
    }

    public void setFileName(final String fileName) {
        m_fileName = fileName;
    }

    public int getMaxFrameNumber() {
        return m_maxFrameNumber;
    }

    public void setMaxFrameNumber(final int maxFrameNumber) {
        m_maxFrameNumber = maxFrameNumber;
    }

    public String getFileExtension() {
        return m_fileExtension;
    }

    public void setFileExtension(final String fileExtension) {
        m_fileExtension = fileExtension;
    }

    public String getImagePath() {
        if (m_maxFrameNumber == 0) {
            return m_parentFolderPath + "/" + m_fileName + m_fileExtension;
        } else {
            return m_parentFolderPath + "/" + m_fileName + "%d" + m_fileExtension;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ImagePathData that = (ImagePathData) o;

        if (m_maxFrameNumber != that.m_maxFrameNumber) return false;
        if (m_fileExtension != null ? !m_fileExtension.equals(that.m_fileExtension) : that.m_fileExtension != null)
            return false;
        if (m_fileName != null ? !m_fileName.equals(that.m_fileName) : that.m_fileName != null) return false;
        if (m_parentFolderPath != null ? !m_parentFolderPath.equals(that.m_parentFolderPath) : that.m_parentFolderPath != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = m_parentFolderPath != null ? m_parentFolderPath.hashCode() : 0;
        result = 31 * result + (m_fileName != null ? m_fileName.hashCode() : 0);
        result = 31 * result + m_maxFrameNumber;
        result = 31 * result + (m_fileExtension != null ? m_fileExtension.hashCode() : 0);
        return result;
    }
}
