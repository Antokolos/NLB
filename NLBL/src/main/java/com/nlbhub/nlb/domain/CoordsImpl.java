/**
 * @(#)CoordsImpl.java
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

import com.nlbhub.nlb.api.Coords;
import com.nlbhub.nlb.exception.NLBFileManipulationException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.util.FileManipulator;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.File;
import java.io.IOException;

/**
 * The CoordsImpl class
 *
 * @author Anton P. Kolosov
 * @version 1.0 11/1/13
 */
@XmlAccessorType(XmlAccessType.NONE)
public class CoordsImpl implements Coords {
    private static final String LEFT_FILE_NAME = "left";
    private static final String TOP_FILE_NAME = "top";
    private static final String WIDTH_FILE_NAME = "width";
    private static final String HEIGHT_FILE_NAME = "height";
    private float m_left = (float) 0.0;
    private float m_top = (float) 0.0;
    private float m_width = (float) 0.0;
    private float m_height = (float) 0.0;

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     */
    public CoordsImpl() {
    }

    public float getLeft() {
        return m_left;
    }

    public void setLeft(float left) {
        m_left = left;
    }

    public float getTop() {
        return m_top;
    }

    public void setTop(float top) {
        m_top = top;
    }

    public float getWidth() {
        return m_width;
    }

    public void setWidth(float width) {
        m_width = width;
    }

    public float getHeight() {
        return m_height;
    }

    public void setHeight(float height) {
        m_height = height;
    }

    public void writeCoords(
            FileManipulator fileManipulator,
            File coordsDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        fileManipulator.writeRequiredString(coordsDir, LEFT_FILE_NAME, String.valueOf(m_left));
        fileManipulator.writeRequiredString(coordsDir, TOP_FILE_NAME, String.valueOf(m_top));
        fileManipulator.writeRequiredString(coordsDir, WIDTH_FILE_NAME, String.valueOf(m_width));
        fileManipulator.writeRequiredString(coordsDir, HEIGHT_FILE_NAME, String.valueOf(m_height));
    }

    public void read(File coordsDir) throws NLBIOException {
        try {
            final String coordsDirPath = coordsDir.getCanonicalPath();
            m_left = Float.parseFloat(
                    FileManipulator.getRequiredFileAsString(
                            coordsDir,
                            LEFT_FILE_NAME,
                            "Error reading left coords file; dir = " + coordsDirPath
                    )
            );
            m_top = Float.parseFloat(
                    FileManipulator.getRequiredFileAsString(
                            coordsDir,
                            TOP_FILE_NAME,
                            "Error reading top coords file; dir = " + coordsDirPath
                    )
            );
            m_width = Float.parseFloat(
                    FileManipulator.getRequiredFileAsString(
                            coordsDir,
                            WIDTH_FILE_NAME,
                            "Error reading width coords file; dir = " + coordsDirPath
                    )
            );
            m_height = Float.parseFloat(
                    FileManipulator.getRequiredFileAsString(
                            coordsDir,
                            HEIGHT_FILE_NAME,
                            "Error reading height coords file; dir = " + coordsDirPath
                    )
            );
        } catch (IOException e) {
            throw new NLBIOException("IOException occurred", e);
        }
    }
}
