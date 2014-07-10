package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.NLBObserver;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.util.MultiLangString;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DialogLinkProperties extends JDialog implements NLBObserver {
    private final String m_observerId;
    private Link m_link;
    private NonLinearBookFacade m_nlbFacade;
    private Variable m_variable;
    private Variable m_constraint;
    private MultiLangString m_linkTexts;
    private String m_selectedLanguage;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField m_linkIdTextField;
    private JTextField m_linkTextTextField;
    private JButton m_button1;
    private JTextField m_linkConstraintsTextField;
    private JTextField m_linkVariableTextField;
    private JButton m_modificationsButton;
    private JButton m_undoButton;
    private JButton m_redoButton;
    private JCheckBox m_autoCheckBox;
    private JComboBox m_languageComboBox;
    private JTabbedPane m_tabbedPane1;

    public DialogLinkProperties(
            final NonLinearBookFacade nlbFacade,
            final Link link
    ) {
        m_nlbFacade = nlbFacade;
        setLinkProperties(link);
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
        m_autoCheckBox.setSelected(link.isAuto());
        m_linkConstraintsTextField.setText(m_constraint != null ? m_constraint.getValue() : "");

        DefaultComboBoxModel<String> languageComboboxModel = new DefaultComboBoxModel<>();
        languageComboboxModel.addElement(Constants.RU);
        languageComboboxModel.addElement(Constants.EN);
        m_languageComboBox.setModel(languageComboboxModel);

        m_linkTexts = link.getTexts();
        m_selectedLanguage = (String) languageComboboxModel.getSelectedItem();
    }

    private void onOK() {
        refreshTextsForCurrentLanguage();
        m_nlbFacade.updateLink(
                m_link,
                m_linkVariableTextField.getText(),
                m_linkConstraintsTextField.getText(),
                m_linkTexts,
                m_autoCheckBox.isSelected()
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
        buttonCancel.setPreferredSize(new Dimension(77, 25));
        buttonCancel.setText("Cancel");
        panel7.add(buttonCancel);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new BorderLayout(0, 0));
        panel3.add(panel8, BorderLayout.CENTER);
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new BorderLayout(0, 0));
        panel8.add(panel9, BorderLayout.CENTER);
        m_tabbedPane1 = new JTabbedPane();
        panel9.add(m_tabbedPane1, BorderLayout.CENTER);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridBagLayout());
        m_tabbedPane1.addTab("Text", panel10);
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new GridBagLayout());
        panel11.setMinimumSize(new Dimension(468, 33));
        panel11.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel10.add(panel11, gbc);
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setHorizontalScrollBarPolicy(31);
        scrollPane1.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel11.add(scrollPane1, gbc);
        m_linkTextTextField = new JTextField();
        m_linkTextTextField.setColumns(40);
        scrollPane1.setViewportView(m_linkTextTextField);
        final JLabel label1 = new JLabel();
        label1.setFont(new Font(label1.getFont().getName(), label1.getFont().getStyle(), label1.getFont().getSize()));
        label1.setText("Link text");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel10.add(label1, gbc);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel10.add(spacer1, gbc);
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new GridBagLayout());
        m_tabbedPane1.addTab("Properties", panel12);
        final JLabel label2 = new JLabel();
        label2.setText("Link Id");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel12.add(label2, gbc);
        final JPanel panel13 = new JPanel();
        panel13.setLayout(new GridBagLayout());
        panel13.setMinimumSize(new Dimension(468, 33));
        panel13.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel12.add(panel13, gbc);
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
        m_linkIdTextField = new JTextField();
        m_linkIdTextField.setColumns(40);
        m_linkIdTextField.setEditable(false);
        scrollPane2.setViewportView(m_linkIdTextField);
        final JPanel panel14 = new JPanel();
        panel14.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel12.add(panel14, gbc);
        final JPanel panel15 = new JPanel();
        panel15.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel14.add(panel15, BorderLayout.WEST);
        m_button1 = new JButton();
        m_button1.setText("Button");
        panel15.add(m_button1);
        final JPanel panel16 = new JPanel();
        panel16.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel14.add(panel16, BorderLayout.EAST);
        m_modificationsButton = new JButton();
        m_modificationsButton.setText("Modifications...");
        panel16.add(m_modificationsButton);
        m_autoCheckBox = new JCheckBox();
        m_autoCheckBox.setText("Auto");
        panel14.add(m_autoCheckBox, BorderLayout.CENTER);
        final JLabel label3 = new JLabel();
        label3.setText("Link constraints");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel12.add(label3, gbc);
        final JPanel panel17 = new JPanel();
        panel17.setLayout(new GridBagLayout());
        panel17.setMinimumSize(new Dimension(468, 33));
        panel17.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel12.add(panel17, gbc);
        final JScrollPane scrollPane3 = new JScrollPane();
        scrollPane3.setHorizontalScrollBarPolicy(31);
        scrollPane3.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel17.add(scrollPane3, gbc);
        m_linkConstraintsTextField = new JTextField();
        m_linkConstraintsTextField.setColumns(40);
        scrollPane3.setViewportView(m_linkConstraintsTextField);
        final JPanel spacer2 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel12.add(spacer2, gbc);
        final JLabel label4 = new JLabel();
        label4.setText("Link variable");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel12.add(label4, gbc);
        final JPanel panel18 = new JPanel();
        panel18.setLayout(new GridBagLayout());
        panel18.setMinimumSize(new Dimension(468, 33));
        panel18.setPreferredSize(new Dimension(468, 33));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 0);
        panel12.add(panel18, gbc);
        final JScrollPane scrollPane4 = new JScrollPane();
        scrollPane4.setHorizontalScrollBarPolicy(31);
        scrollPane4.setVerticalScrollBarPolicy(21);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel18.add(scrollPane4, gbc);
        m_linkVariableTextField = new JTextField();
        m_linkVariableTextField.setColumns(40);
        scrollPane4.setViewportView(m_linkVariableTextField);
        final JPanel panel19 = new JPanel();
        panel19.setLayout(new BorderLayout(0, 0));
        panel8.add(panel19, BorderLayout.NORTH);
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
        panel1.add(panel20, BorderLayout.CENTER);
        label2.setLabelFor(m_linkIdTextField);
        label3.setLabelFor(m_linkConstraintsTextField);
        label4.setLabelFor(m_linkVariableTextField);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
