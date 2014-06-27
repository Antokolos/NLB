/**
 * @(#)GraphEditor.java
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
import com.nlbhub.nlb.builder.model.LinkSelectionData;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBFileManipulationException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import edu.umd.cs.piccolo.PCamera;
import edu.umd.cs.piccolo.PCanvas;
import edu.umd.cs.piccolo.PLayer;
import edu.umd.cs.piccolo.PNode;
import edu.umd.cs.piccolo.event.PDragEventHandler;
import edu.umd.cs.piccolo.event.PInputEvent;
import edu.umd.cs.piccolo.event.PInputEventFilter;
import edu.umd.cs.piccolo.nodes.PPath;
import edu.umd.cs.piccolo.util.PAffineTransform;
import edu.umd.cs.piccolo.util.PBounds;
import edu.umd.cs.piccolo.util.PNodeFilter;
import edu.umd.cs.piccolo.util.PPaintContext;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * The GraphEditor class
 *
 * @author Anton P. Kolosov
 * @version 1.0 7/6/12
 */
public class GraphEditor extends PCanvas {
    private static enum GraphEditorMode {ADD_PAGE_MODE, ADD_OBJ_MODE, ADD_LINK_MODE, SELECTION_MODE}

    private static final long ANIMATE_DURATION_MILLIS = 1000;
    private static final Font FONT = new Font(Font.MONOSPACED, Font.BOLD, 12);
    // These offsets are used because the drag edge should not be targeted by mouse
    private static final float DRAG_END_OFFSET_X = (float) 10.0;
    private static final float DRAG_END_OFFSET_Y = (float) 10.0;
    private static final Color NORMAL_NODE_COLOR = Color.WHITE;
    private static final Color SELECTED_NODE_COLOR = Color.GRAY;
    private EnumSet<GraphEditorMode> m_graphEditorMode = EnumSet.noneOf(GraphEditorMode.class);
    private PLayer m_dragLayer = getLayer();
    private PLayer m_edgeLayer = null;
    private PLayer m_nodeLayer = null;
    private PLayer m_objLayer = null;
    private GraphDragEventHandler m_dragEventHandler = null;
    private GraphDragEventHandler m_dragEventHandlerObjs = null;
    private GraphDragEventHandlerLinks m_dragEventHandlerLinks = null;
    private final NonLinearBookFacade m_nlbFacade;
    private GraphItemsMapper m_graphItemsMapper = new GraphItemsMapper();
    private NodeResizeExecutor m_nodeResizeExecutor;
    private Point2D m_selectionStart = null;
    private PPath m_selectionFrame = new PPath();
    private BulkSelectionHandler m_bulkSelectionHandler = new BulkSelectionHandler();

    private class GraphDragEventHandlerLinks extends PDragEventHandler {
        PNode m_selectedNode = null;
        Point2D m_pickedNodeOrigin = null;

        @Override
        public void mousePressed(PInputEvent event) {
            super.mousePressed(event);
            reselectNode(event);
        }

        private PNode reselectNode(PInputEvent event) {
            List<PNode> relatedNodes;
            if (m_selectedNode != null) {
                relatedNodes = getRelatedNodesReference(m_selectedNode);
                m_selectedNode.setPaint(LinkPath.NORMAL_PAINT);
                m_selectedNode.repaint();
                for (final PNode relNode : relatedNodes) {
                    relNode.setPaint(LinkPath.NORMAL_PAINT);
                    relNode.repaint();
                }
            }
            PNode node = event.getPickedNode();
            relatedNodes = getRelatedNodesReference(node);
            m_pickedNodeOrigin = node.getFullBoundsReference().getOrigin();
            node.setPaint(LinkPath.SELECTED_PAINT);
            node.repaint();
            for (final PNode relNode : relatedNodes) {
                relNode.setPaint(LinkPath.SELECTED_PAINT);
                relNode.repaint();
            }
            m_selectedNode = node;
            return m_selectedNode;
        }

        private PNode getLinkNode(PNode node) {
            final Link link = getLink(node);
            if (link == null) {
                return node.getParent();
            } else {
                return node;
            }
        }

        private List<PNode> getRelatedNodesReference(PNode node) {
            List<PNode> result = new ArrayList<>();
            if (isLinkNode(node)) {
                ListIterator iterator = node.getChildrenIterator();
                while (iterator.hasNext()) {
                    PNode curNode = (PNode) iterator.next();
                    result.add(curNode);
                }
            } else {
                result.add(node.getParent());
            }

            return result;
        }

