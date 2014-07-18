/**
 * @(#)PagePath.java
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
import com.nlbhub.nlb.util.StringHelper;
import edu.umd.cs.piccolo.nodes.PText;

import java.awt.*;

/**
 * The PagePath class
 *
 * @author Anton P. Kolosov
 * @version 1.0 8/9/12
 */
public class PagePath extends NodePath {
    private static final int MAX_CAPTION_CHARS_IN_PAGE_TEXT = 330;
    private final PText m_startPointNode = new PText("<S>");

    public PagePath(
            final NonLinearBook nonLinearBook,
            final NodeResizeExecutor nodeResizeExecutor,
            final Page page,
            Font font
    ) {
        super(nodeResizeExecutor, page, font);
        m_startPointNode.setVisible(page.getId().equals(nonLinearBook.getStartPoint()));
        addChild(m_startPointNode);
        m_startPointNode.setFont(font);
        m_startPointNode.setPickable(false);
        setPaint(Color.white);
        addAttribute(Constants.NLB_PAGE_ATTR, page);
        addAttribute(Constants.NLB_MODULE_ATTR, nonLinearBook);
        resizeNode(page.getCoords());
    }

    @Override
    public void updateView() {
        Page page = (Page) getAttribute(Constants.NLB_PAGE_ATTR);
        final NonLinearBook nonLinearBook = (NonLinearBook) getAttribute(Constants.NLB_MODULE_ATTR);
        m_startPointNode.setVisible(page.getId().equals(nonLinearBook.getStartPoint()));
        super.updateView();
    }

    @Override
    protected void resizeNode(Coords coords) {
        super.resizeNode(coords);
        m_startPointNode.setBounds(getNodeRect());
    }

    @Override
    protected String buildText() {
        final Page page = (Page) getAttribute(Constants.NLB_PAGE_ATTR);
        final NonLinearBook nonLinearBook = (NonLinearBook) getAttribute(Constants.NLB_MODULE_ATTR);
        final Variable variable = nonLinearBook.getVariableById(page.getVarId());
        final StringBuilder text = new StringBuilder();
        if (!StringHelper.isEmpty(page.getCaption())) {
            int captionSize = page.getCaption().length();
            if (captionSize > MAX_CAPTION_CHARS_IN_PAGE_TEXT) {
                text.append(page.getCaption().substring(0, MAX_CAPTION_CHARS_IN_PAGE_TEXT));
                text.append("...");
            } else {
                text.append(page.getCaption());
            }
            if (variable != null) {
                text.append(": ");
            }
        }
        if (variable != null) {
            text.append("[").append(variable.getName()).append("]");
        }
        return text.toString();
    }
}
