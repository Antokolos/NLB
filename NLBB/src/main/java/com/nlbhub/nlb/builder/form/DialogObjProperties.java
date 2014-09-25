/**
 * @(#)DialogObjProperties.java
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
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.util.MultiLangString;
import com.nlbhub.nlb.util.StringHelper;
import org.jdesktop.swingx.JXImageView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

public class DialogObjProperties extends JDialog implements NLBObserver {
    private final String m_observerId;
    private Obj m_obj;
    private NonLinearBookFacade m_nlbFacade;
    private Variable m_variable;
    private MultiLangString m_objDisplayNames;
    private MultiLangString m_objTexts;
    private MultiLangString m_objActTexts;
    private String m_selectedLanguage;
    private String m_imageFileName;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField m_objIdTextField;
    private JTextField m_objNameTextField;
    private JTextField m_objVariableTextField;
    private JButton m_modificationsButton;
    private JCheckBox m_objIsTakable;
    private JButton m_undoButton;
    private JButton m_redoButton;
    private JTextField m_objDispTextField;
    private JComboBox m_languageComboBox;
    private JTabbedPane m_tabbedPane1;
    private JButton m_setTextColorButton;
    private JButton m_setObjColorButton;
    private JButton m_setBorderColorButton;
    private JButton m_setImageButton;
    private JLabel m_imageFileNameLabel;
    private JXImageView m_imageView;
    private JTextArea m_objTextTextArea;
    private JCheckBox m_imageInScene;
    private JCheckBox m_imageInInventory;
    private JTextArea m_objActTextTextArea;

    public DialogObjProperties(
            final MainFrame mainFrame,
            final NonLinearBookFacade nlbFacade,
            final Obj obj
    ) {
        m_nlbFacade = nlbFacade;
        setObjProperties(obj);
        setContentPane(contentPane);
        setModal(true);
        setTitle("Obj properties");
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

        m_undoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                nlbFacade.undo(obj.getId());
                setObjProperties(obj);
            }
        });

        m_redoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                nlbFacade.redo(obj.getId());
                setObjProperties(obj);
            }
        });

        m_modificationsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                DialogModifications dialog = new DialogModifications(m_nlbFacade, obj);
                dialog.showDialog();
            }
        });

        m_languageComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox cb = (JComboBox) e.getSource();
                refreshTextsForCurrentLanguage();
                String selectedLanguage = (String) cb.getSelectedItem();
                m_objDispTextField.setText(m_objDisplayNames.get(selectedLanguage));
                m_objTextTextArea.setText(m_objTexts.get(selectedLanguage));
                m_objActTextTextArea.setText(m_objActTexts.get(selectedLanguage));
                m_selectedLanguage = selectedLanguage;
            }
        });

        m_setImageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                DialogMediaLibrary dialog = (
                        new DialogMediaLibrary(mainFrame, m_nlbFacade.getMainFacade(), MediaFile.Type.Image)
                );
                dialog.showDialog();
                if (!dialog.isCanceled()) {
                    m_imageFileName = dialog.getSelectedFileName();
                    m_imageFileNameLabel.setText(m_imageFileName);
                    setObjImage(m_imageFileName);
                }
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
        m_objDisplayNames.put(m_selectedLanguage, m_objDispTextField.getText());
        m_objTexts.put(m_selectedLanguage, m_objTextTextArea.getText());
        m_objActTexts.put(m_selectedLanguage, m_objActTextTextArea.getText());
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

    private void setObjProperties(final Obj obj) {
        m_obj = obj;
        m_variable = m_nlbFacade.getNlb().getVariableById(obj.getVarId());
        m_objIdTextField.setText(obj.getId());
        m_objNameTextField.setText(obj.getName());
        m_objDispTextField.setText(obj.getDisp());
        m_objVariableTextField.setText(m_variable != null ? m_variable.getName() : "");
        m_objTextTextArea.setText(obj.getText());
        m_objActTextTextArea.setText(obj.getActText());
        m_objIsTakable.setSelected(obj.isTakable());
        m_imageInScene.setSelected(obj.isImageInScene());
        m_imageInInventory.setSelected(obj.isImageInInventory());

        DefaultComboBoxModel<String> languageComboboxModel = new DefaultComboBoxModel<>();
        languageComboboxModel.addElement(Constants.RU);
        languageComboboxModel.addElement(Constants.EN);
        m_languageComboBox.setModel(languageComboboxModel);
        m_languageComboBox.setSelectedIndex(
                obj.getCurrentNLB().getLanguage().equals(Constants.RU) ? 0 : 1
        );

        m_objDisplayNames = obj.getDisps();
        m_objTexts = obj.getTexts();
        m_objActTexts = obj.getActTexts();
        m_selectedLanguage = (String) languageComboboxModel.getSelectedItem();
        m_imageFileName = obj.getImageFileName();
        m_imageFileNameLabel.setText(m_imageFileName);
        setObjImage(obj.getImageFileName());
    }

    private void onOK() {
        refreshTextsForCurrentLanguage();
        m_nlbFacade.updateObj(
                m_obj,
                m_objVariableTextField.getText(),
                m_objNameTextField.getText(),
                m_imageFileName,
                m_objDisplayNames,
                m_objTexts,
                m_objActTexts,
                m_objIsTakable.isSelected(),
                m_imageInScene.isSelected(),
                m_imageInInventory.isSelected()
        );
        m_nlbFacade.removeObserver(m_observerId);
        dispose();
    }

    private void onCancel() {
        m_nlbFacade.redoAll(m_obj.getId());
        m_nlbFacade.removeObserver(m_observerId);
        dispose();
    }

    @Override
    public void updateView() {
        m_undoButton.setEnabled(m_nlbFacade.canUndo(m_obj.getId()));
        m_redoButton.setEnabled(m_nlbFacade.canRedo(m_obj.getId()));
    }

    private void setObjImage(final String imageFileName) {
        try {
            if (StringHelper.isEmpty(m_imageFileName)) {
                m_imageView.setVisible(false);
            } else {
                m_imageView.setImage(
                        new File(m_nlbFacade.getMainFacade().getNlb().getImagesDir(), imageFileName)
                );
                m_imageView.setVisible(true);
            }
        } catch (IOException ignore) {
            // do nothing
        }
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
        buttonOK.setMaximumSize(new Dimension(120, 36));
        buttonOK.setMinimumSize(new Dimension(120, 36));
        buttonOK.setPreferredSize(new Dimension(120, 36));
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
        buttonCancel.setMaximumSize(new Dimension(120, 36));
        buttonCancel.setMinimumSize(new Dimension(120, 36));
        buttonCancel.setPreferredSize(new Dimension(120, 36));
        buttonCancel.setText("Cancel");
        panel7.add(buttonCancel);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new BorderLayout(0, 0));
        panel4.add(panel8, BorderLayout.CENTER);
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel8.add(panel9, BorderLayout.NORTH);
        m_setImageButton = new JButton();
        m_setImageButton.setMaximumSize(new Dimension(120, 36));
        m_setImageButton.setMinimumSize(new Dimension(120, 36));
        m_setImageButton.setPreferredSize(new Dimension(120, 36));
        m_setImageButton.setText("Set image...");
        panel9.add(m_setImageButton);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new BorderLayout(0, 0));
        panel8.add(panel10, BorderLayout.CENTER);
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new BorderLayout(0, 0));
        panel10.add(panel11, BorderLayout.NORTH);
        m_imageFileNameLabel = new JLabel();
        m_imageFileNameLabel.setHorizontalAlignment(0);
        m_imageFileNameLabel.setHorizontalTextPosition(0);
        m_imageFileNameLabel.setText("<NO IMAGE>");
        panel11.add(m_imageFileNameLabel, BorderLayout.CENTER);
        m_imageView = new JXImageView();
        panel10.add(m_imageView, BorderLayout.CENTER);
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new BorderLayout(0, 0));
        panel3.add(panel12, BorderLayout.CENTER);
        final JPanel panel13 = new JPanel();
        panel13.setLayout(new BorderLayout(0, 0));
        panel12.add(panel13, BorderLayout.NORTH);
        final JToolBar toolBar1 = new JToolBar();
        toolBar1.setBorderPainted(false);
        toolBar1.setFloatable(false);
        panel13.add(toolBar1, BorderLayout.WEST);
        m_undoButton = new JButton();
        m_undoButton.setIcon(new ImageIcon(getClass().getResource("/common/undo.png")));
        m_undoButton.setText("Undo");
        toolBar1.add(m_undoButton);
        m_redoButton = new JButton();
        m_redoButton.setIcon(new ImageIcon(getClass().getResource("/common/redo.png")));
        m_redoButton.setText("Redo");
        toolBar1.add(m_redoButton);
        m_languageComboBox = new JComboBox();
        panel13.add(m_languageComboBox, BorderLayout.EAST);
        final JPanel panel14 = new JPanel();
        panel14.setLayout(new BorderLayout(0, 0));
        panel12.add(panel14, BorderLayout.CENTER);
        m_tabbedPane1 = new JTabbedPane();
        panel14.add(m_tabbedPane1, BorderLayout.CENTER);
        final JPanel panel15 = new JPanel();
        panel15.setLayout(new GridBagLayout());
        m_tabbedPane1.addTab("Text", panel15);
        final JPanel panel16 = new JPanel();
        panel16.setLayout(new GridBagLayout());
        panel16.setMinimumSize(new Dimension(468, 33));
        panel16.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel15.add(panel16, gbc);
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setHorizontalScrollBarPolicy(31);
        scrollPane1.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel16.add(scrollPane1, gbc);
        m_objDispTextField = new JTextField();
        m_objDispTextField.setColumns(40);
        scrollPane1.setViewportView(m_objDispTextField);
        final JLabel label1 = new JLabel();
        label1.setFont(new Font(label1.getFont().getName(), label1.getFont().getStyle(), label1.getFont().getSize()));
        label1.setText("Obj display name");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel15.add(label1, gbc);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel15.add(spacer1, gbc);
        final JPanel panel17 = new JPanel();
        panel17.setLayout(new GridBagLayout());
        panel17.setMinimumSize(new Dimension(468, 33));
        panel17.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel15.add(panel17, gbc);
        final JScrollPane scrollPane2 = new JScrollPane();
        scrollPane2.setHorizontalScrollBarPolicy(31);
        scrollPane2.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel17.add(scrollPane2, gbc);
        m_objTextTextArea = new JTextArea();
        m_objTextTextArea.setColumns(50);
        m_objTextTextArea.setLineWrap(true);
        m_objTextTextArea.setRows(10);
        m_objTextTextArea.setWrapStyleWord(true);
        scrollPane2.setViewportView(m_objTextTextArea);
        final JLabel label2 = new JLabel();
        label2.setFont(new Font(label2.getFont().getName(), label2.getFont().getStyle(), label2.getFont().getSize()));
        label2.setText("Obj text");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel15.add(label2, gbc);
        final JPanel panel18 = new JPanel();
        panel18.setLayout(new GridBagLayout());
        panel18.setMinimumSize(new Dimension(468, 33));
        panel18.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel15.add(panel18, gbc);
        final JScrollPane scrollPane3 = new JScrollPane();
        scrollPane3.setHorizontalScrollBarPolicy(31);
        scrollPane3.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel18.add(scrollPane3, gbc);
        m_objActTextTextArea = new JTextArea();
        m_objActTextTextArea.setColumns(50);
        m_objActTextTextArea.setLineWrap(true);
        m_objActTextTextArea.setRows(10);
        m_objActTextTextArea.setWrapStyleWord(true);
        scrollPane3.setViewportView(m_objActTextTextArea);
        final JLabel label3 = new JLabel();
        label3.setFont(new Font(label3.getFont().getName(), label3.getFont().getStyle(), label3.getFont().getSize()));
        label3.setText("Obj Act text");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel15.add(label3, gbc);
        final JPanel panel19 = new JPanel();
        panel19.setLayout(new GridBagLayout());
        m_tabbedPane1.addTab("Properties", panel19);
        final JLabel label4 = new JLabel();
        label4.setText("Obj Id");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel19.add(label4, gbc);
        final JLabel label5 = new JLabel();
        label5.setFont(new Font(label5.getFont().getName(), label5.getFont().getStyle(), label5.getFont().getSize()));
        label5.setText("Obj name");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel19.add(label5, gbc);
        final JPanel panel20 = new JPanel();
        panel20.setLayout(new GridBagLayout());
        panel20.setMinimumSize(new Dimension(468, 33));
        panel20.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel19.add(panel20, gbc);
        final JScrollPane scrollPane4 = new JScrollPane();
        scrollPane4.setHorizontalScrollBarPolicy(31);
        scrollPane4.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel20.add(scrollPane4, gbc);
        m_objIdTextField = new JTextField();
        m_objIdTextField.setColumns(40);
        m_objIdTextField.setEditable(false);
        scrollPane4.setViewportView(m_objIdTextField);
        final JPanel panel21 = new JPanel();
        panel21.setLayout(new GridBagLayout());
        panel21.setMinimumSize(new Dimension(468, 33));
        panel21.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel19.add(panel21, gbc);
        final JScrollPane scrollPane5 = new JScrollPane();
        scrollPane5.setHorizontalScrollBarPolicy(31);
        scrollPane5.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel21.add(scrollPane5, gbc);
        m_objNameTextField = new JTextField();
        m_objNameTextField.setColumns(40);
        scrollPane5.setViewportView(m_objNameTextField);
        final JPanel panel22 = new JPanel();
        panel22.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel19.add(panel22, gbc);
        final JPanel panel23 = new JPanel();
        panel23.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel22.add(panel23, BorderLayout.EAST);
        m_modificationsButton = new JButton();
        m_modificationsButton.setText("Modifications...");
        panel23.add(m_modificationsButton);
        final JPanel panel24 = new JPanel();
        panel24.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel22.add(panel24, BorderLayout.WEST);
        m_setTextColorButton = new JButton();
        m_setTextColorButton.setEnabled(false);
        m_setTextColorButton.setText("Set text color");
        panel24.add(m_setTextColorButton);
        m_setObjColorButton = new JButton();
        m_setObjColorButton.setEnabled(false);
        m_setObjColorButton.setText("Set obj color");
        panel24.add(m_setObjColorButton);
        m_setBorderColorButton = new JButton();
        m_setBorderColorButton.setEnabled(false);
        m_setBorderColorButton.setText("Set border color");
        panel24.add(m_setBorderColorButton);
        final JPanel spacer2 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel19.add(spacer2, gbc);
        final JLabel label6 = new JLabel();
        label6.setText("Obj variable");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel19.add(label6, gbc);
        final JPanel panel25 = new JPanel();
        panel25.setLayout(new GridBagLayout());
        panel25.setMinimumSize(new Dimension(468, 33));
        panel25.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel19.add(panel25, gbc);
        final JScrollPane scrollPane6 = new JScrollPane();
        scrollPane6.setHorizontalScrollBarPolicy(31);
        scrollPane6.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel25.add(scrollPane6, gbc);
        m_objVariableTextField = new JTextField();
        m_objVariableTextField.setColumns(40);
        scrollPane6.setViewportView(m_objVariableTextField);
        final JPanel panel26 = new JPanel();
        panel26.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        panel19.add(panel26, gbc);
        m_objIsTakable = new JCheckBox();
        m_objIsTakable.setText("Can be taken to the inventory");
        panel26.add(m_objIsTakable);
        m_imageInScene = new JCheckBox();
        m_imageInScene.setText("Image in scene");
        panel26.add(m_imageInScene);
        m_imageInInventory = new JCheckBox();
        m_imageInInventory.setText("Image in inventory");
        panel26.add(m_imageInInventory);
        final JPanel panel27 = new JPanel();
        panel27.setLayout(new BorderLayout(0, 0));
        panel1.add(panel27, BorderLayout.CENTER);
        label1.setLabelFor(m_objDispTextField);
        label2.setLabelFor(m_objTextTextArea);
        label3.setLabelFor(m_objActTextTextArea);
        label4.setLabelFor(m_objIdTextField);
        label5.setLabelFor(m_objNameTextField);
        label6.setLabelFor(m_objVariableTextField);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
