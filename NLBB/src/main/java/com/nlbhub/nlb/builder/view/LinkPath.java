/**
 * @(#)LinkPath.java
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
 * Copyright (c) 2012 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.builder.view;

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.builder.config.Parameters;
import com.nlbhub.nlb.util.StringHelper;
import edu.umd.cs.piccolo.nodes.PText;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * The LinkPath class
 *
 * @author Anton P. Kolosov
 * @version 1.0 8/9/12
 */
public class LinkPath extends ItemPath implements NLBObserver {
    private static final int MAX_CAPTION_CHARS_IN_LINK_TEXT = 22;
    public static final Paint NORMAL_PAINT = Color.WHITE;
    public static final Paint NORMAL_STROKE_PAINT = Color.BLACK;
    public static final Paint SELECTED_PAINT = Color.GRAY;
    private NodePath m_nodeFrom;
    private NodePath m_nodeTo;
    private boolean m_linkToSelf;
    private PText m_textNode;
    private FontMetrics m_metrics;

    private class PositionAndTotalCount {
        private int m_position;
        private int m_totalCount;

        private PositionAndTotalCount() {
            m_position = 0;
            m_totalCount = 0;
        }

        public int getPosition() {
            return m_position;
        }

        public void setPosition(int position) {
            m_position = position;
        }

        public int getTotalCount() {
            return m_totalCount;
        }

        public void setTotalCount(int totalCount) {
            m_totalCount = totalCount;
        }

        public boolean isLast() {
            return m_position == m_totalCount - 1;
        }
    }

    public LinkPath(
            final NonLinearBook nonLinearBook,
            Font font,
            final FontMetrics metrics,
            NodePath nodeFrom,
            NodePath nodeTo,
            final Link link
    ) {
        m_metrics = metrics;
        m_nodeFrom = nodeFrom;
        m_nodeTo = nodeTo;
        NodeItem itemFrom = (NodeItem) nodeFrom.getAttribute(Constants.NLB_PAGE_ATTR);
        if (itemFrom == null) {
            itemFrom = (NodeItem) nodeFrom.getAttribute(Constants.NLB_OBJ_ATTR);
        }
        NodeItem itemTo = (NodeItem) nodeTo.getAttribute(Constants.NLB_PAGE_ATTR);
        if (itemTo == null) {
            itemTo = (NodeItem) nodeTo.getAttribute(Constants.NLB_OBJ_ATTR);
        }
        m_linkToSelf = itemFrom.getId().equals(itemTo.getId());

        m_textNode = new PText();
        m_textNode.setFont(font);
        m_textNode.setHorizontalAlignment(Component.LEFT_ALIGNMENT);
        m_textNode.setConstrainHeightToTextHeight(true);
        m_textNode.setConstrainWidthToTextWidth(true);
        /*graphEditor.getNlbFacade().updateLinkCoords(
            link,
            m_textNode.getFont().getSize2D()
            + (float) Parameters.singleton().getTextOffsetAboveLink()
        );*/
        m_textNode.setPickable(true);
        // changeTextPosition() will be called on update, it will set correct coordinates.
        addChild(m_textNode);

        setPaint(NORMAL_PAINT);
        setStrokePaint(NORMAL_STROKE_PAINT);
        addAttribute(Constants.NLB_LINK_ATTR, link);
        addAttribute(Constants.NLB_MODULE_ATTR, nonLinearBook);
        link.addObserver(this);
    }

    public NodePath getNodeFrom() {
        return m_nodeFrom;
    }

    public NodePath getNodeTo() {
        return m_nodeTo;
    }

    private PositionAndTotalCount findLinkPositionBetweenAdjacentLinks() {
        PositionAndTotalCount result = new PositionAndTotalCount();
        NodeItem itemFrom = (NodeItem) m_nodeFrom.getAttribute(Constants.NLB_PAGE_ATTR);
        if (itemFrom == null) {
            itemFrom = (NodeItem) m_nodeFrom.getAttribute(Constants.NLB_OBJ_ATTR);
        }
        NodeItem itemTo = (NodeItem) m_nodeTo.getAttribute(Constants.NLB_PAGE_ATTR);
        if (itemTo == null) {
            itemTo = (NodeItem) m_nodeTo.getAttribute(Constants.NLB_OBJ_ATTR);
        }
        final Link thisLink = (Link) getAttribute(Constants.NLB_LINK_ATTR);
        java.util.List<Link> pageFromLinks = itemFrom.getLinks();
        int totalCount = 0;
        for (final Link link : pageFromLinks) {
            if (link.getTarget().equals(itemTo.getId()) && !link.isDeleted()) {
                if (link.getId().equals(thisLink.getId())) {
                    result.setPosition(totalCount++);
                } else {
                    ++totalCount;
                }
            }
        }
        result.setTotalCount(totalCount);
        return result;
    }

