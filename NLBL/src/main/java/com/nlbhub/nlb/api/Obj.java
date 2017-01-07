/**
 * @(#)Obj.java
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
package com.nlbhub.nlb.api;

import com.nlbhub.nlb.util.MultiLangString;

import java.util.List;
import java.util.Map;

/**
 * The Obj class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/15/14
 */
public interface Obj extends NodeItem {
    public static enum ContainerType {Page, Obj, None}
    public static enum MovementDirection {Top, Left, None, Right, Bottom}
    public static enum Effect {MoveIn, MoveOut, FadeIn, FadeOut, ZoomIn, ZoomOut, Overlap, None}
    public static enum CoordsOrigin {LeftTop, MiddleTop, RightTop, LeftMiddle, MiddleMiddle, RightMiddle, LeftBottom, MiddleBottom, RightBottom};

    public static final String DEFAULT_IMAGE_FILE_NAME = Constants.EMPTY_STRING;
    public static final boolean DEFAULT_ANIMATED_IMAGE = false;
    public static final String DEFAULT_SOUND_FILE_NAME = Constants.EMPTY_STRING;
    public static final boolean DEFAULT_SOUND_SFX = true;
    public static final boolean DEFAULT_SUPPRESS_DSC = false;
    public static final MultiLangString DEFAULT_TEXT = MultiLangString.createEmptyText();
    public static final MultiLangString DEFAULT_ACT_TEXT = MultiLangString.createEmptyText();
    public static final String DEFAULT_VARID = Constants.EMPTY_STRING;
    public static final String DEFAULT_CONSTRID = Constants.EMPTY_STRING;
    public static final String DEFAULT_COMMON_TO_ID = Constants.EMPTY_STRING;
    public static final String DEFAULT_NAME = Constants.EMPTY_STRING;
    public static final MultiLangString DEFAULT_DISP = MultiLangString.createEmptyText();
    public static final boolean DEFAULT_GRAPHICAL = false;
    public static final boolean DEFAULT_PRESERVED = false;
    public static final boolean DEFAULT_COLLAPSABLE = false;
    public static final boolean DEFAULT_CLEAR_UNDER_TOOLTIP = false;
    public static final String DEFAULT_MORPH_OVER_ID = Constants.EMPTY_STRING;
    public static final String DEFAULT_MORPH_OUT_ID = Constants.EMPTY_STRING;
    public static final String DEFAULT_OFFSET = Constants.EMPTY_STRING;
    public static final boolean DEFAULT_TAKABLE = false;
    public static final boolean DEFAULT_IMAGE_IN_SCENE = true;
    public static final boolean DEFAULT_IMAGE_IN_INVENTORY = true;
    public static final String DEFAULT_CONTAINER_ID = Constants.EMPTY_STRING;
    public static final MovementDirection DEFAULT_MOVEMENT_DIRECTION = MovementDirection.None;
    public static final Effect DEFAULT_EFFECT = Effect.None;
    public static final int DEFAULT_MAX_FRAME = 8;

    public String getText();

    public String getActText();

    public MultiLangString getTexts();

    public MultiLangString getActTexts();

    public Theme getTheme();

    public String getVarId();

    public String getConstrId();

    public String getCommonToId();

    public String getName();

    public String getImageFileName();

    public String getSoundFileName();

    public boolean isSoundSFX();

    public boolean isAnimatedImage();

    public boolean isSuppressDsc();

    /**
     * Returns display name of the object, which is used in the text, e.g. when inserted into the inventory
     * @return display name of the object
     */
    public String getDisp();

    public MultiLangString getDisps();

    public boolean isGraphical();

    public boolean isPreserved();

    public boolean isCollapsable();

    public MovementDirection getMovementDirection();

    public Effect getEffect();

    public int getMaxFrame();

    public CoordsOrigin getCoordsOrigin();

    public boolean isClearUnderTooltip();

    public String getMorphOverId();

    public Obj getMorphOverObj();

    public String getMorphOutId();

    public Obj getMorphOutObj();

    public String getOffset();

    public Coords getRelativeCoords(final boolean lookInMorphs);

    public boolean isTakable();

    public boolean isImageInScene();

    public boolean isImageInInventory();

    public String getContainerId();

    public ContainerType getContainerType();

    public String getCumulativeText(final List<String> objIdsToBeExcluded, Map<String, Object> visitedVars);
}
