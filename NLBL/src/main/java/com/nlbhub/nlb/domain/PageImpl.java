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
import com.nlbhub.nlb.util.MultiLangString;
import com.nlbhub.nlb.util.StringHelper;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The PageImpl class
 *
 * @author Anton P. Kolosov
 * @version 1.0 7/6/12
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "page")
public class PageImpl extends AbstractNodeItem implements Page {
    private static final String TEXT_SUBDIR_NAME = "text";
    private static final String IMAGE_FILE_NAME = "image";
    private static final String SOUND_FILE_NAME = "sound";
    private static final String VARID_FILE_NAME = "varid";
    private static final String CAPTION_SUBDIR_NAME = "caption";
    private static final String USE_CAPT_FILE_NAME = "use_capt";
    private static final String MODULE_SUBDIR_NAME = "module";
    private static final String MODNAME_FILE_NAME = "modname";
    private static final String TRAVTEXT_FILE_NAME = "travtext";
    private static final String AUTOTRAV_FILE_NAME = "autotrav";
    private static final String AUTORET_FILE_NAME = "autoret";
    private static final String RETTEXT_SUBDIR_NAME = "rettext";
    private static final String RETPAGE_FILE_NAME = "retpage";
    private static final String MODCNSID_FILE_NAME = "modcnsid";
    private static final String AUTO_IN_TEXT_SUBDIR_NAME = "aintext";
    private static final String AUTO_OUT_TEXT_SUBDIR_NAME = "aouttext";
    private static final String AUTO_IN_FILE_NAME = "auto_in";
    private static final String AUTO_OUT_FILE_NAME = "auto_out";
    private static final String AUTOWIRE_IN_CONSTRID_FILE_NAME = "autoid";
    private static final String AUTOWIRE_OUT_CONSTRID_FILE_NAME = "autoutid";

    private static final String DEFAULT_MODULE_NAME_FORMAT = "%s's submodule";
    private String m_imageFileName = DEFAULT_IMAGE_FILE_NAME;
    private String m_soundFileName = DEFAULT_SOUND_FILE_NAME;
    private String m_varId = DEFAULT_VARID;
    private MultiLangString m_caption = DEFAULT_CAPTION;
    private boolean m_useCaption = DEFAULT_USE_CAPTION;
    private MultiLangString m_text = DEFAULT_TEXT;
    private String m_moduleName;
    private String m_defaultModuleName;
    private MultiLangString m_traverseText;
    private boolean m_autoTraverse = DEFAULT_AUTO_TRAVERSE;
    private boolean m_autoReturn = DEFAULT_AUTO_RETURN;
    private MultiLangString m_returnText = DEFAULT_RETURN_TEXT;
    private String m_returnPageId = DEFAULT_RETURN_PAGE_ID;
    private String m_moduleConstrId = DEFAULT_MODULE_CONSTR_ID;

    private NonLinearBookImpl m_module;

    private MultiLangString m_autowireInText = DEFAULT_AUTOWIRE_IN_TEXT;
    private MultiLangString m_autowireOutText = DEFAULT_AUTOWIRE_OUT_TEXT;

    private boolean m_autoIn = DEFAULT_AUTO_IN;
    private boolean m_autoOut = DEFAULT_AUTO_OUT;
    private String m_autowireInConstrId = DEFAULT_AUTOWIRE_IN_CONSTR_ID;
    private String m_autowireOutConstrId = DEFAULT_AUTOWIRE_OUT_CONSTR_ID;

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     * Do not use it for any other purpose!
     */
    public PageImpl() {
        super();
    }

    public PageImpl(NonLinearBook currentNLB) {
        super(currentNLB);
        init();
    }

