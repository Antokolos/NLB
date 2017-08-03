/**
 * @(#)Export.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2017 Anton P. Kolosov
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
 * Copyright (c) 2017 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.api.config;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * @author Anton P. Kolosov
 * @version 1.0
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "export")
public class Export {
    @XmlElement(name = "convertpng2jpg")
    private boolean m_convertpng2jpg;
    @XmlElement(name = "quality")
    private int m_quality;
    @XmlElement(name = "game-forcedsc")
    private boolean m_gameForceDsc;
    @XmlElementWrapper(name = "texts")
    @XmlElement(name = "text")
    private List<Text> m_texts;

    public boolean isConvertpng2jpg() {
        return m_convertpng2jpg;
    }

    public void setConvertpng2jpg(boolean convertpng2jpg) {
        m_convertpng2jpg = convertpng2jpg;
    }

    public int getQuality() {
        return m_quality;
    }

    public void setQuality(int quality) {
        m_quality = quality;
    }

    public boolean isGameForceDsc() {
        return m_gameForceDsc;
    }

    public void setGameForceDsc(boolean gameForceDsc) {
        m_gameForceDsc = gameForceDsc;
    }

    public List<Text> getTexts() {
        return m_texts;
    }

    public void setTexts(List<Text> texts) {
        m_texts = texts;
    }

    public Text getTextByLang(String lang) {
        if (m_texts == null) {
            return null;
        }
        for (Text text : m_texts) {
            if (lang.equalsIgnoreCase(text.getLang())) {
                return text;
            }
        }
        return null;
    }
}
