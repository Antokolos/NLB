/**
 * @(#)ObjPath.java
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

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.util.StringHelper;
import edu.umd.cs.piccolo.nodes.PText;

import java.awt.*;

/**
 * The ObjPath class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/19/13
 */
public class ObjPath extends NodePath {
    private static final int MAX_NAME_CHARS_IN_NAME_TEXT = 42;
    private static final String INFO_OBJ = "<Obj> ";
    private static final String INFO_CONTAINED = "<C> ";
    private final PText m_infoNode = new PText(INFO_OBJ);
    private String m_infoText = INFO_OBJ;

    public ObjPath(
            final NonLinearBook nonLinearBook,
            final NodeResizeExecutor nodeResizeExecutor,
            final Obj obj,
            Font font
    ) {
        super(nodeResizeExecutor, obj, font);
        m_infoNode.setVisible(true);
        addChild(m_infoNode);
        m_infoNode.setFont(font);
        m_infoNode.setPickable(false);
        setPaint(Color.white);
        addAttribute(Constants.NLB_OBJ_ATTR, obj);
        addAttribute(Constants.NLB_MODULE_ATTR, nonLinearBook);
        resizeNode(obj.getCoords());
    }

    @Override
    protected void resizeNode(Coords coords) {
        super.resizeNode(coords);
        m_infoNode.setBounds(getNodeRect());
        m_infoNode.setText(m_infoText);
    }

    @Override
    public void updateView() {
        Obj obj = (Obj) getAttribute(Constants.NLB_OBJ_ATTR);
        m_infoText = INFO_OBJ;
        if (!StringHelper.isEmpty(obj.getContainerId())) {
            m_infoText += INFO_CONTAINED;
        }
        super.updateView();
    }

    @Override
    protected String buildText() {
        Obj obj = (Obj) getAttribute(Constants.NLB_OBJ_ATTR);
        final NonLinearBook nonLinearBook = (NonLinearBook) getAttribute(Constants.NLB_MODULE_ATTR);
        final Variable variable = nonLinearBook.getVariableById(obj.getVarId());
        final Variable constraint = nonLinearBook.getVariableById(obj.getConstrId());
        final StringBuilder text = new StringBuilder();
        if (!StringHelper.isEmpty(obj.getName())) {
            int nameSize = obj.getName().length();
            if (nameSize > MAX_NAME_CHARS_IN_NAME_TEXT) {
                text.append(obj.getName().substring(0, MAX_NAME_CHARS_IN_NAME_TEXT));
                text.append("...");
            } else {
                text.append(obj.getName());
            }
        }
        if (variable != null) {
            text.append(": [").append(variable.getName()).append("]");
        }
        if (constraint != null) {
            text.append(": /").append(constraint.getValue()).append("/");
        }
        return text.toString();
    }
}