    private Point2D getLinkOrigin() {
        final Point2D centerFrom = m_nodeFrom.getFullBoundsReference().getCenter2D();
        final Point2D centerTo = m_nodeTo.getFullBoundsReference().getCenter2D();
        final Point2D result = new Point2D.Float();
        result.setLocation(
                (float) ((centerFrom.getX() + centerTo.getX()) / 2.0),
                (float) ((centerFrom.getY() + centerTo.getY()) / 2.0)
        );
        return result;
    }

    private double getTextRotationAngle() {
        final Point2D centerFrom = m_nodeFrom.getFullBoundsReference().getCenter2D();
        final Point2D centerTo = m_nodeTo.getFullBoundsReference().getCenter2D();
        double ratio = (
                (centerTo.getY() - centerFrom.getY()) / (centerTo.getX() - centerFrom.getX())
        );
        double theta = Math.atan(Math.abs(ratio));
        return (ratio > 0) ? theta : 2 * Math.PI - theta;// * 360 / (2*Math.PI);
    }

    private void update() {
        final Link thisLink = (Link) getAttribute(Constants.NLB_LINK_ATTR);
        setVisible(!thisLink.isDeleted());
        PositionAndTotalCount positionAndTotalCount = findLinkPositionBetweenAdjacentLinks();
        final Coords coords = thisLink.getCoords();
        if (m_linkToSelf) {
            changeTextPositionForSelfLink(coords, positionAndTotalCount);
            updateSelfLink(positionAndTotalCount);
        } else {
            changeTextPosition(coords, positionAndTotalCount);
            updateNonSelfLink(positionAndTotalCount);
        }
    }

    private void changeTextPosition(
            final Coords coords,
            final PositionAndTotalCount positionAndTotalCount
    ) {
        final Point2D origin = getLinkOrigin();
        // get the height of a line of text in this
        // font and render context
        // int hgt = metrics.getHeight();

        // get the advance of my text in this font
        // and render context
        int adv = m_metrics.stringWidth(m_textNode.getText());
        m_textNode.setBounds(
                -adv / 2.0 + coords.getLeft(),
                -coords.getHeight() * (positionAndTotalCount.getPosition() + 1) + coords.getTop(),
                coords.getWidth(),
                coords.getHeight()
        );
        m_textNode.setRotation(getTextRotationAngle());
        m_textNode.setOffset(
                origin.getX(),
                origin.getY()
        );
    }

    private void changeTextPositionForSelfLink(
            final Coords coords,
            final PositionAndTotalCount positionAndTotalCount
    ) {
        final Point2D origin = new Point2D.Float();
        origin.setLocation(
                (float) m_nodeFrom.getFullBoundsReference().getMaxX(),
                (float) m_nodeFrom.getFullBoundsReference().getMinY()
        );
        m_textNode.setBounds(
                coords.getLeft(),
                -coords.getHeight() * (positionAndTotalCount.getPosition() + 1) + coords.getTop(),
                coords.getWidth(),
                coords.getHeight()
        );
        m_textNode.setOffset(
                origin.getX(),
                origin.getY()
        );
    }

    private void updateSelfLink(final PositionAndTotalCount positionAndTotalCount) {
        // Note that the node's "FullBounds" must be used (instead of just the "Bound")
        // because the nodes have non-identity transforms which must be included when
        // determining their position.

        reset();

        final double shift = (
                Parameters.singleton().getArrowOffsetCoef()
                        * ((double) (positionAndTotalCount.getPosition() + 1))
        );
        // Beginning of the edge
        Point2D ptBegin = new Point2D.Double(
                m_nodeFrom.getFullBoundsReference().getCenter2D().getX()
                        + shift
                        + m_nodeFrom.getFullBoundsReference().getWidth() / 2.0,
                m_nodeFrom.getFullBoundsReference().getCenter2D().getY()
        );
        // End of the edge
        Point2D ptEnd = new Point2D.Double(
                m_nodeTo.getFullBoundsReference().getCenter2D().getX(),
                m_nodeTo.getFullBoundsReference().getCenter2D().getY()
        );
        int iRadiusFrom = 0;
        int iRadiusTo = (int) (m_nodeTo.getFullBoundsReference().getWidth() / 2.0);


        // Draw the edge (to the arrow)
        if (positionAndTotalCount.isLast()) {
            moveTo(
                    (float) m_nodeFrom.getFullBoundsReference().getMaxX(),
                    (float) m_nodeFrom.getFullBoundsReference().getMinY()
            );
            lineTo(
                    (float) ptBegin.getX(),
                    (float) m_nodeFrom.getFullBoundsReference().getMinY()
            );
            curveTo(
                    (float) ptBegin.getX(),
                    (float) m_nodeFrom.getFullBoundsReference().getMinY(),
                    (float) (ptBegin.getX() + m_nodeFrom.getFullBoundsReference().getHeight() / 2.0),
                    (float) ((m_nodeFrom.getFullBoundsReference().getMinY() + ptBegin.getY()) / 2.0),
                    (float) ptBegin.getX(),
                    (float) ptBegin.getY()
            );
        }
        boolean somethingWasDrawed = drawArrow(
                ptBegin,
                ptEnd,
                positionAndTotalCount,
                iRadiusFrom,
                iRadiusTo,
                positionAndTotalCount.isLast(),
                true
        );
        if (positionAndTotalCount.isLast()) {
            lineTo(
                    (float) ptBegin.getX(),
                    (float) ptBegin.getY()
            );

            curveTo(
                    (float) ptBegin.getX(),
                    (float) ptBegin.getY(),
                    (float) (ptBegin.getX() + m_nodeFrom.getFullBoundsReference().getHeight() / 2.0),
                    (float) ((m_nodeFrom.getFullBoundsReference().getMinY() + ptBegin.getY()) / 2.0),
                    (float) ptBegin.getX(),
                    (float) m_nodeFrom.getFullBoundsReference().getMinY()
            );
            lineTo(
                    (float) ptBegin.getX(),
                    (float) m_nodeFrom.getFullBoundsReference().getMinY()
            );
        }
        if (somethingWasDrawed) {
            closePath();
        }

    }

