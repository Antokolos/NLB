/**
 * @(#)DialogSearch.java
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
import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.SearchContract;
import com.nlbhub.nlb.api.SearchResultTableModel;
import com.nlbhub.nlb.builder.model.SearchResultsTableModelSwing;
import org.jdesktop.swingx.JXTable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DialogSearch extends JDialog {
    private NonLinearBook m_nlb;
    private String m_modulePageId;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField m_searchText;
    private JCheckBox m_pagesCheckBox;
    private JCheckBox m_ignoreCaseCheckBox;
    private JCheckBox m_wholeWordsCheckBox;
    private JCheckBox m_objectsCheckBox;
    private JCheckBox m_linksCheckBox;
    private JButton m_goToButton;
    private JXTable m_searchResultsTable;
    private JCheckBox m_variablesCheckBox;
    private JCheckBox m_idsCheckBox;
    private SearchResultsTableModelSwing m_tableModel;

    public DialogSearch(
            final MainFrame mainFrame,
            final NonLinearBook nlb,
            final String modulePageId,
            final String searchText
    ) {
        m_nlb = nlb;
        m_modulePageId = modulePageId;
        setContentPane(contentPane);
        setModal(true);
        setTitle("Search");
        getRootPane().setDefaultButton(buttonOK);

        m_searchText.setText(searchText);

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

        m_goToButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                mainFrame.goTo(
                        (String) m_tableModel.getValueAt(m_searchResultsTable.getSelectedRow(), 1),
                        (String) m_tableModel.getValueAt(m_searchResultsTable.getSelectedRow(), 0)
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
        SearchResultTableModel searchResultTableModel = m_nlb.searchText(
                new SearchContract(
                        m_searchText.getText(),
                        m_idsCheckBox.isSelected(),
                        m_pagesCheckBox.isSelected(),
                        m_objectsCheckBox.isSelected(),
                        m_linksCheckBox.isSelected(),
                        m_variablesCheckBox.isSelected(),
                        m_ignoreCaseCheckBox.isSelected(),
                        m_wholeWordsCheckBox.isSelected(),
                        PropertyManager.getSettings().getDefaultConfig().getGeneral().isFindUnusualQuotes()
                ),
                m_modulePageId
        );
        m_tableModel = new SearchResultsTableModelSwing(searchResultTableModel);
        m_searchResultsTable.setModel(m_tableModel);
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

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        contentPane = new JPanel();
        contentPane.setLayout(new BorderLayout(0, 0));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new BorderLayout(0, 0));
        contentPane.add(panel1, BorderLayout.CENTER);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new BorderLayout(0, 0));
        panel1.add(panel2, BorderLayout.NORTH);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new BorderLayout(0, 0));
        panel2.add(panel3, BorderLayout.CENTER);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new BorderLayout(0, 0));
        panel3.add(panel4, BorderLayout.EAST);
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridBagLayout());
        panel4.add(panel5, BorderLayout.NORTH);
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel5.add(panel6, gbc);
        buttonOK = new JButton();
        buttonOK.setPreferredSize(new Dimension(95, 25));
        buttonOK.setText("Search...");
        panel6.add(buttonOK);
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel5.add(panel7, gbc);
        buttonCancel = new JButton();
        buttonCancel.setPreferredSize(new Dimension(95, 25));
        buttonCancel.setText("Close");
        panel7.add(buttonCancel);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new BorderLayout(0, 0));
        panel3.add(panel8, BorderLayout.CENTER);
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridBagLayout());
        panel8.add(panel9, BorderLayout.CENTER);
        final JLabel label1 = new JLabel();
        label1.setText("Search text");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel9.add(label1, gbc);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridBagLayout());
        panel10.setMinimumSize(new Dimension(468, 33));
        panel10.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel9.add(panel10, gbc);
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setHorizontalScrollBarPolicy(31);
        scrollPane1.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel10.add(scrollPane1, gbc);
        m_searchText = new JTextField();
        m_searchText.setColumns(40);
        m_searchText.setEditable(true);
        scrollPane1.setViewportView(m_searchText);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel9.add(spacer1, gbc);
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel9.add(panel11, gbc);
        panel11.setBorder(BorderFactory.createTitledBorder("Find where?"));
        m_idsCheckBox = new JCheckBox();
        m_idsCheckBox.setSelected(true);
        m_idsCheckBox.setText("Ids");
        panel11.add(m_idsCheckBox);
        m_pagesCheckBox = new JCheckBox();
        m_pagesCheckBox.setSelected(true);
        m_pagesCheckBox.setText("Pages");
        panel11.add(m_pagesCheckBox);
        m_objectsCheckBox = new JCheckBox();
        m_objectsCheckBox.setSelected(true);
        m_objectsCheckBox.setText("Objects");
        panel11.add(m_objectsCheckBox);
        m_linksCheckBox = new JCheckBox();
        m_linksCheckBox.setSelected(true);
        m_linksCheckBox.setText("Links");
        panel11.add(m_linksCheckBox);
        m_variablesCheckBox = new JCheckBox();
        m_variablesCheckBox.setSelected(true);
        m_variablesCheckBox.setText("Variables");
        panel11.add(m_variablesCheckBox);
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        panel9.add(panel12, gbc);
        panel12.setBorder(BorderFactory.createTitledBorder("Find how?"));
        m_ignoreCaseCheckBox = new JCheckBox();
        m_ignoreCaseCheckBox.setText("Ignore case");
        panel12.add(m_ignoreCaseCheckBox);
        m_wholeWordsCheckBox = new JCheckBox();
        m_wholeWordsCheckBox.setText("Whole words");
        panel12.add(m_wholeWordsCheckBox);
        final JPanel panel13 = new JPanel();
        panel13.setLayout(new BorderLayout(0, 0));
        panel1.add(panel13, BorderLayout.CENTER);
        final JPanel panel14 = new JPanel();
        panel14.setLayout(new BorderLayout(0, 0));
        panel13.add(panel14, BorderLayout.EAST);
        final JPanel panel15 = new JPanel();
        panel15.setLayout(new BorderLayout(0, 0));
        panel14.add(panel15, BorderLayout.CENTER);
        final JPanel panel16 = new JPanel();
        panel16.setLayout(new GridBagLayout());
        panel15.add(panel16, BorderLayout.NORTH);
        final JPanel panel17 = new JPanel();
        panel17.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel16.add(panel17, gbc);
        m_goToButton = new JButton();
        m_goToButton.setPreferredSize(new Dimension(95, 25));
        m_goToButton.setText("Go to");
        panel17.add(m_goToButton);
        final JPanel panel18 = new JPanel();
        panel18.setLayout(new BorderLayout(0, 0));
        panel13.add(panel18, BorderLayout.CENTER);
        final JScrollPane scrollPane2 = new JScrollPane();
        panel18.add(scrollPane2, BorderLayout.CENTER);
        m_searchResultsTable = new JXTable();
        m_searchResultsTable.setSortable(false);
        m_searchResultsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane2.setViewportView(m_searchResultsTable);
        label1.setLabelFor(m_searchText);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
