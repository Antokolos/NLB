/**
 * @(#)Page.java
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

/**
 * The Page class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/15/14
 */
public interface Page extends NodeItem {
    public static final String DEFAULT_IMAGE_FILE_NAME = Constants.EMPTY_STRING;
    public static final String DEFAULT_SOUND_FILE_NAME = Constants.EMPTY_STRING;
    public static final MultiLangString DEFAULT_TEXT = MultiLangString.createEmptyText();
    public static final String DEFAULT_VARID = Constants.EMPTY_STRING;
    public static final String DEFAULT_TVARID = Constants.EMPTY_STRING;
    public static final MultiLangString DEFAULT_CAPTION = MultiLangString.createEmptyText();
    public static final MultiLangString DEFAULT_TRAVERSE_TEXT = MultiLangString.createDefaultTraverseText();
    public static final MultiLangString DEFAULT_AUTOWIRE_IN_TEXT = MultiLangString.createDefaultLinkText();
    public static final MultiLangString DEFAULT_AUTOWIRE_OUT_TEXT = MultiLangString.createDefaultLinkText();
    public static final boolean DEFAULT_USE_CAPTION = false;
    public static final boolean DEFAULT_USE_MPL = false;
    public static final boolean DEFAULT_IMAGE_BACKGROUND = false;
    public static final boolean DEFAULT_IMAGE_ANIMATED = false;
    public static final boolean DEFAULT_SOUND_SFX = false;
    public static final boolean DEFAULT_AUTO_TRAVERSE = false;
    public static final boolean DEFAULT_AUTO_RETURN = false;
    public static final boolean DEFAULT_AUTO_IN = false;
    public static final boolean DEFAULT_AUTO_OUT = false;
    public static final String DEFAULT_AUTOWIRE_IN_CONSTR_ID = Constants.EMPTY_STRING;
    public static final String DEFAULT_AUTOWIRE_OUT_CONSTR_ID = Constants.EMPTY_STRING;
    public static final boolean DEFAULT_GLOBAL_AUTOWIRED = false;
    /**
     * Set to empty String.
     * This means that by default pages should not return to the parent module
     */
    public static final MultiLangString DEFAULT_RETURN_TEXT = MultiLangString.createEmptyText();
    public static final String DEFAULT_RETURN_PAGE_ID = Constants.EMPTY_STRING;
    public static final String DEFAULT_MODULE_CONSTR_ID = Constants.EMPTY_STRING;

    public String getImageFileName();

    public boolean isImageBackground();

    public boolean isImageAnimated();

    public String getSoundFileName();

    public boolean isSoundSFX();

    public String getText();

    public MultiLangString getTexts();

    public String getVarId();

    public String getTimerVarId();

    public String getCaption();

    public MultiLangString getCaptions();

    public boolean isUseCaption();

    public boolean isUseMPL();

    public boolean isLeaf();

    public boolean isFinish();

    public String getTraverseText();

    public MultiLangString getTraverseTexts();

    public boolean isAutoTraverse();

    public boolean isAutoReturn();

    public String getReturnText();

    public MultiLangString getReturnTexts();

    public String getReturnPageId();

    /**
     * If false, then return link from module from this page should not be added
     * (truly end of the story)
     *
     * @return
     */
    public boolean shouldReturn();

    public String getModuleConstrId();

    public String getModuleName();

    public NonLinearBook getModule();

    public boolean isAutowire();
    public boolean isGlobalAutowire();
    public String getAutowireInText();
    public MultiLangString getAutowireInTexts();
    public String getAutowireOutText();
    public MultiLangString getAutowireOutTexts();
    public boolean isAutoIn();
    public boolean isAutoOut();
    public String getAutowireInConstrId();
    public String getAutowireOutConstrId();
}
