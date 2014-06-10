/**
 * @(#)NodePath.java
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
package com.nlbhub.nlb.builder.view;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.Coords;
import com.nlbhub.nlb.api.NLBObserver;
import com.nlbhub.nlb.api.NodeItem;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.nodes.PText;
import edu.umd.cs.piccolo.util.PBounds;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * The NodePath class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/20/13
 */
public abstract class NodePath extends ItemPath implements NLBObserver {
    private ResizeBar m_leftResizeBar;
    private ResizeBar m_rightResizeBar;
    private ResizeBar m_topResizeBar;
    private ResizeBar m_bottomResizeBar;
    private java.awt.geom.Rectangle2D.Float m_nodeRect;
    private PText m_textNode;
    private java.awt.geom.Rectangle2D.Float m_textRect;

    protected NodePath(
            final NodeResizeExecutor nodeResizeExecutor,
            final NodeItem nodeItem,
            final Font font
    ) {
        final Coords coords = nodeItem.getCoords();
        m_nodeRect = new java.awt.geom.Rectangle2D.Float();
        m_textRect = new java.awt.geom.Rectangle2D.Float();
        m_nodeRect.setFrame(
                coords.getLeft(),
                coords.getTop(),
                coords.getWidth(),
                coords.getHeight()
        );
        append(m_nodeRect, false);
        m_textNode = new PText();
        m_textNode.setFont(font);
        m_textNode.setHorizontalAlignment(Component.CENTER_ALIGNMENT);
        m_textNode.setConstrainHeightToTextHeight(false);
        m_textNode.setConstrainWidthToTextWidth(false);
        m_textNode.setPickable(false);
        addChild(m_textNode);
        m_leftResizeBar = new ResizeBar(
                nodeResizeExecutor,
                this,
                coords,
                NodeItem.Orientation.LEFT
        );
        addChild(m_leftResizeBar);
        m_rightResizeBar = new ResizeBar(
                nodeResizeExecutor,
                this,
                coords,
                NodeItem.Orientation.RIGHT
        );
        addChild(m_rightResizeBar);
        m_topResizeBar = new ResizeBar(
                nodeResizeExecutor,
                this,
                coords,
                NodeItem.Orientation.TOP
        );
        addChild(m_topResizeBar);
        m_bottomResizeBar = new ResizeBar(
                nodeResizeExecutor,
                this,
                coords,
                NodeItem.Orientation.BOTTOM
        );
        addChild(m_bottomResizeBar);
        nodeItem.addObserver(this);
    }

    protected void resizeNode(Coords coords) {
        // Ignores offset introduced by dragging because it is already taken into account in coords
        setOffset(0.0, 0.0);
        m_nodeRect.setFrame(
                coords.getLeft(),
                coords.getTop(),
                coords.getWidth(),
                coords.getHeight()
        );
        setBounds(m_nodeRect);

        m_leftResizeBar.reposition(coords);
        m_rightResizeBar.reposition(coords);
        m_topResizeBar.reposition(coords);
        m_bottomResizeBar.reposition(coords);
        final float fontSize = m_textNode.getFont().getSize2D();
        m_textRect.setFrame(
                coords.getLeft(),
                coords.getTop() + fontSize,
                coords.getWidth(),
                coords.getHeight() - fontSize
        );
        m_textNode.setBounds(m_textRect);
    }

    public Rectangle2D.Float getNodeRect() {
        return m_nodeRect;
    }

    public double countPageRadius(@NotNull PNode nodeTo) {
        // Using Pythagoras theorem and triangle similarity.
        // Count both possible radii and select the smallest to simplify orientation issues.
        double flRadiusHorz;    // "Horizontal radius"
        double flRadiusVert;    // "Vertical radius"
        double flHorzKatetA;    // For calculation of flRadiusHorz
        double flHorzKatetB;    // For calculation of flRadiusHorz,
        // obtained from triangle similarity.
        double flLambdaHorz;    // Similarity coefficient for the horizontal rectangles
        double flVertKatetA;     // For calculation of flRadiusVert
        double flVertKatetB;    // For calculation of flRadiusVert,
        // obtained from triangle similarity.
        double flLambdaVert;    // Similarity coefficient for the vertical rectangles
        Point2D ptThis;    // Center of the "from" page (this page)
        Point2D ptTo;      // Center of the "to" page

        final PBounds nodeFromFullBR = getFullBoundsReference();
        final PBounds nodeToFullBR = nodeTo.getFullBoundsReference();
        flHorzKatetA = nodeFromFullBR.getWidth() / 2.0;
        flVertKatetA = nodeFromFullBR.getHeight() / 2.0;
        ptThis = new Point2D.Double(
                nodeFromFullBR.getMinX() + nodeFromFullBR.getWidth() / 2.0,
                nodeFromFullBR.getMinY() + nodeFromFullBR.getHeight() / 2.0
        );
        ptTo = new Point2D.Double(
                nodeToFullBR.getMinX() + nodeToFullBR.getWidth() / 2.0,
                nodeToFullBR.getMinY() + nodeToFullBR.getHeight() / 2.0
        );

        flLambdaHorz = Math.abs(ptTo.getX() - ptThis.getX()) / flHorzKatetA;
        flLambdaVert = Math.abs(ptTo.getY() - ptThis.getY()) / flVertKatetA;

        flHorzKatetB = Math.abs(ptTo.getY() - ptThis.getY()) / flLambdaHorz;
        flVertKatetB = Math.abs(ptTo.getX() - ptThis.getX()) / flLambdaVert;

        // Use the Pythagoras theorem
        flRadiusHorz = Math.sqrt(flHorzKatetA * flHorzKatetA + flHorzKatetB * flHorzKatetB);
        flRadiusVert = Math.sqrt(flVertKatetA * flVertKatetA + flVertKatetB * flVertKatetB);

        // Return the smallest length
        if (flRadiusHorz < flRadiusVert)
            return flRadiusHorz;
        else
            return flRadiusVert;
    }

    @Override
    public void updateView() {
        NodeItem nodeItem = (NodeItem) getAttribute(Constants.NLB_PAGE_ATTR);
        if (nodeItem == null) {
            nodeItem = (NodeItem) getAttribute(Constants.NLB_OBJ_ATTR);
        }
        setVisible(!nodeItem.isDeleted());
        resizeNode(nodeItem.getCoords());
        m_textNode.setText(buildText());
    }
}
