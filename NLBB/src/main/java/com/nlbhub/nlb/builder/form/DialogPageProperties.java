/**
 * @(#)DialogPageProperties.java
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

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.builder.model.LinksTableModelSwing;
import com.nlbhub.nlb.builder.util.ImageHelper;
import com.nlbhub.nlb.builder.util.WheelScaleListener;
import com.nlbhub.nlb.builder.util.Zoomer;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.util.MultiLangString;
import com.nlbhub.nlb.util.StringHelper;
import org.jdesktop.swingx.JXImageView;
import org.jdesktop.swingx.JXTable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

public class DialogPageProperties extends JDialog implements NLBObserver {
    private final String m_observerId;
    private Zoomer m_zoomer;
    private Page m_page;
    private NonLinearBookFacade m_nlbFacade;
    private MultiLangString m_pageCaptionTexts;
    private MultiLangString m_pageTexts;
    private MultiLangString m_traverseTexts;
    private MultiLangString m_returnTexts;
    private MultiLangString m_autowireInTexts;
    private MultiLangString m_autowireOutTexts;
    private String m_selectedLanguage;
    private String m_imageFileName;
    private String m_soundFileName;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField m_pageCaptionTextField;
    private JCheckBox m_useCheckBox;
    private JTextField m_pageVariableTextField;
    private JButton m_setTextColorButton;
    private JButton m_setPageColorButton;
    private JButton m_setBorderColorButton;
    private JTextArea m_pageText;
    private JButton m_moveUpButton;
    private JButton m_moveDownButton;
    private JButton m_editButton;
    private JButton m_deleteButton;
    private JTextField m_pageIdTextField;
    private JXTable m_linksTable;
    private LinksTableModelSwing m_linksTableModelSwing;
    private JButton m_modificationsButton;
    private JButton m_undoButton;
    private JButton m_redoButton;
    private JTextField m_moduleNameTextField;
    private JTextField m_traverseTextTextField;
    private JTextField m_returnTextTextField;
    private JTextField m_moduleConstraintTextField;
    private JTextField m_returnPageIdTextField;
    private JCheckBox m_autoTraverseCheckBox;
    private JCheckBox m_autoReturnCheckBox;
    private JComboBox m_languageComboBox;
    private JButton m_setImageButton;
    private JLabel m_imageFileNameLabel;
    private JXImageView m_imageView;
    private JCheckBox m_autoInCheckBox;
    private JCheckBox m_autoOutCheckBox;
    private JCheckBox m_autowireCheckBox;
    private JTextField m_autowireInConstraintTextField;
    private JTextField m_autowireInTextTextField;
    private JTextField m_autowireOutTextTextField;
    private JLabel m_autowireInTextLabel;
    private JLabel m_autowireOutTextLabel;
    private JPanel m_autowireInTextPanel;
    private JPanel m_autowireOutTextPanel;
    private JLabel m_returnTextLabel;
    private JLabel m_traverseTextLabel;
    private JPanel m_returnTextPanel;
    private JPanel m_traverseTextPanel;
    private JTextField m_autowireOutConstraintTextField;
    private JButton m_setSoundButton;
    private JLabel m_soundFileNameLabel;
    private JCheckBox m_backgroundCheckBox;
    private JCheckBox m_useMPLCheckBox;
    private JTextField m_timerVariableTextField;
    private JCheckBox m_soundSFXCheckBox;
    private JButton m_buttonZoomIn;
    private JButton m_buttonZoomOut;
    private JCheckBox m_globalAutowireCheckBox;
    private JCheckBox m_animatedCheckBox;
    private JCheckBox m_externalCheckBox;
    private JTextField m_defaultTagTextField;
    private JComboBox m_themeComboBox;
    private JCheckBox m_noSaveCheckBox;
    private JCheckBox m_autosFirstCheckBox;
    private JCheckBox m_needsActionCheckBox;

    public DialogPageProperties(final MainFrame mainFrame, final NonLinearBookFacade nlbFacade, final Page page) {
        m_nlbFacade = nlbFacade;
        setPageProperties(page);
        setTitle("Page properties");
        setContentPane(contentPane);
        m_zoomer = new Zoomer(m_imageView);
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

        m_editButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onEditSelectedLink();
                m_linksTableModelSwing.fireTableDataChanged();
            }
        });

        m_deleteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onDeleteSelectedLink();
                m_linksTableModelSwing.fireTableDataChanged();
            }
        });

        m_moveUpButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onMoveUpSelectedLink();
                m_linksTableModelSwing.fireTableDataChanged();
            }
        });

        m_moveDownButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onMoveDownSelectedLink();
                m_linksTableModelSwing.fireTableDataChanged();
            }
        });

        m_modificationsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                DialogModifications dialog = new DialogModifications(m_nlbFacade, page);
                dialog.showDialog();
            }
        });

        m_undoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                nlbFacade.undo(page.getId());
                setPageProperties(page);
                setPageImage(page.getImageFileName());
            }
        });

        m_redoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                nlbFacade.redo(page.getId());
                setPageProperties(page);
                setPageImage(page.getImageFileName());
            }
        });

        m_setSoundButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                DialogMediaLibrary dialog = (
                        new DialogMediaLibrary(
                                mainFrame,
                                m_nlbFacade.getMainFacade(),
                                MediaFile.Type.Sound,
                                m_soundFileName.split(Constants.MEDIA_FILE_NAME_SEP)
                        )
                );
                dialog.showDialog();
                if (!dialog.isCanceled()) {
                    String selectedFileName = dialog.getSelectedFileName();
                    if (selectedFileName != null) {
                        m_soundFileName = selectedFileName;
                        m_soundFileNameLabel.setText(m_soundFileName);
                    }
                }
            }
        });

        m_setImageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                DialogMediaLibrary dialog = (
                        new DialogMediaLibrary(
                                mainFrame,
                                m_nlbFacade.getMainFacade(),
                                MediaFile.Type.Image,
                                m_imageFileName.split(Constants.MEDIA_FILE_NAME_SEP)
                        )
                );
                dialog.showDialog();
                if (!dialog.isCanceled()) {
                    String selectedFileName = dialog.getSelectedFileName();
                    if (selectedFileName != null) {
                        m_imageFileName = selectedFileName;
                        m_imageFileNameLabel.setText(m_imageFileName);
                        setPageImage(m_imageFileName);
                    }
                }
            }
        });

        m_languageComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox cb = (JComboBox) e.getSource();
                refreshTextsForCurrentLanguage();
                String selectedLanguage = (String) cb.getSelectedItem();
                m_pageCaptionTextField.setText(m_pageCaptionTexts.get(selectedLanguage));
                m_pageText.setText(m_pageTexts.get(selectedLanguage));
                m_traverseTextTextField.setText(m_traverseTexts.get(selectedLanguage));
                m_returnTextTextField.setText(m_returnTexts.get(selectedLanguage));
                m_autowireInTextTextField.setText(m_autowireInTexts.get(selectedLanguage));
                m_autowireOutTextTextField.setText(m_autowireOutTexts.get(selectedLanguage));
                m_selectedLanguage = selectedLanguage;
            }
        });

        m_buttonZoomIn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                m_zoomer.zoomIn();
            }
        });

        m_buttonZoomOut.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                m_zoomer.zoomOut();
            }
        });

        /*
        Possible image correction on resize.
        Slow as is (some caching is needed in order to not read image file every time)
        m_imageView.addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                setPageImage(m_imageFileName);
            }

            @Override
            public void componentMoved(ComponentEvent e) {
            }

            @Override
            public void componentShown(ComponentEvent e) {
            }

            @Override
            public void componentHidden(ComponentEvent e) {
            }
        });
        */
        m_imageView.addMouseWheelListener(new WheelScaleListener(m_imageView));

        m_autowireCheckBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                toggleAutowireTexts(e.getStateChange() == ItemEvent.SELECTED);
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
        m_pageCaptionTexts.put(m_selectedLanguage, m_pageCaptionTextField.getText());
        m_pageTexts.put(m_selectedLanguage, m_pageText.getText());
        m_traverseTexts.put(m_selectedLanguage, m_traverseTextTextField.getText());
        m_autowireInTexts.put(m_selectedLanguage, m_autowireInTextTextField.getText());
        m_autowireOutTexts.put(m_selectedLanguage, m_autowireOutTextTextField.getText());
        m_returnTexts.put(m_selectedLanguage, m_returnTextTextField.getText());
    }

    public void showDialog() {
        pack();
        updateView();
        // this solves the problem where the dialog was not getting
        // focus the second time it was displayed
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setPageImage(m_page.getImageFileName());
                buttonOK.requestFocusInWindow();
            }
        });
        setVisible(true);
    }

    private void setPageProperties(Page page) {
        Variable variable;
        Variable timerVariable;
        Variable deftagVariable;
        Variable moduleConstraint;
        Variable autowireInConstraint;
        Variable autowireOutConstraint;
        m_page = page;

        m_imageFileName = page.getImageFileName();
        m_backgroundCheckBox.setSelected(page.isImageBackground());
        m_animatedCheckBox.setSelected(page.isImageAnimated());
        m_imageFileNameLabel.setText(m_imageFileName);

        m_soundFileName = page.getSoundFileName();
        m_soundSFXCheckBox.setSelected(page.isSoundSFX());
        m_soundFileNameLabel.setText(m_soundFileName);

        DefaultComboBoxModel<String> languageComboboxModel = new DefaultComboBoxModel<>();
        languageComboboxModel.addElement(Constants.RU);
        languageComboboxModel.addElement(Constants.EN);
        m_languageComboBox.setModel(languageComboboxModel);
        m_languageComboBox.setSelectedIndex(
                page.getCurrentNLB().getLanguage().equals(Constants.RU) ? 0 : 1
        );

        m_pageCaptionTexts = page.getCaptions();
        m_pageTexts = page.getTexts();
        m_traverseTexts = page.getTraverseTexts();
        m_returnTexts = page.getReturnTexts();
        m_selectedLanguage = (String) languageComboboxModel.getSelectedItem();

        variable = m_nlbFacade.getNlb().getVariableById(page.getVarId());
        timerVariable = m_nlbFacade.getNlb().getVariableById(page.getTimerVarId());
        deftagVariable = m_nlbFacade.getNlb().getVariableById(page.getDefaultTagId());
        moduleConstraint = m_nlbFacade.getNlb().getVariableById(page.getModuleConstrId());
        autowireInConstraint = m_nlbFacade.getNlb().getVariableById(page.getAutowireInConstrId());
        autowireOutConstraint = m_nlbFacade.getNlb().getVariableById(page.getAutowireOutConstrId());
        DefaultComboBoxModel<Theme> themeComboboxModel = new DefaultComboBoxModel<>();
        Theme[] possibleValues = page.getTheme().getDeclaringClass().getEnumConstants();
        for (Theme theme : possibleValues) {
            themeComboboxModel.addElement(theme);
        }
        m_themeComboBox.setModel(themeComboboxModel);
        m_themeComboBox.setSelectedItem(page.getTheme());
        m_pageIdTextField.setText(page.getId());
        m_pageVariableTextField.setText(variable != null ? variable.getName() : "");
        m_timerVariableTextField.setText(timerVariable != null ? timerVariable.getName() : "");
        m_defaultTagTextField.setText(deftagVariable != null ? deftagVariable.getValue() : "");
        m_moduleConstraintTextField.setText(
                moduleConstraint != null ? moduleConstraint.getValue() : ""
        );
        m_pageCaptionTextField.setText(m_page.getCaption());
        m_useCheckBox.setSelected(m_page.isUseCaption());
        m_useMPLCheckBox.setSelected(m_page.isUseMPL());
        m_globalAutowireCheckBox.setSelected(m_page.isGlobalAutowire());
        m_noSaveCheckBox.setSelected(m_page.isNoSave());
        m_autosFirstCheckBox.setSelected(m_page.isAutosFirst());
        m_pageText.setText(page.getText());

        m_moduleNameTextField.setText(page.getModuleName());
        m_externalCheckBox.setSelected(page.isModuleExternal());
        m_traverseTextTextField.setText(page.getTraverseText());
        m_autoTraverseCheckBox.setSelected(page.isAutoTraverse());
        m_autoReturnCheckBox.setSelected(page.isAutoReturn());
        m_returnTextTextField.setText(page.getReturnText());
        m_returnPageIdTextField.setText(page.getReturnPageId());
        m_autowireInTextTextField.setText(page.getAutowireInText());
        m_autowireOutTextTextField.setText(page.getAutowireOutText());

        m_linksTableModelSwing = new LinksTableModelSwing(m_page.getLinks());
        m_linksTable.setModel(m_linksTableModelSwing);

        m_autowireCheckBox.setSelected(page.isAutowire());
        m_autowireInTexts = page.getAutowireInTexts();
        m_autowireOutTexts = page.getAutowireOutTexts();
        m_autoInCheckBox.setSelected(page.isAutoIn());
        m_needsActionCheckBox.setSelected(page.isNeedsAction());
        m_autoOutCheckBox.setSelected(page.isAutoOut());
        m_autowireInConstraintTextField.setText(autowireInConstraint != null ? autowireInConstraint.getValue() : "");
        m_autowireOutConstraintTextField.setText(autowireOutConstraint != null ? autowireOutConstraint.getValue() : "");

        toggleModuleTraversalTexts(!page.getModule().isEmpty());
        toggleAutowireTexts(page.isAutowire());
    }

    private void toggleModuleTraversalTexts(final boolean visible) {
        m_traverseTextLabel.setVisible(visible);
        m_traverseTextPanel.setVisible(visible);
    }

    private void toggleAutowireTexts(final boolean visible) {
        m_autowireInTextLabel.setVisible(visible);
        m_autowireInTextPanel.setVisible(visible);
        m_autowireOutTextLabel.setVisible(visible);
        m_autowireOutTextPanel.setVisible(visible);
    }

    private void setPageImage(final String imageFileName) {
        try {
            if (StringHelper.isEmpty(m_imageFileName)) {
                m_imageView.setVisible(false);
            } else {
                File file = new File(m_nlbFacade.getMainFacade().getNlb().getImagesDir(), imageFileName);
                if (file.exists()) {
                    m_imageView.setImage(file);
                    m_imageView.setScale(ImageHelper.getScaleToFit(m_imageView, file));
                    m_imageView.setVisible(true);
                } else {
                    m_imageView.setVisible(false);
                }
            }
        } catch (IOException ignore) {
            // do nothing
        }
    }

    private void onOK() {
        refreshTextsForCurrentLanguage();
        m_nlbFacade.updatePage(
                m_page,
                m_imageFileName,
                m_backgroundCheckBox.isSelected(),
                m_animatedCheckBox.isSelected(),
                m_soundFileName,
                m_soundSFXCheckBox.isSelected(),
                m_pageVariableTextField.getText(),
                m_timerVariableTextField.getText(),
                m_defaultTagTextField.getText(),
                m_pageTexts,
                m_pageCaptionTexts,
                (Theme) m_themeComboBox.getSelectedItem(),
                m_useCheckBox.isSelected(),
                m_useMPLCheckBox.isSelected(),
                m_moduleNameTextField.getText(),
                m_externalCheckBox.isSelected(),
                m_traverseTexts,
                m_autoTraverseCheckBox.isSelected(),
                m_autoReturnCheckBox.isSelected(),
                m_returnTexts,
                m_returnPageIdTextField.getText(),
                m_moduleConstraintTextField.getText(),
                m_autowireCheckBox.isSelected(),
                m_autowireInTexts,
                m_autowireOutTexts,
                m_autoInCheckBox.isSelected(),
                m_needsActionCheckBox.isSelected(),
                m_autoOutCheckBox.isSelected(),
                m_autowireInConstraintTextField.getText(),
                m_autowireOutConstraintTextField.getText(),
                m_globalAutowireCheckBox.isSelected(),
                m_noSaveCheckBox.isSelected(),
                m_autosFirstCheckBox.isSelected(),
                ((LinksTableModelSwing) m_linksTable.getModel()).getTableModel()
        );
        m_nlbFacade.removeObserver(m_observerId);
        dispose();
    }

    private void onCancel() {
        m_nlbFacade.redoAll(m_page.getId());
        m_nlbFacade.removeObserver(m_observerId);
        dispose();
    }

    private void onEditSelectedLink() {
        LinksTableModelSwing model = (LinksTableModelSwing) m_linksTable.getModel();
        DialogLinkProperties dialog = (
                new DialogLinkProperties(
                        m_nlbFacade,
                        model.getLinkAt(m_linksTable.getSelectedRow())
                )
        );
        dialog.showDialog();
    }

    private void onDeleteSelectedLink() {
        LinksTableModelSwing model = (LinksTableModelSwing) m_linksTable.getModel();
        // Can delete link right now via the following call:
        // m_nlbFacade.deleteLink(model.getLinkAt(m_linksTable.getSelectedRow()));
        // But it is better to batch delete all links at once later, when OK button will be clicked.
        // So for now just delete it from the model, actual deletion from the book will be performed
        // later.
        model.deleteLinkAt(m_linksTable.getSelectedRow());
    }

    private void onMoveUpSelectedLink() {
        LinksTableModelSwing model = (LinksTableModelSwing) m_linksTable.getModel();
        final int selectedRow = m_linksTable.getSelectedRow();
        if (selectedRow > 0) {
            model.moveUp(selectedRow);
            m_linksTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1);
        }
    }

    private void onMoveDownSelectedLink() {
        LinksTableModelSwing model = (LinksTableModelSwing) m_linksTable.getModel();
        final int selectedRow = m_linksTable.getSelectedRow();
        if (selectedRow < m_linksTable.getRowCount() - 1) {
            model.moveDown(selectedRow);
            m_linksTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1);
        }
    }

    @Override
    public void updateView() {
        m_undoButton.setEnabled(m_nlbFacade.canUndo(m_page.getId()));
        m_redoButton.setEnabled(m_nlbFacade.canRedo(m_page.getId()));
    }

}
