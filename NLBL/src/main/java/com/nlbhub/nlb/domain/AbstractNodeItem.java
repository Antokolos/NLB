/**
 * @(#)AbstractNodeItem.java
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
package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBFileManipulationException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.util.FileManipulator;
import com.nlbhub.nlb.util.StringHelper;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlElement;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * The AbstractNodeItem class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/20/13
 */
public abstract class AbstractNodeItem extends AbstractModifyingItem implements NodeItem {
    private static final String COORDS_DIR_NAME = "coords";
    private static final String LINKS_DIR_NAME = "links";
    private static final String LNKORDER_FILE_NAME = "lnkorder";
    private static final String LNKORDER_SEPARATOR = "\n";
    private static final String CONTENT_FILE_NAME = "content";
    private static final String CONTENT_SEPARATOR = "\n";
    private static final String STROKE_FILE_NAME = "stroke";
    private static final String FILL_FILE_NAME = "fill";
    private static final String TEXTCOLOR_FILE_NAME = "txtcolor";

    private String m_stroke = DEFAULT_STROKE;
    private String m_fill = DEFAULT_FILL;
    private String m_textColor = DEFAULT_TEXTCOLOR;
    private CoordsImpl m_coords = new CoordsImpl();
    private List<LinkImpl> m_links = new LinkedList<>();
    private List<String> m_containedObjIds = new ArrayList<>();
    private ObserverHandler m_observerHandler = new ObserverHandler();

    class ResizeNodeCommand implements NLBCommand {
        private NodeItem.Orientation m_orientation;
        private double m_deltaX;
        private double m_deltaY;
        private List<Link> m_adjacentLinks = new ArrayList<>();

        public ResizeNodeCommand(
                Orientation orientation,
                double deltaX,
                double deltaY,
                List<Link> adjacentLinks
        ) {
            m_orientation = orientation;
            m_deltaX = deltaX;
            m_deltaY = deltaY;
            m_adjacentLinks.addAll(adjacentLinks);
        }

        @Override
        public void execute() {
            resizeNode(m_orientation, m_deltaX, m_deltaY);
            notifyObservers();
            for (Link link : m_adjacentLinks) {
                link.notifyObservers();
            }
        }

        @Override
        public void revert() {
            resizeNode(m_orientation, -m_deltaX, -m_deltaY);
            notifyObservers();
            for (Link link : m_adjacentLinks) {
                link.notifyObservers();
            }
        }
    }

    /**
     * The AddLinkCommand class
     * This class has package-level visibility and private constructor. It is aggregated with
     * AbstractNodeItem.
     *
     * @author Anton P. Kolosov
     * @version 1.0 1/24/14
     */
    class AddLinkCommand implements NLBCommand {
        LinkImpl m_link;

        public AddLinkCommand(LinkImpl link) {
            m_link = link;
            // m_link.setDeleted(true);   Not fully exists for now, but don't do this, because it
            // affects link object immediately
        }

        @Override
        public void execute() {
            m_link.setDeleted(false);
            addLink(m_link);
            m_link.notifyObservers();
        }

        @Override
        public void revert() {
            m_link.setDeleted(true);
            ListIterator<LinkImpl> linksIterator = m_links.listIterator();
            while (linksIterator.hasNext()) {
                LinkImpl link = linksIterator.next();
                if (link.getId().equals(m_link.getId())) {
                    linksIterator.remove();
                    break;
                }
            }
            m_link.notifyObservers();
        }
    }

    /**
     * The DeleteLinkCommand class
     * Does not fully deletes link from list, only sets its deleted flag
     * This class has package-level visibility and private constructor. It is aggregated with
     * AbstractNodeItem.
     *
     * @author Anton P. Kolosov
     * @version 1.0 1/24/14
     */
    class DeleteLinkCommand implements NLBCommand {
        private LinkImpl m_link;
        private boolean m_previousDeletedState;

        public DeleteLinkCommand(Link link) {
            m_link = getLinkById(link.getId());
            m_previousDeletedState = m_link.isDeleted();
        }

        @Override
        public void execute() {
            m_link.setDeleted(true);
            m_link.notifyObservers();
        }

        @Override
        public void revert() {
            m_link.setDeleted(m_previousDeletedState);
            m_link.notifyObservers();
        }
    }