        private boolean isLinkNode(PNode node) {
            return getLink(node) != null;
        }

        public LinkSelectionData getSelectedLink() {
            final PNode linkNode = getLinkNode(m_selectedNode);
            final Link link = getLink(linkNode);
            return new LinkSelectionData(link);
        }

        public PNode getSelectedLinkNode() {
            return getLinkNode(m_selectedNode);
        }

        private Link getLink(final PNode node) {
            return (Link) node.getAttribute(Constants.NLB_LINK_ATTR);
        }

        protected void startDrag(PInputEvent e) {
            super.startDrag(e);
            e.setHandled(true);
        }

        @Override
        protected void endDrag(PInputEvent event) {
            super.endDrag(event);

            // Links itself cannot be dragged, only its labels can.
            if (!isLinkNode(m_selectedNode)) {
                // Dragging only lastly selected node, not event.getPickedNode()
                final PNode pickedNode = m_selectedNode;
                Link link = getLink(pickedNode);
                if (link == null) {
                    link = getLink(pickedNode.getParent());
                }
                final Coords coords = link.getCoords();
                final Point2D shiftedOrigin = m_selectedNode.getFullBoundsReference().getOrigin();
                PAffineTransform transform = m_selectedNode.getInverseTransform();
                Point2D ptShO = null;
                ptShO = transform.transform(shiftedOrigin, ptShO);
                Point2D ptO = null;
                ptO = transform.transform(m_pickedNodeOrigin, ptO);
                if (m_pickedNodeOrigin != null) {
                    float oldLeft = coords.getLeft();
                    float oldTop = coords.getTop();
                    m_nlbFacade.updateLinkCoords(
                            link,
                            oldLeft + (float) (ptShO.getX() - ptO.getX()),
                            oldTop + (float) (ptShO.getY() - ptO.getY())
                    );
                }
            }
        }

        @Override
        protected void drag(PInputEvent event) {
            // Links itself cannot be dragged, only its labels can.
            if (!isLinkNode(m_selectedNode)) {
                super.drag(event);
            }
        }


    }

    // Create event handler to move nodes and update edges
    private class GraphDragEventHandler<T extends NodePath> extends PDragEventHandler {
        T m_prevNode = null;
        T m_selectedNode = null;
        PPath m_dragEdge = new PPath();
        Color m_mouseOverColor;
        String m_attributeName;

        private GraphDragEventHandler(Color mouseOverColor, String attributeName) {
            m_mouseOverColor = mouseOverColor;
            m_attributeName = attributeName;
        }

        {
            m_dragEdge.setStrokePaint(LinkPath.NORMAL_STROKE_PAINT);
            m_dragLayer.addChild(m_dragEdge);
            PInputEventFilter filter = new PInputEventFilter();
            filter.setOrMask(InputEvent.BUTTON1_MASK | InputEvent.BUTTON3_MASK);
            setEventFilter(filter);
        }

        public void mouseEntered(PInputEvent e) {
            super.mouseEntered(e);
            if (e.getButton() == MouseEvent.NOBUTTON) {
                e.getPickedNode().setPaint(m_mouseOverColor);
            }
        }

        public void mouseExited(PInputEvent e) {
            super.mouseExited(e);
            if (e.getButton() == MouseEvent.NOBUTTON) {
                PNode pickedNode = e.getPickedNode();
                if (m_bulkSelectionHandler.isSelected(pickedNode)) {
                    pickedNode.setPaint(SELECTED_NODE_COLOR);
                } else {
                    pickedNode.setPaint(NORMAL_NODE_COLOR);
                }
            }
            if (m_selectedNode != null) {
                m_selectedNode.setPaint(SELECTED_NODE_COLOR);
            }
        }

        protected void startDrag(PInputEvent e) {
            if (!isAddLinkMode()) {
                super.startDrag(e);
                e.setHandled(true);
                moveToFront(e.getPickedNode());
            }
        }

        private void moveToFront(PNode node) {
            node.moveToFront();
            List<PNode> containedObjsNodes = m_graphItemsMapper.getContainedObjsNodes(node);
            for (PNode containedNode : containedObjsNodes) {
                containedNode.moveInFrontOf(node);
            }
        }

