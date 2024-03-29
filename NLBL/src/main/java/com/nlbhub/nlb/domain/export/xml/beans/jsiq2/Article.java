/**
 * @(#)Article.java
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
package com.nlbhub.nlb.domain.export.xml.beans.jsiq2;

import jakarta.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "article")
public class Article {
    @XmlAttribute(name = "id")
    private String m_id;
    private Metadata m_metadata;
    private List<Script> m_scripts = new ArrayList<>();
    private List<Action> m_actions = new ArrayList<>();
    private String text;

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public Article() {
    }

    public String getId() {
        return m_id;
    }

    public void setId(String id) {
        m_id = id;
    }

    @XmlElement(name = "metadata")
    public Metadata getMetadata() {
        return m_metadata;
    }

    public void setMetadata(Metadata metadata) {
        m_metadata = metadata;
    }

    @XmlElement(name = "script")
    public List<Script> getScripts() {
        return m_scripts;
    }

    public void setScripts(List<Script> scripts) {
        m_scripts = scripts;
    }

    public void addScript(Script script) {
        m_scripts.add(script);
    }

    @XmlElement(name = "action")
    public List<Action> getActions() {
        return m_actions;
    }

    public void setActions(List<Action> actions) {
        m_actions = actions;
    }

    public void addAction(Action action) {
        m_actions.add(action);
    }

    @XmlElement(name = "text")
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
