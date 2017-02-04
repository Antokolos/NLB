/**
 * @(#)DialogBookProperties.java
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

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.domain.NonLinearBookFacade;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DialogBookProperties extends JDialog {
    private NonLinearBookFacade m_nlbFacade;
    private DefaultComboBoxModel<String> m_languageComboboxModel;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextArea m_licenseTextArea;
    private JComboBox m_languageComboBox;
    private JTextField m_authorTextField;
    private JTextField m_versionTextField;
    private JCheckBox m_propagateToSubmodulesCheckBox;
    private JCheckBox m_fullAutowireCheckBox;
    private JCheckBox m_suppressMediaCheckBox;
    private JCheckBox m_suppressSoundCheckBox;
    private JTextField m_titleTextField;
    private JTextField m_perfectGameAchievementTextField;

    public DialogBookProperties(final NonLinearBookFacade nlbFacade) {
        m_nlbFacade = nlbFacade;
        setBookProperties();
        setContentPane(contentPane);
        setTitle("Book properties");
        setModal(true);
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

    private void setBookProperties() {
        NonLinearBook nonLinearBook = m_nlbFacade.getNlb();
        m_licenseTextArea.setText(nonLinearBook.getLicense());
        m_titleTextField.setText(nonLinearBook.getTitle());
        m_authorTextField.setText(nonLinearBook.getAuthor());
        m_versionTextField.setText(nonLinearBook.getVersion());
        m_perfectGameAchievementTextField.setText(nonLinearBook.getPerfectGameAchievementName());
        m_fullAutowireCheckBox.setSelected(nonLinearBook.isFullAutowire());
        m_suppressMediaCheckBox.setSelected(nonLinearBook.isSuppressMedia());
        m_suppressSoundCheckBox.setSelected(nonLinearBook.isSuppressSound());
        m_languageComboboxModel = new DefaultComboBoxModel<>();
        m_languageComboboxModel.addElement(Constants.RU);
        m_languageComboboxModel.addElement(Constants.EN);
        m_languageComboBox.setModel(m_languageComboboxModel);
        m_languageComboBox.setSelectedIndex(
                nonLinearBook.getLanguage().equals(Constants.RU) ? 0 : 1
        );
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

    private void onOK() {
        m_nlbFacade.updateBookProperties(
                m_licenseTextArea.getText(),
                m_languageComboboxModel.getElementAt(m_languageComboBox.getSelectedIndex()),
                m_titleTextField.getText(),
                m_authorTextField.getText(),
                m_versionTextField.getText(),
                m_perfectGameAchievementTextField.getText(),
                m_fullAutowireCheckBox.isSelected(),
                m_suppressMediaCheckBox.isSelected(),
                m_suppressSoundCheckBox.isSelected(),
                m_propagateToSubmodulesCheckBox.isSelected()
        );
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
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
        buttonOK.setMaximumSize(new Dimension(85, 25));
        buttonOK.setMinimumSize(new Dimension(85, 25));
        buttonOK.setPreferredSize(new Dimension(85, 25));
        buttonOK.setText("OK");
        panel6.add(buttonOK);
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel5.add(panel7, gbc);
        buttonCancel = new JButton();
        buttonCancel.setMaximumSize(new Dimension(85, 25));
        buttonCancel.setMinimumSize(new Dimension(85, 25));
        buttonCancel.setPreferredSize(new Dimension(85, 25));
        buttonCancel.setText("Cancel");
        panel7.add(buttonCancel);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new BorderLayout(0, 0));
        panel3.add(panel8, BorderLayout.CENTER);
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new BorderLayout(0, 0));
        panel8.add(panel9, BorderLayout.CENTER);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridBagLayout());
        panel9.add(panel10, BorderLayout.CENTER);
        final JLabel label1 = new JLabel();
        label1.setText("License text");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(label1, gbc);
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel11, gbc);
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setHorizontalScrollBarPolicy(31);
        scrollPane1.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel11.add(scrollPane1, gbc);
        m_licenseTextArea = new JTextArea();
        m_licenseTextArea.setColumns(50);
        m_licenseTextArea.setRows(10);
        scrollPane1.setViewportView(m_licenseTextArea);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 10;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel10.add(spacer1, gbc);
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel12, gbc);
        final JScrollPane scrollPane2 = new JScrollPane();
        scrollPane2.setHorizontalScrollBarPolicy(31);
        scrollPane2.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel12.add(scrollPane2, gbc);
        m_languageComboBox = new JComboBox();
        scrollPane2.setViewportView(m_languageComboBox);
        final JLabel label2 = new JLabel();
        label2.setText("Active language");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(label2, gbc);
        final JPanel panel13 = new JPanel();
        panel13.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel13, gbc);
        final JScrollPane scrollPane3 = new JScrollPane();
        scrollPane3.setHorizontalScrollBarPolicy(31);
        scrollPane3.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel13.add(scrollPane3, gbc);
        m_authorTextField = new JTextField();
        scrollPane3.setViewportView(m_authorTextField);
        final JLabel label3 = new JLabel();
        label3.setText("Author");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(label3, gbc);
        final JPanel panel14 = new JPanel();
        panel14.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel14, gbc);
        final JScrollPane scrollPane4 = new JScrollPane();
        scrollPane4.setHorizontalScrollBarPolicy(31);
        scrollPane4.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel14.add(scrollPane4, gbc);
        m_versionTextField = new JTextField();
        scrollPane4.setViewportView(m_versionTextField);
        final JLabel label4 = new JLabel();
        label4.setText("Version");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(label4, gbc);
        final JPanel panel15 = new JPanel();
        panel15.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 7;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel15, gbc);
        final JScrollPane scrollPane5 = new JScrollPane();
        scrollPane5.setHorizontalScrollBarPolicy(31);
        scrollPane5.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel15.add(scrollPane5, gbc);
        m_propagateToSubmodulesCheckBox = new JCheckBox();
        m_propagateToSubmodulesCheckBox.setSelected(true);
        m_propagateToSubmodulesCheckBox.setText("Propagate to submodules");
        scrollPane5.setViewportView(m_propagateToSubmodulesCheckBox);
        final JPanel panel16 = new JPanel();
        panel16.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel16, gbc);
        final JScrollPane scrollPane6 = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel16.add(scrollPane6, gbc);
        m_fullAutowireCheckBox = new JCheckBox();
        m_fullAutowireCheckBox.setText("Full autowire");
        scrollPane6.setViewportView(m_fullAutowireCheckBox);
        final JPanel panel17 = new JPanel();
        panel17.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 8;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel17, gbc);
        final JScrollPane scrollPane7 = new JScrollPane();
        scrollPane7.setHorizontalScrollBarPolicy(31);
        scrollPane7.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel17.add(scrollPane7, gbc);
        m_suppressMediaCheckBox = new JCheckBox();
        m_suppressMediaCheckBox.setSelected(false);
        m_suppressMediaCheckBox.setText("Suppress media");
        scrollPane7.setViewportView(m_suppressMediaCheckBox);
        final JPanel panel18 = new JPanel();
        panel18.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 9;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel18, gbc);
        final JScrollPane scrollPane8 = new JScrollPane();
        scrollPane8.setHorizontalScrollBarPolicy(31);
        scrollPane8.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel18.add(scrollPane8, gbc);
        m_suppressSoundCheckBox = new JCheckBox();
        m_suppressSoundCheckBox.setSelected(false);
        m_suppressSoundCheckBox.setText("Suppress sound");
        scrollPane8.setViewportView(m_suppressSoundCheckBox);
        final JPanel panel19 = new JPanel();
        panel19.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel19, gbc);
        final JScrollPane scrollPane9 = new JScrollPane();
        scrollPane9.setHorizontalScrollBarPolicy(31);
        scrollPane9.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel19.add(scrollPane9, gbc);
        m_titleTextField = new JTextField();
        scrollPane9.setViewportView(m_titleTextField);
        final JLabel label5 = new JLabel();
        label5.setText("Title");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(label5, gbc);
        final JPanel panel20 = new JPanel();
        panel20.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel20, gbc);
        final JScrollPane scrollPane10 = new JScrollPane();
        scrollPane10.setHorizontalScrollBarPolicy(31);
        scrollPane10.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel20.add(scrollPane10, gbc);
        m_perfectGameAchievementTextField = new JTextField();
        scrollPane10.setViewportView(m_perfectGameAchievementTextField);
        final JLabel label6 = new JLabel();
        label6.setText("Perfect game achievement");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(label6, gbc);
        final JPanel panel21 = new JPanel();
        panel21.setLayout(new BorderLayout(0, 0));
        panel1.add(panel21, BorderLayout.CENTER);
        label1.setLabelFor(m_licenseTextArea);
        label2.setLabelFor(m_languageComboBox);
        label3.setLabelFor(m_authorTextField);
        label4.setLabelFor(m_versionTextField);
        label5.setLabelFor(m_titleTextField);
        label6.setLabelFor(m_perfectGameAchievementTextField);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
