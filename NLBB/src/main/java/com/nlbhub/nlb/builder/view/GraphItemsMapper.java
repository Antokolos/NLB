/**
 * @(#)GraphItemsMapper.java
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

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.util.StringHelper;
import edu.umd.cs.piccolo.PLayer;
import edu.umd.cs.piccolo.PNode;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

/**
 * The GraphItemsMapper class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/21/14
 */
public class GraphItemsMapper {
    private Map<String, PagePath> m_pages = new HashMap<>();
    private Map<String, ObjPath> m_objs = new HashMap<>();
    private Map<String, LinkPath> m_links = new HashMap<>();

    public void init(
        final NonLinearBookFacade nlbFacade,
        final NodeResizeExecutor nodeResizeExecutor,
        final Font font,
        final FontMetrics metrics
    ) {
        clear();
        final NonLinearBook nlb = nlbFacade.getNlb();
        final Map<String, Page> nlbPages = nlb.getPages();
        for (Map.Entry<String, Page> entry : nlbPages.entrySet()) {
            addNode(nlb, nodeResizeExecutor, entry.getValue(), font);
        }
        final Map<String, Obj> nlbObjs = nlb.getObjs();
        for (Map.Entry<String, Obj> entry : nlbObjs.entrySet()) {
            addObjNode(nlb, nodeResizeExecutor, entry.getValue(), font);
        }

        for (Map.Entry<String, ObjPath> pathEntry : m_objs.entrySet()) {
            final ObjPath objPathFrom = pathEntry.getValue();
            final Obj objFrom = (Obj) objPathFrom.getAttribute(Constants.NLB_OBJ_ATTR);
            for (final Link link : objFrom.getLinks()) {
                addLink(nlb, font, metrics, objPathFrom, getObjById(link.getTarget()), link);
            }
            for (String containedObjId : objFrom.getContainedObjIds()) {
                final ObjPath objPath = getObjById(containedObjId);
                objPath.moveInFrontOf(objPathFrom);
            }
        }

        for (Map.Entry<String, PagePath> pathEntry : m_pages.entrySet()) {
            final PagePath pagePathFrom = pathEntry.getValue();
            final Page pageFrom = (Page) pagePathFrom.getAttribute(Constants.NLB_PAGE_ATTR);
            for (final Link link : pageFrom.getLinks()) {
                addLink(nlb, font, metrics, pagePathFrom, getPageById(link.getTarget()), link);
            }
        }
    }

    public void clear() {
        m_pages.clear();
        m_objs.clear();
        m_links.clear();
    }

    public Set<Map.Entry<String, PagePath>> pageEntrySet() {
        return m_pages.entrySet();
    }

    public Set<Map.Entry<String, ObjPath>> objEntrySet() {
        return m_objs.entrySet();
    }

    public Set<Map.Entry<String, LinkPath>> linkEntrySet() {
        return m_links.entrySet();
    }

    public void addPage(final PagePath pagePath) {
        final Page page = (Page) pagePath.getAttribute(Constants.NLB_PAGE_ATTR);
        if (page != null) {
            m_pages.put(page.getId(), pagePath);
        }
    }

    public void addObj(final ObjPath objPath) {
        final Obj obj = (Obj) objPath.getAttribute(Constants.NLB_OBJ_ATTR);
        if (obj != null) {
            m_objs.put(obj.getId(), objPath);
        }
    }

    public void addLink(final LinkPath linkPath) {
        final Link link = (Link) linkPath.getAttribute(Constants.NLB_LINK_ATTR);
        if (link != null) {
            m_links.put(link.getId(), linkPath);
        }
    }

    public LinkPath getLinkById(final String id) {
        return m_links.get(id);
    }

    public ObjPath getObjById(final String id) {
        return m_objs.get(id);
    }

    public PagePath getPageById(final String id) {
        return m_pages.get(id);
    }

    public Rectangle2D getItemRectById(final String id) {
        final PagePath page;
        final ObjPath obj;
        final LinkPath link;
        final Rectangle2D rect = new Rectangle2D.Float();
        final Coords coords;
        if ((page = getPageById(id)) == null) {
            if ((obj = getObjById(id)) == null) {
                if ((link = getLinkById(id)) == null) {
                    return null;
                } else {
                    final Link linkData = (Link) link.getAttribute(Constants.NLB_LINK_ATTR);
                    NodePath nodePath = getPageById(linkData.getTarget());
                    if (nodePath != null) {
                        coords = (
                            ((Page) nodePath.getAttribute(Constants.NLB_PAGE_ATTR)).getCoords()
                        );
                    } else {
                        nodePath = getObjById(linkData.getTarget());
                        coords = (
                            ((Obj) nodePath.getAttribute(Constants.NLB_OBJ_ATTR)).getCoords()
                        );
                    }
                }
            } else {
                coords = (
                    ((Obj) obj.getAttribute(Constants.NLB_OBJ_ATTR)).getCoords()
                );
            }
        } else {
            coords = (
                ((Page) page.getAttribute(Constants.NLB_PAGE_ATTR)).getCoords()
            );
        }
        rect.setFrame(coords.getLeft(), coords.getTop(), coords.getWidth(), coords.getHeight());

        return rect;
    }


    public PagePath addNode(
        final NonLinearBook nonLinearBook,
        final NodeResizeExecutor nodeResizeExecutor,
        final Page page,
        final Font font
    ) {
        PagePath pagePath = (
            new PagePath(
                nonLinearBook,
                nodeResizeExecutor,
                page,
                font
            )
        );
        addPage(pagePath);
        page.notifyObservers();
        return pagePath;
    }

    public ObjPath addObjNode(
        final NonLinearBook nonLinearBook,
        final NodeResizeExecutor nodeResizeExecutor,
        final Obj obj,
        final Font font
    ) {
        ObjPath objPath = (
            new ObjPath(
                nonLinearBook,
                nodeResizeExecutor,
                obj,
                font
            )
        );
        addObj(objPath);
        obj.notifyObservers();
        return objPath;
    }

    public LinkPath addLink(
        final NonLinearBook nonLinearBook,
        final Font font,
        final FontMetrics metrics,
        NodePath nodeFrom,
        NodePath nodeTo,
        final Link link
    ) {
        LinkPath linkPath = (
            new LinkPath(
                nonLinearBook,
                font,
                metrics,
                nodeFrom,
                nodeTo,
                link
            )
        );
        addLink(linkPath);
        link.notifyObservers();
        return linkPath;
    }

    public List<PNode> getContainedObjsNodes(PNode container) {
        List<PNode> result = new ArrayList<>();
        NodeItem nodeItem = (NodeItem) container.getAttribute(Constants.NLB_PAGE_ATTR);
        if (nodeItem == null) {
            nodeItem = (NodeItem) container.getAttribute(Constants.NLB_OBJ_ATTR);
        }
        for (final String objId : nodeItem.getContainedObjIds()) {
            result.add(m_objs.get(objId));
        }
        return result;
    }

    public PNode getContainer(PNode node) {
        Obj obj = (Obj) node.getAttribute(Constants.NLB_OBJ_ATTR);
        if (obj == null || StringHelper.isEmpty(obj.getContainerId())) {
            return null;
        }
        PNode result = m_pages.get(obj.getContainerId());
        return (result != null) ? result : m_objs.get(obj.getContainerId());
    }
}
