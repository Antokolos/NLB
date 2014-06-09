/**
 * @(#)LinkImpl.java
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
 * Copyright (c) 2012 Anton P. Kolosov All rights reserved.
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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;

/**
 * The LinkImpl class represents relationship between two Non-Linear Book pages.
 *
 * @author Anton P. Kolosov
 * @version 1.0 8/9/12
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "link")
public class LinkImpl extends AbstractModifyingItem implements Link {
    private static final String COORDS_DIR_NAME = "coords";
    private static final String VARID_FILE_NAME = "varid";
    private static final String TARGET_FILE_NAME = "target";
    private static final String TEXT_FILE_NAME = "text";
    private static final String CONSTRID_FILE_NAME = "constrid";
    private static final String STROKE_FILE_NAME = "stroke";
    private String m_varId = DEFAULT_VAR_ID;
    /** Id of the target page of the link (to which this link leads). */
    private String m_target = DEFAULT_TARGET;
    /** Link text. */
    private String m_text = DEFAULT_TEXT;
    /** Link constraint Id. */
    private String m_constrId = DEFAULT_CONSTR_ID;
    /** Link stroke color (RGB). */
    private String m_stroke = DEFAULT_STROKE;
    private CoordsImpl m_coords = new CoordsImpl();
    private ObserverHandler m_observerHandler = new ObserverHandler();
    private boolean m_isPositiveConstraint = true;
    private boolean m_isObeyToModuleConstraint = true;
    private boolean m_isTraversalLink = false;
    private boolean m_isReturnLink = false;

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     */
    public LinkImpl() {
    }

    public LinkImpl(final NodeItem parent) {
        super();
        setParent(parent);
    }

    public LinkImpl(final NodeItem parent, final Link sourceLink) {
        super();
        setId(sourceLink.getId());
        setDeleted(sourceLink.isDeleted());
        setParent(parent);
        for (Modification modification : sourceLink.getModifications()) {
            addModification(new ModificationImpl(modification));
        }
        m_varId = sourceLink.getVarId();
        m_target = sourceLink.getTarget();
        m_text = sourceLink.getText();
        m_constrId = sourceLink.getConstrId();
        m_stroke = sourceLink.getStroke();
        m_coords.setLeft(sourceLink.getCoords().getLeft());
        m_coords.setTop(sourceLink.getCoords().getTop());
        m_coords.setWidth(sourceLink.getCoords().getWidth());
        m_coords.setHeight(sourceLink.getCoords().getHeight());
        m_isPositiveConstraint = sourceLink.isPositiveConstraint();
        m_isObeyToModuleConstraint = sourceLink.isObeyToModuleConstraint();
        m_isTraversalLink = sourceLink.isTraversalLink();
        m_isReturnLink = sourceLink.isReturnLink();
    }

    public LinkImpl(final NodeItem parent, String target) {
        this(parent);
        m_target = target;
    }

    @Override
    public boolean isDeleted() {
        final IdentifiableItem parent = getParent();
        final boolean parentDeleted = (parent != null) && parent.isDeleted();
        return super.isDeleted() || parentDeleted;
    }

    @Override
    public SearchResult searchText(
        String searchText,
        boolean searchInId,
        boolean ignoreCase,
        boolean wholeWords
    ) {
        SearchResult result = super.searchText(searchText, searchInId, ignoreCase, wholeWords);
        if (result != null) {
            return result;
        } else if (textMatches(m_text, searchText, ignoreCase, wholeWords)) {
            result = new SearchResult();
            result.setId(getId());
            result.setInformation(m_text);
            return result;
        }
        return null;
    }

    @XmlElement(name = "varid")
    public String getVarId() {
        return m_varId;
    }

    public void setVarId(String varId) {
        m_varId = varId;
    }

    @XmlElement(name = "target")
    public String getTarget() {
        return m_target;
    }

    public void setTarget(String target) {
        m_target = target;
    }

    @XmlElement(name = "text")
    public String getText() {
        return m_text;
    }

    public void setText(String text) {
        m_text = text;
    }

    @XmlElement(name = "constrid")
    public String getConstrId() {
        return m_constrId;
    }

    @Override
    @XmlElement(name = "is-positive")
    public boolean isPositiveConstraint() {
        // Normal links always has positive constraints, unless it was cloned from LinkLw
        return m_isPositiveConstraint;
    }

    @Override
    @XmlElement(name = "is-obey-to-module-constraint")
    public boolean isObeyToModuleConstraint() {
        // Normal links always should obey to module constraints, unless it was cloned from LinkLw
        return m_isObeyToModuleConstraint;
    }

    @Override
    @XmlElement(name = "is-traversal")
    public boolean isTraversalLink() {
        return m_isTraversalLink;
    }

    @Override
    @XmlElement(name = "is-return")
    public boolean isReturnLink() {
        return m_isReturnLink;
    }

    public void setConstrId(String constrId) {
        m_constrId = constrId;
    }

    @XmlElement(name = "stroke")
    public String getStroke() {
        return m_stroke;
    }

    public void setStroke(String stroke) {
        m_stroke = stroke;
    }

    public CoordsImpl getCoords() {
        return m_coords;
    }

    private void writeCoords(
        FileManipulator fileManipulator,
        File linkDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        final File coordsDir = new File(linkDir, COORDS_DIR_NAME);
        fileManipulator.createDir(
            coordsDir,
            "Cannot create link text block coords directory for link with Id = " + getId()
        );
        m_coords.writeCoords(fileManipulator, coordsDir);
    }

    private void readCoords(File linkDir) throws NLBIOException {
        final File coordsDir = new File(linkDir, COORDS_DIR_NAME);
        if (!coordsDir.exists()) {
            throw new NLBIOException(
                "Invalid NLB structure: coords directory does not exist for link with Id = " + getId()
            );
        }
        m_coords.read(coordsDir);
    }

    public void writeLink(
        FileManipulator fileManipulator,
        File linksDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        final File linkDir = new File(linksDir, getId());
        if (isDeleted()) {
            // Completely remove link directory
            fileManipulator.deleteFileOrDir(linkDir);
        } else {
            fileManipulator.createDir(
                linkDir,
                "Cannot create NLB link directory for link with Id = " + getId()
            );
            fileManipulator.writeString(linkDir, VARID_FILE_NAME, m_varId, DEFAULT_VAR_ID);
            fileManipulator.writeString(linkDir, TARGET_FILE_NAME, m_target, DEFAULT_TARGET);
            fileManipulator.writeString(linkDir, TEXT_FILE_NAME, m_text, DEFAULT_TEXT);
            fileManipulator.writeString(linkDir, CONSTRID_FILE_NAME, m_constrId, DEFAULT_CONSTR_ID);
            fileManipulator.writeString(linkDir, STROKE_FILE_NAME, m_stroke, DEFAULT_STROKE);
            writeCoords(fileManipulator, linkDir);
            writeModOrderFile(fileManipulator, linkDir);
            writeModifications(fileManipulator, linkDir);
        }
    }

    public void readLink(File linkDir) throws NLBIOException, NLBConsistencyException {
        setId(linkDir.getName());
        m_varId = (
            FileManipulator.getOptionalFileAsString(
                    linkDir,
                    VARID_FILE_NAME,
                    DEFAULT_VAR_ID
            )
        );
        // Target is not optional, default target indicates an error
        m_target = (
            FileManipulator.getFileAsString(
                    linkDir,
                    TARGET_FILE_NAME,
                    "Error while reading link target for link with Id = " + getId()
            )
        );
        m_text = (
            FileManipulator.getOptionalFileAsString(
                    linkDir,
                    TEXT_FILE_NAME,
                    DEFAULT_TEXT
            )
        );
        m_constrId = (
            FileManipulator.getOptionalFileAsString(
                    linkDir,
                    CONSTRID_FILE_NAME,
                    DEFAULT_CONSTR_ID
            )
        );
        m_stroke = (
            FileManipulator.getOptionalFileAsString(
                    linkDir,
                    STROKE_FILE_NAME,
                    DEFAULT_STROKE
            )
        );
        readCoords(linkDir);
        readModifications(linkDir);
    }

    @Override
    public String addObserver(NLBObserver observer) {
        return m_observerHandler.addObserver(observer);
    }

    @Override
    public void removeObserver(String observerId) {
        m_observerHandler.removeObserver(observerId);
    }

    @Override
    public void notifyObservers() {
        m_observerHandler.notifyObservers();
    }
}
