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
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class DialogObjProperties extends JDialog implements NLBObserver {
    private final String m_observerId;
    private Zoomer m_zoomer;
    private Obj m_obj;
    private NonLinearBookFacade m_nlbFacade;
    private MultiLangString m_objDisplayNames;
    private MultiLangString m_objTexts;
    private MultiLangString m_objActTexts;
    private MultiLangString m_objNouseTexts;
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
    private JCheckBox m_callback;
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
    private JRadioButton m_coordsOriginLeftTop;
    private JRadioButton m_coordsOriginLeftMiddle;
    private JRadioButton m_coordsOriginLeftBottom;
    private JRadioButton m_coordsOriginMiddleTop;
    private JRadioButton m_coordsOriginMiddleMiddle;
    private JRadioButton m_coordsOriginMiddleBottom;
    private JRadioButton m_coordsOriginRightTop;
    private JRadioButton m_coordsOriginRightMiddle;
    private JRadioButton m_coordsOriginRightBottom;
    private JRadioButton m_effectOverlap;
    private JSpinner m_spinnerMaxFrame;
    private JTextArea m_objDispTextArea;
    private JCheckBox m_objIsActOnKey;
    private JSpinner m_spinnerPreloadFrames;
    private JCheckBox m_objLoadOnce;
    private JCheckBox m_objIsCacheText;
    private JCheckBox m_objIsLooped;
    private JSpinner m_spinnerStartFrame;
    private JCheckBox m_objIsNoRedrawOnAct;
    private JTextArea m_objNouseTextTextArea;
    private JCheckBox m_objIsShowOnCursor;
    private JSpinner m_spinnerPauseFrames;

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
                m_objDispTextArea.setText(m_objDisplayNames.get(selectedLanguage));
                m_objTextTextArea.setText(m_objTexts.get(selectedLanguage));
                m_objActTextTextArea.setText(m_objActTexts.get(selectedLanguage));
                m_objNouseTextTextArea.setText(m_objNouseTexts.get(selectedLanguage));
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
        m_objDisplayNames.put(m_selectedLanguage, m_objDispTextArea.getText());
        m_objTexts.put(m_selectedLanguage, m_objTextTextArea.getText());
        m_objActTexts.put(m_selectedLanguage, m_objActTextTextArea.getText());
        m_objNouseTexts.put(m_selectedLanguage, m_objNouseTextTextArea.getText());
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
        m_objDispTextArea.setText(obj.getDisp());
        m_objVariableTextField.setText(variable != null ? variable.getName() : "");
        m_objDefaultTagTextField.setText(deftag != null ? deftag.getValue() : "");
        m_objConstraintTextField.setText(constraint != null ? constraint.getValue() : "");
        m_objCommonToTextField.setText(commonTo != null ? commonTo.getName() : "");
        m_objTextTextArea.setText(obj.getText());
        m_objActTextTextArea.setText(obj.getActText());
        m_objNouseTextTextArea.setText(obj.getNouseText());
        m_suppressDsc.setSelected(obj.isSuppressDsc());
        m_objIsTakable.setSelected(obj.isTakable());
        m_callback.setSelected(obj.isCallback());
        m_objIsGraphical.setSelected(obj.isGraphical());
        m_objIsShowOnCursor.setSelected(obj.isShowOnCursor());
        m_objIsPreserved.setSelected(obj.isPreserved());
        m_objLoadOnce.setSelected(obj.isLoadOnce());
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
        m_spinnerStartFrame.setValue(obj.getStartFrame());
        m_spinnerMaxFrame.setValue(obj.getMaxFrame());
        m_spinnerPreloadFrames.setValue(obj.getPreloadFrames());
        m_spinnerPauseFrames.setValue(obj.getPauseFrames());
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
            case Overlap:
                m_effectOverlap.setSelected(true);
                break;
            case None:
            default:
                m_effectNone.setSelected(true);
        }
        switch (obj.getCoordsOrigin()) {
            case LeftTop:
                m_coordsOriginLeftTop.setSelected(true);
                break;
            case MiddleTop:
                m_coordsOriginMiddleTop.setSelected(true);
                break;
            case RightTop:
                m_coordsOriginRightTop.setSelected(true);
                break;
            case LeftMiddle:
                m_coordsOriginLeftMiddle.setSelected(true);
                break;
            case MiddleMiddle:
                m_coordsOriginMiddleMiddle.setSelected(true);
                break;
            case RightMiddle:
                m_coordsOriginRightMiddle.setSelected(true);
                break;
            case LeftBottom:
                m_coordsOriginLeftBottom.setSelected(true);
                break;
            case MiddleBottom:
                m_coordsOriginMiddleBottom.setSelected(true);
                break;
            case RightBottom:
                m_coordsOriginRightBottom.setSelected(true);
                break;
            default:
                m_coordsOriginLeftTop.setSelected(true);
        }
        m_objIsClearUnderTooltip.setSelected(obj.isClearUnderTooltip());
        m_objIsActOnKey.setSelected(obj.isActOnKey());
        m_objIsCacheText.setSelected(obj.isCacheText());
        m_objIsLooped.setSelected(obj.isLooped());
        m_objIsNoRedrawOnAct.setSelected(obj.isNoRedrawOnAct());
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
        m_objNouseTexts = obj.getNouseTexts();
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
                m_objNouseTexts,
                m_objIsGraphical.isSelected(),
                m_objIsShowOnCursor.isSelected(),
                m_objIsPreserved.isSelected(),
                m_objLoadOnce.isSelected(),
                m_objIsCollapsable.isSelected(),
                m_offset.getText(),
                getMovementDirection(),
                getEffect(),
                Integer.parseInt(m_spinnerStartFrame.getValue().toString()),
                Integer.parseInt(m_spinnerMaxFrame.getValue().toString()),
                Integer.parseInt(m_spinnerPreloadFrames.getValue().toString()),
                Integer.parseInt(m_spinnerPauseFrames.getValue().toString()),
                getCoordsOrigin(),
                m_objIsClearUnderTooltip.isSelected(),
                m_objIsActOnKey.isSelected(),
                m_objIsCacheText.isSelected(),
                m_objIsLooped.isSelected(),
                m_objIsNoRedrawOnAct.isSelected(),
                m_morphOver.getText(),
                m_morphOut.getText(),
                m_objIsTakable.isSelected(),
                m_callback.isSelected(),
                m_imageInScene.isSelected(),
                m_imageInInventory.isSelected()
        );
        m_nlbFacade.removeObserver(m_observerId);
        dispose();
    }

    private Obj.CoordsOrigin getCoordsOrigin() {
        if (m_coordsOriginLeftTop.isSelected()) {
            return Obj.CoordsOrigin.LeftTop;
        } else if (m_coordsOriginMiddleTop.isSelected()) {
            return Obj.CoordsOrigin.MiddleTop;
        } else if (m_coordsOriginRightTop.isSelected()) {
            return Obj.CoordsOrigin.RightTop;
        } else if (m_coordsOriginLeftMiddle.isSelected()) {
            return Obj.CoordsOrigin.LeftMiddle;
        } else if (m_coordsOriginMiddleMiddle.isSelected()) {
            return Obj.CoordsOrigin.MiddleMiddle;
        } else if (m_coordsOriginRightMiddle.isSelected()) {
            return Obj.CoordsOrigin.RightMiddle;
        } else if (m_coordsOriginLeftBottom.isSelected()) {
            return Obj.CoordsOrigin.LeftBottom;
        } else if (m_coordsOriginMiddleBottom.isSelected()) {
            return Obj.CoordsOrigin.MiddleBottom;
        } else if (m_coordsOriginRightBottom.isSelected()) {
            return Obj.CoordsOrigin.RightBottom;
        } else {
            return Obj.CoordsOrigin.LeftTop;
        }
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
        } else if (m_effectOverlap.isSelected()) {
            return Obj.Effect.Overlap;
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

}
