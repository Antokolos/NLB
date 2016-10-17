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
import com.nlbhub.nlb.builder.util.ImageHelper;
import com.nlbhub.nlb.builder.util.WheelScaleListener;
import com.nlbhub.nlb.builder.util.Zoomer;
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
    private Zoomer m_zoomer;
    private Obj m_obj;
    private NonLinearBookFacade m_nlbFacade;
    private MultiLangString m_objDisplayNames;
    private MultiLangString m_objTexts;
    private MultiLangString m_objActTexts;
    private String m_selectedLanguage;
    private String m_imageFileName;
    private String m_soundFileName;
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
    private JTextField m_objConstraintTextField;
    private JCheckBox m_animatedImageCheckBox;
    private JButton m_buttonZoomIn;
    private JButton m_buttonZoomOut;
    private JCheckBox m_suppressDsc;
    private JButton m_setSoundButton;
    private JLabel m_soundFileNameLabel;
    private JCheckBox m_soundSFXCheckBox;
    private JTextField m_objCommonToTextField;
    private JTextField m_objDefaultTagTextField;
    private JCheckBox m_objIsGraphical;
    private JTextField m_morphOver;
    private JTextField m_morphOut;
    private JCheckBox m_objIsPreserved;
    private JCheckBox m_objIsClearUnderTooltip;
    private JRadioButton m_movementDirectionTop;
    private JRadioButton m_movementDirectionBottom;
    private JRadioButton m_movementDirectionLeft;
    private JRadioButton m_movementDirectionRight;
    private JRadioButton m_movementDirectionNone;
    private JCheckBox m_objIsCollapsable;
    private JTextField m_offset;
    private JRadioButton m_effectMoveIn;
    private JRadioButton m_effectMoveOut;
    private JRadioButton m_effectFadeIn;
    private JRadioButton m_effectFadeOut;
    private JRadioButton m_effectZoomIn;
    private JRadioButton m_effectZoomOut;
    private JRadioButton m_effectNone;

    public DialogObjProperties(
            final MainFrame mainFrame,
            final NonLinearBookFacade nlbFacade,
            final Obj obj
    ) {
        m_nlbFacade = nlbFacade;
        setObjProperties(obj);
        setContentPane(contentPane);
        m_zoomer = new Zoomer(m_imageView);
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
                setObjImage(obj.getImageFileName());
            }
        });

        m_redoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                nlbFacade.redo(obj.getId());
                setObjProperties(obj);
                setObjImage(obj.getImageFileName());
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
                    m_soundFileName = dialog.getSelectedFileName();
                    m_soundFileNameLabel.setText(m_soundFileName);
                }
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
                        new DialogMediaLibrary(
                                mainFrame,
                                m_nlbFacade.getMainFacade(),
                                MediaFile.Type.Image,
                                m_imageFileName.split(Constants.MEDIA_FILE_NAME_SEP)
                        )
                );
                dialog.showDialog();
                if (!dialog.isCanceled()) {
                    m_imageFileName = dialog.getSelectedFileName();
                    m_imageFileNameLabel.setText(m_imageFileName);
                    setObjImage(m_imageFileName);
                }
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
                setObjImage(m_imageFileName);
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
                setObjImage(m_obj.getImageFileName());
                buttonOK.requestFocusInWindow();
            }
        });
        setVisible(true);
    }

    private void setObjProperties(final Obj obj) {
        m_obj = obj;
        Variable variable = m_nlbFacade.getNlb().getVariableById(obj.getVarId());
        Variable deftag = m_nlbFacade.getNlb().getVariableById(obj.getDefaultTagId());
        Variable constraint = m_nlbFacade.getNlb().getVariableById(obj.getConstrId());
        Variable commonTo = m_nlbFacade.getNlb().getVariableById(obj.getCommonToId());
        Variable morphOver = m_nlbFacade.getNlb().getVariableById(obj.getMorphOverId());
        Variable morphOut = m_nlbFacade.getNlb().getVariableById(obj.getMorphOutId());
        m_objIdTextField.setText(obj.getId());
        m_objNameTextField.setText(obj.getName());
        m_objDispTextField.setText(obj.getDisp());
        m_objVariableTextField.setText(variable != null ? variable.getName() : "");
        m_objDefaultTagTextField.setText(deftag != null ? deftag.getValue() : "");
        m_objConstraintTextField.setText(constraint != null ? constraint.getValue() : "");
        m_objCommonToTextField.setText(commonTo != null ? commonTo.getName() : "");
        m_objTextTextArea.setText(obj.getText());
        m_objActTextTextArea.setText(obj.getActText());
        m_suppressDsc.setSelected(obj.isSuppressDsc());
        m_objIsTakable.setSelected(obj.isTakable());
        m_objIsGraphical.setSelected(obj.isGraphical());
        m_objIsPreserved.setSelected(obj.isPreserved());
        m_objIsCollapsable.setSelected(obj.isCollapsable());
        m_offset.setText(obj.getOffset());
        switch (obj.getMovementDirection()) {
            case Top:
                m_movementDirectionTop.setSelected(true);
                break;
            case Left:
                m_movementDirectionLeft.setSelected(true);
                break;
            case Right:
                m_movementDirectionRight.setSelected(true);
                break;
            case Bottom:
                m_movementDirectionBottom.setSelected(true);
                break;
            case None:
            default:
                m_movementDirectionNone.setSelected(true);
        }
        switch (obj.getEffect()) {
            case MoveIn:
                m_effectMoveIn.setSelected(true);
                break;
            case MoveOut:
                m_effectMoveOut.setSelected(true);
                break;
            case ZoomIn:
                m_effectZoomIn.setSelected(true);
                break;
            case ZoomOut:
                m_effectZoomOut.setSelected(true);
                break;
            case FadeIn:
                m_effectFadeIn.setSelected(true);
                break;
            case FadeOut:
                m_effectFadeOut.setSelected(true);
                break;
            case None:
            default:
                m_effectNone.setSelected(true);
        }
        m_objIsClearUnderTooltip.setSelected(obj.isClearUnderTooltip());
        m_morphOver.setText(morphOver != null ? morphOver.getName() : "");
        m_morphOut.setText(morphOut != null ? morphOut.getName() : "");
        m_imageInScene.setSelected(obj.isImageInScene());
        m_imageInInventory.setSelected(obj.isImageInInventory());

        m_soundFileName = obj.getSoundFileName();
        m_soundSFXCheckBox.setSelected(obj.isSoundSFX());
        m_soundFileNameLabel.setText(m_soundFileName);

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
        m_animatedImageCheckBox.setSelected(obj.isAnimatedImage());
        m_imageFileNameLabel.setText(m_imageFileName);
    }

    private void onOK() {
        refreshTextsForCurrentLanguage();
        m_nlbFacade.updateObj(
                m_obj,
                m_objVariableTextField.getText(),
                m_objDefaultTagTextField.getText(),
                m_objConstraintTextField.getText(),
                m_objCommonToTextField.getText(),
                m_objNameTextField.getText(),
                m_imageFileName,
                m_soundFileName,
                m_soundSFXCheckBox.isSelected(),
                m_animatedImageCheckBox.isSelected(),
                m_suppressDsc.isSelected(),
                m_objDisplayNames,
                m_objTexts,
                m_objActTexts,
                m_objIsGraphical.isSelected(),
                m_objIsPreserved.isSelected(),
                m_objIsCollapsable.isSelected(),
                m_offset.getText(),
                getMovementDirection(),
                getEffect(),
                m_objIsClearUnderTooltip.isSelected(),
                m_morphOver.getText(),
                m_morphOut.getText(),
                m_objIsTakable.isSelected(),
                m_imageInScene.isSelected(),
                m_imageInInventory.isSelected()
        );
        m_nlbFacade.removeObserver(m_observerId);
        dispose();
    }

    private Obj.Effect getEffect() {
        if (m_effectFadeIn.isSelected()) {
            return Obj.Effect.FadeIn;
        } else if (m_effectFadeOut.isSelected()) {
            return Obj.Effect.FadeOut;
        } else if (m_effectZoomIn.isSelected()) {
            return Obj.Effect.ZoomIn;
        } else if (m_effectZoomOut.isSelected()) {
            return Obj.Effect.ZoomOut;
        } else if (m_effectMoveIn.isSelected()) {
            return Obj.Effect.MoveIn;
        } else if (m_effectMoveOut.isSelected()) {
            return Obj.Effect.MoveOut;
        } else {
            return Obj.Effect.None;
        }
    }

    Obj.MovementDirection getMovementDirection() {
        if (m_movementDirectionTop.isSelected()) {
            return Obj.MovementDirection.Top;
        } else if (m_movementDirectionLeft.isSelected()) {
            return Obj.MovementDirection.Left;
        } else if (m_movementDirectionRight.isSelected()) {
            return Obj.MovementDirection.Right;
        } else if (m_movementDirectionBottom.isSelected()) {
            return Obj.MovementDirection.Bottom;
        } else {
            return Obj.MovementDirection.None;
        }
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
        panel9.setLayout(new BorderLayout(0, 0));
        panel8.add(panel9, BorderLayout.NORTH);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel9.add(panel10, BorderLayout.NORTH);
        m_setSoundButton = new JButton();
        m_setSoundButton.setMaximumSize(new Dimension(120, 36));
        m_setSoundButton.setMinimumSize(new Dimension(120, 36));
        m_setSoundButton.setPreferredSize(new Dimension(120, 36));
        m_setSoundButton.setText("Set sound...");
        panel10.add(m_setSoundButton);
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new BorderLayout(0, 0));
        panel9.add(panel11, BorderLayout.CENTER);
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new BorderLayout(0, 0));
        panel11.add(panel12, BorderLayout.NORTH);
        m_soundFileNameLabel = new JLabel();
        m_soundFileNameLabel.setHorizontalAlignment(0);
        m_soundFileNameLabel.setHorizontalTextPosition(0);
        m_soundFileNameLabel.setText("<NO_SOUND>");
        panel12.add(m_soundFileNameLabel, BorderLayout.CENTER);
        m_soundSFXCheckBox = new JCheckBox();
        m_soundSFXCheckBox.setText("SFX");
        panel12.add(m_soundSFXCheckBox, BorderLayout.NORTH);
        final JPanel panel13 = new JPanel();
        panel13.setLayout(new BorderLayout(0, 0));
        panel8.add(panel13, BorderLayout.CENTER);
        final JPanel panel14 = new JPanel();
        panel14.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel13.add(panel14, BorderLayout.NORTH);
        m_setImageButton = new JButton();
        m_setImageButton.setMaximumSize(new Dimension(120, 36));
        m_setImageButton.setMinimumSize(new Dimension(120, 36));
        m_setImageButton.setPreferredSize(new Dimension(120, 36));
        m_setImageButton.setText("Set image...");
        panel14.add(m_setImageButton);
        final JPanel panel15 = new JPanel();
        panel15.setLayout(new BorderLayout(0, 0));
        panel13.add(panel15, BorderLayout.CENTER);
        final JPanel panel16 = new JPanel();
        panel16.setLayout(new BorderLayout(0, 0));
        panel15.add(panel16, BorderLayout.NORTH);
        m_imageFileNameLabel = new JLabel();
        m_imageFileNameLabel.setHorizontalAlignment(0);
        m_imageFileNameLabel.setHorizontalTextPosition(0);
        m_imageFileNameLabel.setText("<NO IMAGE>");
        panel16.add(m_imageFileNameLabel, BorderLayout.CENTER);
        final JPanel panel17 = new JPanel();
        panel17.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel16.add(panel17, BorderLayout.SOUTH);
        m_buttonZoomIn = new JButton();
        m_buttonZoomIn.setText("+");
        panel17.add(m_buttonZoomIn);
        m_buttonZoomOut = new JButton();
        m_buttonZoomOut.setText("―");
        panel17.add(m_buttonZoomOut);
        m_imageView = new JXImageView();
        panel15.add(m_imageView, BorderLayout.CENTER);
        final JPanel panel18 = new JPanel();
        panel18.setLayout(new BorderLayout(0, 0));
        panel3.add(panel18, BorderLayout.CENTER);
        final JPanel panel19 = new JPanel();
        panel19.setLayout(new BorderLayout(0, 0));
        panel18.add(panel19, BorderLayout.NORTH);
        final JToolBar toolBar1 = new JToolBar();
        toolBar1.setBorderPainted(false);
        toolBar1.setFloatable(false);
        panel19.add(toolBar1, BorderLayout.WEST);
        m_undoButton = new JButton();
        m_undoButton.setIcon(new ImageIcon(getClass().getResource("/common/undo.png")));
        m_undoButton.setText("Undo");
        toolBar1.add(m_undoButton);
        m_redoButton = new JButton();
        m_redoButton.setIcon(new ImageIcon(getClass().getResource("/common/redo.png")));
        m_redoButton.setText("Redo");
        toolBar1.add(m_redoButton);
        m_languageComboBox = new JComboBox();
        panel19.add(m_languageComboBox, BorderLayout.EAST);
        final JPanel panel20 = new JPanel();
        panel20.setLayout(new BorderLayout(0, 0));
        panel18.add(panel20, BorderLayout.CENTER);
        m_tabbedPane1 = new JTabbedPane();
        panel20.add(m_tabbedPane1, BorderLayout.CENTER);
        final JPanel panel21 = new JPanel();
        panel21.setLayout(new GridBagLayout());
        m_tabbedPane1.addTab("Text", panel21);
        final JPanel panel22 = new JPanel();
        panel22.setLayout(new GridBagLayout());
        panel22.setMinimumSize(new Dimension(468, 33));
        panel22.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel21.add(panel22, gbc);
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setHorizontalScrollBarPolicy(31);
        scrollPane1.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel22.add(scrollPane1, gbc);
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
        panel21.add(label1, gbc);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel21.add(spacer1, gbc);
        final JPanel panel23 = new JPanel();
        panel23.setLayout(new GridBagLayout());
        panel23.setMinimumSize(new Dimension(468, 33));
        panel23.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel21.add(panel23, gbc);
        final JScrollPane scrollPane2 = new JScrollPane();
        scrollPane2.setHorizontalScrollBarPolicy(31);
        scrollPane2.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel23.add(scrollPane2, gbc);
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
        panel21.add(label2, gbc);
        final JPanel panel24 = new JPanel();
        panel24.setLayout(new GridBagLayout());
        panel24.setMinimumSize(new Dimension(468, 33));
        panel24.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel21.add(panel24, gbc);
        final JScrollPane scrollPane3 = new JScrollPane();
        scrollPane3.setHorizontalScrollBarPolicy(31);
        scrollPane3.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel24.add(scrollPane3, gbc);
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
        panel21.add(label3, gbc);
        final JPanel panel25 = new JPanel();
        panel25.setLayout(new GridBagLayout());
        m_tabbedPane1.addTab("Properties", panel25);
        final JLabel label4 = new JLabel();
        label4.setText("Obj Id");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel25.add(label4, gbc);
        final JLabel label5 = new JLabel();
        label5.setFont(new Font(label5.getFont().getName(), label5.getFont().getStyle(), label5.getFont().getSize()));
        label5.setText("Obj name");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel25.add(label5, gbc);
        final JPanel panel26 = new JPanel();
        panel26.setLayout(new GridBagLayout());
        panel26.setMinimumSize(new Dimension(468, 33));
        panel26.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel25.add(panel26, gbc);
        final JScrollPane scrollPane4 = new JScrollPane();
        scrollPane4.setHorizontalScrollBarPolicy(31);
        scrollPane4.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel26.add(scrollPane4, gbc);
        m_objIdTextField = new JTextField();
        m_objIdTextField.setColumns(40);
        m_objIdTextField.setEditable(false);
        scrollPane4.setViewportView(m_objIdTextField);
        final JPanel panel27 = new JPanel();
        panel27.setLayout(new GridBagLayout());
        panel27.setMinimumSize(new Dimension(468, 33));
        panel27.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel25.add(panel27, gbc);
        final JScrollPane scrollPane5 = new JScrollPane();
        scrollPane5.setHorizontalScrollBarPolicy(31);
        scrollPane5.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel27.add(scrollPane5, gbc);
        m_objNameTextField = new JTextField();
        m_objNameTextField.setColumns(40);
        scrollPane5.setViewportView(m_objNameTextField);
        final JPanel panel28 = new JPanel();
        panel28.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 7;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel25.add(panel28, gbc);
        final JPanel panel29 = new JPanel();
        panel29.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel28.add(panel29, BorderLayout.EAST);
        m_modificationsButton = new JButton();
        m_modificationsButton.setText("Modifications...");
        panel29.add(m_modificationsButton);
        final JPanel panel30 = new JPanel();
        panel30.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel28.add(panel30, BorderLayout.WEST);
        m_setTextColorButton = new JButton();
        m_setTextColorButton.setEnabled(false);
        m_setTextColorButton.setText("Set text color");
        panel30.add(m_setTextColorButton);
        m_setObjColorButton = new JButton();
        m_setObjColorButton.setEnabled(false);
        m_setObjColorButton.setText("Set obj color");
        panel30.add(m_setObjColorButton);
        m_setBorderColorButton = new JButton();
        m_setBorderColorButton.setEnabled(false);
        m_setBorderColorButton.setText("Set border color");
        panel30.add(m_setBorderColorButton);
        final JPanel spacer2 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel25.add(spacer2, gbc);
        final JLabel label6 = new JLabel();
        label6.setText("Obj variable");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel25.add(label6, gbc);
        final JPanel panel31 = new JPanel();
        panel31.setLayout(new GridBagLayout());
        panel31.setMinimumSize(new Dimension(468, 33));
        panel31.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel25.add(panel31, gbc);
        final JScrollPane scrollPane6 = new JScrollPane();
        scrollPane6.setHorizontalScrollBarPolicy(31);
        scrollPane6.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel31.add(scrollPane6, gbc);
        m_objVariableTextField = new JTextField();
        m_objVariableTextField.setColumns(40);
        scrollPane6.setViewportView(m_objVariableTextField);
        final JPanel panel32 = new JPanel();
        panel32.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.BOTH;
        panel25.add(panel32, gbc);
        m_objIsTakable = new JCheckBox();
        m_objIsTakable.setText("Can be taken to the inventory");
        panel32.add(m_objIsTakable);
        m_suppressDsc = new JCheckBox();
        m_suppressDsc.setText("Suppress dsc");
        panel32.add(m_suppressDsc);
        m_imageInScene = new JCheckBox();
        m_imageInScene.setText("Image in scene");
        panel32.add(m_imageInScene);
        m_imageInInventory = new JCheckBox();
        m_imageInInventory.setText("Image in inventory");
        panel32.add(m_imageInInventory);
        m_animatedImageCheckBox = new JCheckBox();
        m_animatedImageCheckBox.setText("Animated image");
        panel32.add(m_animatedImageCheckBox);
        final JPanel panel33 = new JPanel();
        panel33.setLayout(new GridBagLayout());
        panel33.setMinimumSize(new Dimension(468, 33));
        panel33.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel25.add(panel33, gbc);
        final JScrollPane scrollPane7 = new JScrollPane();
        scrollPane7.setHorizontalScrollBarPolicy(31);
        scrollPane7.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel33.add(scrollPane7, gbc);
        m_objConstraintTextField = new JTextField();
        m_objConstraintTextField.setColumns(40);
        scrollPane7.setViewportView(m_objConstraintTextField);
        final JLabel label7 = new JLabel();
        label7.setText("Obj constraint");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        panel25.add(label7, gbc);
        final JPanel panel34 = new JPanel();
        panel34.setLayout(new GridBagLayout());
        panel34.setMinimumSize(new Dimension(468, 33));
        panel34.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel25.add(panel34, gbc);
        final JScrollPane scrollPane8 = new JScrollPane();
        scrollPane8.setHorizontalScrollBarPolicy(31);
        scrollPane8.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel34.add(scrollPane8, gbc);
        m_objCommonToTextField = new JTextField();
        m_objCommonToTextField.setColumns(40);
        scrollPane8.setViewportView(m_objCommonToTextField);
        final JLabel label8 = new JLabel();
        label8.setText("Common To");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.anchor = GridBagConstraints.EAST;
        panel25.add(label8, gbc);
        final JPanel panel35 = new JPanel();
        panel35.setLayout(new GridBagLayout());
        panel35.setMinimumSize(new Dimension(468, 33));
        panel35.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel25.add(panel35, gbc);
        final JScrollPane scrollPane9 = new JScrollPane();
        scrollPane9.setHorizontalScrollBarPolicy(31);
        scrollPane9.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel35.add(scrollPane9, gbc);
        m_objDefaultTagTextField = new JTextField();
        m_objDefaultTagTextField.setColumns(40);
        scrollPane9.setViewportView(m_objDefaultTagTextField);
        final JLabel label9 = new JLabel();
        label9.setText("Default tag");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.EAST;
        panel25.add(label9, gbc);
        final JPanel panel36 = new JPanel();
        panel36.setLayout(new GridBagLayout());
        m_tabbedPane1.addTab("Graphical", panel36);
        final JPanel spacer3 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel36.add(spacer3, gbc);
        final JLabel label10 = new JLabel();
        label10.setText("Morph over");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel36.add(label10, gbc);
        final JPanel spacer4 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel36.add(spacer4, gbc);
        final JPanel panel37 = new JPanel();
        panel37.setLayout(new GridBagLayout());
        panel37.setMinimumSize(new Dimension(468, 33));
        panel37.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel36.add(panel37, gbc);
        final JScrollPane scrollPane10 = new JScrollPane();
        scrollPane10.setHorizontalScrollBarPolicy(31);
        scrollPane10.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel37.add(scrollPane10, gbc);
        m_morphOver = new JTextField();
        m_morphOver.setColumns(40);
        scrollPane10.setViewportView(m_morphOver);
        final JPanel panel38 = new JPanel();
        panel38.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        panel36.add(panel38, gbc);
        m_objIsGraphical = new JCheckBox();
        m_objIsGraphical.setText("Graphical");
        panel38.add(m_objIsGraphical);
        m_objIsPreserved = new JCheckBox();
        m_objIsPreserved.setText("Preserved");
        panel38.add(m_objIsPreserved);
        m_objIsCollapsable = new JCheckBox();
        m_objIsCollapsable.setText("Collapsable");
        panel38.add(m_objIsCollapsable);
        m_objIsClearUnderTooltip = new JCheckBox();
        m_objIsClearUnderTooltip.setText("Clear under tooltip");
        panel38.add(m_objIsClearUnderTooltip);
        final JPanel panel39 = new JPanel();
        panel39.setLayout(new GridBagLayout());
        panel39.setMinimumSize(new Dimension(468, 33));
        panel39.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel36.add(panel39, gbc);
        final JScrollPane scrollPane11 = new JScrollPane();
        scrollPane11.setHorizontalScrollBarPolicy(31);
        scrollPane11.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel39.add(scrollPane11, gbc);
        m_morphOut = new JTextField();
        m_morphOut.setColumns(40);
        scrollPane11.setViewportView(m_morphOut);
        final JLabel label11 = new JLabel();
        label11.setText("Morph out");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel36.add(label11, gbc);
        final JPanel panel40 = new JPanel();
        panel40.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel36.add(panel40, gbc);
        final JPanel panel41 = new JPanel();
        panel41.setLayout(new BorderLayout(0, 0));
        panel41.setMinimumSize(new Dimension(300, 150));
        panel41.setPreferredSize(new Dimension(300, 150));
        panel40.add(panel41);
        panel41.setBorder(BorderFactory.createTitledBorder("Movement direction"));
        final JPanel panel42 = new JPanel();
        panel42.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel41.add(panel42, BorderLayout.NORTH);
        m_movementDirectionTop = new JRadioButton();
        m_movementDirectionTop.setMinimumSize(new Dimension(90, 26));
        m_movementDirectionTop.setPreferredSize(new Dimension(90, 26));
        m_movementDirectionTop.setText("Top");
        panel42.add(m_movementDirectionTop);
        final JPanel panel43 = new JPanel();
        panel43.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel41.add(panel43, BorderLayout.SOUTH);
        m_movementDirectionBottom = new JRadioButton();
        m_movementDirectionBottom.setMinimumSize(new Dimension(90, 26));
        m_movementDirectionBottom.setPreferredSize(new Dimension(90, 26));
        m_movementDirectionBottom.setText("Bottom");
        panel43.add(m_movementDirectionBottom);
        final JPanel panel44 = new JPanel();
        panel44.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel41.add(panel44, BorderLayout.CENTER);
        m_movementDirectionNone = new JRadioButton();
        m_movementDirectionNone.setMinimumSize(new Dimension(90, 26));
        m_movementDirectionNone.setPreferredSize(new Dimension(90, 26));
        m_movementDirectionNone.setText("None");
        panel44.add(m_movementDirectionNone);
        final JPanel panel45 = new JPanel();
        panel45.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel41.add(panel45, BorderLayout.WEST);
        m_movementDirectionLeft = new JRadioButton();
        m_movementDirectionLeft.setMinimumSize(new Dimension(60, 26));
        m_movementDirectionLeft.setPreferredSize(new Dimension(60, 26));
        m_movementDirectionLeft.setText("Left");
        panel45.add(m_movementDirectionLeft);
        final JPanel panel46 = new JPanel();
        panel46.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel41.add(panel46, BorderLayout.EAST);
        m_movementDirectionRight = new JRadioButton();
        m_movementDirectionRight.setMinimumSize(new Dimension(60, 26));
        m_movementDirectionRight.setPreferredSize(new Dimension(60, 26));
        m_movementDirectionRight.setText("Right");
        panel46.add(m_movementDirectionRight);
        final JPanel panel47 = new JPanel();
        panel47.setLayout(new BorderLayout(0, 0));
        panel47.setMinimumSize(new Dimension(300, 150));
        panel47.setPreferredSize(new Dimension(300, 150));
        panel40.add(panel47);
        panel47.setBorder(BorderFactory.createTitledBorder("Effect"));
        final JPanel panel48 = new JPanel();
        panel48.setLayout(new BorderLayout(0, 0));
        panel47.add(panel48, BorderLayout.CENTER);
        final JPanel panel49 = new JPanel();
        panel49.setLayout(new GridBagLayout());
        panel48.add(panel49, BorderLayout.CENTER);
        m_effectMoveIn = new JRadioButton();
        m_effectMoveIn.setMinimumSize(new Dimension(90, 26));
        m_effectMoveIn.setPreferredSize(new Dimension(90, 26));
        m_effectMoveIn.setText("movein");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel49.add(m_effectMoveIn, gbc);
        final JPanel spacer5 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel49.add(spacer5, gbc);
        final JPanel spacer6 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel49.add(spacer6, gbc);
        m_effectMoveOut = new JRadioButton();
        m_effectMoveOut.setMinimumSize(new Dimension(90, 26));
        m_effectMoveOut.setPreferredSize(new Dimension(90, 26));
        m_effectMoveOut.setText("moveout");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel49.add(m_effectMoveOut, gbc);
        m_effectFadeIn = new JRadioButton();
        m_effectFadeIn.setMinimumSize(new Dimension(90, 26));
        m_effectFadeIn.setPreferredSize(new Dimension(90, 26));
        m_effectFadeIn.setText("fadein");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel49.add(m_effectFadeIn, gbc);
        m_effectFadeOut = new JRadioButton();
        m_effectFadeOut.setMinimumSize(new Dimension(90, 26));
        m_effectFadeOut.setPreferredSize(new Dimension(90, 26));
        m_effectFadeOut.setText("fadeout");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        panel49.add(m_effectFadeOut, gbc);
        m_effectZoomIn = new JRadioButton();
        m_effectZoomIn.setMinimumSize(new Dimension(90, 26));
        m_effectZoomIn.setPreferredSize(new Dimension(90, 26));
        m_effectZoomIn.setText("zoomin");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel49.add(m_effectZoomIn, gbc);
        m_effectZoomOut = new JRadioButton();
        m_effectZoomOut.setMinimumSize(new Dimension(90, 26));
        m_effectZoomOut.setPreferredSize(new Dimension(90, 26));
        m_effectZoomOut.setText("zoomout");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        panel49.add(m_effectZoomOut, gbc);
        m_effectNone = new JRadioButton();
        m_effectNone.setMinimumSize(new Dimension(90, 26));
        m_effectNone.setPreferredSize(new Dimension(90, 26));
        m_effectNone.setText("none");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 2;
        panel49.add(m_effectNone, gbc);
        final JPanel panel50 = new JPanel();
        panel50.setLayout(new GridBagLayout());
        panel50.setMinimumSize(new Dimension(468, 33));
        panel50.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel36.add(panel50, gbc);
        final JScrollPane scrollPane12 = new JScrollPane();
        scrollPane12.setHorizontalScrollBarPolicy(31);
        scrollPane12.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel50.add(scrollPane12, gbc);
        m_offset = new JTextField();
        m_offset.setColumns(40);
        scrollPane12.setViewportView(m_offset);
        final JLabel label12 = new JLabel();
        label12.setText("Offset");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        panel36.add(label12, gbc);
        final JPanel panel51 = new JPanel();
        panel51.setLayout(new BorderLayout(0, 0));
        panel1.add(panel51, BorderLayout.CENTER);
        label1.setLabelFor(m_objDispTextField);
        label2.setLabelFor(m_objTextTextArea);
        label3.setLabelFor(m_objActTextTextArea);
        label4.setLabelFor(m_objIdTextField);
        label5.setLabelFor(m_objNameTextField);
        label6.setLabelFor(m_objVariableTextField);
        label7.setLabelFor(m_objConstraintTextField);
        label8.setLabelFor(m_objCommonToTextField);
        label9.setLabelFor(m_objDefaultTagTextField);
        label10.setLabelFor(m_morphOver);
        label11.setLabelFor(m_morphOut);
        label12.setLabelFor(m_offset);
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(m_movementDirectionNone);
        buttonGroup.add(m_movementDirectionNone);
        buttonGroup.add(m_movementDirectionBottom);
        buttonGroup.add(m_movementDirectionTop);
        buttonGroup.add(m_movementDirectionRight);
        buttonGroup.add(m_movementDirectionLeft);
        buttonGroup = new ButtonGroup();
        buttonGroup.add(m_effectNone);
        buttonGroup.add(m_effectNone);
        buttonGroup.add(m_effectFadeIn);
        buttonGroup.add(m_effectMoveOut);
        buttonGroup.add(m_effectZoomIn);
        buttonGroup.add(m_effectMoveIn);
        buttonGroup.add(m_effectZoomOut);
        buttonGroup.add(m_effectFadeOut);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
