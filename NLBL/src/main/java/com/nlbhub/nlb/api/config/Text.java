/**
 * @(#)Text.java
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

/**
 * @author Anton P. Kolosov
 * @version 1.0
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "text")
public class Text {
    @XmlAttribute(name = "lang")
    private String m_lang;
    @XmlElement(name = "game-act")
    private String m_gameAct;
    @XmlElement(name = "game-inv")
    private String m_gameInv;
    @XmlElement(name = "game-nouse")
    private String m_gameNouse;

    public String getLang() {
        return m_lang;
    }

    public void setLang(String lang) {
        m_lang = lang;
    }

    public String getGameAct() {
        return m_gameAct;
    }

    public void setGameAct(String gameAct) {
        m_gameAct = gameAct;
    }

    public String getGameInv() {
        return m_gameInv;
    }

    public void setGameInv(String gameInv) {
        m_gameInv = gameInv;
    }

    public String getGameNouse() {
        return m_gameNouse;
    }

    public void setGameNouse(String gameNouse) {
        m_gameNouse = gameNouse;
    }
}
