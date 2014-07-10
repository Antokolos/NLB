/**
 * @(#)LinkLw.java
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
import com.nlbhub.nlb.util.StringHelper;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The LinkLw class
 *
 * @author Anton P. Kolosov
 * @version 1.0 2/18/14
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "link")
public class LinkLw implements Link {
    public static enum Type {Traverse, Return}

    private Type m_type;
    private String m_target;
    private IdentifiableItem m_parent;
    private MultiLangString m_text;
    private String m_constrId;
    private boolean m_auto;
    private boolean m_positiveConstraint;
    private boolean m_shouldObeyToModuleConstraint;

    public LinkLw(
            Type type,
            String target,
            IdentifiableItem parent,
            MultiLangString text,
            String constrId,
            boolean auto,
            boolean positiveConstraint,
            boolean shouldObeyToModuleConstraint
    ) {
        m_type = type;
        m_target = target;
        m_parent = parent;
        m_text = text;
        m_constrId = constrId;
        m_auto = auto;
        m_positiveConstraint = positiveConstraint;
        m_shouldObeyToModuleConstraint = shouldObeyToModuleConstraint;
    }

    @Override
    @XmlElement(name = "varid")
    public String getVarId() {
        return Constants.EMPTY_STRING;
    }

    @Override
    @XmlElement(name = "target")
    public String getTarget() {
        return m_target;
    }

    @Override
    @XmlElement(name = "text")
    public String getText() {
        return m_text.get(m_parent.getCurrentNLB().getLanguage());
    }

    @Override
    public MultiLangString getTexts() {
        return MultiLangString.createCopy(m_text);
    }

    @Override
    @XmlElement(name = "constrid")
    public String getConstrId() {
        return m_constrId;
    }

    @Override
    @XmlElement(name = "is-positive")
    public boolean isPositiveConstraint() {
        return m_positiveConstraint;
    }

    @Override
    @XmlElement(name = "is-obey-to-module-constraint")
    public boolean isObeyToModuleConstraint() {
        return m_shouldObeyToModuleConstraint;
    }

    @Override
    @XmlElement(name = "is-traversal")
    public boolean isTraversalLink() {
        return m_type == Type.Traverse;
    }

    @Override
    @XmlElement(name = "is-return")
    public boolean isReturnLink() {
        return m_type == Type.Return;
    }

    @Override
    @XmlElement(name = "stroke")
    public String getStroke() {
        return Constants.EMPTY_STRING;
    }

    @Override
    public Coords getCoords() {
        return new CoordsLw();
    }

    @Override
    @XmlElement(name = "is-auto")
    public boolean isAuto() {
        return m_auto;
    }

    @Override
    public List<Modification> getModifications() {
        return new ArrayList<>();
    }

    @Override
    public Modification getModificationById(@NotNull String modId) {
        return null;
    }

    @Override
    public String getId() {
        return m_parent.getId() + "_" + m_type.name();
    }

    @Override
    public String getFullId() {
        String[] temp = {m_parent.getId(), getId()};
        return StringHelper.formatSequence(Arrays.asList(temp));
    }

    @Override
    public boolean isDeleted() {
        return false;
    }

    @Override
    public IdentifiableItem getParent() {
        return m_parent;
    }

    @Override
    public NonLinearBook getCurrentNLB() {
        return m_parent.getCurrentNLB();
    }

    @Override
    public String addObserver(NLBObserver observer) {
        return Constants.EMPTY_STRING;
    }

    @Override
    public void removeObserver(String observerId) {
    }

    @Override
    public void notifyObservers() {
    }
}
