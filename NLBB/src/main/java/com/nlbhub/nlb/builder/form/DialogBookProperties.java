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
import com.nlbhub.nlb.api.Theme;
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
    private JComboBox m_themeComboBox;

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
        DefaultComboBoxModel<Theme> themeComboboxModel = new DefaultComboBoxModel<>();
        Theme[] possibleValues = nonLinearBook.getTheme().getDeclaringClass().getEnumConstants();
        for (Theme theme : possibleValues) {
            themeComboboxModel.addElement(theme);
        }
        m_themeComboBox.setModel(themeComboboxModel);
        m_themeComboBox.setSelectedItem(nonLinearBook.getTheme());
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
                (Theme) m_themeComboBox.getSelectedItem(),
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

}