    private void updateNonSelfLink(final PositionAndTotalCount positionAndTotalCount) {
        // Note that the node's "FullBounds" must be used (instead of just the "Bound")
        // because the nodes have non-identity transforms which must be included when
        // determining their position.

        reset();


        // Beginning of the edge
        Point2D ptBegin = new Point2D.Double(
                m_nodeFrom.getFullBoundsReference().getCenter2D().getX(),
                m_nodeFrom.getFullBoundsReference().getCenter2D().getY()
        );
        // End of the edge
        Point2D ptEnd = new Point2D.Double(
                m_nodeTo.getFullBoundsReference().getCenter2D().getX(),
                m_nodeTo.getFullBoundsReference().getCenter2D().getY()
        );
        int iRadiusFrom = (int) m_nodeFrom.countPageRadius(m_nodeTo);
        int iRadiusTo = (int) m_nodeTo.countPageRadius(m_nodeFrom);

        boolean somethingWasDrawed = (
                drawArrow(ptBegin, ptEnd, positionAndTotalCount, iRadiusFrom, iRadiusTo, false, false)
        );
        if (somethingWasDrawed) {
            closePath();
        }
    }

    /**
     * @param ptBegin
     * @param ptEnd
     * @param positionAndTotalCount
     * @param iRadiusFrom           "Radius" of the beginning page
     *                              (distance from the rectangle center to the
     *                              intersection point of the link edge with the boundary)
     * @param iRadiusTo             "Radius" of the end page
     *                              (distance from the rectangle center to the intersection point of the
     *                              link edge with the boundary)
     */
    private boolean drawArrow(
            final Point2D ptBegin,
            final Point2D ptEnd,
            final PositionAndTotalCount positionAndTotalCount,
            int iRadiusFrom,
            int iRadiusTo,
            boolean continueDrawing,
            boolean forceDraw
    ) {
        final double arrowOffset = (
                Parameters.singleton().getArrowOffsetCoef()
                        * ((double) positionAndTotalCount.getPosition())
        );
        Point2D ptEnd_Arrow1;    // Point for painting of the arrow at the end of the edge
        Point2D ptEnd_Arrow2;    // Point for painting of the arrow at the end of the edge
        Point2D ptEndArrowOnMainLine; // (ptEnd_Arrow1 + ptEnd_Arrow2) / 2
        Point2D lineVectorTo;    // Vector of the ptEnd shift to the end page radius
        // (needed to shift ptEnd to the page boundary)
        Point2D lineVectorFrom;    // Vector of the ptBegin shift to the end page radius
        // (needed to shift ptBegin to the page boundary)
        Point2D lineVector_1;    // Vector of the unit size
        Point2D Ort_To_lineVector_1;    // Vector that is perpendicular to the lineVector
        double lineVectorNorm;    // Link edge length in the Euclidean norm


        // Vector collinear to the link edge
        lineVectorTo = new Point2D.Double(
                ptEnd.getX() - ptBegin.getX(), ptEnd.getY() - ptBegin.getY()
        );
        // Find its norm (length of the edge)
        lineVectorNorm = (
                Math.sqrt(
                        lineVectorTo.getX() * lineVectorTo.getX() + lineVectorTo.getY() * lineVectorTo.getY()
                )
        );

        // If edge length <= sum of the radii of the beginning and the end pages => pages are
        // intersecting, therefore we should not draw any edge
        if ((lineVectorNorm > (iRadiusFrom + iRadiusTo)) || forceDraw) {
            // Else draw the link edge

            // Normalize the vector
            lineVector_1 = new Point2D.Double(
                    lineVectorTo.getX() / lineVectorNorm,
                    lineVectorTo.getY() / lineVectorNorm
            );

            // Make the length of the vectors equal to the radii of the beginning and the end pages
            lineVectorFrom = new Point2D.Double(
                    lineVector_1.getX() * iRadiusFrom,
                    lineVector_1.getY() * iRadiusFrom
            );
            lineVectorTo.setLocation(
                    lineVector_1.getX() * (iRadiusTo + arrowOffset),
                    lineVector_1.getY() * (iRadiusTo + arrowOffset)
            );

            // Shift the beginning of the edge
            ptBegin.setLocation(
                    ptBegin.getX() + lineVectorFrom.getX(),
                    ptBegin.getY() + lineVectorFrom.getY()
            );

            // Shift the end of the edge
            ptEnd.setLocation(
                    ptEnd.getX() - lineVectorTo.getX(),
                    ptEnd.getY() - lineVectorTo.getY()
            );

            // Ort to the vector (a, b) - is (-b, a)
            Ort_To_lineVector_1 = new Point2D.Double(
                    -lineVector_1.getY(), lineVector_1.getX()
            );

            // Calculate the arrow coordinates
            final double arrowWidthCoef = Parameters.singleton().getArrowWidthCoef();
            final double arrowHeightCoef = Parameters.singleton().getArrowHeightCoef();
            ptEnd_Arrow1 = new Point2D.Double(
                    arrowWidthCoef * (-lineVector_1.getX() - Ort_To_lineVector_1.getX() * arrowHeightCoef) + ptEnd.getX(),
                    arrowWidthCoef * (-lineVector_1.getY() - Ort_To_lineVector_1.getY() * arrowHeightCoef) + ptEnd.getY()
            );
            ptEnd_Arrow2 = new Point2D.Double(
                    arrowWidthCoef * (-lineVector_1.getX() + Ort_To_lineVector_1.getX() * arrowHeightCoef) + ptEnd.getX(),
                    arrowWidthCoef * (-lineVector_1.getY() + Ort_To_lineVector_1.getY() * arrowHeightCoef) + ptEnd.getY()
            );

            ptEndArrowOnMainLine = new Point2D.Double(
                    (ptEnd_Arrow1.getX() + ptEnd_Arrow2.getX()) / 2.0,
                    (ptEnd_Arrow1.getY() + ptEnd_Arrow2.getY()) / 2.0
            );

            // Draw the edge (to the arrow)
            if (continueDrawing) {
                lineTo((float) ptBegin.getX(), (float) ptBegin.getY());
            } else {
                moveTo((float) ptBegin.getX(), (float) ptBegin.getY());
            }

            lineTo(
                    (float) (ptEndArrowOnMainLine.getX()),
                    (float) (ptEndArrowOnMainLine.getY())
            );

            // Draw the arrow at the end of the edge
            lineTo((float) ptEnd_Arrow1.getX(), (float) ptEnd_Arrow1.getY());
            lineTo((float) ptEnd.getX(), (float) ptEnd.getY());
            lineTo((float) ptEnd_Arrow2.getX(), (float) ptEnd_Arrow2.getY());
            lineTo(
                    (float) (ptEndArrowOnMainLine.getX()),
                    (float) (ptEndArrowOnMainLine.getY())
            );
            return true;
        }
        return false;
    }

    @Override
    public void updateView() {
        update();
        m_textNode.setText(buildText());
    }

    @Override
    protected String buildText() {
        final NonLinearBook nonLinearBook = (NonLinearBook) getAttribute(Constants.NLB_MODULE_ATTR);
        final Link link = (Link) getAttribute(Constants.NLB_LINK_ATTR);
        final Variable variable = nonLinearBook.getVariableById(link.getVarId());
        final Variable variableCons = nonLinearBook.getVariableById(link.getConstrId());
        final StringBuilder text = new StringBuilder();
        if (link.isAuto()) {
            text.append("<A> ");
        }
        if (!StringHelper.isEmpty(link.getText())) {
            int captionSize = link.getText().length();
            if (captionSize > MAX_CAPTION_CHARS_IN_LINK_TEXT) {
                text.append(link.getText().substring(0, MAX_CAPTION_CHARS_IN_LINK_TEXT));
                text.append("...");
            } else {
                text.append(link.getText());
            }
            if (variable != null) {
                text.append(": ");
            }
        }
        if (variable != null) {
            text.append("[").append(variable.getName()).append("]");
        }
        if (variableCons != null) {
            text.append(": /").append(variableCons.getValue()).append("/");
        }
        return text.toString();
    }
}
