/**
 * @(#)DialogSearchResults.java
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
package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.builder.model.SearchResultsTableModelSwing;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import org.jdesktop.swingx.JXTable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DialogSearchResults extends JDialog {

    public static enum SearchType {Leafs, CheckBook, Variables}

    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JXTable m_searchResults;
    private JButton m_buttonGoTo;
    private SearchResultsTableModelSwing m_tableModel;

    public DialogSearchResults(
            final MainFrame mainFrame,
            final NonLinearBook nlb,
            final String modulePageId,
            final SearchType searchType
    ) throws NLBConsistencyException {
        setContentPane(contentPane);
        switch (searchType) {
            case Variables:
                m_tableModel = new SearchResultsTableModelSwing(nlb.getVariables(modulePageId));
                break;
            case CheckBook:
                m_tableModel = new SearchResultsTableModelSwing(nlb.checkBook(modulePageId));
                break;
            case Leafs:
            default:
                m_tableModel = new SearchResultsTableModelSwing(nlb.getLeafs(modulePageId));
                break;
        }
        m_searchResults.setModel(m_tableModel);

        setModal(true);
        setTitle("Search results");
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        m_buttonGoTo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                mainFrame.goTo(
                        (String) m_tableModel.getValueAt(m_searchResults.getSelectedRow(), 1),
                        (String) m_tableModel.getValueAt(m_searchResults.getSelectedRow(), 0)
                );
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onOK() {
        // add your code here
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public void showDialog() {
        pack();
        // this solves the problem where the dialog was not getting
        // focus the second time it was displayed
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                buttonOK.requestFocusInWindow();
            }
        });
        setVisible(true);
    }

}