    /**
     * NB: Please take into account that it will create full copy, including ids and such
     */
    public PageImpl(PageImpl source) {
        super(source);
        m_imageFileName = source.getImageFileName();
        m_soundFileName = source.getSoundFileName();
        setVarId(source.getVarId());
        setCaptions(source.getCaptions());
        setUseCaption(source.isUseCaption());
        setTexts(source.getTexts());
        setModuleName(source.getModuleName());
        resetDefaultModuleName();
        setTraverseTexts(source.getTraverseTexts());
        setAutoTraverse(source.isAutoTraverse());
        setAutoReturn(source.isAutoReturn());
        setReturnTexts(source.getReturnTexts());
        setReturnPageId(source.getReturnPageId());
        setModuleConstrId(source.getModuleConstrId());
        m_module = new NonLinearBookImpl(getCurrentNLB(), this);
        m_module.append(source.getModuleImpl());
        setAutowireInTexts(source.getAutowireInTexts());
        setAutowireOutTexts(source.getAutowireOutTexts());
        setAutoIn(source.isAutoIn());
        setAutoOut(source.isAutoOut());
        setAutowireInConstrId(source.getAutowireInConstrId());
        setAutowireOutConstrId(source.getAutowireOutConstrId());
    }

    private void init() {
        m_module = new NonLinearBookImpl(getCurrentNLB(), this);
        resetDefaultModuleName();
        m_moduleName = m_defaultModuleName;
        m_traverseText = MultiLangString.createCopy(DEFAULT_TRAVERSE_TEXT);
    }

    private void resetDefaultModuleName() {
        m_defaultModuleName = String.format(DEFAULT_MODULE_NAME_FORMAT, getId());
    }

    @Override
    public SearchResult searchText(SearchContract contract) {
        SearchResult result = super.searchText(contract);
        if (result != null) {
            return result;
        } else if (
                textMatches(m_text, contract)
                        || textMatches(m_caption, contract)
                        || textMatches(m_returnText, contract)
                        || textMatches(m_traverseText, contract)
                        || textMatches(m_autowireInText, contract)
                        || textMatches(m_autowireOutText, contract)
                        || textMatches(m_imageFileName, contract)
                        || textMatches(m_soundFileName, contract)
                        || textMatches(m_moduleName, contract)
                ) {
            result = new SearchResult();
            result.setId(getId());
            result.setInformation(getCaption());
            return result;
        }
        return null;
    }

    public PageImpl(NonLinearBook currentNLB, float left, float top) {
        super(currentNLB, left, top);
        init();
    }

    public void setImageFileName(String imageFileName) {
        m_imageFileName = imageFileName;
    }

    @Override
    public String getImageFileName() {
        return m_imageFileName;
    }

    public void setSoundFileName(String soundFileName) {
        m_soundFileName = soundFileName;
    }

    @Override
    public String getSoundFileName() {
        return m_soundFileName;
    }

    public void setText(String text) {
        m_text.put(getCurrentNLB().getLanguage(), text);
    }

    @Override
    @XmlElement(name = "text")
    public String getText() {
        return m_text.get(getCurrentNLB().getLanguage());
    }

    @Override
    public MultiLangString getTexts() {
        return MultiLangString.createCopy(m_text);
    }

    public void setTexts(final MultiLangString text) {
        m_text = text;
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
        return m_caption.get(getCurrentNLB().getLanguage());
    }

    @Override
    public MultiLangString getCaptions() {
        return MultiLangString.createCopy(m_caption);
    }

    public void setCaptions(final MultiLangString caption) {
        m_caption = caption;
    }

    public void setCaption(String caption) {
        m_caption.put(getCurrentNLB().getLanguage(), caption);
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
        return m_traverseText.get(getCurrentNLB().getLanguage());
    }

    @Override
    public MultiLangString getTraverseTexts() {
        return MultiLangString.createCopy(m_traverseText);
    }

    public void setTraverseTexts(final MultiLangString traverseText) {
        m_traverseText = traverseText;
    }

    @Override
    @XmlElement(name = "is-auto-traverse")
    public boolean isAutoTraverse() {
        return m_autoTraverse;
    }

    public void setAutoTraverse(boolean autoTraverse) {
        m_autoTraverse = autoTraverse;
    }

    @Override
    @XmlElement(name = "is-auto-return")
    public boolean isAutoReturn() {
        return m_autoReturn;
    }

    public void setAutoReturn(boolean autoReturn) {
        m_autoReturn = autoReturn;
    }

    @Override
    public String getReturnText() {
        return m_returnText.get(getCurrentNLB().getLanguage());
    }

    @Override
    public MultiLangString getReturnTexts() {
        return MultiLangString.createCopy(m_returnText);
    }

