/**
 * @(#)Variable.java
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
 * The Variable class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/15/14
 */
public interface Variable extends IdentifiableItem {

    static final String NA = "N/A";
    public static final String DEFAULT_NAME = NA;
    public static final String DEFAULT_VALUE = NA;
    public static final DataType DEFAULT_DATATYPE = DataType.AUTO;

    public Type getType();

    public DataType getDataType();

    public String getName();

    public String getTarget();

    public String getValue();

    /**
     * The DataType class represents possible data type of the variable
     * (as the result of JS evaluation).
     *
     * @author Anton P. Kolosov
     */
    public enum DataType {
        AUTO, BOOLEAN, NUMBER, STRING
    }

    public enum Type {PAGE, TIMER, OBJ, OBJCONSTRAINT, OBJREF, LINK, LINKCONSTRAINT, VAR, EXPRESSION, TAG, MODCONSTRAINT, AUTOWIRECONSTRAINT}
}
