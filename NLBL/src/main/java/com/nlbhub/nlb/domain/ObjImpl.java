/**
 * @(#)ObjImpl.java
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

import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBFileManipulationException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.util.FileManipulator;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.io.IOException;

/**
 * The ObjImpl class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/19/13
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "obj")
public class ObjImpl extends AbstractNodeItem implements Obj {
    private static final String TEXT_FILE_NAME = "text";
    private static final String VARID_FILE_NAME = "varid";
    private static final String NAME_FILE_NAME = "name";
    private static final String TAKABLE_FILE_NAME = "takable";
    private static final String CONTAINERID_FILE_NAME = "containerid";

    /**
     * Object variable. Will be modified when object is used (act in INSTEAD)
     */
    private String m_varId = DEFAULT_VARID;
    private String m_name = DEFAULT_NAME;
    private String m_text = DEFAULT_TEXT;
    /**
     * Object can be taken to the inventory
     */
    private boolean m_takable = DEFAULT_TAKABLE;
    private String m_containerId = DEFAULT_CONTAINER_ID;

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     */
    public ObjImpl() {
        super();
    }

    @Override
    public SearchResult searchText(String searchText, boolean searchInId, boolean ignoreCase, boolean wholeWords) {
        SearchResult result = super.searchText(searchText, searchInId, ignoreCase, wholeWords);
        if (result != null) {
            return result;
        } else if (
                textMatches(m_text, searchText, ignoreCase, wholeWords)
                        || textMatches(m_name, searchText, ignoreCase, wholeWords)
                ) {
            result = new SearchResult();
            result.setId(getId());
            result.setInformation(m_name);
            return result;
        }
        return null;
    }

    public ObjImpl(float left, float top) {
        super(left, top);
    }

    public void setText(String text) {
        m_text = text;
    }

    @Override
    @XmlElement(name = "text")
    public String getText() {
        return m_text;
    }

    @Override
    @XmlElement(name = "varid")
    public String getVarId() {
        return m_varId;
    }

    public void setVarId(String varId) {
        m_varId = varId;
    }

    @Override
    @XmlElement(name = "name")
    public String getName() {
        return m_name;
    }

    public void setName(String name) {
        m_name = name;
    }

    @Override
    @XmlElement(name = "takable")
    public boolean isTakable() {
        return m_takable;
    }

    public void setTakable(boolean takable) {
        m_takable = takable;
    }

    @Override
    @XmlElement(name = "containerId")
    public String getContainerId() {
        return m_containerId;
    }

    public void setContainerId(String containerId) {
        m_containerId = containerId;
    }

    public void writeObj(
            final @NotNull FileManipulator fileManipulator,
            final @NotNull File objsDir,
            final @NotNull NonLinearBookImpl nonLinearBook
    ) throws IOException, NLBIOException, NLBFileManipulationException, NLBVCSException {
        final File objDir = new File(objsDir, getId());
        if (isDeleted()) {
            // Completely remove obj directory
            fileManipulator.deleteFileOrDir(objDir);
        } else {
            fileManipulator.createDir(
                    objDir,
                    "Cannot create NLB obj directory for obj with Id = " + getId()
            );
            fileManipulator.writeOptionalString(objDir, VARID_FILE_NAME, m_varId, DEFAULT_VARID);
            fileManipulator.writeOptionalString(objDir, NAME_FILE_NAME, m_name, DEFAULT_NAME);
            fileManipulator.writeOptionalString(objDir, TEXT_FILE_NAME, m_text, DEFAULT_TEXT);
            fileManipulator.writeOptionalString(objDir, TAKABLE_FILE_NAME, String.valueOf(m_takable), String.valueOf(DEFAULT_TAKABLE));
            fileManipulator.writeOptionalString(objDir, CONTAINERID_FILE_NAME, m_containerId, DEFAULT_CONTAINER_ID);

            writeModOrderFile(fileManipulator, objDir);
            writeModifications(fileManipulator, objDir);
            writeNodeItemProperties(fileManipulator, objDir, nonLinearBook);
        }
    }

    public void readObj(File objDir) throws NLBIOException, NLBConsistencyException {
        setId(objDir.getName());
        m_varId = (
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        VARID_FILE_NAME,
                        DEFAULT_VARID
                )
        );
        m_name = (
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        NAME_FILE_NAME,
                        DEFAULT_NAME
                )
        );
        m_text = (
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        TEXT_FILE_NAME,
                        DEFAULT_TEXT
                )
        );
        m_takable = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        TAKABLE_FILE_NAME,
                        String.valueOf(DEFAULT_TAKABLE)
                )
        );
        m_containerId = (
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        CONTAINERID_FILE_NAME,
                        DEFAULT_CONTAINER_ID
                )
        );
        readNodeItemProperties(objDir);
        readModifications(objDir);
    }
}