    /**
     * The DeleteLinkCommand class
     *
     * @author Anton P. Kolosov
     * @version 1.0 1/24/14
     */
    class SortLinksCommand implements NLBCommand {
        private AbstractNodeItem m_nodeItem;
        private List<String> m_previousSortingOrder;
        private List<String> m_newSortingOrder;

        public SortLinksCommand(AbstractNodeItem nodeItem, List<String> newSortingOrder) {
            m_nodeItem = nodeItem;
            m_newSortingOrder = newSortingOrder;
            m_previousSortingOrder = new ArrayList<>();
            for (Link link : m_links) {
                m_previousSortingOrder.add(link.getId());
            }
        }

        @Override
        public void execute() {
            m_nodeItem.applyLinkSortingOrder(m_newSortingOrder);
            m_nodeItem.notifyObservers();
        }

        @Override
        public void revert() {
            m_nodeItem.applyLinkSortingOrder(m_previousSortingOrder);
            m_nodeItem.notifyObservers();
        }
    }

    private void applyLinkSortingOrder(List<String> sortingOrder) {
        List<LinkImpl> sortedLinks = new LinkedList<>();
        // TODO: Optimize sorting
        for (final String linkId : sortingOrder) {
            ListIterator<LinkImpl> linksIterator = m_links.listIterator();
            while (linksIterator.hasNext()) {
                final LinkImpl link = linksIterator.next();
                if (link.getId().equals(linkId)) {
                    linksIterator.remove();
                    sortedLinks.add(link);
                }
            }
        }
        // m_links can be not empty. Add remaining elements to the tail
        sortedLinks.addAll(m_links);
        m_links = sortedLinks;
    }

    /**
     * Default contructor. It is needed for JAXB conversion, do not remove!
     * Do not use it for any other purpose!
     */
    public AbstractNodeItem() {
        super();
    }

    public AbstractNodeItem(final @NotNull NodeItem nodeItem, NonLinearBook currentNLB) {
        super(nodeItem, currentNLB);
        m_stroke = nodeItem.getStroke();
        m_fill = nodeItem.getFill();
        m_textColor = nodeItem.getTextColor();
        Coords coords = nodeItem.getCoords();
        m_coords.setHeight(coords.getHeight());
        m_coords.setWidth(coords.getWidth());
        m_coords.setLeft(coords.getLeft());
        m_coords.setTop(coords.getTop());
        for (Link link : nodeItem.getLinks()) {
            m_links.add(new LinkImpl(this, link));
        }
        for (String containedObjId : nodeItem.getContainedObjIds()) {
            m_containedObjIds.add(containedObjId);
        }
    }

    ResizeNodeCommand createResizeNodeCommand(
            Orientation orientation,
            double deltaX,
            double deltaY,
            List<Link> adjacentLinks
    ) {
        return new ResizeNodeCommand(orientation, deltaX, deltaY, adjacentLinks);
    }

    ResizeNodeCommand createResizeNodeCommand(
            Orientation orientation,
            double deltaX,
            double deltaY
    ) {
        return new ResizeNodeCommand(orientation, deltaX, deltaY, new ArrayList<Link>());
    }

    AddLinkCommand createAddLinkCommand(LinkImpl link) {
        return new AddLinkCommand(link);
    }

    DeleteLinkCommand createDeleteLinkCommand(Link link) {
        return new DeleteLinkCommand(link);
    }

    SortLinksCommand createSortLinksCommand(
            List<Link> newSortingOrder
    ) {
        List<String> idsSortingOrder = new ArrayList<>();
        for (final Link link : newSortingOrder) {
            idsSortingOrder.add(link.getId());
        }
        return new SortLinksCommand(this, idsSortingOrder);
    }

    protected AbstractNodeItem(NonLinearBook currentNLB) {
        super(currentNLB);
    }

    public AbstractNodeItem(NonLinearBook currentNLB, float left, float top) {
        super(currentNLB);
        m_coords.setLeft(left);
        m_coords.setTop(top);
        m_coords.setWidth(DEFAULT_NODE_WIDTH);
        m_coords.setHeight(DEFAULT_NODE_HEIGHT);
    }

    @Override
    @XmlElement(name = "stroke")
    public String getStroke() {
        return m_stroke;
    }

    public void setStroke(String stroke) {
        m_stroke = stroke;
    }

