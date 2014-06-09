/**
 * @(#)StringHelper.java
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
package com.nlbhub.nlb.util;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.domain.LinkImpl;

import java.util.List;

/**
 * The StringHelper class
 *
 * @author Anton P. Kolosov
 * @version 1.0 11/20/13
 */
public class StringHelper {
    private final static String DELIMITER = ";";

    public static boolean isEmpty(final String string) {
        return string == null || Constants.EMPTY_STRING.equals(string);
    }

    public static String formatSequence(final List<String> strings) {
        final StringBuilder sb = new StringBuilder();
        final int lastElemIndex = strings.size() - 1;
        if (lastElemIndex >= 0) {
            for (int i = 0; i < lastElemIndex; i++) {
                sb.append(strings.get(i)).append(DELIMITER);
            }
            sb.append(strings.get(lastElemIndex));
        }
        return sb.toString();
    }

    public static String[] getItems(final String sequenceString) {
        if (sequenceString == null) {
            return new String[0];
        } else {
            return sequenceString.split(DELIMITER);
        }
    }

    public static String createRepeatedString(int length, String fill) {
        return new String(new char[length]).replace("\0", fill);
    }
}
