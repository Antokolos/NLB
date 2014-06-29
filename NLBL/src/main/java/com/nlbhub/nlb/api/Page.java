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

/**
 * The Page class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/15/14
 */
public interface Page extends NodeItem {
    public static final String DEFAULT_TEXT = Constants.EMPTY_STRING;
    public static final String DEFAULT_VARID = Constants.EMPTY_STRING;
    public static final String DEFAULT_CAPTION = Constants.EMPTY_STRING;
    public static final boolean DEFAULT_USE_CAPTION = false;
    public static final boolean DEFAULT_AUTO_TRAVERSE = false;
    /**
     * Set to empty String.
     * This means that by default pages should not return to the parent module
     */
    public static final String DEFAULT_RETURN_TEXT = Constants.EMPTY_STRING;
    public static final String DEFAULT_RETURN_PAGE_ID = Constants.EMPTY_STRING;
    public static final String DEFAULT_MODULE_CONSTR_ID = Constants.EMPTY_STRING;

    public String getText();

    public String getVarId();

    public String getCaption();

    public boolean isUseCaption();

    public boolean isLeaf();

    public String getTraverseText();

    public boolean isAutoTraverse();

    public String getReturnText();

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
}