    @Override
    @XmlElement(name = "fill")
    public String getFill() {
        return m_fill;
    }

    public void setFill(String fill) {
        m_fill = fill;
    }

    @Override
    @XmlElement(name = "textColor")
    public String getTextColor() {
        return m_textColor;
    }

    public void setTextColor(String textColor) {
        m_textColor = textColor;
    }

    @Override
    @XmlElement(name = "containedobj")
    public List<String> getContainedObjIds() {
        return m_containedObjIds;
    }

    public void addContainedObjId(@NotNull String containedObjId) {
        m_containedObjIds.add(containedObjId);
    }

    public void removeContainedObjId(@NotNull String containedObjId) {
        ListIterator<String> iterator = m_containedObjIds.listIterator();
        while (iterator.hasNext()) {
            String temp = iterator.next();
            if (containedObjId.equals(temp)) {
                iterator.remove();
                break;
            }
        }
    }

    @Override
    public CoordsImpl getCoords() {
        return m_coords;
    }

    @Override
    public List<Link> getLinks() {
        List<Link> result = new ArrayList<>();
        result.addAll(getLinkImpls());
        return result;
    }

    @XmlElement(name = "link")
    public List<LinkImpl> getLinkImpls() {
        return m_links;
    }

    public int getLinkCount() {
        return m_links.size();
    }

    public void addLink(@NotNull LinkImpl link) {
        m_links.add(link);
    }

    public void writeNodeItemProperties(
            final @NotNull FileManipulator fileManipulator,
            final @NotNull File nodeDir,
            final @NotNull NonLinearBookImpl nonLinearBook
    ) throws IOException, NLBIOException, NLBFileManipulationException, NLBVCSException {
        fileManipulator.writeOptionalString(nodeDir, STROKE_FILE_NAME, m_stroke, DEFAULT_STROKE);
        fileManipulator.writeOptionalString(nodeDir, FILL_FILE_NAME, m_fill, DEFAULT_FILL);
        fileManipulator.writeOptionalString(nodeDir, TEXTCOLOR_FILE_NAME, m_textColor, DEFAULT_TEXTCOLOR);
        writeLinkOrderFile(fileManipulator, nodeDir);
        writeContent(fileManipulator, nodeDir, nonLinearBook);
        writeCoords(fileManipulator, nodeDir);
        writeLinks(fileManipulator, nodeDir);
    }

    public void readNodeItemProperties(File nodeDir) throws NLBIOException, NLBConsistencyException {
        m_stroke = (
                FileManipulator.getOptionalFileAsString(
                        nodeDir,
                        STROKE_FILE_NAME,
                        DEFAULT_STROKE
                )
        );
        m_fill = (
                FileManipulator.getOptionalFileAsString(
                        nodeDir,
                        FILL_FILE_NAME,
                        DEFAULT_FILL
                )
        );
        m_textColor = (
                FileManipulator.getOptionalFileAsString(
                        nodeDir,
                        TEXTCOLOR_FILE_NAME,
                        DEFAULT_TEXTCOLOR
                )
        );
        readContent(nodeDir);
        readCoords(nodeDir);
        readLinks(nodeDir);
    }

    private void readLinks(File nodeDir) throws NLBIOException, NLBConsistencyException {
        String linkOrderString = FileManipulator.getOptionalFileAsString(
                nodeDir,
                LNKORDER_FILE_NAME,
                DEFAULT_LNKORDER
        );
        final File linksDir = new File(nodeDir, LINKS_DIR_NAME);
        if (!linksDir.exists() && !linkOrderString.isEmpty()) {
            throw new NLBIOException(
                    "Invalid NLB structure: links directory does not exist for node with Id = "
                            + getId()
                            + ", but this node has existent links"
            );
        }
        m_links.clear();
        File[] linkDirs = linksDir.listFiles();
        if (linkDirs == null) {
            if (linkOrderString.isEmpty()) {
                linkDirs = new File[0];
            } else {
                throw new NLBIOException(
                        "Error when enumerating links' directory contents for node with Id = " + getId()
                );
            }
        }
        if (linkOrderString.isEmpty()) {
            if (linkDirs.length > 0) {
                throw new NLBConsistencyException(
                        "Inconsistent NLB structure: '" + LINKS_DIR_NAME + "' directory "
                                + "should be empty for node with id = "
                                + getId()
                );
            }
        } else {
            List<String> linkOrderList = Arrays.asList(linkOrderString.split(LNKORDER_SEPARATOR));
            List<File> linkDirsSortedList = createSortedDirList(linkDirs, linkOrderList);

            for (int i = 0; i < linkDirsSortedList.size(); i++) {
                final LinkImpl link = new LinkImpl(this);
                link.readLink(linkDirsSortedList.get(i));
                m_links.add(link);
            }
        }

    }

