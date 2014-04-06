/**
 * @(#)UpdateNodeCoordsCommand.java
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
package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.NLBCommand;
import com.nlbhub.nlb.api.NodeItem;

import java.util.List;

/**
 * The UpdateNodeCoordsCommand class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/17/14
 */
public class UpdateNodeCoordsCommand implements NLBCommand {
    private AbstractNodeItem m_nodeItem;
    private List<Link> m_associatedLinks;
    private float m_left;
    private float m_top;
    private float m_height;
    private float m_width;
    private float m_leftPrev;
    private float m_topPrev;
    private float m_heightPrev;
    private float m_widthPrev;

    private UpdateNodeCoordsCommand(NonLinearBookImpl nonLinearBook, NodeItem node) {
        m_nodeItem = nonLinearBook.getPageImplById(node.getId());
        if (m_nodeItem == null) {
            m_nodeItem = nonLinearBook.getObjImplById(node.getId());
        }
        m_associatedLinks = nonLinearBook.getAssociatedLinks(node);
        final CoordsImpl coords = m_nodeItem.getCoords();
        m_leftPrev = coords.getLeft();
        m_topPrev = coords.getTop();
        m_heightPrev = coords.getHeight();
        m_widthPrev = coords.getWidth();
    }

    public UpdateNodeCoordsCommand(
        NonLinearBookImpl nonLinearBook,
        NodeItem node,
        final float left,
        final float top
    ) {
        this(nonLinearBook, node);
        m_width = m_widthPrev;
        m_height = m_heightPrev;
        m_left = left;
        m_top = top;
    }

    public UpdateNodeCoordsCommand(
        NonLinearBookImpl nonLinearBook,
        NodeItem node,
        final float left,
        final float top,
        final float width,
        final float height
    ) {
        this(nonLinearBook, node);
        m_width = width;
        m_height = height;
        m_left = left;
        m_top = top;
    }

    @Override
    public void execute() {
        final CoordsImpl coords = m_nodeItem.getCoords();
        coords.setLeft(m_left);
        coords.setTop(m_top);
        coords.setWidth(m_width);
        coords.setHeight(m_height);
        m_nodeItem.notifyObservers();
        for (Link link : m_associatedLinks) {
            link.notifyObservers();
        }
    }

    @Override
    public void revert() {
        final CoordsImpl coords = m_nodeItem.getCoords();
        coords.setLeft(m_leftPrev);
        coords.setTop(m_topPrev);
        coords.setWidth(m_widthPrev);
        coords.setHeight(m_heightPrev);
        m_nodeItem.notifyObservers();
        for (Link link : m_associatedLinks) {
            link.notifyObservers();
        }
    }
}