        protected void drag(PInputEvent e) {
            if (!isAddLinkMode()) {
                super.drag(e);
                final PNode pickedNode = e.getPickedNode();
                final Dimension2D delta = e.getDeltaRelativeTo(pickedNode);
                offsetContainedObjects(pickedNode, (float) delta.getWidth(), (float) delta.getHeight());

                NodeItem nodeItem = (NodeItem) pickedNode.getAttribute(Constants.NLB_PAGE_ATTR);
                if (nodeItem == null) {
                    nodeItem = (NodeItem) pickedNode.getAttribute(Constants.NLB_OBJ_ATTR);
                }
                m_nlbFacade.invalidateAssociatedLinks(nodeItem);
                e.setHandled(true);
            }
        }

        private void offsetContainedObjects(PNode container, float deltaX, float deltaY) {
            List<PNode> containedObjsNodes = m_graphItemsMapper.getContainedObjsNodes(container);
            for (PNode node : containedObjsNodes) {
                node.moveInFrontOf(container);
                node.offset(deltaX, deltaY);
                NodeItem nodeItem = (NodeItem) node.getAttribute(Constants.NLB_PAGE_ATTR);
                if (nodeItem == null) {
                    nodeItem = (NodeItem) node.getAttribute(Constants.NLB_OBJ_ATTR);
                }
                m_nlbFacade.invalidateAssociatedLinks(nodeItem);
                offsetContainedObjects(node, deltaX, deltaY);
            }
        }

        @Override
        protected void endDrag(PInputEvent e) {
            if (!isAddLinkMode()) {
                super.endDrag(e);
                final PNode pickedNode = e.getPickedNode();

                Point2D ptDst = pickedNode.getGlobalFullBounds().getCenter2D();
                NodeItem nodeItem = (NodeItem) pickedNode.getAttribute(Constants.NLB_PAGE_ATTR);
                if (nodeItem == null) {
                    nodeItem = (NodeItem) pickedNode.getAttribute(Constants.NLB_OBJ_ATTR);
                }
                Coords coords = nodeItem.getCoords();
                ptDst.setLocation(
                        ptDst.getX() - coords.getWidth() / 2.0,
                        ptDst.getY() - coords.getHeight() / 2.0
                );
                float deltaX = (float) ptDst.getX() - coords.getLeft();
                float deltaY = (float) ptDst.getY() - coords.getTop();
                if (
                        Math.abs(deltaX) > Constants.FL_ZERO_TOLERANCE
                                || Math.abs(deltaY) > Constants.FL_ZERO_TOLERANCE
                        ) {
                    m_nlbFacade.updateNodeCoords(
                            nodeItem,
                            (float) ptDst.getX(),
                            (float) ptDst.getY(),
                            deltaX,
                            deltaY
                    );

                    final Obj obj = (Obj) pickedNode.getAttribute(Constants.NLB_OBJ_ATTR);
                    if (obj != null) {
                        final PBounds pickedNodeBounds = pickedNode.getGlobalFullBounds();
                        PNodeFilter nodeFilter = new PNodeFilter() {
                            @Override
                            public boolean accept(PNode pNode) {
                                PNode containerNode = m_graphItemsMapper.getContainer(pNode);
                                if (containerNode != null) {
                                    NodeItem nodeItem = (
                                            (NodeItem) containerNode.getAttribute(Constants.NLB_PAGE_ATTR)
                                    );
                                    if (nodeItem == null) {
                                        nodeItem = (NodeItem) containerNode.getAttribute(Constants.NLB_OBJ_ATTR);
                                    }
                                    if (nodeItem.getId().equals(obj.getId())) {
                                        // Do not select nodes that has pickedNode as their container
                                        return false;
                                    }
                                }
                                return pNode.getGlobalFullBounds().intersects(pickedNodeBounds);
                            }

                            @Override
                            public boolean acceptChildrenOf(PNode pNode) {
                                return accept(pNode);
                            }
                        };
                        Collection<PNode> resultantObjNodes = new ArrayList<>();
                        Collection<PNode> resultantNodes = new ArrayList<>();
                        // select firstly matching object nodes...
                        resultantObjNodes = m_objLayer.getAllNodes(nodeFilter, resultantObjNodes);
                        boolean processed = changeContainer(obj, resultantObjNodes);
                        if (!processed) {
                            // ...and then matching page nodes
                            resultantNodes = m_nodeLayer.getAllNodes(nodeFilter, resultantNodes);
                            processed = changeContainer(obj, resultantNodes);
                            if (!processed && obj.getContainerId() != null) {
                                // Remove from previous container
                                m_nlbFacade.changeContainer(
                                        obj.getContainerId(),
                                        null,
                                        obj.getId()
                                );
                            }
                        }
                    }
                }
            }
        }

