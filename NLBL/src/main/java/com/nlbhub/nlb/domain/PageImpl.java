/**
 * @(#)PageImpl.java
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
import com.nlbhub.nlb.util.StringHelper;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * The PageImpl class
 *
 * @author Anton P. Kolosov
 * @version 1.0 7/6/12
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "page")
public class PageImpl extends AbstractNodeItem implements Page {
    private static final String TEXT_FILE_NAME = "text";
    private static final String VARID_FILE_NAME = "varid";
    private static final String CAPTION_FILE_NAME = "caption";
    private static final String USE_CAPT_FILE_NAME = "use_capt";
    private static final String MODULE_SUBDIR_NAME = "module";
    private static final String MODNAME_FILE_NAME = "modname";
    private static final String TRAVTEXT_FILE_NAME = "travtext";
    private static final String RETTEXT_FILE_NAME = "rettext";
    private static final String RETPAGE_FILE_NAME = "retpage";
    private static final String MODCNSID_FILE_NAME = "modcnsid";

    private static final String DEFAULT_MODULE_NAME_FORMAT = "%s's submodule";
    private static final String DEFAULT_TRAVERSE_TEXT_FORMAT = "Go to %s";
    /**
     * Set to empty String.
     * This means that by default pages should not return to the parent module
     * */
    private static final String DEFAULT_RETURN_TEXT = Constants.EMPTY_STRING;

    private String m_varId;
    private String m_caption = Constants.EMPTY_STRING;
    private boolean m_useCaption = false;
    private String m_text = Constants.EMPTY_STRING;
    private String m_moduleName;
    private String m_traverseText;
    private String m_returnText;
    private String m_returnPageId = Constants.EMPTY_STRING;
    private String m_moduleConstrId;

    private NonLinearBook m_currentNLB;
    private NonLinearBookImpl m_module;

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     */
    public PageImpl() {
        super();
        init();
    }

    public PageImpl(NonLinearBook currentNLB) {
        super();
        m_currentNLB = currentNLB;
        init();
    }

    private void init() {
        m_module = new NonLinearBookImpl(m_currentNLB, this);
        m_moduleName = String.format(DEFAULT_MODULE_NAME_FORMAT, getId());
        m_traverseText = String.format(DEFAULT_TRAVERSE_TEXT_FORMAT, m_moduleName);
        m_returnText = DEFAULT_RETURN_TEXT;
        m_returnPageId = Constants.EMPTY_STRING;
    }

    @Override
    public SearchResult searchText(String searchText, boolean searchInId, boolean ignoreCase, boolean wholeWords) {
        SearchResult result = super.searchText(searchText, searchInId, ignoreCase, wholeWords);
        if (result != null) {
            return result;
        } else if (
            textMatches(m_text, searchText, ignoreCase, wholeWords)
            || textMatches(m_caption, searchText, ignoreCase, wholeWords)
        ) {
            result = new SearchResult();
            result.setId(getId());
            result.setInformation(m_caption);
            return result;
        }
        return null;
    }

    public PageImpl(NonLinearBook currentNLB, float left, float top) {
        super(left, top);
        m_currentNLB = currentNLB;
        init();
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
    @XmlElement(name = "moduleconstrid")
    public String getModuleConstrId() {
        return m_moduleConstrId;
    }

    public void setModuleConstrId(String moduleConstrId) {
        m_moduleConstrId = moduleConstrId;
    }

    @Override
    @XmlElement(name = "caption")
    public String getCaption() {
        return m_caption;
    }

    public void setCaption(String caption) {
        m_caption = caption;
    }

    @Override
    @XmlElement(name = "usecaption")
    public boolean isUseCaption() {
        return m_useCaption;
    }

    @Override
    public boolean isLeaf() {
        return getLinkCount() == 0;
    }

    @Override
    public String getTraverseText() {
        return m_traverseText;
    }

    @Override
    public String getReturnText() {
        return m_returnText;
    }

    @Override
    @XmlElement(name = "return-page-id")
    public String getReturnPageId() {
        return m_returnPageId;
    }

    public void setReturnPageId(String returnPageId) {
        m_returnPageId = returnPageId;
    }

    @Override
    public boolean shouldReturn() {
        return !StringHelper.isEmpty(m_returnText);
    }

    @Override
    public String getModuleName() {
        return m_moduleName;
    }

    public void setModuleName(String moduleName) {
        m_moduleName = moduleName;
    }

    public void setTraverseText(String traverseText) {
        m_traverseText = traverseText;
    }

    public void setReturnText(String returnText) {
        m_returnText = returnText;
    }

    @Override
    public NonLinearBook getModule() {
        return m_module;
    }

    /**
     * For internal use only!
     * @return
     */
    public NonLinearBookImpl getModuleImpl() {
        return m_module;
    }

    public void setUseCaption(boolean useCaption) {
        m_useCaption = useCaption;
    }

    public void writePage(
        final @NotNull FileManipulator fileManipulator,
        final @NotNull File pagesDir,
        final @NotNull NonLinearBookImpl nonLinearBook
    ) throws
        IOException,
        NLBIOException,
        NLBFileManipulationException,
        NLBVCSException,
        NLBConsistencyException {
        final File pageDir = new File(pagesDir, getId());
        if (isDeleted()) {
            // Completely remove page directory
            fileManipulator.deleteDir(pageDir);
        } else {
            fileManipulator.createDir(
                pageDir,
                "Cannot create NLB page directory for page with Id = " + getId()
            );
            m_module.setRootDir(new File(pageDir, MODULE_SUBDIR_NAME));
            m_module.save(fileManipulator);
            fileManipulator.writeString(pageDir, VARID_FILE_NAME, m_varId);
            fileManipulator.writeString(pageDir, CAPTION_FILE_NAME, m_caption);
            fileManipulator.writeString(pageDir, USE_CAPT_FILE_NAME, String.valueOf(m_useCaption));
            fileManipulator.writeString(pageDir, TEXT_FILE_NAME, m_text);
            fileManipulator.writeString(pageDir, MODNAME_FILE_NAME, m_moduleName);
            fileManipulator.writeString(pageDir, TRAVTEXT_FILE_NAME, m_traverseText);
            fileManipulator.writeString(pageDir, RETTEXT_FILE_NAME, m_returnText);
            fileManipulator.writeString(pageDir, RETPAGE_FILE_NAME, m_returnPageId);
            fileManipulator.writeString(pageDir, MODCNSID_FILE_NAME, m_moduleConstrId);
            writeModOrderFile(fileManipulator, pageDir);
            writeModifications(fileManipulator, pageDir);
            writeNodeItemProperties(fileManipulator, pageDir, nonLinearBook);
        }
    }

    public void readPage(
        final File pageDir
    ) throws NLBIOException, NLBConsistencyException, NLBVCSException {
        try {
            setId(pageDir.getName());
            final File moduleDir = new File(pageDir, MODULE_SUBDIR_NAME);
            m_module.loadAndSetParent(moduleDir.getCanonicalPath(), m_currentNLB, this);
            m_varId = (
                FileManipulator.getFileAsString(
                    pageDir,
                    VARID_FILE_NAME,
                    "Error while reading page variable Id for page with Id = " + getId()
                )
            );
            m_caption = (
                FileManipulator.getFileAsString(
                    pageDir,
                    CAPTION_FILE_NAME,
                    "Error while reading page caption for page with Id = " + getId()
                )
            );
            m_useCaption = "true".equals(
                FileManipulator.getFileAsString(
                    pageDir,
                    USE_CAPT_FILE_NAME,
                    "Error while reading page use caption flag for page with Id = " + getId()
                )
            );
            m_text = (
                FileManipulator.getFileAsString(
                    pageDir,
                    TEXT_FILE_NAME,
                    "Error while reading page text for page with Id = " + getId()
                )
            );
            m_moduleName = (
                FileManipulator.getOptionalFileAsString(
                    pageDir,
                    MODNAME_FILE_NAME
                )
            );
            m_traverseText = (
                FileManipulator.getOptionalFileAsString(
                    pageDir,
                    TRAVTEXT_FILE_NAME
                )
            );
            m_returnText = (
                FileManipulator.getOptionalFileAsString(
                    pageDir,
                    RETTEXT_FILE_NAME
                )
            );
            m_returnPageId = (
                FileManipulator.getOptionalFileAsString(
                    pageDir,
                    RETPAGE_FILE_NAME
                )
            );
            m_moduleConstrId = (
                FileManipulator.getOptionalFileAsString(
                    pageDir,
                    MODCNSID_FILE_NAME
                )
            );
            readNodeItemProperties(pageDir);
            readModifications(pageDir);
        } catch (IOException e) {
            throw new NLBIOException(
                "Cannot get module dir canonical path for page with Id = " + getId(),
                e
            );
        }
    }

    public static PageImpl createFilteredClone(
        final PageImpl source,
        final List<String> linkIdsToBeExcluded,
        List<Link> linksToBeAdded
    ) {
        PageImpl result = new PageImpl();
        result.setId(source.getId());
        result.setText(source.getText());
        final Coords sourceCoords = source.getCoords();
        final CoordsImpl resultCoords = result.getCoords();
        resultCoords.setLeft(sourceCoords.getLeft());
        resultCoords.setTop(sourceCoords.getTop());
        resultCoords.setWidth(sourceCoords.getWidth());
        resultCoords.setHeight(sourceCoords.getHeight());
        result.setDeleted(source.isDeleted());
        result.setReturnPageId(source.getReturnPageId());
        AbstractNodeItem.filterTargetLinkList(result, source, linkIdsToBeExcluded);
        for (Link link : linksToBeAdded) {
            result.addLink(new LinkImpl(source, link));
        }
        return result;
    }
}
