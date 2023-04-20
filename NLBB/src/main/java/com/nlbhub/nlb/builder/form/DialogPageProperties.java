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
        panel1.add(panel2, BorderLayout.SOUTH);
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
        buttonOK.setMaximumSize(new Dimension(75, 25));
        buttonOK.setMinimumSize(new Dimension(75, 25));
        buttonOK.setPreferredSize(new Dimension(75, 25));
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
        buttonCancel.setMaximumSize(new Dimension(77, 25));
        buttonCancel.setMinimumSize(new Dimension(77, 25));
        buttonCancel.setPreferredSize(new Dimension(77, 25));
        buttonCancel.setText("Cancel");
        panel7.add(buttonCancel);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new BorderLayout(0, 0));
        panel3.add(panel8, BorderLayout.CENTER);
        final JTabbedPane tabbedPane1 = new JTabbedPane();
        panel8.add(tabbedPane1, BorderLayout.CENTER);
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new BorderLayout(0, 0));
        tabbedPane1.addTab("Additional Text", panel9);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridBagLayout());
        panel9.add(panel10, BorderLayout.CENTER);
        m_traverseTextLabel = new JLabel();
        m_traverseTextLabel.setText("Traverse text");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(m_traverseTextLabel, gbc);
        final JLabel label1 = new JLabel();
        label1.setText("Page caption");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(label1, gbc);
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel10.add(panel11, gbc);
        m_useCheckBox = new JCheckBox();
        m_useCheckBox.setHorizontalAlignment(2);
        m_useCheckBox.setText("Use");
        panel11.add(m_useCheckBox, BorderLayout.CENTER);
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new GridBagLayout());
        panel12.setMinimumSize(new Dimension(143, 33));
        panel12.setPreferredSize(new Dimension(605, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel12, gbc);
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setHorizontalScrollBarPolicy(31);
        scrollPane1.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel12.add(scrollPane1, gbc);
        m_pageCaptionTextField = new JTextField();
        m_pageCaptionTextField.setColumns(32);
        m_pageCaptionTextField.setFocusAccelerator('C');
        m_pageCaptionTextField.setHorizontalAlignment(10);
        scrollPane1.setViewportView(m_pageCaptionTextField);
        m_traverseTextPanel = new JPanel();
        m_traverseTextPanel.setLayout(new GridBagLayout());
        m_traverseTextPanel.setMinimumSize(new Dimension(56, 33));
        m_traverseTextPanel.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(m_traverseTextPanel, gbc);
        final JScrollPane scrollPane2 = new JScrollPane();
        scrollPane2.setHorizontalScrollBarPolicy(31);
        scrollPane2.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        m_traverseTextPanel.add(scrollPane2, gbc);
        m_traverseTextTextField = new JTextField();
        m_traverseTextTextField.setColumns(32);
        scrollPane2.setViewportView(m_traverseTextTextField);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel10.add(spacer1, gbc);
        m_autowireInTextPanel = new JPanel();
        m_autowireInTextPanel.setLayout(new GridBagLayout());
        m_autowireInTextPanel.setMinimumSize(new Dimension(56, 33));
        m_autowireInTextPanel.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(m_autowireInTextPanel, gbc);
        final JScrollPane scrollPane3 = new JScrollPane();
        scrollPane3.setHorizontalScrollBarPolicy(31);
        scrollPane3.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        m_autowireInTextPanel.add(scrollPane3, gbc);
        m_autowireInTextTextField = new JTextField();
        m_autowireInTextTextField.setColumns(32);
        scrollPane3.setViewportView(m_autowireInTextTextField);
        m_autowireOutTextPanel = new JPanel();
        m_autowireOutTextPanel.setLayout(new GridBagLayout());
        m_autowireOutTextPanel.setMinimumSize(new Dimension(56, 33));
        m_autowireOutTextPanel.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(m_autowireOutTextPanel, gbc);
        final JScrollPane scrollPane4 = new JScrollPane();
        scrollPane4.setHorizontalScrollBarPolicy(31);
        scrollPane4.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        m_autowireOutTextPanel.add(scrollPane4, gbc);
        m_autowireOutTextTextField = new JTextField();
        m_autowireOutTextTextField.setColumns(32);
        scrollPane4.setViewportView(m_autowireOutTextTextField);
        m_autowireInTextLabel = new JLabel();
        m_autowireInTextLabel.setText("Autowire in text");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(m_autowireInTextLabel, gbc);
        m_autowireOutTextLabel = new JLabel();
        m_autowireOutTextLabel.setText("Autowire out text");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(m_autowireOutTextLabel, gbc);
        m_returnTextPanel = new JPanel();
        m_returnTextPanel.setLayout(new GridBagLayout());
        m_returnTextPanel.setMinimumSize(new Dimension(56, 33));
        m_returnTextPanel.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(m_returnTextPanel, gbc);
        final JScrollPane scrollPane5 = new JScrollPane();
        scrollPane5.setHorizontalScrollBarPolicy(31);
        scrollPane5.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        m_returnTextPanel.add(scrollPane5, gbc);
        m_returnTextTextField = new JTextField();
        m_returnTextTextField.setColumns(32);
        scrollPane5.setViewportView(m_returnTextTextField);
        m_returnTextLabel = new JLabel();
        m_returnTextLabel.setText("Return text");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(m_returnTextLabel, gbc);
        final JPanel panel13 = new JPanel();
        panel13.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel10.add(panel13, gbc);
        m_useMPLCheckBox = new JCheckBox();
        m_useMPLCheckBox.setText("Use MPL");
        panel13.add(m_useMPLCheckBox, BorderLayout.CENTER);
        final JPanel panel14 = new JPanel();
        panel14.setLayout(new BorderLayout(0, 0));
        tabbedPane1.addTab("Properties", panel14);
        final JPanel panel15 = new JPanel();
        panel15.setLayout(new BorderLayout(0, 0));
        panel14.add(panel15, BorderLayout.CENTER);
        final JPanel panel16 = new JPanel();
        panel16.setLayout(new GridBagLayout());
        panel15.add(panel16, BorderLayout.CENTER);
        final JPanel panel17 = new JPanel();
        panel17.setLayout(new GridBagLayout());
        panel17.setMinimumSize(new Dimension(143, 33));
        panel17.setPreferredSize(new Dimension(605, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel16.add(panel17, gbc);
        final JScrollPane scrollPane6 = new JScrollPane();
        scrollPane6.setEnabled(true);
        scrollPane6.setHorizontalScrollBarPolicy(31);
        scrollPane6.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel17.add(scrollPane6, gbc);
        m_pageIdTextField = new JTextField();
        m_pageIdTextField.setEditable(false);
        m_pageIdTextField.setFocusAccelerator('I');
        scrollPane6.setViewportView(m_pageIdTextField);
        final JLabel label2 = new JLabel();
        label2.setText("Page Id");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel16.add(label2, gbc);
        final JPanel spacer2 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel16.add(spacer2, gbc);
        final JPanel panel18 = new JPanel();
        panel18.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel16.add(panel18, gbc);
        final JPanel panel19 = new JPanel();
        panel19.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel18.add(panel19, BorderLayout.WEST);
        m_setTextColorButton = new JButton();
        m_setTextColorButton.setEnabled(false);
        m_setTextColorButton.setText("Set text color");
        panel19.add(m_setTextColorButton);
        m_setPageColorButton = new JButton();
        m_setPageColorButton.setEnabled(false);
        m_setPageColorButton.setText("Set page color");
        panel19.add(m_setPageColorButton);
        m_setBorderColorButton = new JButton();
        m_setBorderColorButton.setEnabled(false);
        m_setBorderColorButton.setText("Set border color");
        panel19.add(m_setBorderColorButton);
        m_noSaveCheckBox = new JCheckBox();
        m_noSaveCheckBox.setText("No Save");
        panel19.add(m_noSaveCheckBox);
        m_autosFirstCheckBox = new JCheckBox();
        m_autosFirstCheckBox.setText("Autos First");
        panel19.add(m_autosFirstCheckBox);
        final JPanel panel20 = new JPanel();
        panel20.setLayout(new GridBagLayout());
        panel20.setMinimumSize(new Dimension(56, 33));
        panel20.setPreferredSize(new Dimension(518, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel16.add(panel20, gbc);
        final JScrollPane scrollPane7 = new JScrollPane();
        scrollPane7.setHorizontalScrollBarPolicy(31);
        scrollPane7.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel20.add(scrollPane7, gbc);
        m_pageVariableTextField = new JTextField();
        m_pageVariableTextField.setColumns(42);
        m_pageVariableTextField.setFocusAccelerator('V');
        scrollPane7.setViewportView(m_pageVariableTextField);
        final JLabel label3 = new JLabel();
        label3.setHorizontalAlignment(10);
        label3.setHorizontalTextPosition(11);
        label3.setText("Page variable");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel16.add(label3, gbc);
        final JLabel label4 = new JLabel();
        label4.setHorizontalAlignment(10);
        label4.setHorizontalTextPosition(11);
        label4.setText("Timer variable");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel16.add(label4, gbc);
        final JPanel panel21 = new JPanel();
        panel21.setLayout(new GridBagLayout());
        panel21.setMinimumSize(new Dimension(56, 33));
        panel21.setPreferredSize(new Dimension(518, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel16.add(panel21, gbc);
        final JScrollPane scrollPane8 = new JScrollPane();
        scrollPane8.setHorizontalScrollBarPolicy(31);
        scrollPane8.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel21.add(scrollPane8, gbc);
        m_timerVariableTextField = new JTextField();
        m_timerVariableTextField.setColumns(42);
        m_timerVariableTextField.setFocusAccelerator('V');
        scrollPane8.setViewportView(m_timerVariableTextField);
        final JPanel panel22 = new JPanel();
        panel22.setLayout(new GridBagLayout());
        panel22.setMinimumSize(new Dimension(468, 33));
        panel22.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel16.add(panel22, gbc);
        final JScrollPane scrollPane9 = new JScrollPane();
        scrollPane9.setHorizontalScrollBarPolicy(31);
        scrollPane9.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel22.add(scrollPane9, gbc);
        m_defaultTagTextField = new JTextField();
        m_defaultTagTextField.setColumns(40);
        scrollPane9.setViewportView(m_defaultTagTextField);
        final JLabel label5 = new JLabel();
        label5.setText("Default tag");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        panel16.add(label5, gbc);
        final JPanel panel23 = new JPanel();
        panel23.setLayout(new GridBagLayout());
        panel23.setMinimumSize(new Dimension(468, 33));
        panel23.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel16.add(panel23, gbc);
        final JScrollPane scrollPane10 = new JScrollPane();
        scrollPane10.setHorizontalScrollBarPolicy(31);
        scrollPane10.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel23.add(scrollPane10, gbc);
        m_themeComboBox = new JComboBox();
        scrollPane10.setViewportView(m_themeComboBox);
        final JLabel label6 = new JLabel();
        label6.setText("Theme");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.EAST;
        panel16.add(label6, gbc);
        final JPanel panel24 = new JPanel();
        panel24.setLayout(new BorderLayout(0, 0));
        tabbedPane1.addTab("Links", panel24);
        final JPanel panel25 = new JPanel();
        panel25.setLayout(new BorderLayout(0, 0));
        panel24.add(panel25, BorderLayout.CENTER);
        final JPanel panel26 = new JPanel();
        panel26.setLayout(new GridBagLayout());
        panel25.add(panel26, BorderLayout.EAST);
        final JPanel panel27 = new JPanel();
        panel27.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel26.add(panel27, gbc);
        m_moveUpButton = new JButton();
        m_moveUpButton.setPreferredSize(new Dimension(98, 25));
        m_moveUpButton.setText("Move up");
        panel27.add(m_moveUpButton);
        final JPanel panel28 = new JPanel();
        panel28.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel26.add(panel28, gbc);
        m_moveDownButton = new JButton();
        m_moveDownButton.setPreferredSize(new Dimension(98, 25));
        m_moveDownButton.setText("Move down");
        panel28.add(m_moveDownButton);
        final JPanel panel29 = new JPanel();
        panel29.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        panel26.add(panel29, gbc);
        m_editButton = new JButton();
        m_editButton.setPreferredSize(new Dimension(98, 25));
        m_editButton.setText("Edit");
        panel29.add(m_editButton);
        final JPanel panel30 = new JPanel();
        panel30.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        panel26.add(panel30, gbc);
        m_deleteButton = new JButton();
        m_deleteButton.setPreferredSize(new Dimension(98, 25));
        m_deleteButton.setText("Delete");
        panel30.add(m_deleteButton);
        final JPanel panel31 = new JPanel();
        panel31.setLayout(new BorderLayout(0, 0));
        panel25.add(panel31, BorderLayout.CENTER);
        final JScrollPane scrollPane11 = new JScrollPane();
        panel31.add(scrollPane11, BorderLayout.CENTER);
        m_linksTable = new JXTable();
        m_linksTable.setSortable(false);
        m_linksTable.setVisibleRowCount(5);
        m_linksTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane11.setViewportView(m_linksTable);
        final JPanel panel32 = new JPanel();
        panel32.setLayout(new BorderLayout(0, 0));
        tabbedPane1.addTab("Modifications", panel32);
        final JPanel panel33 = new JPanel();
        panel33.setLayout(new BorderLayout(0, 0));
        panel32.add(panel33, BorderLayout.CENTER);
        final JPanel panel34 = new JPanel();
        panel34.setLayout(new BorderLayout(0, 0));
        panel32.add(panel34, BorderLayout.EAST);
        final JPanel panel35 = new JPanel();
        panel35.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel34.add(panel35, BorderLayout.CENTER);
        m_modificationsButton = new JButton();
        m_modificationsButton.setText("Modifications...");
        panel35.add(m_modificationsButton);
        final JPanel panel36 = new JPanel();
        panel36.setLayout(new BorderLayout(0, 0));
        tabbedPane1.addTab("Module", panel36);
        final JPanel panel37 = new JPanel();
        panel37.setLayout(new GridBagLayout());
        panel36.add(panel37, BorderLayout.CENTER);
        final JPanel panel38 = new JPanel();
        panel38.setLayout(new GridBagLayout());
        panel38.setMinimumSize(new Dimension(56, 33));
        panel38.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel37.add(panel38, gbc);
        final JScrollPane scrollPane12 = new JScrollPane();
        scrollPane12.setHorizontalScrollBarPolicy(31);
        scrollPane12.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel38.add(scrollPane12, gbc);
        m_returnPageIdTextField = new JTextField();
        m_returnPageIdTextField.setColumns(42);
        scrollPane12.setViewportView(m_returnPageIdTextField);
        final JLabel label7 = new JLabel();
        label7.setText("Return page Id");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel37.add(label7, gbc);
        final JPanel spacer3 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel37.add(spacer3, gbc);
        final JPanel panel39 = new JPanel();
        panel39.setLayout(new GridBagLayout());
        panel39.setMinimumSize(new Dimension(56, 33));
        panel39.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel37.add(panel39, gbc);
        final JScrollPane scrollPane13 = new JScrollPane();
        scrollPane13.setHorizontalScrollBarPolicy(31);
        scrollPane13.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel39.add(scrollPane13, gbc);
        m_moduleNameTextField = new JTextField();
        m_moduleNameTextField.setColumns(42);
        scrollPane13.setViewportView(m_moduleNameTextField);
        final JLabel label8 = new JLabel();
        label8.setHorizontalTextPosition(11);
        label8.setText("Module name");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel37.add(label8, gbc);
        final JPanel panel40 = new JPanel();
        panel40.setLayout(new GridBagLayout());
        panel40.setMinimumSize(new Dimension(56, 33));
        panel40.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel37.add(panel40, gbc);
        final JScrollPane scrollPane14 = new JScrollPane();
        scrollPane14.setHorizontalScrollBarPolicy(31);
        scrollPane14.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel40.add(scrollPane14, gbc);
        m_moduleConstraintTextField = new JTextField();
        m_moduleConstraintTextField.setColumns(42);
        scrollPane14.setViewportView(m_moduleConstraintTextField);
        final JLabel label9 = new JLabel();
        label9.setText("Module constraint");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel37.add(label9, gbc);
        final JPanel panel41 = new JPanel();
        panel41.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        panel37.add(panel41, gbc);
        final JPanel panel42 = new JPanel();
        panel42.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel41.add(panel42, BorderLayout.CENTER);
        m_autoTraverseCheckBox = new JCheckBox();
        m_autoTraverseCheckBox.setText("Auto Traverse");
        panel42.add(m_autoTraverseCheckBox);
        m_autoReturnCheckBox = new JCheckBox();
        m_autoReturnCheckBox.setText("Auto Return");
        panel42.add(m_autoReturnCheckBox);
        m_externalCheckBox = new JCheckBox();
        m_externalCheckBox.setText("External");
        panel42.add(m_externalCheckBox);
        final JPanel panel43 = new JPanel();
        panel43.setLayout(new BorderLayout(0, 0));
        tabbedPane1.addTab("Autowire", panel43);
        final JPanel panel44 = new JPanel();
        panel44.setLayout(new GridBagLayout());
        panel43.add(panel44, BorderLayout.CENTER);
        final JPanel spacer4 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel44.add(spacer4, gbc);
        final JPanel panel45 = new JPanel();
        panel45.setLayout(new GridBagLayout());
        panel45.setMinimumSize(new Dimension(56, 33));
        panel45.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel44.add(panel45, gbc);
        final JScrollPane scrollPane15 = new JScrollPane();
        scrollPane15.setHorizontalScrollBarPolicy(31);
        scrollPane15.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel45.add(scrollPane15, gbc);
        m_autowireInConstraintTextField = new JTextField();
        m_autowireInConstraintTextField.setColumns(42);
        scrollPane15.setViewportView(m_autowireInConstraintTextField);
        final JLabel label10 = new JLabel();
        label10.setHorizontalTextPosition(11);
        label10.setText("Autowire in constraint");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel44.add(label10, gbc);
        final JPanel panel46 = new JPanel();
        panel46.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        panel44.add(panel46, gbc);
        final JPanel panel47 = new JPanel();
        panel47.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel46.add(panel47, BorderLayout.CENTER);
        m_autoInCheckBox = new JCheckBox();
        m_autoInCheckBox.setText("Auto In");
        panel47.add(m_autoInCheckBox);
        m_needsActionCheckBox = new JCheckBox();
        m_needsActionCheckBox.setText("Needs Action");
        panel47.add(m_needsActionCheckBox);
        m_autoOutCheckBox = new JCheckBox();
        m_autoOutCheckBox.setText("Auto Out");
        panel47.add(m_autoOutCheckBox);
        final JPanel panel48 = new JPanel();
        panel48.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel44.add(panel48, gbc);
        final JPanel panel49 = new JPanel();
        panel49.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel48.add(panel49, BorderLayout.CENTER);
        m_autowireCheckBox = new JCheckBox();
        m_autowireCheckBox.setText("Autowire");
        panel49.add(m_autowireCheckBox);
        m_globalAutowireCheckBox = new JCheckBox();
        m_globalAutowireCheckBox.setText("Global Autowire");
        panel49.add(m_globalAutowireCheckBox);
        final JPanel panel50 = new JPanel();
        panel50.setLayout(new GridBagLayout());
        panel50.setMinimumSize(new Dimension(56, 33));
        panel50.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel44.add(panel50, gbc);
        final JScrollPane scrollPane16 = new JScrollPane();
        scrollPane16.setHorizontalScrollBarPolicy(31);
        scrollPane16.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel50.add(scrollPane16, gbc);
        m_autowireOutConstraintTextField = new JTextField();
        m_autowireOutConstraintTextField.setColumns(42);
        scrollPane16.setViewportView(m_autowireOutConstraintTextField);
        final JLabel label11 = new JLabel();
        label11.setHorizontalTextPosition(11);
        label11.setText("Autowire out constraint");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel44.add(label11, gbc);
        final JPanel panel51 = new JPanel();
        panel51.setLayout(new BorderLayout(0, 0));
        panel1.add(panel51, BorderLayout.CENTER);
        final JPanel panel52 = new JPanel();
        panel52.setLayout(new BorderLayout(0, 0));
        panel51.add(panel52, BorderLayout.CENTER);
        final JPanel panel53 = new JPanel();
        panel53.setLayout(new BorderLayout(0, 0));
        panel52.add(panel53, BorderLayout.CENTER);
        final JPanel panel54 = new JPanel();
        panel54.setLayout(new BorderLayout(0, 0));
        panel53.add(panel54, BorderLayout.CENTER);
        final JPanel panel55 = new JPanel();
        panel55.setLayout(new GridBagLayout());
        panel55.setMinimumSize(new Dimension(10, 250));
        panel54.add(panel55, BorderLayout.CENTER);
        panel55.setBorder(BorderFactory.createTitledBorder(null, "Page text", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane17 = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel55.add(scrollPane17, gbc);
        m_pageText = new JTextArea();
        m_pageText.setColumns(50);
        m_pageText.setFocusAccelerator('T');
        m_pageText.setLineWrap(true);
        m_pageText.setRows(10);
        m_pageText.setWrapStyleWord(true);
        scrollPane17.setViewportView(m_pageText);
        final JPanel panel56 = new JPanel();
        panel56.setLayout(new BorderLayout(0, 0));
        panel54.add(panel56, BorderLayout.EAST);
        final JPanel panel57 = new JPanel();
        panel57.setLayout(new BorderLayout(0, 0));
        panel56.add(panel57, BorderLayout.CENTER);
        final JPanel panel58 = new JPanel();
        panel58.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel57.add(panel58, BorderLayout.NORTH);
        m_setImageButton = new JButton();
        m_setImageButton.setMaximumSize(new Dimension(120, 36));
        m_setImageButton.setMinimumSize(new Dimension(120, 36));
        m_setImageButton.setPreferredSize(new Dimension(120, 36));
        m_setImageButton.setText("Set image...");
        panel58.add(m_setImageButton);
        final JPanel panel59 = new JPanel();
        panel59.setLayout(new BorderLayout(0, 0));
        panel57.add(panel59, BorderLayout.CENTER);
        final JPanel panel60 = new JPanel();
        panel60.setLayout(new BorderLayout(0, 0));
        panel59.add(panel60, BorderLayout.NORTH);
        m_imageFileNameLabel = new JLabel();
        m_imageFileNameLabel.setHorizontalAlignment(0);
        m_imageFileNameLabel.setHorizontalTextPosition(0);
        m_imageFileNameLabel.setText("<NO IMAGE>");
        panel60.add(m_imageFileNameLabel, BorderLayout.CENTER);
        final JPanel panel61 = new JPanel();
        panel61.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel60.add(panel61, BorderLayout.SOUTH);
        m_buttonZoomIn = new JButton();
        m_buttonZoomIn.setText("+");
        panel61.add(m_buttonZoomIn);
        m_buttonZoomOut = new JButton();
        m_buttonZoomOut.setText("―");
        panel61.add(m_buttonZoomOut);
        final JPanel panel62 = new JPanel();
        panel62.setLayout(new BorderLayout(0, 0));
        panel60.add(panel62, BorderLayout.NORTH);
        m_backgroundCheckBox = new JCheckBox();
        m_backgroundCheckBox.setText("Background");
        panel62.add(m_backgroundCheckBox, BorderLayout.WEST);
        m_animatedCheckBox = new JCheckBox();
        m_animatedCheckBox.setText("Animated");
        panel62.add(m_animatedCheckBox, BorderLayout.EAST);
        m_imageView = new JXImageView();
        panel59.add(m_imageView, BorderLayout.CENTER);
        final JPanel panel63 = new JPanel();
        panel63.setLayout(new BorderLayout(0, 0));
        panel56.add(panel63, BorderLayout.NORTH);
        final JPanel panel64 = new JPanel();
        panel64.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel63.add(panel64, BorderLayout.NORTH);
        m_setSoundButton = new JButton();
        m_setSoundButton.setMaximumSize(new Dimension(120, 36));
        m_setSoundButton.setMinimumSize(new Dimension(120, 36));
        m_setSoundButton.setPreferredSize(new Dimension(120, 36));
        m_setSoundButton.setText("Set sound...");
        panel64.add(m_setSoundButton);
        final JPanel panel65 = new JPanel();
        panel65.setLayout(new BorderLayout(0, 0));
        panel63.add(panel65, BorderLayout.CENTER);
        final JPanel panel66 = new JPanel();
        panel66.setLayout(new BorderLayout(0, 0));
        panel65.add(panel66, BorderLayout.NORTH);
        m_soundFileNameLabel = new JLabel();
        m_soundFileNameLabel.setHorizontalAlignment(0);
        m_soundFileNameLabel.setHorizontalTextPosition(0);
        m_soundFileNameLabel.setText("<NO_SOUND>");
        panel66.add(m_soundFileNameLabel, BorderLayout.CENTER);
        m_soundSFXCheckBox = new JCheckBox();
        m_soundSFXCheckBox.setText("SFX");
        panel66.add(m_soundSFXCheckBox, BorderLayout.NORTH);
        final JPanel panel67 = new JPanel();
        panel67.setLayout(new BorderLayout(0, 0));
        panel1.add(panel67, BorderLayout.NORTH);
        final JToolBar toolBar1 = new JToolBar();
        toolBar1.setBorderPainted(false);
        toolBar1.setFloatable(false);
        panel67.add(toolBar1, BorderLayout.CENTER);
        m_undoButton = new JButton();
        m_undoButton.setIcon(new ImageIcon(getClass().getResource("/common/undo.png")));
        m_undoButton.setText("Undo");
        toolBar1.add(m_undoButton);
        m_redoButton = new JButton();
        m_redoButton.setIcon(new ImageIcon(getClass().getResource("/common/redo.png")));
        m_redoButton.setText("Redo");
        toolBar1.add(m_redoButton);
        m_languageComboBox = new JComboBox();
        panel67.add(m_languageComboBox, BorderLayout.EAST);
        m_traverseTextLabel.setLabelFor(m_traverseTextTextField);
        label1.setLabelFor(m_pageCaptionTextField);
        m_autowireInTextLabel.setLabelFor(m_autowireInTextTextField);
        m_autowireOutTextLabel.setLabelFor(m_autowireOutTextTextField);
        m_returnTextLabel.setLabelFor(m_returnTextTextField);
        label2.setLabelFor(m_pageIdTextField);
        label3.setLabelFor(m_pageVariableTextField);
        label4.setLabelFor(m_pageVariableTextField);
        label5.setLabelFor(m_defaultTagTextField);
        label6.setLabelFor(m_themeComboBox);
        label7.setLabelFor(m_returnPageIdTextField);
        label8.setLabelFor(m_moduleNameTextField);
        label9.setLabelFor(m_moduleConstraintTextField);
        label10.setLabelFor(m_autowireInConstraintTextField);
        label11.setLabelFor(m_autowireOutConstraintTextField);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
