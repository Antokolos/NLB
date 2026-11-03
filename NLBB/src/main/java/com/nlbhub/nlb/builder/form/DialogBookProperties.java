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

import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.builder.util.LanguageHelper;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.vcs.Author;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DialogBookProperties extends JDialog {
    private NonLinearBookFacade m_nlbFacade;
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
    private JCheckBox m_forceDscCheckBox;
    private JCheckBox m_choiceLinksCheckBox;
    private JTextField m_titleTextField;
    private JTextField m_perfectGameAchievementTextField;
    private JComboBox m_themeComboBox;
    /**
     * Имя и e-mail для коммитов из редактора — не свойства книги, а настройки git (user.name,
     * user.email) в .git/config её клона: у каждого соавтора свои. Показаны действующие (из
     * репозитория книги, иначе из общих настроек git)
     */
    private JTextField m_commitAuthorTextField;
    private JTextField m_commitEmailTextField;
    private Author m_committer;

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
        m_forceDscCheckBox.setSelected(nonLinearBook.isForceDsc());
        m_choiceLinksCheckBox.setSelected(nonLinearBook.isChoiceLinks());
        LanguageHelper.initLanguageComboBox(m_languageComboBox, nonLinearBook);
        DefaultComboBoxModel<Theme> themeComboboxModel = new DefaultComboBoxModel<>();
        Theme[] possibleValues = nonLinearBook.getTheme().getDeclaringClass().getEnumConstants();
        for (Theme theme : possibleValues) {
            themeComboboxModel.addElement(theme);
        }
        m_themeComboBox.setModel(themeComboboxModel);
        m_themeComboBox.setSelectedItem(nonLinearBook.getTheme());
        m_committer = m_nlbFacade.getCommitter();
        m_commitAuthorTextField.setText(m_committer != null ? m_committer.getName() : "");
        m_commitEmailTextField.setText(m_committer != null ? m_committer.getEmail() : "");
        final boolean canSet = m_nlbFacade.canSetCommitter();
        m_commitAuthorTextField.setEnabled(canSet);
        m_commitEmailTextField.setEnabled(canSet);
        final String tip = canSet
                ? "Used for commits from the editor. Stored in the book's .git/config (only in your copy of the book, not in the book files)"
                : "Available when the book is saved in a Git repository";
        m_commitAuthorTextField.setToolTipText(tip);
        m_commitEmailTextField.setToolTipText(tip);
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
        final String language = LanguageHelper.validateLanguage(
                m_languageComboBox, m_languageComboBox.getEditor().getItem(), this
        );
        if (language == null) {
            return;
        }
        m_nlbFacade.updateBookProperties(
                m_licenseTextArea.getText(),
                (Theme) m_themeComboBox.getSelectedItem(),
                language,
                m_titleTextField.getText(),
                m_authorTextField.getText(),
                m_versionTextField.getText(),
                m_perfectGameAchievementTextField.getText(),
                m_fullAutowireCheckBox.isSelected(),
                m_suppressMediaCheckBox.isSelected(),
                m_suppressSoundCheckBox.isSelected(),
                m_forceDscCheckBox.isSelected(),
                m_choiceLinksCheckBox.isSelected(),
                m_propagateToSubmodulesCheckBox.isSelected()
        );
        // имя и e-mail для коммитов — в .git/config книги, только если их поменяли (иначе общие
        // настройки git, показанные в полях, переписались бы в настройки книги)
        final String name = m_commitAuthorTextField.getText().trim();
        final String email = m_commitEmailTextField.getText().trim();
        final String oldName = m_committer != null ? m_committer.getName() : "";
        final String oldEmail = m_committer != null ? m_committer.getEmail() : "";
        if (m_nlbFacade.canSetCommitter() && (!name.equals(oldName) || !email.equals(oldEmail))) {
            try {
                m_nlbFacade.setCommitter(name.isEmpty() && email.isEmpty() ? null : new Author(name, email));
            } catch (NLBVCSException e) {
                JOptionPane.showMessageDialog(this, "Cannot save commit author: " + e.getMessage());
                return;
            }
        }
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

}
