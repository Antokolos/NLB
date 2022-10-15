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
import com.nlbhub.nlb.util.MultiLangString;

import jakarta.xml.bind.annotation.*;
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
    private static final String TEXT_SUBDIR_NAME = "text";
    private static final String ALT_TEXT_SUBDIR_NAME = "alt_text";
    private static final String CONSTRID_FILE_NAME = "constrid";
    private static final String STROKE_FILE_NAME = "stroke";
    private static final String AUTO_FILE_NAME = "auto";
    private static final String ONCE_FILE_NAME = "once";
    private String m_varId = DEFAULT_VAR_ID;
    /**
     * Id of the target page of the link (to which this link leads).
     */
    private String m_target = DEFAULT_TARGET;
    /**
     * Link text.
     */
    private MultiLangString m_text = DEFAULT_TEXT;
    /**
     * Link alt text (when link is inaccessible).
     */
    private MultiLangString m_altText = DEFAULT_ALT_TEXT;
    /**
     * Link constraint Id.
     */
    private String m_constrId = DEFAULT_CONSTR_ID;
    /**
     * Link stroke color (RGB).
     */
    private String m_stroke = DEFAULT_STROKE;
    private CoordsImpl m_coords = new CoordsImpl();
    private boolean m_auto = DEFAUlT_AUTO;
    private boolean m_once = DEFAUlT_ONCE;
    private ObserverHandler m_observerHandler = new ObserverHandler();
    private boolean m_isPositiveConstraint = true;
    private boolean m_isObeyToModuleConstraint = true;
    private boolean m_isTraversalLink = false;
    private boolean m_isReturnLink = false;

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     * Do not use it for any other purpose!
     */
    public LinkImpl() {
        super();
    }

    public LinkImpl(final NodeItem parent) {
        super(parent.getCurrentNLB());
        setParent(parent);
    }

    public LinkImpl(final NodeItem parent, final Link sourceLink) {
        super(parent.getCurrentNLB());
        setId(sourceLink.getId());
        setDeleted(sourceLink.isDeleted());
        setParent(parent);
        for (Modification modification : sourceLink.getModifications()) {
            addModification(new ModificationImpl(modification, this, parent.getCurrentNLB()));
        }
        m_varId = sourceLink.getVarId();
        m_target = sourceLink.getTarget();
        m_text = sourceLink.getTexts();
        m_altText = sourceLink.getAltTexts();
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
        m_auto = sourceLink.isAuto();
        m_once = sourceLink.isOnce();
    }

    public LinkImpl(final NodeItem parent, String target) {
        this(parent);
        m_target = target;
    }

    @Override
    public SearchResult searchText(SearchContract contract) {
        SearchResult result = super.searchText(contract);
        if (result != null) {
            return result;
        } else if (textMatches(m_text, contract)) {
            result = new SearchResult();
            result.setId(getId());
            result.setInformation(getText());
            return result;
        } else if (textMatches(m_altText, contract)) {
            result = new SearchResult();
            result.setId(getId());
            result.setInformation(getAltText());
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
        return m_text.get(getCurrentNLB().getLanguage());
    }

    @Override
    public MultiLangString getTexts() {
        return MultiLangString.createCopy(m_text);
    }

    @XmlElement(name = "alt-text")
    @Override
    public String getAltText() {
        return m_altText.get(getCurrentNLB().getLanguage());
    }

    @Override
    public MultiLangString getAltTexts() {
        return MultiLangString.createCopy(m_altText);
    }

    public void setTexts(final MultiLangString text) {
        m_text = text;
    }

    public void setText(String text) {
        m_text.put(getCurrentNLB().getLanguage(), text);
    }

    public void setAltTexts(final MultiLangString altText) {
        m_altText = altText;
    }

    public void setAltText(String altText) {
        m_altText.put(getCurrentNLB().getLanguage(), altText);
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

    @Override
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

    @Override
    @XmlElement(name = "is-auto")
    public boolean isAuto() {
        return m_auto;
    }

    public void setAuto(boolean auto) {
        m_auto = auto;
    }

    @Override
    @XmlElement(name = "is-once")
    public boolean isOnce() {
        return m_once;
    }

    public void setOnce(boolean once) {
        m_once = once;
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
            fileManipulator.writeOptionalString(linkDir, VARID_FILE_NAME, m_varId, DEFAULT_VAR_ID);
            fileManipulator.writeOptionalString(linkDir, TARGET_FILE_NAME, m_target, DEFAULT_TARGET);
            fileManipulator.writeOptionalMultiLangString(
                    new File(linkDir, TEXT_SUBDIR_NAME),
                    m_text,
                    DEFAULT_TEXT
            );
            fileManipulator.writeOptionalMultiLangString(
                    new File(linkDir, ALT_TEXT_SUBDIR_NAME),
                    m_altText,
                    DEFAULT_ALT_TEXT
            );
            fileManipulator.writeOptionalString(linkDir, CONSTRID_FILE_NAME, m_constrId, DEFAULT_CONSTR_ID);
            fileManipulator.writeOptionalString(linkDir, STROKE_FILE_NAME, m_stroke, DEFAULT_STROKE);
            fileManipulator.writeOptionalString(
                    linkDir,
                    AUTO_FILE_NAME,
                    String.valueOf(m_auto),
                    String.valueOf(DEFAUlT_AUTO)
            );
            fileManipulator.writeOptionalString(
                    linkDir,
                    ONCE_FILE_NAME,
                    String.valueOf(m_once),
                    String.valueOf(DEFAUlT_ONCE)
            );
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
                FileManipulator.getRequiredFileAsString(
                        linkDir,
                        TARGET_FILE_NAME,
                        "Error while reading link target for link with Id = " + getId()
                )
        );
        m_text = (
                FileManipulator.readOptionalMultiLangString(
                        new File(linkDir, TEXT_SUBDIR_NAME),
                        DEFAULT_TEXT
                )
        );
        m_altText = (
                FileManipulator.readOptionalMultiLangString(
                        new File(linkDir, ALT_TEXT_SUBDIR_NAME),
                        DEFAULT_ALT_TEXT
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
        m_auto = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        linkDir,
                        AUTO_FILE_NAME,
                        String.valueOf(DEFAUlT_AUTO)
                )
        );
        m_once = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        linkDir,
                        ONCE_FILE_NAME,
                        String.valueOf(DEFAUlT_ONCE)
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
