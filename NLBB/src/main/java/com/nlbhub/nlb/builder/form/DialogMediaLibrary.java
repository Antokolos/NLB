/**
 * @(#)DialogMediaLibrary.java
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
import com.nlbhub.nlb.api.MediaFile;
import com.nlbhub.nlb.builder.model.ListSingleSelectionModel;
import com.nlbhub.nlb.builder.model.MediaFileModelSwing;
import com.nlbhub.nlb.builder.util.ImageHelper;
import com.nlbhub.nlb.builder.util.WheelScaleListener;
import com.nlbhub.nlb.builder.util.Zoomer;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBFileManipulationException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.util.StringHelper;
import org.jdesktop.swingx.JXImageView;
import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.table.TableColumnExt;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class DialogMediaLibrary extends JDialog {
    private final static String PH_IMAGE_EXT = ".png";
    private final static File PLACEHOLDER_IMAGE = new File("template/sample" + PH_IMAGE_EXT);
    private final static String PH_SOUND_EXT = ".ogg";
    private final static File PLACEHOLDER_SOUND = new File("template/sample" + PH_SOUND_EXT);
    private final String[] m_itemsSelectedByDefault;
    private final JFileChooser m_fileChooser = new JFileChooser();
    private MediaFileModelSwing m_mediaFileModelSwing;
    private Zoomer m_zoomer;
    private String m_selectedFileName;
    private boolean m_isCanceled = false;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JPanel m_imagePreviewPanel;
    private JButton m_buttonAdd;
    private JXTable m_mediaFileList;
    private JXImageView m_imageView;
    private JButton m_buttonRemove;
    private JButton m_noneButton;
    private JButton m_voidButton;
    private JButton m_buttonPlaceholder;
    private JButton m_buttonSearchUsages;
    private JButton m_buttonZoomIn;
    private JButton m_buttonZoomOut;

    public DialogMediaLibrary(
            final MainFrame mainFrame,
            final NonLinearBookFacade nonLinearBookFacade,
            final MediaFile.Type mediaType,
            String[] itemsSelectedByDefault
    ) {
        final DialogMediaLibrary self = this;
        setContentPane(contentPane);
        m_zoomer = new Zoomer(m_imageView);
        m_mediaFileModelSwing = new MediaFileModelSwing(nonLinearBookFacade, mediaType);
        m_mediaFileList.setModel(m_mediaFileModelSwing);
        ListSingleSelectionModel listSingleSelectionModel = new ListSingleSelectionModel();
        listSingleSelectionModel.addListSelectionListener(
                new ListSelectionListener() {
                    @Override
                    public void valueChanged(ListSelectionEvent e) {
                        ListSelectionModel lsm = (ListSelectionModel) e.getSource();
                        if (mediaType != MediaFile.Type.Image) {
                            return;
                        }
                        try {
                            if (lsm.isSelectionEmpty()) {
                                // TODO: should clear
                            } else {
                                // Find out which indexes are selected.
                                int minIndex = lsm.getMinSelectionIndex();
                                int maxIndex = lsm.getMaxSelectionIndex();
                                for (int i = minIndex; i <= maxIndex; i++) {
                                    if (lsm.isSelectedIndex(i)) {
                                        File imageFile = (
                                                new File(
                                                        nonLinearBookFacade.getNlb().getImagesDir(),
                                                        (String) m_mediaFileModelSwing.getValueAt(i, 0)
                                                )
                                        );
                                        m_imageView.setImage(imageFile);
                                        m_imageView.setScale(ImageHelper.getScaleToFit(m_imageView, imageFile));
                                        break;
                                    }
                                }
                            }
                        } catch (IOException ignore) {
                            // ignore
                        }
                    }
                }
        );
        m_itemsSelectedByDefault = itemsSelectedByDefault;
        m_mediaFileList.setSelectionModel(listSingleSelectionModel);
        TableColumnExt flagColumn = m_mediaFileList.getColumnExt(1);
        JComboBox<String> flags = new JComboBox<>();
        flags.addItem(Constants.YES);
        flags.addItem(Constants.NO);
        flagColumn.setCellEditor(new DefaultCellEditor(flags));
        TableColumnExt redirectColumn = m_mediaFileList.getColumnExt(2);
        JComboBox<String> redirects = new JComboBox<>();
        for (String value : m_mediaFileModelSwing.getRedirectsValues()) {
            redirects.addItem(value);
        }
        redirectColumn.setCellEditor(new DefaultCellEditor(redirects));
        TableColumnExt constraintColumn = m_mediaFileList.getColumnExt(3);
        JComboBox<String> constraints = new JComboBox<>();
        for (String value : m_mediaFileModelSwing.getConstraintsValues()) {
            constraints.addItem(value);
        }
        constraintColumn.setCellEditor(new DefaultCellEditor(constraints));
        TableColumnExt presetColumn = m_mediaFileList.getColumnExt(4);
        JComboBox<String> presets = new JComboBox<>();
        for (String value : m_mediaFileModelSwing.getPresetsValues()) {
            presets.addItem(value);
        }
        presetColumn.setCellEditor(new DefaultCellEditor(presets));
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        m_noneButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onNone();
            }
        });

        m_voidButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onVoid();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        m_buttonSearchUsages.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int selectedRow = m_mediaFileList.getSelectedRow();
                DialogSearch dialog = (
                        new DialogSearch(
                                mainFrame,
                                nonLinearBookFacade.getNlb(),
                                Constants.MAIN_MODULE_NAME,
                                (selectedRow == -1)
                                        ? Constants.EMPTY_STRING
                                        : (String) m_mediaFileModelSwing.getValueAt(selectedRow, 0)
                        )
                );
                dialog.showDialog();
            }
        });

        m_buttonPlaceholder.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    String placeholderName = JOptionPane.showInputDialog("Placeholder name:");
                    if (StringHelper.notEmpty(placeholderName)) {
                        switch (mediaType) {
                            case Image:
                                File tempFile = File.createTempFile("ph_img", "dat");
                                tempFile.deleteOnExit();
                                if (ImageHelper.textToGraphics(placeholderName, tempFile)) {
                                    nonLinearBookFacade.addImageFile(tempFile, placeholderName + PH_IMAGE_EXT);
                                } else {
                                    nonLinearBookFacade.addImageFile(PLACEHOLDER_IMAGE, placeholderName + PH_IMAGE_EXT);
                                }
                                break;
                            case Sound:
                                nonLinearBookFacade.addSoundFile(PLACEHOLDER_SOUND, placeholderName + PH_SOUND_EXT);
                                break;
                        }
                        m_mediaFileList.updateUI();
                    }
                } catch (NLBConsistencyException | NLBIOException | NLBVCSException | NLBFileManipulationException | IOException ex) {
                    JOptionPane.showMessageDialog(
                            self,
                            "Error while adding: " + ex.toString()
                    );
                }
            }
        });

        m_buttonAdd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    File mediaFile = chooseMediaFile();
                    if (mediaFile != null) {
                        switch (mediaType) {
                            case Image:
                                nonLinearBookFacade.addImageFile(mediaFile, null);
                                break;
                            case Sound:
                                nonLinearBookFacade.addSoundFile(mediaFile, null);
                                break;
                        }
                    }
                    m_mediaFileList.updateUI();
                } catch (NLBFileManipulationException | NLBIOException | NLBConsistencyException | NLBVCSException ex) {
                    JOptionPane.showMessageDialog(
                            self,
                            "Error while adding: " + ex.toString()
                    );
                }
            }
        });

        m_buttonRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    int selectedRow = m_mediaFileList.getSelectedRow();
                    String mediaFileName = (String) m_mediaFileModelSwing.getValueAt(selectedRow, 0);
                    switch (mediaType) {
                        case Image:
                            nonLinearBookFacade.removeImageFile(mediaFileName);
                            break;
                        case Sound:
                            nonLinearBookFacade.removeSoundFile(mediaFileName);
                            break;
                    }
                    m_mediaFileList.updateUI();
                } catch (NLBFileManipulationException | NLBIOException | NLBConsistencyException ex) {
                    JOptionPane.showMessageDialog(
                            self,
                            "Error while removing: " + ex.toString()
                    );
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

        switch (mediaType) {
            case Image:
                m_voidButton.setVisible(false);
                break;
            case Sound:
                m_imagePreviewPanel.setVisible(false);
                break;
        }
    }

    private void selectItems(String[] itemsSelectedByDefault) {
        m_mediaFileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        //select needed lines
        ListSelectionModel model = m_mediaFileList.getSelectionModel();
        model.clearSelection();
        Integer[] focusedLines = getFocusedLines(Arrays.asList(itemsSelectedByDefault));
        for (Integer actualRow : focusedLines) {
            model.addSelectionInterval(actualRow, actualRow);
        }
        //then select column if needed
        //table.setColumnSelectionInterval(from, to)
        //at the end request focus
        m_mediaFileList.requestFocusInWindow();
    }

    private Integer[] getFocusedLines(Collection<String> itemsSelectedByDefault) {
        List<Integer> selectedLines = new ArrayList<>();
        for (int i = 0; i < m_mediaFileModelSwing.getRowCount(); i++) {
            String value = (String) m_mediaFileModelSwing.getValueAt(i, 0);
            if (itemsSelectedByDefault.contains(value)) {
                selectedLines.add(i);
            }
        }
        return selectedLines.toArray(new Integer[selectedLines.size()]);
    }

    private File chooseMediaFile() {
        int returnVal = m_fileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            return m_fileChooser.getSelectedFile();
        }

        return null;
    }

    public void showDialog() {
        pack();
        // this solves the problem where the dialog was not getting
        // focus the second time it was displayed
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                selectItems(m_itemsSelectedByDefault);
                buttonOK.requestFocusInWindow();
            }
        });
        setVisible(true);
    }

    private String getSelectedFileNames(int[] selectedRows) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (int selectedRow : selectedRows) {
            if (first) {
                first = false;
            } else {
                result.append(Constants.MEDIA_FILE_NAME_SEP);
            }
            result.append((String) m_mediaFileModelSwing.getValueAt(selectedRow, 0));
        }
        return result.toString();
    }

    private void onOK() {
        int[] selectedRows = m_mediaFileList.getSelectedRows();
        m_selectedFileName = (selectedRows.length == 0) ? null : getSelectedFileNames(selectedRows);
        dispose();
    }

    private void onNone() {
        m_selectedFileName = Constants.EMPTY_STRING;
        dispose();
    }

    private void onVoid() {
        m_selectedFileName = Constants.VOID;
        dispose();
    }

    private void onCancel() {
        m_isCanceled = true;
        m_selectedFileName = null;
        dispose();
    }

    public boolean isCanceled() {
        return m_isCanceled;
    }

    public String getSelectedFileName() {
        return m_selectedFileName;
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
        contentPane.add(panel1, BorderLayout.SOUTH);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        panel1.add(panel2, BorderLayout.EAST);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridBagLayout());
        panel2.add(panel3);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(panel4, gbc);
        buttonOK = new JButton();
        buttonOK.setMaximumSize(new Dimension(85, 25));
        buttonOK.setMinimumSize(new Dimension(85, 25));
        buttonOK.setPreferredSize(new Dimension(85, 25));
        buttonOK.setText("OK");
        panel4.add(buttonOK);
        m_noneButton = new JButton();
        m_noneButton.setMaximumSize(new Dimension(85, 25));
        m_noneButton.setMinimumSize(new Dimension(85, 25));
        m_noneButton.setPreferredSize(new Dimension(85, 25));
        m_noneButton.setText("None");
        panel4.add(m_noneButton);
        m_voidButton = new JButton();
        m_voidButton.setMaximumSize(new Dimension(85, 25));
        m_voidButton.setMinimumSize(new Dimension(85, 25));
        m_voidButton.setPreferredSize(new Dimension(85, 25));
        m_voidButton.setText("Void");
        panel4.add(m_voidButton);
        buttonCancel = new JButton();
        buttonCancel.setMaximumSize(new Dimension(85, 25));
        buttonCancel.setMinimumSize(new Dimension(85, 25));
        buttonCancel.setPreferredSize(new Dimension(85, 25));
        buttonCancel.setText("Cancel");
        panel4.add(buttonCancel);
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        panel1.add(panel5, BorderLayout.WEST);
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridBagLayout());
        panel5.add(panel6);
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel6.add(panel7, gbc);
        m_buttonPlaceholder = new JButton();
        m_buttonPlaceholder.setMaximumSize(new Dimension(110, 25));
        m_buttonPlaceholder.setMinimumSize(new Dimension(110, 25));
        m_buttonPlaceholder.setPreferredSize(new Dimension(110, 25));
        m_buttonPlaceholder.setText("Placeholder");
        panel7.add(m_buttonPlaceholder);
        m_buttonAdd = new JButton();
        m_buttonAdd.setMaximumSize(new Dimension(85, 25));
        m_buttonAdd.setMinimumSize(new Dimension(85, 25));
        m_buttonAdd.setPreferredSize(new Dimension(85, 25));
        m_buttonAdd.setText("Add...");
        panel7.add(m_buttonAdd);
        m_buttonRemove = new JButton();
        m_buttonRemove.setMaximumSize(new Dimension(85, 25));
        m_buttonRemove.setMinimumSize(new Dimension(85, 25));
        m_buttonRemove.setPreferredSize(new Dimension(85, 25));
        m_buttonRemove.setText("Remove");
        panel7.add(m_buttonRemove);
        m_buttonSearchUsages = new JButton();
        m_buttonSearchUsages.setMaximumSize(new Dimension(115, 25));
        m_buttonSearchUsages.setMinimumSize(new Dimension(115, 25));
        m_buttonSearchUsages.setPreferredSize(new Dimension(115, 25));
        m_buttonSearchUsages.setText("Search usages...");
        panel7.add(m_buttonSearchUsages);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new BorderLayout(0, 0));
        contentPane.add(panel8, BorderLayout.CENTER);
        m_imagePreviewPanel = new JPanel();
        m_imagePreviewPanel.setLayout(new BorderLayout(0, 0));
        m_imagePreviewPanel.setMinimumSize(new Dimension(220, 220));
        m_imagePreviewPanel.setPreferredSize(new Dimension(220, 220));
        panel8.add(m_imagePreviewPanel, BorderLayout.WEST);
        m_imageView = new JXImageView();
        m_imagePreviewPanel.add(m_imageView, BorderLayout.CENTER);
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        m_imagePreviewPanel.add(panel9, BorderLayout.NORTH);
        m_buttonZoomIn = new JButton();
        m_buttonZoomIn.setText("+");
        panel9.add(m_buttonZoomIn);
        m_buttonZoomOut = new JButton();
        m_buttonZoomOut.setText("―");
        panel9.add(m_buttonZoomOut);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new BorderLayout(0, 0));
        panel8.add(panel10, BorderLayout.CENTER);
        final JScrollPane scrollPane1 = new JScrollPane();
        panel10.add(scrollPane1, BorderLayout.CENTER);
        m_mediaFileList = new JXTable();
        m_mediaFileList.setSortable(false);
        m_mediaFileList.setVisibleRowCount(10);
        m_mediaFileList.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane1.setViewportView(m_mediaFileList);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
