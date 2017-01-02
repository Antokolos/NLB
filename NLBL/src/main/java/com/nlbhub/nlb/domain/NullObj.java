/**
 * @(#)NullObj.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2015 Anton P. Kolosov
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
 * Copyright (c) 2015 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.util.MultiLangString;

import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import java.util.Map;

/**
 * The NullObj class
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class NullObj extends AbstractNodeItem implements Obj {
    private static final NullObj SINGLETON = new NullObj();

    public static NullObj create() {
        return SINGLETON;
    }

    public void setId(String id) {}

    protected NullObj() {
    }

    @Override
    @XmlElement(name = "id")
    public String getId() {
        return Variable.NA;
    }

    @Override
    public String getText() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public String getActText() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public MultiLangString getTexts() {
        return Obj.DEFAULT_TEXT;
    }

    @Override
    public MultiLangString getActTexts() {
        return Obj.DEFAULT_ACT_TEXT;
    }

    @Override
    public Theme getTheme() {
        return Theme.DEFAULT;
    }

    @Override
    public String getVarId() {
        return Obj.DEFAULT_VARID;
    }

    @Override
    public String getConstrId() {
        return Obj.DEFAULT_CONSTRID;
    }

    @Override
    public String getCommonToId() {
        return Obj.DEFAULT_COMMON_TO_ID;
    }

    @Override
    public String getName() {
        return Obj.DEFAULT_NAME;
    }

    @Override
    public String getImageFileName() {
        return Obj.DEFAULT_IMAGE_FILE_NAME;
    }

    @Override
    public String getSoundFileName() {
        return Obj.DEFAULT_SOUND_FILE_NAME;
    }

    @Override
    public boolean isSoundSFX() {
        return Obj.DEFAULT_SOUND_SFX;
    }

    @Override
    public boolean isAnimatedImage() {
        return Obj.DEFAULT_ANIMATED_IMAGE;
    }

    @Override
    public boolean isSuppressDsc() {
        return Obj.DEFAULT_SUPPRESS_DSC;
    }

    @Override
    public String getDisp() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public MultiLangString getDisps() {
        return Obj.DEFAULT_DISP;
    }

    @Override
    public boolean isGraphical() {
        return Obj.DEFAULT_GRAPHICAL;
    }

    @Override
    public boolean isPreserved() {
        return Obj.DEFAULT_PRESERVED;
    }

    @Override
    public boolean isCollapsable() {
        return Obj.DEFAULT_COLLAPSABLE;
    }

    @Override
    public MovementDirection getMovementDirection() {
        return Obj.DEFAULT_MOVEMENT_DIRECTION;
    }

    @Override
    public Effect getEffect() {
        return DEFAULT_EFFECT;
    }

    @Override
    public CoordsOrigin getCoordsOrigin() {
        return CoordsOrigin.LeftTop;
    }

    @Override
    public boolean isClearUnderTooltip() {
        return Obj.DEFAULT_CLEAR_UNDER_TOOLTIP;
    }

    public String getMorphOverId() {
        return Obj.DEFAULT_MORPH_OVER_ID;
    }

    @Override
    public Obj getMorphOverObj() {
        return null;
    }

    public String getMorphOutId() {
        return Obj.DEFAULT_MORPH_OUT_ID;
    }

    @Override
    public Obj getMorphOutObj() {
        return null;
    }

    @Override
    public String getOffset() {
        return DEFAULT_OFFSET;
    }

    @Override
    public Coords getRelativeCoords(final boolean lookInMorphs) {
        return CoordsLw.ZERO_COORDS;
    }

    @Override
    public boolean isTakable() {
        return Obj.DEFAULT_TAKABLE;
    }

    @Override
    public boolean isImageInScene() {
        return Obj.DEFAULT_IMAGE_IN_SCENE;
    }

    @Override
    public boolean isImageInInventory() {
        return Obj.DEFAULT_IMAGE_IN_INVENTORY;
    }

    @Override
    public String getContainerId() {
        return Obj.DEFAULT_CONTAINER_ID;
    }

    @Override
    public ContainerType getContainerType() {
        return ContainerType.None;
    }

    @Override
    public String getCumulativeText(List<String> objIdsToBeExcluded, Map<String, Object> visitedVars) {
        return Constants.EMPTY_STRING;
    }
}
