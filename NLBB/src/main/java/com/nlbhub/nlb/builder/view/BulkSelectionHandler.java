/**
 * @(#)BulkSelectionHandler.java
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
package com.nlbhub.nlb.builder.view;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.NodeItem;
import edu.umd.cs.piccolo.PLayer;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.nodes.PPath;
import edu.umd.cs.piccolo.util.PNodeFilter;

import java.awt.*;
import java.util.*;

/**
 * @author Anton P. Kolosov
 */
public class BulkSelectionHandler {
    private Set<String> m_selectedPagesIds = new HashSet<>();
    private Set<String> m_selectedObjsIds = new HashSet<>();

    public void clear() {
        m_selectedPagesIds.clear();
        m_selectedObjsIds.clear();
    }

    public Set<String> getSelectedPagesIds() {
        return m_selectedPagesIds;
    }

    public Set<String> getSelectedObjsIds() {
        return m_selectedObjsIds;
    }

    public void makeSelection(
            final PPath selectionFrame,
            final PLayer objLayer,
            final PLayer nodeLayer
    ) {
        clear();
        PNodeFilter nodeFilter = new PNodeFilter() {
            @Override
            public boolean accept(PNode pNode) {
                return pNode.getGlobalFullBounds().intersects(selectionFrame.getGlobalFullBounds());
            }

            @Override
            public boolean acceptChildrenOf(PNode pNode) {
                return accept(pNode);
            }
        };
        Collection<PNode> resultantObjNodes = new ArrayList<>();
        Collection<PNode> resultantNodes = new ArrayList<>();
        resultantObjNodes = objLayer.getAllNodes(nodeFilter, resultantObjNodes);
        resultantNodes = nodeLayer.getAllNodes(nodeFilter, resultantNodes);
        for (PNode node : resultantObjNodes) {
            NodeItem nodeItem = (NodeItem) node.getAttribute(Constants.NLB_OBJ_ATTR);
            if (nodeItem != null) {
                m_selectedObjsIds.add(nodeItem.getId());
                for (String containedObjId : nodeItem.getContainedObjIds()) {
                    m_selectedObjsIds.add(containedObjId);
                }
            }
        }
        for (PNode node : resultantNodes) {
            NodeItem nodeItem = (NodeItem) node.getAttribute(Constants.NLB_PAGE_ATTR);
            if (nodeItem != null) {
                m_selectedPagesIds.add(nodeItem.getId());
                for (String containedObjId : nodeItem.getContainedObjIds()) {
                    m_selectedObjsIds.add(containedObjId);
                }
            }
        }
    }

    public boolean isSelected(final PNode node) {
        NodeItem nodeItem = (NodeItem) node.getAttribute(Constants.NLB_PAGE_ATTR);
        if (nodeItem != null) {
            return m_selectedPagesIds.contains(nodeItem.getId());
        } else {
            nodeItem = (NodeItem) node.getAttribute(Constants.NLB_OBJ_ATTR);
            if (nodeItem != null) {
                return m_selectedObjsIds.contains(nodeItem.getId());
            }
        }
        return false;
    }

    public void recolor(final GraphItemsMapper graphItemsMapper, final Color color) {
        for (String selectedPageId : getSelectedPagesIds()) {
            graphItemsMapper.getPageById(selectedPageId).setPaint(color);
        }
        for (String selectedObjId : getSelectedObjsIds()) {
            graphItemsMapper.getObjById(selectedObjId).setPaint(color);
        }
    }
}
