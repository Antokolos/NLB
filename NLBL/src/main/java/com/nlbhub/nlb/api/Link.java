/**
 * @(#)Link.java
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

/**
 * The Link class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/15/14
 */
public interface Link extends ModifyingItem, NLBObservable {
    public final static String DEFAULT_VAR_ID = Constants.EMPTY_STRING;
    public final static String DEFAULT_TARGET = Constants.EMPTY_STRING;
    public final static MultiLangString DEFAULT_TEXT = MultiLangString.createDefaultLinkText();
    public final static MultiLangString DEFAULT_ALT_TEXT = MultiLangString.createEmptyText();
    public final static String DEFAULT_CONSTR_ID = Constants.EMPTY_STRING;
    public final static String DEFAULT_STROKE = "0000FF";
    public final static boolean DEFAUlT_AUTO = false;
    public final static boolean DEFAUlT_NEEDS_ACTION = false;
    public final static boolean DEFAUlT_ONCE = false;
    public final static boolean DEFAUlT_TECHNICAL = false;

    public String getVarId();

    public String getTarget();

    public String getText();

    public MultiLangString getTexts();

    public String getAltText();

    public MultiLangString getAltTexts();

    public String getConstrId();

    public String getStroke();

    public Coords getCoords();

    public boolean isAuto();

    public boolean isNeedsAction();

    public boolean isOnce();

    /**
     * If true, constraint is fulfilled if its expression is true.
     * If false, constraint is fulfilled if its expression is false
     * (equivalent of boolean NOT operator)
     */
    public boolean isPositiveConstraint();

    /**
     * If true, module constraint also constrains this link, in addition to link's own constraint.
     * If false, module constraint should not be applied.
     *
     * @return
     */
    public boolean isObeyToModuleConstraint();

    public boolean isTraversalLink();

    public boolean isReturnLink();
    public boolean isTechnical();
}