    public void setReturnTexts(final MultiLangString returnText) {
        m_returnText = returnText;
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
        return !StringHelper.isEmpty(m_returnText) || m_autoReturn;
    }

    @Override
    public String getModuleName() {
        return m_moduleName;
    }

    public void setModuleName(String moduleName) {
        m_moduleName = moduleName;
    }

    public void setTraverseText(String traverseText) {
        m_traverseText.put(getCurrentNLB().getLanguage(), traverseText);
    }

    public void setReturnText(String returnText) {
        m_returnText.put(getCurrentNLB().getLanguage(), returnText);
    }

    @Override
    public NonLinearBook getModule() {
        return m_module;
    }

    public void setAutoIn(boolean autoIn) {
        m_autoIn = autoIn;
    }

    public void setAutoOut(boolean autoOut) {
        m_autoOut = autoOut;
    }

    public void setAutowireInConstrId(String autowireInConstrId) {
        m_autowireInConstrId = autowireInConstrId;
    }

    public void setAutowireOutConstrId(String autowireOutConstrId) {
        m_autowireOutConstrId = autowireOutConstrId;
    }

    @Override
    public boolean isAutowire() {
        return getCurrentNLB().isAutowired(getId());
    }

    @Override
    public String getAutowireInText() {
        return m_autowireInText.get(getCurrentNLB().getLanguage());
    }

    @Override
    public MultiLangString getAutowireInTexts() {
        return MultiLangString.createCopy(m_autowireInText);
    }

    public void setAutowireInText(String autowireInText) {
        m_autowireInText.put(getCurrentNLB().getLanguage(), autowireInText);
    }

    public void setAutowireInTexts(MultiLangString autowireInText) {
        m_autowireInText = autowireInText;
    }

    @Override
    public String getAutowireOutText() {
        return m_autowireOutText.get(getCurrentNLB().getLanguage());
    }

    @Override
    public MultiLangString getAutowireOutTexts() {
        return MultiLangString.createCopy(m_autowireOutText);
    }

    public void setAutowireOutText(String autowireOutText) {
        m_autowireOutText.put(getCurrentNLB().getLanguage(), autowireOutText);
    }

    public void setAutowireOutTexts(MultiLangString autowireOutText) {
        m_autowireOutText = autowireOutText;
    }

    @Override
    public boolean isAutoIn() {
        return m_autoIn;
    }

    @Override
    public boolean isAutoOut() {
        return m_autoOut;
    }

    @Override
    public String getAutowireInConstrId() {
        return m_autowireInConstrId;
    }

    @Override
    public String getAutowireOutConstrId() {
        return m_autowireOutConstrId;
    }

