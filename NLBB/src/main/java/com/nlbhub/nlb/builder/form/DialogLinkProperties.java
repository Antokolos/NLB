/**
 * @(#)DialogLinkProperties.java
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
import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.NLBObserver;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.util.MultiLangString;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.util.Locale;

public class DialogLinkProperties extends JDialog implements NLBObserver {
    private final String m_observerId;
    private Link m_link;
    private NonLinearBookFacade m_nlbFacade;
    private Variable m_variable;
    private Variable m_constraint;
    private MultiLangString m_linkTexts;
    private MultiLangString m_altTexts;
    private String m_selectedLanguage;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField m_linkIdTextField;
    private JTextField m_linkTextTextField;
    private JButton m_linkColorButton;
    private JTextField m_linkConstraintsTextField;
    private JTextField m_linkVariableTextField;
    private JButton m_modificationsButton;
    private JButton m_undoButton;
    private JButton m_redoButton;
    private JCheckBox m_autoCheckBox;
    private JComboBox m_languageComboBox;
    private JTabbedPane m_tabbedPane1;
    private JCheckBox m_onceCheckBox;
    private JTextField m_altTextTextField;

    public DialogLinkProperties(
            final NonLinearBookFacade nlbFacade,
            final Link link
    ) {
        m_nlbFacade = nlbFacade;
        setLinkProperties(link);
        setContentPane(contentPane);
        setModal(true);
        setTitle("Link properties");
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

        m_modificationsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                DialogModifications dialog = new DialogModifications(m_nlbFacade, link);
                dialog.showDialog();
            }
        });
        m_undoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                nlbFacade.undo(link.getId());
                setLinkProperties(link);
            }
        });

        m_redoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                nlbFacade.redo(link.getId());
                setLinkProperties(link);
            }
        });

        m_languageComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox cb = (JComboBox) e.getSource();
                refreshTextsForCurrentLanguage();
                String selectedLanguage = (String) cb.getSelectedItem();
                m_linkTextTextField.setText(m_linkTexts.get(selectedLanguage));
                m_altTextTextField.setText(m_altTexts.get(selectedLanguage));
                m_selectedLanguage = selectedLanguage;
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
        m_observerId = nlbFacade.addObserver(this);
    }

    private void refreshTextsForCurrentLanguage() {
        m_linkTexts.put(m_selectedLanguage, m_linkTextTextField.getText());
        m_altTexts.put(m_selectedLanguage, m_altTextTextField.getText());
    }

    public void showDialog() {
        pack();
        updateView();
        // this solves the problem where the dialog was not getting
        // focus the second time it was displayed
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                buttonOK.requestFocusInWindow();
            }
        });
        setVisible(true);
    }

    private void setLinkProperties(final Link link) {
        m_link = link;
        m_variable = m_nlbFacade.getNlb().getVariableById(link.getVarId());
        m_constraint = m_nlbFacade.getNlb().getVariableById(link.getConstrId());
        m_linkIdTextField.setText(link.getId());
        m_linkVariableTextField.setText(m_variable != null ? m_variable.getName() : "");
        m_linkTextTextField.setText(link.getText());
        m_altTextTextField.setText(link.getAltText());
        m_autoCheckBox.setSelected(link.isAuto());
        m_onceCheckBox.setSelected(link.isOnce());
        m_linkConstraintsTextField.setText(m_constraint != null ? m_constraint.getValue() : "");

        DefaultComboBoxModel<String> languageComboboxModel = new DefaultComboBoxModel<>();
        languageComboboxModel.addElement(Constants.RU);
        languageComboboxModel.addElement(Constants.EN);
        m_languageComboBox.setModel(languageComboboxModel);
        m_languageComboBox.setSelectedIndex(
                link.getCurrentNLB().getLanguage().equals(Constants.RU) ? 0 : 1
        );

        m_linkTexts = link.getTexts();
        m_altTexts = link.getAltTexts();
        m_selectedLanguage = (String) languageComboboxModel.getSelectedItem();
    }

    private void onOK() {
        refreshTextsForCurrentLanguage();
        m_nlbFacade.updateLink(
                m_link,
                m_linkVariableTextField.getText(),
                m_linkConstraintsTextField.getText(),
                m_linkTexts,
                m_altTexts,
                m_autoCheckBox.isSelected(),
                m_onceCheckBox.isSelected()
        );
        m_nlbFacade.removeObserver(m_observerId);
        dispose();
    }

    private void onCancel() {
        m_nlbFacade.redoAll(m_link.getId());
        m_nlbFacade.removeObserver(m_observerId);
        dispose();
    }

    @Override
    public void updateView() {
        m_undoButton.setEnabled(m_nlbFacade.canUndo(m_link.getId()));
        m_redoButton.setEnabled(m_nlbFacade.canRedo(m_link.getId()));
    }

}
