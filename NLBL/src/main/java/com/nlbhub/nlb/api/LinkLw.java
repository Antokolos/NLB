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

import com.nlbhub.nlb.domain.ModificationImpl;
import com.nlbhub.nlb.domain.SearchResult;
import com.nlbhub.nlb.domain.VariableImpl;
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
    public static enum Type {Traverse, Return, AutowiredIn, AutowiredOut}

    private Type m_type;
    private String m_target;
    private IdentifiableItem m_parent;
    private MultiLangString m_text;
    private String m_constrId;
    private String m_varId;
    private boolean m_auto;
    private boolean m_positiveConstraint;
    private boolean m_shouldObeyToModuleConstraint;
    private List<Modification> m_modifications = new ArrayList<>();

    /**
     * @param type
     * @param target
     * @param parent
     * @param text
     * @param constrId
     * @param varId has meaning only for MPL links; represent ID of the MPL link variable
     * @param auto
     * @param positiveConstraint
     * @param shouldObeyToModuleConstraint
     * @param mplLink should be true if link is MPL, false otherwise
     * @param modifications modifications to be added or null. Will be completely ignored for autowired links.
     */
    public LinkLw(
            Type type,
            String target,
            Page parent,
            MultiLangString text,
            String constrId,
            String varId,
            boolean auto,
            boolean positiveConstraint,
            boolean shouldObeyToModuleConstraint,
            boolean mplLink,
            List<Modification> modifications
    ) {
        m_type = type;
        m_target = target;
        m_parent = parent;
        m_text = text;
        m_constrId = constrId;
        m_varId = varId;
        m_auto = auto;
        m_positiveConstraint = positiveConstraint;
        m_shouldObeyToModuleConstraint = shouldObeyToModuleConstraint;
        if (mplLink) {
            if (modifications != null) {
                for (Modification modification : modifications) {
                    m_modifications.add(modification);
                }
            }
        } else {
            if (m_type == Type.AutowiredOut || (m_type == Type.AutowiredIn && !parent.isAutowire())) {
                ModificationImpl modification = new ModificationImpl();
                modification.setType(Modification.Type.ASSIGN.name());
                modification.setParent(this);
                modification.setVarId(
                        (m_type == Type.AutowiredIn) ? parent.getId() : target
                );

                modification.setExprId((m_type == Type.AutowiredIn) ? NonLinearBook.TRUE_VARID : NonLinearBook.FALSE_VARID);
                m_modifications.add(modification);
            }
        }
    }

    @Override
    @XmlElement(name = "varid")
    public String getVarId() {
        return m_varId;
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
        return m_modifications;
    }

    @Override
    public Modification getModificationById(@NotNull String modId) {
        for (Modification modification : m_modifications) {
            if (modification.getId().equals(modId)) {
                return modification;
            }
        }
        return null;
    }

    @Override
    public String getId() {
        return m_parent.getId() + "_" + m_target + "_" + m_type.name();
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
    public boolean hasDeletedParent() {
        IdentifiableItem item = this;
        while ((item = item.getParent()) != null) {
            if (item.isDeleted()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public NonLinearBook getCurrentNLB() {
        return m_parent.getCurrentNLB();
    }

    @Override
    public SearchResult searchText(SearchContract contract) {
        // No search functionality needed for Lw links
        return null;
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

    public static String decorateId(String id) {
        return "vl_" + id.replaceAll("-", "_");
    }
}