    /**
     * For internal use only!
     *
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
            final @NotNull NonLinearBookImpl nonLinearBook,
            final @NotNull PartialProgressData partialProgressData
    ) throws
            IOException,
            NLBIOException,
            NLBFileManipulationException,
            NLBVCSException,
            NLBConsistencyException {
        final File pageDir = new File(pagesDir, getId());
        if (isDeleted()) {
            // Completely remove page directory
            fileManipulator.deleteFileOrDir(pageDir);
        } else {
            fileManipulator.createDir(
                    pageDir,
                    "Cannot create NLB page directory for page with Id = " + getId()
            );
            final File moduleDir = new File(pageDir, MODULE_SUBDIR_NAME);
            if (m_module.isEmpty()) {
                if (moduleDir.exists()) {
                    fileManipulator.deleteFileOrDir(moduleDir);
                }
            } else {
                m_module.setRootDir(moduleDir);
                m_module.save(fileManipulator, new DummyProgressData(), partialProgressData);
            }
            fileManipulator.writeOptionalString(pageDir, VARID_FILE_NAME, m_varId, DEFAULT_VARID);
            fileManipulator.writeOptionalMultiLangString(
                    new File(pageDir, CAPTION_SUBDIR_NAME),
                    m_caption,
                    DEFAULT_CAPTION
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    USE_CAPT_FILE_NAME,
                    String.valueOf(m_useCaption),
                    String.valueOf(DEFAULT_USE_CAPTION)
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    IMAGE_FILE_NAME,
                    m_imageFileName,
                    DEFAULT_IMAGE_FILE_NAME
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    SOUND_FILE_NAME,
                    m_soundFileName,
                    DEFAULT_SOUND_FILE_NAME
            );
            fileManipulator.writeOptionalMultiLangString(
                    new File(pageDir, TEXT_SUBDIR_NAME),
                    m_text,
                    DEFAULT_TEXT
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    MODNAME_FILE_NAME,
                    m_moduleName,
                    m_defaultModuleName
            );
            fileManipulator.writeOptionalMultiLangString(
                    new File(pageDir, TRAVTEXT_FILE_NAME),
                    m_traverseText,
                    DEFAULT_TRAVERSE_TEXT
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    AUTOTRAV_FILE_NAME,
                    String.valueOf(m_autoTraverse),
                    String.valueOf(DEFAULT_AUTO_TRAVERSE)
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    AUTORET_FILE_NAME,
                    String.valueOf(m_autoReturn),
                    String.valueOf(DEFAULT_AUTO_RETURN)
            );
            fileManipulator.writeOptionalMultiLangString(
                    new File(pageDir, RETTEXT_SUBDIR_NAME),
                    m_returnText,
                    DEFAULT_RETURN_TEXT
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    RETPAGE_FILE_NAME,
                    m_returnPageId,
                    DEFAULT_RETURN_PAGE_ID
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    MODCNSID_FILE_NAME,
                    m_moduleConstrId,
                    DEFAULT_MODULE_CONSTR_ID
            );
            fileManipulator.writeOptionalMultiLangString(
                    new File(pageDir, AUTO_IN_TEXT_SUBDIR_NAME),
                    m_autowireInText,
                    DEFAULT_AUTOWIRE_IN_TEXT
            );
            fileManipulator.writeOptionalMultiLangString(
                    new File(pageDir, AUTO_OUT_TEXT_SUBDIR_NAME),
                    m_autowireOutText,
                    DEFAULT_AUTOWIRE_OUT_TEXT
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    AUTO_IN_FILE_NAME,
                    String.valueOf(m_autoIn),
                    String.valueOf(DEFAULT_AUTO_IN)
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    AUTO_OUT_FILE_NAME,
                    String.valueOf(m_autoOut),
                    String.valueOf(DEFAULT_AUTO_OUT)
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    AUTOWIRE_IN_CONSTRID_FILE_NAME,
                    m_autowireInConstrId,
                    DEFAULT_AUTOWIRE_IN_CONSTR_ID
            );
            fileManipulator.writeOptionalString(
                    pageDir,
                    AUTOWIRE_OUT_CONSTRID_FILE_NAME,
                    m_autowireOutConstrId,
                    DEFAULT_AUTOWIRE_OUT_CONSTR_ID
            );
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
            resetDefaultModuleName();
            final File moduleDir = new File(pageDir, MODULE_SUBDIR_NAME);
            m_module.loadAndSetParent(moduleDir.getCanonicalPath(), getCurrentNLB(), this);
            m_varId = (
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            VARID_FILE_NAME,
                            DEFAULT_VARID
                    )
            );
            m_caption = (
                    FileManipulator.readOptionalMultiLangString(
                            new File(pageDir, CAPTION_SUBDIR_NAME),
                            DEFAULT_CAPTION
                    )
            );
            m_useCaption = "true".equals(
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            USE_CAPT_FILE_NAME,
                            String.valueOf(DEFAULT_USE_CAPTION)
                    )
            );
            m_imageFileName = (
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            IMAGE_FILE_NAME,
                            DEFAULT_IMAGE_FILE_NAME
                    )
            );
            m_soundFileName = (
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            SOUND_FILE_NAME,
                            DEFAULT_SOUND_FILE_NAME
                    )
            );
            m_text = (
                    FileManipulator.readOptionalMultiLangString(
                            new File(pageDir, TEXT_SUBDIR_NAME),
                            DEFAULT_TEXT
                    )
            );
            m_moduleName = (
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            MODNAME_FILE_NAME,
                            m_defaultModuleName
                    )
            );
            m_traverseText = (
                    FileManipulator.readOptionalMultiLangString(
                            new File(pageDir, TRAVTEXT_FILE_NAME),
                            DEFAULT_TRAVERSE_TEXT
                    )
            );
            m_autoTraverse = "true".equals(
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            AUTOTRAV_FILE_NAME,
                            String.valueOf(DEFAULT_AUTO_TRAVERSE)
                    )
            );
            m_autoReturn = "true".equals(
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            AUTORET_FILE_NAME,
                            String.valueOf(DEFAULT_AUTO_RETURN)
                    )
            );
            m_returnText = (
                    FileManipulator.readOptionalMultiLangString(
                            new File(pageDir, RETTEXT_SUBDIR_NAME),
                            DEFAULT_RETURN_TEXT
                    )
            );
            m_returnPageId = (
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            RETPAGE_FILE_NAME,
                            DEFAULT_RETURN_PAGE_ID
                    )
            );
            m_moduleConstrId = (
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            MODCNSID_FILE_NAME,
                            DEFAULT_MODULE_CONSTR_ID
                    )
            );
            m_autowireInText = (
                    FileManipulator.readOptionalMultiLangString(
                            new File(pageDir, AUTO_IN_TEXT_SUBDIR_NAME),
                            DEFAULT_AUTOWIRE_IN_TEXT
                    )
            );
            m_autowireOutText = (
                    FileManipulator.readOptionalMultiLangString(
                            new File(pageDir, AUTO_OUT_TEXT_SUBDIR_NAME),
                            DEFAULT_AUTOWIRE_OUT_TEXT
                    )
            );
            m_autoIn = "true".equals(
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            AUTO_IN_FILE_NAME,
                            String.valueOf(DEFAULT_AUTO_IN)
                    )
            );
            m_autoOut = "true".equals(
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            AUTO_OUT_FILE_NAME,
                            String.valueOf(DEFAULT_AUTO_OUT)
                    )
            );
            m_autowireInConstrId = (
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            AUTOWIRE_IN_CONSTRID_FILE_NAME,
                            DEFAULT_AUTOWIRE_IN_CONSTR_ID
                    )
            );
            m_autowireOutConstrId = (
                    FileManipulator.getOptionalFileAsString(
                            pageDir,
                            AUTOWIRE_OUT_CONSTRID_FILE_NAME,
                            DEFAULT_AUTOWIRE_OUT_CONSTR_ID
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

    public PageImpl createFilteredClone(
            final List<String> linkIdsToBeExcluded,
            final List<Link> linksToBeAdded
    ) {
        PageImpl result = new PageImpl(getCurrentNLB());
        result.setId(getId());
        result.setText(getText());
        final Coords sourceCoords = getCoords();
        final CoordsImpl resultCoords = result.getCoords();
        resultCoords.setLeft(sourceCoords.getLeft());
        resultCoords.setTop(sourceCoords.getTop());
        resultCoords.setWidth(sourceCoords.getWidth());
        resultCoords.setHeight(sourceCoords.getHeight());
        result.setDeleted(isDeleted());
        result.setReturnPageId(getReturnPageId());
        result.setVarId(getVarId());
        result.setCaption(getCaption());
        result.setModuleConstrId(getModuleConstrId());
        result.setModuleName(getModuleName());
        result.setReturnText(getReturnText());
        result.setTraverseText(getTraverseText());
        result.setUseCaption(isUseCaption());
        result.setAutoTraverse(isAutoTraverse());
        result.setAutoReturn(isAutoReturn());
        result.setAutowireInText(getAutowireInText());
        result.setAutowireOutText(getAutowireOutText());
        result.setAutoIn(isAutoIn());
        result.setAutoOut(isAutoOut());
        result.setAutowireInConstrId(getAutowireInConstrId());
        result.setAutowireOutConstrId(getAutowireOutConstrId());
        result.setFill(getFill());
        result.setParent(getParent());
        result.setStroke(getStroke());
        result.setTextColor(getTextColor());
        AbstractNodeItem.filterTargetLinkList(result, this, linkIdsToBeExcluded);
        for (Link link : linksToBeAdded) {
            result.addLink(new LinkImpl(this, link));
        }
        return result;
    }
}
