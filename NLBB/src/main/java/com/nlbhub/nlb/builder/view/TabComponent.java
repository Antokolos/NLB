/**
 * @(#)TabComponent.java
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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * The TabComponent class
 *
 * @author Anton P. Kolosov
 * @version 1.0 2/25/14
 */
public class TabComponent {
    public static final String CLOSED_IN_CODE = "CLOSED_IN_CODE";
    private final JPanel m_pnlTab;
    private final JLabel m_lblTitle;
    private final JButton m_btnClose;
    private final ActionListener m_closeActionListener;

    public TabComponent(
            final String tabName,
            final ActionListener closeActionListener
    ) {
        m_pnlTab = new JPanel(new GridBagLayout());
        m_pnlTab.setOpaque(false);
        m_lblTitle = new JLabel(tabName);
        m_btnClose = new JButton("x");
        m_btnClose.setBorder(new EmptyBorder(5, 10, 5, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;

        m_pnlTab.add(m_lblTitle, gbc);

        gbc.gridx++;
        gbc.weightx = 0;
        m_pnlTab.add(m_btnClose, gbc);

        m_closeActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                closeActionListener.actionPerformed(actionEvent);
                m_btnClose.removeActionListener(this);
            }
        };

        m_btnClose.addActionListener(m_closeActionListener);
    }

    public void setTabName(String tabName) {
        m_lblTitle.setText(tabName);
    }

    public JPanel getPnlTab() {
        return m_pnlTab;
    }

    public void close() {
        m_closeActionListener.actionPerformed(
                new ActionEvent(m_btnClose, ActionEvent.ACTION_PERFORMED, CLOSED_IN_CODE)
        );
    }
}
