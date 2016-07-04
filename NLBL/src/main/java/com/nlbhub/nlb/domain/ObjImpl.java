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
import java.util.Map;

/**
 * The ObjImpl class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/19/13
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "obj")
public class ObjImpl extends AbstractNodeItem implements Obj {
    private static final String TEXT_SUBDIR_NAME = "text";
    private static final String ACT_TEXT_SUBDIR_NAME = "acttext";
    private static final String VARID_FILE_NAME = "varid";
    private static final String CONSTRID_FILE_NAME = "constrid";
    private static final String COMMONTOID_FILE_NAME = "commonto";
    private static final String NAME_FILE_NAME = "name";
    private static final String IMAGE_FILE_NAME = "image";
    private static final String SOUND_FILE_NAME = "sound";
    private static final String SOUND_SFX_FILE_NAME = "soundsfx";
    private static final String SUPPRESS_DSC_FILE_NAME = "suppdsc";
    private static final String ANIMATED_FILE_NAME = "animated";
    private static final String DISP_SUBDIR_NAME = "disp";
    private static final String GRAPHICAL_FILE_NAME = "graphical";
    private static final String PRESERVED_FILE_NAME = "preserved";
    private static final String MORPH_OVER_FILE_NAME = "morphover";
    private static final String MORPH_OUT_FILE_NAME = "morphout";
    private static final String TAKABLE_FILE_NAME = "takable";
    private static final String IMAGE_IN_SCENE_FILE_NAME = "imgscene";
    private static final String IMAGE_IN_INVENTORY_FILE_NAME = "imginv";
    private static final String CONTAINERID_FILE_NAME = "containerid";

    /**
     * Object variable. Will be modified when object is used (act in INSTEAD)
     */
    private String m_varId = DEFAULT_VARID;
    private String m_constrId = DEFAULT_CONSTRID;
    private String m_commonToId = DEFAULT_COMMON_TO_ID;
    private String m_name = DEFAULT_NAME;
    private MultiLangString m_disp = DEFAULT_DISP;
    private MultiLangString m_text = DEFAULT_TEXT;
    private MultiLangString m_actText = DEFAULT_ACT_TEXT;
    private boolean m_graphical = DEFAULT_GRAPHICAL;
    private boolean m_preserved = DEFAULT_PRESERVED;
    private String m_morphOverId = DEFAULT_MORPH_OVER_ID;
    private String m_morphOutId = DEFAULT_MORPH_OUT_ID;
    /**
     * Object can be taken to the inventory
     */
    private boolean m_takable = DEFAULT_TAKABLE;
    private String m_containerId = DEFAULT_CONTAINER_ID;
    private String m_imageFileName = DEFAULT_IMAGE_FILE_NAME;
    private boolean m_animatedImage = DEFAULT_ANIMATED_IMAGE;
    private String m_soundFileName = DEFAULT_SOUND_FILE_NAME;
    private boolean m_soundSFX = DEFAULT_SOUND_SFX;
    private boolean m_suppressDsc = DEFAULT_SUPPRESS_DSC;
    private boolean m_imageInScene = DEFAULT_IMAGE_IN_SCENE;
    private boolean m_imageInInventory = DEFAULT_IMAGE_IN_INVENTORY;

    @Override
    public SearchResult searchText(SearchContract contract) {
        SearchResult result = super.searchText(contract);
        if (result != null) {
            return result;
        } else if (
                textMatches(m_text, contract)
                        || textMatches(m_actText, contract)
                        || textMatches(m_name, contract)
                        || textMatches(m_disp, contract)
                        || textMatches(m_imageFileName, contract)
                        || textMatches(m_soundFileName, contract)
                ) {
            result = new SearchResult();
            result.setId(getId());
            result.setInformation(getName());
            return result;
        }
        return null;
    }

    public ObjImpl(Obj source, NonLinearBook currentNLB) {
        super(source, currentNLB);
        m_varId = source.getVarId();
        m_constrId = source.getConstrId();
        m_commonToId = source.getCommonToId();
        m_name = source.getName();
        m_imageFileName = source.getImageFileName();
        m_soundFileName = source.getSoundFileName();
        m_soundSFX = source.isSoundSFX();
        m_animatedImage = source.isAnimatedImage();
        setDisps(source.getDisps());
        setTexts(source.getTexts());
        setActTexts(source.getActTexts());
        m_graphical = source.isGraphical();
        m_preserved = source.isPreserved();
        m_morphOverId = source.getMorphOverId();
        m_morphOutId = source.getMorphOutId();
        m_takable = source.isTakable();
        m_suppressDsc = source.isSuppressDsc();
        m_imageInScene = source.isImageInScene();
        m_imageInInventory = source.isImageInInventory();
        m_containerId = source.getContainerId();
    }

    public ObjImpl(NonLinearBook currentNLB) {
        super(currentNLB);
    }

    public ObjImpl(NonLinearBook currentNLB, float left, float top) {
        super(currentNLB, left, top);
    }

    public void setText(String text) {
        m_text.put(getCurrentNLB().getLanguage(), text);
    }

    @Override
    @XmlElement(name = "text")
    public String getText() {
        return m_text.get(getCurrentNLB().getLanguage());
    }

    public void setActText(String actText) {
        m_actText.put(getCurrentNLB().getLanguage(), actText);
    }

    @Override
    @XmlElement(name = "acttext")
    public String getActText() {
        return m_actText.get(getCurrentNLB().getLanguage());
    }

    @Override
    public MultiLangString getTexts() {
        return MultiLangString.createCopy(m_text);
    }

    public void setTexts(final MultiLangString text) {
        m_text = text;
    }

    public MultiLangString getActTexts() {
        return MultiLangString.createCopy(m_actText);
    }

    @Override
    public Theme getTheme() {
        NonLinearBook currentNLB = getCurrentNLB();
        String containerId = getContainerId();
        if (containerId == null) {
            return Theme.DEFAULT;
        }
        Page containerPage = currentNLB.getPageById(containerId);
        if (containerPage == null) {
            Obj containerObj = currentNLB.getObjById(containerId);
            if (containerObj != null) {
                return containerObj.getTheme();
            }
        } else {
            return containerPage.getTheme();
        }
        return Theme.DEFAULT;
    }

    public void setActTexts(MultiLangString actText) {
        m_actText = actText;
    }

    @Override
    @XmlElement(name = "varid")
    public String getVarId() {
        return m_varId;
    }

    @Override
    @XmlElement(name = "constrid")
    public String getConstrId() {
        return m_constrId;
    }

    @Override
    @XmlElement(name = "commontoid")
    public String getCommonToId() {
        return m_commonToId;
    }

    public void setCommonToId(String commonToId) {
        m_commonToId = commonToId;
    }

    public void setVarId(String varId) {
        m_varId = varId;
    }

    public void setConstrId(String constrId) {
        m_constrId = constrId;
    }

    @Override
    @XmlElement(name = "name")
    public String getName() {
        return m_name;
    }

    @Override
    public String getImageFileName() {
        return m_imageFileName;
    }

    @Override
    public String getSoundFileName() {
        return m_soundFileName;
    }

    public void setSoundFileName(String soundFileName) {
        m_soundFileName = soundFileName;
    }

    @Override
    public boolean isSoundSFX() {
        return m_soundSFX;
    }

    public void setSoundSFX(boolean soundSFX) {
        m_soundSFX = soundSFX;
    }

    public void setAnimatedImage(final boolean animatedImage) {
        m_animatedImage = animatedImage;
    }

    @Override
    public boolean isAnimatedImage() {
        return m_animatedImage;
    }

    public void setSuppressDsc(boolean suppressDsc) {
        m_suppressDsc = suppressDsc;
    }

    @Override
    public boolean isSuppressDsc() {
        return m_suppressDsc;
    }

    public void setImageFileName(String imageFileName) {
        m_imageFileName = imageFileName;
    }

    public void setName(String name) {
        m_name = name;
    }

    @Override
    @XmlElement(name = "disp")
    public String getDisp() {
        return m_disp.get(getCurrentNLB().getLanguage());
    }

    @Override
    public MultiLangString getDisps() {
        return MultiLangString.createCopy(m_disp);
    }

    public void setGraphical(boolean graphical) {
        m_graphical = graphical;
    }

    @Override
    public boolean isGraphical() {
        return m_graphical;
    }

    @Override
    public boolean isPreserved() {
        return m_preserved;
    }

    public void setPreserved(boolean preserved) {
        m_preserved = preserved;
    }

    public String getMorphOverId() {
        return m_morphOverId;
    }

    @Override
    public Obj getMorphOverObj() {
        return getCurrentNLB().getObjById(getObjIdByMorphId(m_morphOverId));
    }

    public void setMorphOverId(String morphOverId) {
        m_morphOverId = morphOverId;
    }

    public String getMorphOutId() {
        return m_morphOutId;
    }

    @Override
    public Obj getMorphOutObj() {
        return getCurrentNLB().getObjById(getObjIdByMorphId(m_morphOutId));
    }

    public void setMorphOutId(String morphOutId) {
        m_morphOutId = morphOutId;
    }

    @Override
    public Coords getRelativeCoords(final boolean lookInMorphs) {
        NonLinearBook nlb = getCurrentNLB();
        NodeItem node = nlb.getPageById(m_containerId);
        if (node == null) {
            node = getCurrentNLB().getObjById(m_containerId);
        }
        if (node == null) {
            return getRelativeCoordsByMorph(lookInMorphs);
        }
        Coords coordsParent = node.getCoords();
        Coords coordsThis = getCoords();
        CoordsLw result = new CoordsLw();
        result.setLeft(coordsThis.getLeft() - coordsParent.getLeft());
        result.setTop(coordsThis.getTop() - coordsParent.getTop());
        result.setWidth(coordsParent.getWidth());
        result.setHeight(coordsParent.getHeight());
        return result;
    }

    private Coords getRelativeCoordsByMorph(final boolean lookInMorphs) {
        if (!lookInMorphs) {
            return CoordsLw.ZERO_COORDS;
        }
        Obj morphOut = getMorphOutObj();
        if (morphOut != null) {
            return morphOut.getRelativeCoords(false);
        }
        Obj morphOver = getMorphOverObj();
        if (morphOver != null) {
            return morphOver.getRelativeCoords(false);
        }
        return CoordsLw.ZERO_COORDS;
    }

    private String getObjIdByMorphId(String morphId) {
        if (!StringHelper.isEmpty(morphId)) {
            Variable morphVar = getCurrentNLB().getVariableById(morphId);
            if (!morphVar.isDeleted()) {
                return morphVar.getValue();
            }
        }
        return Constants.EMPTY_STRING;
    }

    public void setDisps(final MultiLangString disp) {
        m_disp = disp;
    }

    public void setDisp(String disp) {
        m_disp.put(getCurrentNLB().getLanguage(), disp);
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
    @XmlElement(name = "image-in-scene")
    public boolean isImageInScene() {
        return m_imageInScene;
    }

    public void setImageInScene(boolean imageInScene) {
        m_imageInScene = imageInScene;
    }

    @Override
    @XmlElement(name = "image-in-inventory")
    public boolean isImageInInventory() {
        return m_imageInInventory;
    }

    public void setImageInInventory(boolean imageInInventory) {
        m_imageInInventory = imageInInventory;
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
            fileManipulator.writeOptionalString(objDir, CONSTRID_FILE_NAME, m_constrId, DEFAULT_CONSTRID);
            fileManipulator.writeOptionalString(objDir, COMMONTOID_FILE_NAME, m_commonToId, DEFAULT_COMMON_TO_ID);
            fileManipulator.writeOptionalString(
                    objDir,
                    NAME_FILE_NAME,
                    m_name,
                    DEFAULT_NAME
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    IMAGE_FILE_NAME,
                    m_imageFileName,
                    DEFAULT_IMAGE_FILE_NAME
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    SOUND_FILE_NAME,
                    m_soundFileName,
                    DEFAULT_SOUND_FILE_NAME
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    SOUND_SFX_FILE_NAME,
                    String.valueOf(m_soundSFX),
                    String.valueOf(DEFAULT_SOUND_SFX)
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    ANIMATED_FILE_NAME,
                    String.valueOf(m_animatedImage),
                    String.valueOf(DEFAULT_ANIMATED_IMAGE)
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    SUPPRESS_DSC_FILE_NAME,
                    String.valueOf(m_suppressDsc),
                    String.valueOf(DEFAULT_SUPPRESS_DSC)
            );
            fileManipulator.writeOptionalMultiLangString(
                    new File(objDir, DISP_SUBDIR_NAME),
                    m_disp,
                    DEFAULT_DISP
            );
            fileManipulator.writeOptionalMultiLangString(
                    new File(objDir, TEXT_SUBDIR_NAME),
                    m_text,
                    DEFAULT_TEXT
            );
            fileManipulator.writeOptionalMultiLangString(
                    new File(objDir, ACT_TEXT_SUBDIR_NAME),
                    m_actText,
                    DEFAULT_ACT_TEXT
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    GRAPHICAL_FILE_NAME,
                    String.valueOf(m_graphical),
                    String.valueOf(DEFAULT_GRAPHICAL)
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    PRESERVED_FILE_NAME,
                    String.valueOf(m_preserved),
                    String.valueOf(DEFAULT_PRESERVED)
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    MORPH_OVER_FILE_NAME,
                    m_morphOverId,
                    DEFAULT_MORPH_OVER_ID
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    MORPH_OUT_FILE_NAME,
                    m_morphOutId,
                    DEFAULT_MORPH_OUT_ID
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    TAKABLE_FILE_NAME,
                    String.valueOf(m_takable),
                    String.valueOf(DEFAULT_TAKABLE)
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    IMAGE_IN_SCENE_FILE_NAME,
                    String.valueOf(m_imageInScene),
                    String.valueOf(DEFAULT_IMAGE_IN_SCENE)
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    IMAGE_IN_INVENTORY_FILE_NAME,
                    String.valueOf(m_imageInInventory),
                    String.valueOf(DEFAULT_IMAGE_IN_INVENTORY)
            );
            fileManipulator.writeOptionalString(
                    objDir,
                    CONTAINERID_FILE_NAME,
                    m_containerId,
                    DEFAULT_CONTAINER_ID
            );

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
        m_constrId = (
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        CONSTRID_FILE_NAME,
                        DEFAULT_CONSTRID
                )
        );
        m_commonToId = (
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        COMMONTOID_FILE_NAME,
                        DEFAULT_COMMON_TO_ID
                )
        );
        m_name = (
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        NAME_FILE_NAME,
                        DEFAULT_NAME
                )
        );
        m_imageFileName = (
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        IMAGE_FILE_NAME,
                        DEFAULT_IMAGE_FILE_NAME
                )
        );
        m_soundFileName = (
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        SOUND_FILE_NAME,
                        DEFAULT_SOUND_FILE_NAME
                )
        );
        m_soundSFX = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        SOUND_SFX_FILE_NAME,
                        String.valueOf(DEFAULT_SOUND_SFX)
                )
        );
        m_animatedImage = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        ANIMATED_FILE_NAME,
                        String.valueOf(DEFAULT_ANIMATED_IMAGE)
                )
        );
        m_suppressDsc = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        SUPPRESS_DSC_FILE_NAME,
                        String.valueOf(DEFAULT_SUPPRESS_DSC)
                )
        );
        m_disp = (
                FileManipulator.readOptionalMultiLangString(
                        new File(objDir, DISP_SUBDIR_NAME),
                        DEFAULT_DISP
                )
        );
        m_text = (
                FileManipulator.readOptionalMultiLangString(
                        new File(objDir, TEXT_SUBDIR_NAME),
                        DEFAULT_TEXT
                )
        );
        m_actText = (
                FileManipulator.readOptionalMultiLangString(
                        new File(objDir, ACT_TEXT_SUBDIR_NAME),
                        DEFAULT_ACT_TEXT
                )
        );
        m_graphical = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        GRAPHICAL_FILE_NAME,
                        String.valueOf(DEFAULT_GRAPHICAL)
                )
        );
        m_preserved = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        PRESERVED_FILE_NAME,
                        String.valueOf(DEFAULT_PRESERVED)
                )
        );
        m_morphOverId = (
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        MORPH_OVER_FILE_NAME,
                        DEFAULT_MORPH_OVER_ID
                )
        );
        m_morphOutId = (
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        MORPH_OUT_FILE_NAME,
                        DEFAULT_MORPH_OUT_ID
                )
        );
        m_takable = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        TAKABLE_FILE_NAME,
                        String.valueOf(DEFAULT_TAKABLE)
                )
        );
        m_imageInScene = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        IMAGE_IN_SCENE_FILE_NAME,
                        String.valueOf(DEFAULT_IMAGE_IN_SCENE)
                )
        );
        m_imageInInventory = "true".equals(
                FileManipulator.getOptionalFileAsString(
                        objDir,
                        IMAGE_IN_INVENTORY_FILE_NAME,
                        String.valueOf(DEFAULT_IMAGE_IN_INVENTORY)
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

    public String getCumulativeText(final List<String> objIdsToBeExcluded, Map<String, Object> visitedVars) {
        StringBuilder result = new StringBuilder();
        if (!objIdsToBeExcluded.contains(getId())) {
            result.append(StringHelper.replaceVariables(getText(), visitedVars));
            for (String objId : getContainedObjIds()) {
                Obj obj = getCurrentNLB().getObjById(objId);
                if (obj != null) {
                    result.append(obj.getCumulativeText(objIdsToBeExcluded, visitedVars));
                }
            }
        }
        return result.toString();
    }
}