        private boolean changeContainer(
                Obj obj,
                Collection<PNode> possibleContainers
        ) {
            boolean processed = false;
            for (PNode node : possibleContainers) {
                NodeItem nodeItem = (NodeItem) node.getAttribute(Constants.NLB_PAGE_ATTR);
                if (nodeItem == null) {
                    nodeItem = (NodeItem) node.getAttribute(Constants.NLB_OBJ_ATTR);
                }
                final boolean sameContainer = (
                        nodeItem != null
                                && nodeItem.getId().equals(obj.getContainerId())
                );
                if (sameContainer) {
                    // If same container is present in the list of the possible containers,
                    // then we will not change this
                    return true;
                }
                if (
                        nodeItem != null
                                && !nodeItem.getId().equals(obj.getId())  // not self
                        ) {
                    m_nlbFacade.changeContainer(
                            obj.getContainerId(),
                            nodeItem.getId(),
                            obj.getId()
                    );
                    // TODO: Use only first elem for now
                    processed = true;
                    break;
                }
            }
            return processed;
        }

        @Override
        public void mouseClicked(PInputEvent event) {
            super.mouseClicked(event);
            m_bulkSelectionHandler.recolor(m_graphItemsMapper, NORMAL_NODE_COLOR);
            m_bulkSelectionHandler.clear();

            PNode node = event.getPickedNode();
            if (isAddLinkMode()) {
                Object nodeData = node.getAttribute(m_attributeName);
                if (nodeData == null) {
                    resetLinkAddition();
                } else {
                    if (m_prevNode != null) {
                        addLink(m_prevNode, (T) node);
                        resetLinkAddition();
                    } else {
                        m_prevNode = (T) node;
                    }
                }
            } else {
                m_prevNode = null;
            }

            if (m_selectedNode != null) {
                m_selectedNode.setPaint(NORMAL_NODE_COLOR);
            }
            m_selectedNode = (T) node;
            m_selectedNode.setPaint(SELECTED_NODE_COLOR);

            // Adding of the pages should not be done here, because this handler does not work
            // if mouse isn't over the graph elements
        }

        public void resetLinkAddition() {
            m_prevNode = null;
            m_dragEdge.reset();
        }

        public void mouseMove(Point2D position) {
            if (m_prevNode != null) {
                Point2D start = m_prevNode.getFullBoundsReference().getCenter2D();
                m_dragEdge.reset();
                m_dragEdge.moveTo((float) start.getX(), (float) start.getY());
                Point2D ptDst = null;
                ptDst = getCamera().getViewTransform().inverseTransform(position, ptDst);
                float offsetX = (
                        (start.getX() < ptDst.getX())
                                ? DRAG_END_OFFSET_X
                                : -DRAG_END_OFFSET_X
                );
                m_dragEdge.lineTo(
                        (float) ptDst.getX() - offsetX,
                        (float) ptDst.getY()
                );
                m_dragEdge.lineTo(
                        (float) ptDst.getX() - offsetX,
                        (float) ptDst.getY() + DRAG_END_OFFSET_Y
                );
                m_dragEdge.lineTo(
                        (float) ptDst.getX() + offsetX,
                        (float) ptDst.getY() + DRAG_END_OFFSET_Y
                );
                m_dragEdge.lineTo(
                        (float) ptDst.getX() + offsetX,
                        (float) ptDst.getY() - DRAG_END_OFFSET_Y
                );
                m_dragEdge.lineTo(
                        (float) ptDst.getX() - offsetX,
                        (float) ptDst.getY() - DRAG_END_OFFSET_Y
                );
                m_dragEdge.lineTo(
                        (float) ptDst.getX() - offsetX,
                        (float) ptDst.getY()
                );
            } else {
                m_dragEdge.reset();
            }
        }

        private PNode getSelectedNode() {
            return m_selectedNode;
        }

        public void clear() {
            m_prevNode = null;
            m_dragEdge.reset();
        }
    }

    public GraphEditor(NonLinearBookFacade nlbFacade) {
        this(nlbFacade, 500, 500);
        init();
    }

