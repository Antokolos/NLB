/**
 * @(#)ResizeBar.java
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

import com.nlbhub.nlb.api.Coords;
import com.nlbhub.nlb.api.NodeItem;
import org.piccolo2d.event.PBasicInputEventHandler;
import org.piccolo2d.event.PInputEvent;
import org.piccolo2d.nodes.PPath;

import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;

/**
 * The ResizeBar class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/25/13
 */
public class ResizeBar extends PPath {
    private float m_resizeBarThickness = (float) 5.0;
    private float m_resizeBarCornerOffsetHorz = (float) 22.0;
    private float m_resizeBarCornerOffsetVert = (float) 12.0;
    private NodeItem.Orientation m_orientation;
    private Rectangle2D.Float m_resizeRect;
    private NodeResizeExecutor m_nodeResizeExecutor;

    private class ResizeBarEventHandler extends PBasicInputEventHandler {
        private NodePath m_resizingNode;
        private ResizeBar m_resizeBar;
        private double m_totalDeltaX = 0.0;
        private double m_totalDeltaY = 0.0;

        private ResizeBarEventHandler(NodePath resizingNode, ResizeBar resizeBar) {
            m_resizingNode = resizingNode;
            m_resizeBar = resizeBar;
        }

        @Override
        public void mousePressed(final PInputEvent event) {
            clearResizeInfo();
            event.setHandled(true);
        }

        private void clearResizeInfo() {
            m_totalDeltaX = 0.0;
            m_totalDeltaY = 0.0;
        }

        @Override
        public void mouseDragged(final PInputEvent event) {
            final Dimension2D delta = event.getDeltaRelativeTo(m_resizeBar);
            // reposition() is also called from the resizeNode to actually translate resize bar.
            // We can call executeResize on each mouse drag, but this results in all these endless
            // drags recorded to undo. Therefore here we just translating resize knob only, without
            // actually resizing target node. Actual resize is executed when mouse is released.
            // This is not so pretty looking but good in terms of performance and usefulness.
            switch (m_resizeBar.m_orientation) {
                case LEFT:
                case RIGHT:
                    m_totalDeltaX += delta.getWidth();
                    translate(delta.getWidth(), 0);
                    break;
                case TOP:
                case BOTTOM:
                    m_totalDeltaY += delta.getHeight();
                    translate(0, delta.getHeight());
            }
            event.setHandled(true);
        }

        @Override
        public void mouseReleased(final PInputEvent event) {
            m_nodeResizeExecutor.executeResize(
                    m_resizingNode,
                    m_resizeBar.m_orientation,
                    m_totalDeltaX,
                    m_totalDeltaY
            );
            clearResizeInfo();
            event.setHandled(true);
        }

        @Override
        public void keyPressed(PInputEvent event) {
            event.setHandled(true);
        }

        @Override
        public void keyReleased(PInputEvent event) {
            event.setHandled(true);
        }

        @Override
        public void keyTyped(PInputEvent event) {
            event.setHandled(true);
        }

        @Override
        public void mouseClicked(PInputEvent event) {
            event.setHandled(true);
        }

        // mouseEntered and mouseExited will be propagated
        // public void mouseEntered(PInputEvent event)
        // public void mouseExited(PInputEvent event)

        @Override
        public void mouseMoved(PInputEvent event) {
            event.setHandled(true);
        }

        @Override
        public void mouseWheelRotated(PInputEvent event) {
            event.setHandled(true);
        }

        @Override
        public void mouseWheelRotatedByBlock(PInputEvent event) {
            event.setHandled(true);
        }

        @Override
        public void keyboardFocusGained(PInputEvent event) {
            event.setHandled(true);
        }

        @Override
        public void keyboardFocusLost(PInputEvent event) {
            event.setHandled(true);
        }
    }

    public ResizeBar(
            NodeResizeExecutor nodeResizeExecutor,
            NodePath node,
            Coords coords,
            NodeItem.Orientation orientation
    ) {
        m_nodeResizeExecutor = nodeResizeExecutor;
        m_orientation = orientation;
        m_resizeRect = new Rectangle2D.Float();
        reposition(coords);
        append(m_resizeRect, false);
        addInputEventListener(new ResizeBarEventHandler(node, this));
    }

    public void reposition(Coords coords) {
        // Ignores offset introduced by dragging because it is already taken into account in coords
        setOffset(0.0, 0.0);
        switch (m_orientation) {
            case LEFT:
                m_resizeRect.setFrame(
                        coords.getLeft() - m_resizeBarThickness,
                        coords.getTop() + m_resizeBarCornerOffsetVert,
                        m_resizeBarThickness,
                        coords.getHeight() - 2 * m_resizeBarCornerOffsetVert
                );
                break;
            case RIGHT:
                m_resizeRect.setFrame(
                        coords.getLeft() + coords.getWidth(),
                        coords.getTop() + m_resizeBarCornerOffsetVert,
                        m_resizeBarThickness,
                        coords.getHeight() - 2 * m_resizeBarCornerOffsetVert
                );
                break;
            case TOP:
                m_resizeRect.setFrame(
                        coords.getLeft() + m_resizeBarCornerOffsetHorz,
                        coords.getTop() - m_resizeBarThickness,
                        coords.getWidth() - 2 * m_resizeBarCornerOffsetHorz,
                        m_resizeBarThickness
                );
                break;
            case BOTTOM:
                m_resizeRect.setFrame(
                        coords.getLeft() + m_resizeBarCornerOffsetHorz,
                        coords.getTop() + coords.getHeight(),
                        coords.getWidth() - 2 * m_resizeBarCornerOffsetHorz,
                        m_resizeBarThickness
                );
                break;
        }
        setBounds(m_resizeRect);
    }
}