    private void readCoords(File nodeDir) throws NLBIOException {
        final File coordsDir = new File(nodeDir, COORDS_DIR_NAME);
        if (!coordsDir.exists()) {
            throw new NLBIOException(
                    "Invalid NLB structure: coords directory does not exist for node with Id = " + getId()
            );
        }
        m_coords.read(coordsDir);
    }

    @Override
    public LinkImpl getLinkById(@NotNull String linkId) {
        for (final LinkImpl link : m_links) {
            if (linkId.equals(link.getId())) {
                return link;
            }
        }
        return null;
    }

    private void writeLinks(
            FileManipulator fileManipulator,
            File nodeDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        final File linksDir = new File(nodeDir, LINKS_DIR_NAME);
        if (m_links.isEmpty()) {
            if (linksDir.exists()) {
                fileManipulator.deleteFileOrDir(linksDir);
            }
        } else {
            fileManipulator.createDir(
                    linksDir,
                    "Cannot create node links directory for node with Id = " + getId()
            );
            for (LinkImpl link : m_links) {
                link.writeLink(fileManipulator, linksDir);
            }
        }
    }

    private void writeLinkOrderFile(
            FileManipulator fileManipulator,
            File nodeDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        StringBuilder sb = new StringBuilder();
        final int lastElemIndex = m_links.size() - 1;
        if (lastElemIndex >= 0) {
            for (int i = 0; i < lastElemIndex; i++) {
                final LinkImpl link = m_links.get(i);
                if (!link.isDeleted()) {
                    sb.append(link.getId()).append(LNKORDER_SEPARATOR);
                }
            }
            if (!m_links.get(lastElemIndex).isDeleted()) {
                sb.append(m_links.get(lastElemIndex).getId());
            }
            fileManipulator.writeOptionalString(nodeDir, LNKORDER_FILE_NAME, String.valueOf(sb.toString()), DEFAULT_LNKORDER);
        } else {
            fileManipulator.writeOptionalString(nodeDir, LNKORDER_FILE_NAME, Constants.EMPTY_STRING, DEFAULT_LNKORDER);
        }
    }

    private void writeCoords(
            FileManipulator fileManipulator,
            File nodeDir
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        final File coordsDir = new File(nodeDir, COORDS_DIR_NAME);
        fileManipulator.createDir(
                coordsDir,
                "Cannot create node coords directory for node with Id = " + getId()
        );
        m_coords.writeCoords(fileManipulator, coordsDir);
    }

    private void writeContent(
            FileManipulator fileManipulator,
            File nodeDir,
            NonLinearBookImpl nonLinearBook
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        StringBuilder sb = new StringBuilder();
        final int lastElemIndex = m_containedObjIds.size() - 1;
        if (lastElemIndex >= 0) {
            for (int i = 0; i < lastElemIndex; i++) {
                final String objId = m_containedObjIds.get(i);
                final ObjImpl obj = nonLinearBook.getObjImplById(objId);
                // TODO: validate NLB before save to exclude this in-place checks!
                if (obj != null && !obj.isDeleted()) {
                    sb.append(objId).append(CONTENT_SEPARATOR);
                }
            }
            final ObjImpl obj = nonLinearBook.getObjImplById(m_containedObjIds.get(lastElemIndex));
            if (obj != null && !obj.isDeleted()) {
                sb.append(m_containedObjIds.get(lastElemIndex));
            }
            fileManipulator.writeOptionalString(nodeDir, CONTENT_FILE_NAME, String.valueOf(sb.toString()), DEFAULT_CONTENT);
        } else {
            fileManipulator.writeOptionalString(nodeDir, CONTENT_FILE_NAME, "", DEFAULT_CONTENT);
        }
    }

