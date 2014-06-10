/**
 * @(#)RGBColor.java
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
package com.nlbhub.nlb.domain.export.hypertext.document;

import com.nlbhub.nlb.exception.HTDocumentException;

/**
 * The RGBColor class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/9/13
 */
public class RGBColor {
    public int m_red;
    public int m_green;
    public int m_blue;

    public RGBColor(int red, int green, int blue) throws HTDocumentException {
        if (
                red < 0 || green < 0 || blue < 0
                        || red > 255 || green > 255 || blue > 255
                ) {
            throw new HTDocumentException("Color components should be in 0-255 range");
        }
        m_red = red;
        m_green = green;
        m_blue = blue;
    }

    public int getRed() {
        return m_red;
    }

    public int getGreen() {
        return m_green;
    }

    public int getBlue() {
        return m_blue;
    }
}
