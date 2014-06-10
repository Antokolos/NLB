package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.api.NLBObserver;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.builder.model.LinksTableModelSwing;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import org.jdesktop.swingx.JXTable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DialogPageProperties extends JDialog implements NLBObserver {
    private final String m_observerId;
    private Page m_page;
    private NonLinearBookFacade m_nlbFacade;
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
    private JButton m_modificationsButton;
    private JButton m_undoButton;
    private JButton m_redoButton;
    private JTextField m_moduleNameTextField;
    private JTextField m_traverseTextTextField;
    private JTextField m_returnTextTextField;
    private JTextField m_moduleConstraintTextField;
    private JTextField m_returnPageIdTextField;

    public DialogPageProperties(final NonLinearBookFacade nlbFacade, final Page page) {
        m_nlbFacade = nlbFacade;
        setPageProperties(page);
        setTitle("Page properties");
        setContentPane(contentPane);
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
                m_linksTable.updateUI();
            }
        });

        m_deleteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onDeleteSelectedLink();
                m_linksTable.updateUI();
            }
        });

        m_moveUpButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onMoveUpSelectedLink();
                m_linksTable.updateUI();
            }
        });

        m_moveDownButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onMoveDownSelectedLink();
                m_linksTable.updateUI();
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
            }
        });

        m_redoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                nlbFacade.redo(page.getId());
                setPageProperties(page);
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

    private void setPageProperties(Page page) {
        Variable variable;
        Variable moduleConstraint;
        m_page = page;
        variable = m_nlbFacade.getNlb().getVariableById(page.getVarId());
        moduleConstraint = m_nlbFacade.getNlb().getVariableById(page.getModuleConstrId());
        m_pageIdTextField.setText(page.getId());
        m_pageVariableTextField.setText(variable != null ? variable.getName() : "");
        m_moduleConstraintTextField.setText(
                moduleConstraint != null ? moduleConstraint.getValue() : ""
        );
        m_pageCaptionTextField.setText(m_page.getCaption());
        m_useCheckBox.setSelected(m_page.isUseCaption());
        m_pageText.setText(page.getText());

        m_moduleNameTextField.setText(page.getModuleName());
        m_traverseTextTextField.setText(page.getTraverseText());
        m_returnTextTextField.setText(page.getReturnText());
        m_returnPageIdTextField.setText(page.getReturnPageId());

        m_linksTable.setModel(new LinksTableModelSwing(m_page.getLinks()));
    }

    private void onOK() {
        m_nlbFacade.updatePage(
                m_page,
                m_pageVariableTextField.getText(),
                m_pageText.getText(),
                m_pageCaptionTextField.getText(),
                m_useCheckBox.isSelected(),
                m_moduleNameTextField.getText(),
                m_traverseTextTextField.getText(),
                m_returnTextTextField.getText(),
                m_returnPageIdTextField.getText(),
                m_moduleConstraintTextField.getText(),
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
        tabbedPane1.addTab("Properties", panel9);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new BorderLayout(0, 0));
        panel9.add(panel10, BorderLayout.CENTER);
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new GridBagLayout());
        panel10.add(panel11, BorderLayout.CENTER);
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new GridBagLayout());
        panel12.setMinimumSize(new Dimension(143, 33));
        panel12.setPreferredSize(new Dimension(605, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel11.add(panel12, gbc);
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
        m_pageCaptionTextField.setColumns(42);
        m_pageCaptionTextField.setFocusAccelerator('C');
        m_pageCaptionTextField.setHorizontalAlignment(10);
        scrollPane1.setViewportView(m_pageCaptionTextField);
        final JPanel panel13 = new JPanel();
        panel13.setLayout(new GridBagLayout());
        panel13.setMinimumSize(new Dimension(56, 33));
        panel13.setPreferredSize(new Dimension(518, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel11.add(panel13, gbc);
        final JScrollPane scrollPane2 = new JScrollPane();
        scrollPane2.setHorizontalScrollBarPolicy(31);
        scrollPane2.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel13.add(scrollPane2, gbc);
        m_pageVariableTextField = new JTextField();
        m_pageVariableTextField.setColumns(42);
        m_pageVariableTextField.setFocusAccelerator('V');
        scrollPane2.setViewportView(m_pageVariableTextField);
        final JLabel label1 = new JLabel();
        label1.setText("Page caption");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel11.add(label1, gbc);
        final JLabel label2 = new JLabel();
        label2.setHorizontalAlignment(10);
        label2.setHorizontalTextPosition(11);
        label2.setText("Page variable");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel11.add(label2, gbc);
        final JPanel panel14 = new JPanel();
        panel14.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel11.add(panel14, gbc);
        m_useCheckBox = new JCheckBox();
        m_useCheckBox.setHorizontalAlignment(2);
        m_useCheckBox.setText("Use");
        panel14.add(m_useCheckBox, BorderLayout.CENTER);
        final JPanel panel15 = new JPanel();
        panel15.setLayout(new GridBagLayout());
        panel15.setMinimumSize(new Dimension(143, 33));
        panel15.setPreferredSize(new Dimension(605, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel11.add(panel15, gbc);
        final JScrollPane scrollPane3 = new JScrollPane();
        scrollPane3.setEnabled(true);
        scrollPane3.setHorizontalScrollBarPolicy(31);
        scrollPane3.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel15.add(scrollPane3, gbc);
        m_pageIdTextField = new JTextField();
        m_pageIdTextField.setEditable(false);
        m_pageIdTextField.setFocusAccelerator('I');
        scrollPane3.setViewportView(m_pageIdTextField);
        final JLabel label3 = new JLabel();
        label3.setText("Page Id");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel11.add(label3, gbc);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel11.add(spacer1, gbc);
        final JPanel panel16 = new JPanel();
        panel16.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel11.add(panel16, gbc);
        final JPanel panel17 = new JPanel();
        panel17.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel16.add(panel17, BorderLayout.WEST);
        m_setTextColorButton = new JButton();
        m_setTextColorButton.setText("Set text color");
        panel17.add(m_setTextColorButton);
        m_setPageColorButton = new JButton();
        m_setPageColorButton.setText("Set page color");
        panel17.add(m_setPageColorButton);
        m_setBorderColorButton = new JButton();
        m_setBorderColorButton.setText("Set border color");
        panel17.add(m_setBorderColorButton);
        final JPanel panel18 = new JPanel();
        panel18.setLayout(new BorderLayout(0, 0));
        tabbedPane1.addTab("Links", panel18);
        final JPanel panel19 = new JPanel();
        panel19.setLayout(new BorderLayout(0, 0));
        panel18.add(panel19, BorderLayout.CENTER);
        final JPanel panel20 = new JPanel();
        panel20.setLayout(new GridBagLayout());
        panel19.add(panel20, BorderLayout.EAST);
        final JPanel panel21 = new JPanel();
        panel21.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel20.add(panel21, gbc);
        m_moveUpButton = new JButton();
        m_moveUpButton.setPreferredSize(new Dimension(98, 25));
        m_moveUpButton.setText("Move up");
        panel21.add(m_moveUpButton);
        final JPanel panel22 = new JPanel();
        panel22.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel20.add(panel22, gbc);
        m_moveDownButton = new JButton();
        m_moveDownButton.setPreferredSize(new Dimension(98, 25));
        m_moveDownButton.setText("Move down");
        panel22.add(m_moveDownButton);
        final JPanel panel23 = new JPanel();
        panel23.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        panel20.add(panel23, gbc);
        m_editButton = new JButton();
        m_editButton.setPreferredSize(new Dimension(98, 25));
        m_editButton.setText("Edit");
        panel23.add(m_editButton);
        final JPanel panel24 = new JPanel();
        panel24.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        panel20.add(panel24, gbc);
        m_deleteButton = new JButton();
        m_deleteButton.setPreferredSize(new Dimension(98, 25));
        m_deleteButton.setText("Delete");
        panel24.add(m_deleteButton);
        final JPanel panel25 = new JPanel();
        panel25.setLayout(new BorderLayout(0, 0));
        panel19.add(panel25, BorderLayout.CENTER);
        final JScrollPane scrollPane4 = new JScrollPane();
        panel25.add(scrollPane4, BorderLayout.CENTER);
        m_linksTable = new JXTable();
        m_linksTable.setVisibleRowCount(5);
        m_linksTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane4.setViewportView(m_linksTable);
        final JPanel panel26 = new JPanel();
        panel26.setLayout(new BorderLayout(0, 0));
        tabbedPane1.addTab("Modifications", panel26);
        final JPanel panel27 = new JPanel();
        panel27.setLayout(new BorderLayout(0, 0));
        panel26.add(panel27, BorderLayout.CENTER);
        final JPanel panel28 = new JPanel();
        panel28.setLayout(new BorderLayout(0, 0));
        panel26.add(panel28, BorderLayout.EAST);
        final JPanel panel29 = new JPanel();
        panel29.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel28.add(panel29, BorderLayout.CENTER);
        m_modificationsButton = new JButton();
        m_modificationsButton.setText("Modifications...");
        panel29.add(m_modificationsButton);
        final JPanel panel30 = new JPanel();
        panel30.setLayout(new BorderLayout(0, 0));
        tabbedPane1.addTab("Module Information", panel30);
        final JPanel panel31 = new JPanel();
        panel31.setLayout(new GridBagLayout());
        panel30.add(panel31, BorderLayout.CENTER);
        final JPanel panel32 = new JPanel();
        panel32.setLayout(new GridBagLayout());
        panel32.setMinimumSize(new Dimension(56, 33));
        panel32.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel31.add(panel32, gbc);
        final JScrollPane scrollPane5 = new JScrollPane();
        scrollPane5.setHorizontalScrollBarPolicy(31);
        scrollPane5.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel32.add(scrollPane5, gbc);
        m_moduleNameTextField = new JTextField();
        m_moduleNameTextField.setColumns(42);
        scrollPane5.setViewportView(m_moduleNameTextField);
        final JLabel label4 = new JLabel();
        label4.setHorizontalTextPosition(11);
        label4.setText("Module name");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel31.add(label4, gbc);
        final JPanel panel33 = new JPanel();
        panel33.setLayout(new GridBagLayout());
        panel33.setMinimumSize(new Dimension(56, 33));
        panel33.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel31.add(panel33, gbc);
        final JScrollPane scrollPane6 = new JScrollPane();
        scrollPane6.setHorizontalScrollBarPolicy(31);
        scrollPane6.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel33.add(scrollPane6, gbc);
        m_traverseTextTextField = new JTextField();
        m_traverseTextTextField.setColumns(42);
        scrollPane6.setViewportView(m_traverseTextTextField);
        final JLabel label5 = new JLabel();
        label5.setText("Traverse text");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel31.add(label5, gbc);
        final JPanel panel34 = new JPanel();
        panel34.setLayout(new GridBagLayout());
        panel34.setMinimumSize(new Dimension(56, 33));
        panel34.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel31.add(panel34, gbc);
        final JScrollPane scrollPane7 = new JScrollPane();
        scrollPane7.setHorizontalScrollBarPolicy(31);
        scrollPane7.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel34.add(scrollPane7, gbc);
        m_moduleConstraintTextField = new JTextField();
        m_moduleConstraintTextField.setColumns(42);
        scrollPane7.setViewportView(m_moduleConstraintTextField);
        final JLabel label6 = new JLabel();
        label6.setText("Module constraint");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel31.add(label6, gbc);
        final JPanel spacer2 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel31.add(spacer2, gbc);
        final JLabel label7 = new JLabel();
        label7.setText("Return text");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        panel31.add(label7, gbc);
        final JPanel panel35 = new JPanel();
        panel35.setLayout(new GridBagLayout());
        panel35.setMinimumSize(new Dimension(56, 33));
        panel35.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel31.add(panel35, gbc);
        final JScrollPane scrollPane8 = new JScrollPane();
        scrollPane8.setHorizontalScrollBarPolicy(31);
        scrollPane8.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel35.add(scrollPane8, gbc);
        m_returnTextTextField = new JTextField();
        m_returnTextTextField.setColumns(42);
        scrollPane8.setViewportView(m_returnTextTextField);
        final JLabel label8 = new JLabel();
        label8.setText("Return page Id");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.EAST;
        panel31.add(label8, gbc);
        final JPanel panel36 = new JPanel();
        panel36.setLayout(new GridBagLayout());
        panel36.setMinimumSize(new Dimension(56, 33));
        panel36.setPreferredSize(new Dimension(505, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel31.add(panel36, gbc);
        final JScrollPane scrollPane9 = new JScrollPane();
        scrollPane9.setHorizontalScrollBarPolicy(31);
        scrollPane9.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel36.add(scrollPane9, gbc);
        m_returnPageIdTextField = new JTextField();
        m_returnPageIdTextField.setColumns(42);
        scrollPane9.setViewportView(m_returnPageIdTextField);
        final JPanel panel37 = new JPanel();
        panel37.setLayout(new BorderLayout(0, 0));
        panel1.add(panel37, BorderLayout.CENTER);
        final JPanel panel38 = new JPanel();
        panel38.setLayout(new BorderLayout(0, 0));
        panel37.add(panel38, BorderLayout.CENTER);
        final JPanel panel39 = new JPanel();
        panel39.setLayout(new BorderLayout(0, 0));
        panel38.add(panel39, BorderLayout.CENTER);
        final JPanel panel40 = new JPanel();
        panel40.setLayout(new BorderLayout(0, 0));
        panel39.add(panel40, BorderLayout.CENTER);
        final JPanel panel41 = new JPanel();
        panel41.setLayout(new GridBagLayout());
        panel41.setMinimumSize(new Dimension(10, 250));
        panel40.add(panel41, BorderLayout.CENTER);
        panel41.setBorder(BorderFactory.createTitledBorder("Page text"));
        final JScrollPane scrollPane10 = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel41.add(scrollPane10, gbc);
        m_pageText = new JTextArea();
        m_pageText.setColumns(50);
        m_pageText.setFocusAccelerator('T');
        m_pageText.setLineWrap(true);
        m_pageText.setRows(10);
        m_pageText.setWrapStyleWord(true);
        scrollPane10.setViewportView(m_pageText);
        final JPanel panel42 = new JPanel();
        panel42.setLayout(new BorderLayout(0, 0));
        panel1.add(panel42, BorderLayout.NORTH);
        final JToolBar toolBar1 = new JToolBar();
        toolBar1.setBorderPainted(false);
        toolBar1.setFloatable(false);
        panel42.add(toolBar1, BorderLayout.CENTER);
        m_undoButton = new JButton();
        m_undoButton.setIcon(new ImageIcon(getClass().getResource("/common/undo.png")));
        m_undoButton.setText("Undo");
        toolBar1.add(m_undoButton);
        m_redoButton = new JButton();
        m_redoButton.setIcon(new ImageIcon(getClass().getResource("/common/redo.png")));
        m_redoButton.setText("Redo");
        toolBar1.add(m_redoButton);
        label1.setLabelFor(m_pageCaptionTextField);
        label2.setLabelFor(m_pageVariableTextField);
        label3.setLabelFor(m_pageIdTextField);
        label4.setLabelFor(m_moduleNameTextField);
        label5.setLabelFor(m_traverseTextTextField);
        label6.setLabelFor(m_moduleConstraintTextField);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
