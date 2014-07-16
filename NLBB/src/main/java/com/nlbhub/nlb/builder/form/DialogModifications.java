/**
 * @(#)DialogModifications.java
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
import com.nlbhub.nlb.builder.model.ModificationsTableModelSwing;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.table.TableColumnExt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DialogModifications extends JDialog implements NLBObserver {
    private final String m_observerId;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JXTable m_modifications;
    private JButton m_addButton;
    private JButton m_removeButton;
    private JButton m_undoButton;
    private JButton m_redoButton;
    private ModificationsTableModelSwing m_modificationsTableModel;
    private ModifyingItem m_modifyingItem = null;
    private final NonLinearBookFacade m_nlbFacade;

    public DialogModifications(
            final NonLinearBookFacade nlbFacade,
            final ModifyingItem modifyingItem
    ) {
        m_nlbFacade = nlbFacade;
        setModifyingItemProperties(modifyingItem);
        TableColumnExt dataTypeColumn = m_modifications.getColumnExt(1);
        JComboBox<String> comboBoxDataType = new JComboBox<>();
        comboBoxDataType.addItem(Variable.DataType.AUTO.name());
        comboBoxDataType.addItem(Variable.DataType.STRING.name());
        comboBoxDataType.addItem(Variable.DataType.NUMBER.name());
        comboBoxDataType.addItem(Variable.DataType.BOOLEAN.name());
        dataTypeColumn.setCellEditor(new DefaultCellEditor(comboBoxDataType));
        TableColumnExt typeColumn = m_modifications.getColumnExt(3);
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.addItem(Modification.Type.ASSIGN.name());
        comboBox.addItem(Modification.Type.ADD.name());
        comboBox.addItem(Modification.Type.REMOVE.name());
        typeColumn.setCellEditor(new DefaultCellEditor(comboBox));

        setContentPane(contentPane);
        setModal(true);
        setTitle("Modifications");
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

        m_addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                onAdd();
            }
        });

        m_removeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                onRemove();
            }
        });

        m_undoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                nlbFacade.undo(modifyingItem.getId() + Constants.MODIFICATIONS_UNDO_ID_POSTFIX);
                setModifyingItemProperties(modifyingItem);
            }
        });

        m_redoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                nlbFacade.redo(modifyingItem.getId() + Constants.MODIFICATIONS_UNDO_ID_POSTFIX);
                setModifyingItemProperties(modifyingItem);
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

    private void setModifyingItemProperties(final ModifyingItem modifyingItem) {
        m_modifyingItem = modifyingItem;
        m_modificationsTableModel = (
                new ModificationsTableModelSwing(m_nlbFacade.getNlb(), modifyingItem.getModifications())
        );
        m_modifications.setModel(m_modificationsTableModel);
    }

    private void onRemove() {
        m_modificationsTableModel.remove(
                m_modificationsTableModel.getModificationIdsAt(m_modifications.getSelectedRows())
        );
        m_modifications.clearSelection();
        m_modifications.updateUI();
    }

    private void onAdd() {
        m_modificationsTableModel.add(m_modifyingItem);
        m_modifications.updateUI();
    }

    private void onOK() {
        m_nlbFacade.updateModifications(m_modifyingItem, m_modificationsTableModel.getTableModel());
        dispose();
    }

    private void onCancel() {
        m_nlbFacade.redoAll(m_modifyingItem.getId() + Constants.MODIFICATIONS_UNDO_ID_POSTFIX);
        m_nlbFacade.removeObserver(m_observerId);
        dispose();
    }

    @Override
    public void updateView() {
        m_undoButton.setEnabled(
                m_nlbFacade.canUndo(m_modifyingItem.getId() + Constants.MODIFICATIONS_UNDO_ID_POSTFIX)
        );
        m_redoButton.setEnabled(
                m_nlbFacade.canRedo(m_modifyingItem.getId() + Constants.MODIFICATIONS_UNDO_ID_POSTFIX)
        );
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
        panel8.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        panel5.add(panel8, gbc);
        m_addButton = new JButton();
        m_addButton.setMaximumSize(new Dimension(85, 25));
        m_addButton.setMinimumSize(new Dimension(85, 25));
        m_addButton.setPreferredSize(new Dimension(85, 25));
        m_addButton.setText("Add");
        panel8.add(m_addButton);
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        panel5.add(panel9, gbc);
        m_removeButton = new JButton();
        m_removeButton.setMaximumSize(new Dimension(85, 25));
        m_removeButton.setMinimumSize(new Dimension(85, 25));
        m_removeButton.setPreferredSize(new Dimension(85, 25));
        m_removeButton.setText("Remove");
        panel9.add(m_removeButton);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new BorderLayout(0, 0));
        panel3.add(panel10, BorderLayout.CENTER);
        final JScrollPane scrollPane1 = new JScrollPane();
        panel10.add(scrollPane1, BorderLayout.CENTER);
        m_modifications = new JXTable();
        m_modifications.setCellSelectionEnabled(true);
        m_modifications.setColumnSelectionAllowed(true);
        m_modifications.setVisibleRowCount(5);
        m_modifications.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane1.setViewportView(m_modifications);
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new BorderLayout(0, 0));
        panel1.add(panel11, BorderLayout.CENTER);
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        contentPane.add(panel12, BorderLayout.NORTH);
        final JLabel label1 = new JLabel();
        label1.setText("Actions");
        panel12.add(label1);
        final JToolBar toolBar1 = new JToolBar();
        toolBar1.setBorderPainted(false);
        toolBar1.setFloatable(false);
        panel12.add(toolBar1);
        m_undoButton = new JButton();
        m_undoButton.setIcon(new ImageIcon(getClass().getResource("/common/undo.png")));
        m_undoButton.setText("Undo");
        toolBar1.add(m_undoButton);
        m_redoButton = new JButton();
        m_redoButton.setIcon(new ImageIcon(getClass().getResource("/common/redo.png")));
        m_redoButton.setText("Redo");
        toolBar1.add(m_redoButton);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