    protected GraphEditor(NonLinearBookFacade nlbFacade, int width, int height) {
        m_nlbFacade = nlbFacade;
        m_nodeResizeExecutor = new NodeResizeExecutor(m_nlbFacade);
        setPreferredSize(new Dimension(width, height));

        m_edgeLayer = new PLayer();
        getRoot().addChild(m_edgeLayer);
        getCamera().addLayer(0, m_edgeLayer);

        m_objLayer = new PLayer();
        getRoot().addChild(m_objLayer);
        getCamera().addLayer(0, m_objLayer);

        m_nodeLayer = new PLayer();
        getRoot().addChild(m_nodeLayer);
        getCamera().addLayer(0, m_nodeLayer);

        int quality = PPaintContext.HIGH_QUALITY_RENDERING;
        setDefaultRenderQuality(quality);
        setInteractingRenderQuality(quality);
        setAnimatingRenderQuality(quality);

        m_dragEventHandler = (
                new GraphDragEventHandler<PagePath>(Color.RED, Constants.NLB_PAGE_ATTR)
        );
        m_dragEventHandlerObjs = (
                new GraphDragEventHandler<ObjPath>(Color.CYAN, Constants.NLB_OBJ_ATTR)
        );
        m_dragEventHandlerLinks = new GraphDragEventHandlerLinks();
        m_nodeLayer.addInputEventListener(m_dragEventHandler);
        m_objLayer.addInputEventListener(m_dragEventHandlerObjs);
        m_edgeLayer.addInputEventListener(m_dragEventHandlerLinks);

        m_selectionFrame.setStrokePaint(Color.BLACK);
        m_dragLayer.addChild(m_selectionFrame);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isAddPageMode()) {
                    addNode(e.getPoint());
                } else if (isAddObjMode()) {
                    addObjNode(e.getPoint());
                } else if (isSelectionMode()) {
                    if (m_selectionStart == null) {
                        m_selectionStart = new Point2D.Double();
                        Point2D ptDst = null;
                        ptDst = getCamera().getViewTransform().inverseTransform(e.getPoint(), ptDst);
                        m_selectionStart.setLocation(ptDst);
                    } else {
                        m_selectionStart = null;
                        m_selectionFrame.reset();
                    }
                }
                super.mouseClicked(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                m_bulkSelectionHandler.recolor(m_graphItemsMapper, NORMAL_NODE_COLOR);
                if (isSelectionMode()) {
                    m_bulkSelectionHandler.makeSelection(m_selectionFrame, m_objLayer, m_nodeLayer);
                }
                m_bulkSelectionHandler.recolor(m_graphItemsMapper, SELECTED_NODE_COLOR);
                super.mouseReleased(e);
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    m_dragEventHandler.resetLinkAddition();
                }
                super.keyPressed(e);
            }
        });
    }

    public void addNode(Point2D point) {
        Point2D ptDst = null;
        ptDst = getCamera().getViewTransform().inverseTransform(point, ptDst);
        final Page page = createPage((float) ptDst.getX(), (float) ptDst.getY());
        m_nodeLayer.addChild(
                m_graphItemsMapper.addNode(
                        m_nlbFacade.getNlb(),
                        m_nodeResizeExecutor,
                        page,
                        FONT
                )
        );
        m_nlbFacade.addPage(page);
    }

    public void addObjNode(Point2D point) {
        Point2D ptDst = null;
        ptDst = getCamera().getViewTransform().inverseTransform(point, ptDst);
        final Obj obj = createObj((float) ptDst.getX(), (float) ptDst.getY());
        m_objLayer.addChild(
                m_graphItemsMapper.addObjNode(
                        m_nlbFacade.getNlb(),
                        m_nodeResizeExecutor,
                        obj,
                        FONT
                )
        );
        m_nlbFacade.addObj(obj);
    }

    public void addLink(NodePath nodeFrom, NodePath nodeTo) {
        final Link link = createLink(nodeFrom, nodeTo);
        m_edgeLayer.addChild(
                m_graphItemsMapper.addLink(
                        m_nlbFacade.getNlb(),
                        FONT,
                        getFontMetrics(FONT),
                        nodeFrom,
                        nodeTo,
                        link
                )
        );
        m_nlbFacade.addLink(link);
        // Update all edges from this node, including just created.
        /*ArrayList edgesFrom = (ArrayList) nodeFrom.getAttribute(Constants.PICCOLO_EDGES);
        for (Object o : edgesFrom) {
            ((LinkPath) o).updateView();
        }*/
    }

    public void mouseMove(Point2D position) {
        m_selectionFrame.reset();
        if (isSelectionMode() && m_selectionStart != null) {
            Point2D ptDst = null;
            ptDst = getCamera().getViewTransform().inverseTransform(position, ptDst);
            m_selectionFrame.moveTo(
                    (float) m_selectionStart.getX(),
                    (float) m_selectionStart.getY()
            );
            m_selectionFrame.lineTo(
                    (float) ptDst.getX(),
                    (float) m_selectionStart.getY()
            );
            m_selectionFrame.lineTo(
                    (float) ptDst.getX(),
                    (float) ptDst.getY()
            );
            m_selectionFrame.lineTo(
                    (float) m_selectionStart.getX(),
                    (float) ptDst.getY()
            );
            m_selectionFrame.lineTo(
                    (float) m_selectionStart.getX(),
                    (float) m_selectionStart.getY()
            );
        } else {
            m_dragEventHandler.mouseMove(position);
            m_dragEventHandlerObjs.mouseMove(position);
        }
    }

    public boolean isAddLinkMode() {
        return m_graphEditorMode.contains(GraphEditorMode.ADD_LINK_MODE);
    }

    public void setAddLinkMode(boolean addLinkMode) {
        if (addLinkMode) {
            m_graphEditorMode.add(GraphEditorMode.ADD_LINK_MODE);
        } else {
            m_graphEditorMode.remove(GraphEditorMode.ADD_LINK_MODE);
        }
    }

    public boolean isAddPageMode() {
        return m_graphEditorMode.contains(GraphEditorMode.ADD_PAGE_MODE);
    }

    public void setAddPageMode(boolean addPageMode) {
        if (addPageMode) {
            m_graphEditorMode.add(GraphEditorMode.ADD_PAGE_MODE);
        } else {
            m_graphEditorMode.remove(GraphEditorMode.ADD_PAGE_MODE);
        }
    }

    public boolean isAddObjMode() {
        return m_graphEditorMode.contains(GraphEditorMode.ADD_OBJ_MODE);
    }

    public void setAddObjMode(boolean addObjMode) {
        if (addObjMode) {
            m_graphEditorMode.add(GraphEditorMode.ADD_OBJ_MODE);
        } else {
            m_graphEditorMode.remove(GraphEditorMode.ADD_OBJ_MODE);
        }
    }

    public boolean isSelectionMode() {
        return m_graphEditorMode.contains(GraphEditorMode.SELECTION_MODE);
    }

    public void setSelectionMode(boolean selectionMode) {
        if (selectionMode) {
            m_graphEditorMode.add(GraphEditorMode.SELECTION_MODE);
        } else {
            m_selectionStart = null;
            m_graphEditorMode.remove(GraphEditorMode.SELECTION_MODE);
        }
    }

    public Page getSelectedPage() {
        PNode selectedNode = m_dragEventHandler.getSelectedNode();
        return (
                (selectedNode == null)
                        ? null
                        : (Page) selectedNode.getAttribute(Constants.NLB_PAGE_ATTR)
        );
    }

    public Obj getSelectedObj() {
        PNode selectedNode = m_dragEventHandlerObjs.getSelectedNode();
        return (
                (selectedNode == null)
                        ? null
                        : (Obj) selectedNode.getAttribute(Constants.NLB_OBJ_ATTR)
        );
    }

    public void updatePage(final Page pageToUpdate) {
        m_nlbFacade.updateNode(pageToUpdate);
    }

    public void updateObj(final Obj objToUpdate) {
        m_nlbFacade.updateNode(objToUpdate);
    }

    public void updateLink(final Link link) {
        m_nlbFacade.updateLink(link);
    }

    public LinkSelectionData getSelectedLink() {
        return m_dragEventHandlerLinks.getSelectedLink();
    }

    private Page createPage(float left, float top) {
        return m_nlbFacade.createPage(left, top);
    }

    private Obj createObj(float left, float top) {
        return m_nlbFacade.createObj(left, top);
    }

    private Link createLink(final NodePath nodeFrom, final NodePath nodeTo) {
        NodeItem item = (NodeItem) nodeFrom.getAttribute(Constants.NLB_PAGE_ATTR);
        NodeItem target = (NodeItem) nodeTo.getAttribute(Constants.NLB_PAGE_ATTR);
        if (item == null) {
            item = (NodeItem) nodeFrom.getAttribute(Constants.NLB_OBJ_ATTR);
            target = (NodeItem) nodeTo.getAttribute(Constants.NLB_OBJ_ATTR);
        }
        return m_nlbFacade.createLink(item, target);
    }

    public void save(final ProgressData progressData)
            throws NLBIOException, NLBConsistencyException, NLBVCSException, NLBFileManipulationException {
        m_nlbFacade.save(false, progressData);
    }

    public void saveAs(
            final File nlbFolder,
            final ProgressData progressData
    ) throws NLBIOException, NLBConsistencyException, NLBVCSException, NLBFileManipulationException {
        m_nlbFacade.saveAs(nlbFolder, progressData);
    }

    public void load(final File nlbFolder, ProgressData progressData) throws NLBIOException, NLBConsistencyException, NLBVCSException {
        try {
            m_nlbFacade.load(nlbFolder.getCanonicalPath(), progressData);
            progressData.setProgressValue(75);
            progressData.setNoteText("Drawing objects...");
            init();
        } catch (IOException e) {
            throw new NLBIOException("IOException while loading book", e);
        }
    }

    private void init() {
        clear();
        m_graphItemsMapper.init(
                m_nlbFacade,
                m_nodeResizeExecutor,
                FONT,
                getFontMetrics(FONT)
        );
        for (Map.Entry<String, PagePath> entry : m_graphItemsMapper.pageEntrySet()) {
            m_nodeLayer.addChild(entry.getValue());
        }
        for (Map.Entry<String, ObjPath> entry : m_graphItemsMapper.objEntrySet()) {
            m_objLayer.addChild(entry.getValue());
        }
        for (Map.Entry<String, LinkPath> entry : m_graphItemsMapper.linkEntrySet()) {
            m_edgeLayer.addChild(entry.getValue());
        }
        m_nlbFacade.updateAllViews();
        m_nodeLayer.repaint();
        m_objLayer.repaint();
        m_edgeLayer.repaint();
    }

    public void clear() {
        m_edgeLayer.removeAllChildren();
        m_nodeLayer.removeAllChildren();
        m_objLayer.removeAllChildren();
        m_dragEventHandler.clear();
        m_dragEventHandlerObjs.clear();
    }

    public void deleteSelectedPage() {
        final PNode node = m_dragEventHandler.getSelectedNode();
        final Page page = (Page) node.getAttribute(Constants.NLB_PAGE_ATTR);
        m_nlbFacade.deleteNode(page);
        // m_nodeLayer.removeChild(node); -- do not remove. Deletion is depicted by changing of the
        // node's visibility state.
    }

    public void deleteSelectedObj() {
        final PNode node = m_dragEventHandlerObjs.getSelectedNode();
        final Obj obj = (Obj) node.getAttribute(Constants.NLB_OBJ_ATTR);
        m_nlbFacade.deleteNode(obj);
        // m_objLayer.removeChild(node); -- do not remove. Deletion is depicted by changing of the
        // node's visibility state.
    }

    public void deleteSelectedLink() {
        final PNode node = m_dragEventHandlerLinks.getSelectedLinkNode();
        if (node != null) {
            final Link link = (Link) node.getAttribute(Constants.NLB_LINK_ATTR);
            m_nlbFacade.deleteLink(link);
            // m_edgeLayer.removeChild(node); -- do not remove. Deletion is depicted by changing of
            // the node's visibility state.
        }
    }

    public void addStartPoint() {
        final Page page = getSelectedPage();
        if (page != null) {
            m_nlbFacade.changeStartPoint(page.getId());
        }
    }

    public void zoomIn() {
        zoom(2);
    }

    public void zoomOut() {
        zoom(0.5);
    }

    private void zoom(double scale) {
        final PCamera camera = getCamera();
        final PBounds viewBounds = camera.getViewBounds();
        Point2D center = viewBounds.getCenter2D();
        final PBounds globalFullBounds = camera.getGlobalFullBounds();
        if (viewBounds.contains(globalFullBounds)) {
            center = globalFullBounds.getCenter2D();
        }
        camera.scaleViewAboutPoint(scale, center.getX(), center.getY());
    }

    public void goTo(final String itemId) {
        Rectangle2D rect = m_graphItemsMapper.getItemRectById(itemId);
        if (rect != null) {
            getCamera().animateViewToCenterBounds(rect, false, ANIMATE_DURATION_MILLIS);
        }
    }
}