    private void readContent(File nodeDir) throws NLBIOException, NLBConsistencyException {
        m_containedObjIds.clear();
        String contentString = FileManipulator.getOptionalFileAsString(
                nodeDir,
                CONTENT_FILE_NAME,
                DEFAULT_CONTENT
        );
        if (!StringHelper.isEmpty(contentString)) {
            // Do not use m_containedObjIds = Arrays.asList,
            // because it can change list type to AbstractList
            m_containedObjIds.addAll(Arrays.asList(contentString.split(CONTENT_SEPARATOR)));
        }
    }

    public static void filterTargetLinkList(
            AbstractNodeItem target,
            AbstractNodeItem source,
            List<String> linkIdsToBeExcluded
    ) {
        target.m_links = new ArrayList<>();
        for (LinkImpl link : source.m_links) {
            if (!linkIdsToBeExcluded.contains(link.getId())) {
                target.m_links.add(link);
            }
        }
    }

    /**
     * Dangerous method to use... Use it only when book does not contain any related information about this link
     * (e.g. link variables, link modifications etc) and link files does not exist on disk. Also please note that
     * if you remove link in this way, it will not be removed from the UI. If you want to safely
     * delete link and its data, use {@link com.nlbhub.nlb.domain.AbstractIdentifiableItem#setDeleted(boolean)}
     *
     * @param linkId ID of the link to be removed from links list
     */
    public void removeLinkById(final String linkId) {
        ListIterator<LinkImpl> iterator = m_links.listIterator();
        while (iterator.hasNext()) {
            LinkImpl temp = iterator.next();
            if (temp.getId().equals(linkId)) {
                iterator.remove();
                break;
            }
        }
    }

    void resizeNode(Orientation orientation, double deltaX, double deltaY) {
        final CoordsImpl coords = getCoords();
        float width;
        float height;
        float ignoredDistance = (float) 0.0;
        switch (orientation) {
            case RIGHT:
                width = coords.getWidth() + (float) deltaX;
                if (width < NodeItem.DEFAULT_NODE_WIDTH) {
                    width = NodeItem.DEFAULT_NODE_WIDTH;
                }
                coords.setWidth(width);
                break;
            case BOTTOM:
                height = coords.getHeight() + (float) deltaY;
                if (height < NodeItem.DEFAULT_NODE_HEIGHT) {
                    height = NodeItem.DEFAULT_NODE_HEIGHT;
                }
                coords.setHeight(height);
                break;
            case LEFT:
                width = coords.getWidth() - (float) deltaX;
                if (width < NodeItem.DEFAULT_NODE_WIDTH) {
                    ignoredDistance = NodeItem.DEFAULT_NODE_WIDTH - width;
                    width = NodeItem.DEFAULT_NODE_WIDTH;
                }
                coords.setLeft(coords.getLeft() + (float) deltaX - ignoredDistance);
                coords.setWidth(width);
                break;
            case TOP:
                height = coords.getHeight() - (float) deltaY;
                if (height < NodeItem.DEFAULT_NODE_HEIGHT) {
                    ignoredDistance = NodeItem.DEFAULT_NODE_HEIGHT - height;
                    height = NodeItem.DEFAULT_NODE_HEIGHT;
                }
                coords.setTop(coords.getTop() + (float) deltaY - ignoredDistance);
                coords.setHeight(height);
                break;
        }
    }

    @Override
    public String addObserver(NLBObserver observer) {
        return m_observerHandler.addObserver(observer);
    }

    @Override
    public void removeObserver(String observerId) {
        m_observerHandler.removeObserver(observerId);
    }

    @Override
    public void notifyObservers() {
        m_observerHandler.notifyObservers();
    }

    @Override
    public String getExternalHierarchy() {
        StringBuilder builder = new StringBuilder();
        NonLinearBook currentNLB = getCurrentNLB();
        List<String> hierarchy = new ArrayList<>();
        boolean proceed;
        do {
            Page parentPage = currentNLB.getParentPage();
            proceed = (parentPage != null && parentPage.isModuleExternal());
            if (proceed) {
                hierarchy.add(parentPage.getModuleName());
                currentNLB = parentPage.getCurrentNLB();
            }
        } while (proceed);
        for (int i = hierarchy.size() - 1; i >= 0; i--) {
            builder.append(hierarchy.get(i));
            if (i > 0) {
                builder.append("/");
            }
        }
        return builder.toString();
    }
}
