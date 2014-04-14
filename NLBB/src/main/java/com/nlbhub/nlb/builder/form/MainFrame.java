/**
 * @(#)MainFrame.java
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
 * Copyright (c) 2012 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.builder.form;

import com.camick.swing.layout.WrapLayout;
import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.builder.model.LinkSelectionData;
import com.nlbhub.nlb.builder.view.GraphEditor;
import com.nlbhub.nlb.builder.view.TabComponent;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.util.BareBonesBrowserLaunch;
import com.nlbhub.nlb.web.Launcher;
import org.jdesktop.swingx.JXStatusBar;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The MainFrame class
 *
 * @author Anton P. Kolosov
 * @version 1.0 7/6/12
 */
public class MainFrame implements NLBObserver {
    /** Logger for this class. */
    private static final Logger LOGGER = (
        LoggerFactory.getLogger(MainFrame.class)
    );
    private static final String MAIN_PANE_KEY = Constants.MAIN_MODULE_NAME;
    private JPanel m_mainFramePanel;
    private JButton m_newFileButton;
    private JButton m_openFileButton;
    private JButton m_saveFileButton;
    private JButton m_saveFileAsButton;
    private JButton m_exportToQSPText;
    private JButton m_exportToURQText;
    private JButton m_cutButton;
    private JXStatusBar m_statusBar;
    private JPanel m_mainView;
    private JToggleButton m_addPageButton;
    private JToggleButton m_addLinkButton;
    private JButton m_undoButton;
    private JButton m_redoButton;
    private JButton m_copyButton;
    private JButton m_pasteButton;
    private JButton m_findButton;
    private JButton m_addStartPointButton;
    private JButton m_editPageButton;
    private JButton m_editLinkButton;
    private JButton m_deletePageButton;
    private JButton m_deleteLinkButton;
    private JButton m_editAllPagesButton;
    private JButton m_showLeafsButton;
    private JButton m_zoomOutButton;
    private JButton m_zoomInButton;
    private JButton m_checkBookButton;
    private JButton m_exportToPDF;
    private JButton m_exportToHTML;
    private JButton m_editBookVarsButton;
    private JButton m_exportSTEAD;
    private JButton m_exportJSIQ;
    private JToggleButton m_addObjButton;
    private JButton m_deleteObjButton;
    private JButton m_editObjButton;
    private JButton m_commitButton;
    private JButton m_startServerButton;
    private JButton m_stopServerButton;
    private JTextArea m_bookInformationArea;
    private JTabbedPane m_graphEditorsPane;
    private JButton m_editModuleButton;
    private JToggleButton m_selectionModeButton;
    private JPanel m_toolbarPanel;
    private final Launcher m_launcher;
    final JFileChooser m_dirChooser;
    final JFileChooser m_fileChooser;
    private Map<String, PaneEditorInfo> m_paneEditorInfoMap = new HashMap<>();
    final PaneEditorInfo m_mainEditorInfo;

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        m_mainFramePanel = new JPanel();
        m_mainFramePanel.setLayout(new BorderLayout(0, 0));
        final JSplitPane splitPane1 = new JSplitPane();
        m_mainFramePanel.add(splitPane1, BorderLayout.CENTER);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new BorderLayout(0, 0));
        panel1.setMinimumSize(new Dimension(200, 10));
        panel1.setPreferredSize(new Dimension(200, 100));
        splitPane1.setLeftComponent(panel1);
        m_bookInformationArea = new JTextArea();
        m_bookInformationArea.setEditable(false);
        m_bookInformationArea.setEnabled(true);
        m_bookInformationArea.setMinimumSize(new Dimension(200, 15));
        m_bookInformationArea.setPreferredSize(new Dimension(200, 15));
        panel1.add(m_bookInformationArea, BorderLayout.CENTER);
        m_mainView = new JPanel();
        m_mainView.setLayout(new BorderLayout(0, 0));
        m_mainView.setMinimumSize(new Dimension(10, 10));
        m_mainView.setPreferredSize(new Dimension(400, 100));
        splitPane1.setRightComponent(m_mainView);
        m_graphEditorsPane = new JTabbedPane();
        m_mainView.add(m_graphEditorsPane, BorderLayout.CENTER);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new BorderLayout(0, 0));
        m_graphEditorsPane.addTab("Untitled", panel2);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        m_mainView.add(panel3, BorderLayout.NORTH);
        m_statusBar = new JXStatusBar();
        m_mainFramePanel.add(m_statusBar, BorderLayout.SOUTH);
        m_toolbarPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        m_mainFramePanel.add(m_toolbarPanel, BorderLayout.NORTH);
        final JToolBar toolBar1 = new JToolBar();
        toolBar1.setBorderPainted(false);
        toolBar1.setFloatable(true);
        toolBar1.setOrientation(0);
        m_toolbarPanel.add(toolBar1);
        m_newFileButton = new JButton();
        m_newFileButton.setBorderPainted(false);
        m_newFileButton.setContentAreaFilled(true);
        m_newFileButton.setFocusPainted(false);
        m_newFileButton.setIcon(new ImageIcon(getClass().getResource("/common/new.png")));
        m_newFileButton.setRolloverEnabled(true);
        m_newFileButton.setText("");
        m_newFileButton.setToolTipText("new file");
        toolBar1.add(m_newFileButton);
        m_openFileButton = new JButton();
        m_openFileButton.setBorderPainted(false);
        m_openFileButton.setFocusPainted(false);
        m_openFileButton.setIcon(new ImageIcon(getClass().getResource("/common/open.png")));
        m_openFileButton.setRolloverEnabled(true);
        m_openFileButton.setText("");
        toolBar1.add(m_openFileButton);
        m_saveFileButton = new JButton();
        m_saveFileButton.setBorderPainted(false);
        m_saveFileButton.setFocusPainted(false);
        m_saveFileButton.setIcon(new ImageIcon(getClass().getResource("/common/save.png")));
        m_saveFileButton.setRolloverEnabled(true);
        m_saveFileButton.setText("");
        toolBar1.add(m_saveFileButton);
        m_saveFileAsButton = new JButton();
        m_saveFileAsButton.setBorderPainted(false);
        m_saveFileAsButton.setFocusPainted(false);
        m_saveFileAsButton.setIcon(new ImageIcon(getClass().getResource("/common/saveas.png")));
        m_saveFileAsButton.setRolloverEnabled(true);
        m_saveFileAsButton.setText("");
        toolBar1.add(m_saveFileAsButton);
        m_commitButton = new JButton();
        m_commitButton.setBorderPainted(false);
        m_commitButton.setFocusPainted(false);
        m_commitButton.setIcon(new ImageIcon(getClass().getResource("/common/commit.png")));
        m_commitButton.setRolloverEnabled(true);
        m_commitButton.setText("");
        toolBar1.add(m_commitButton);
        final JToolBar toolBar2 = new JToolBar();
        toolBar2.setBorderPainted(false);
        m_toolbarPanel.add(toolBar2);
        m_undoButton = new JButton();
        m_undoButton.setBorderPainted(false);
        m_undoButton.setFocusPainted(false);
        m_undoButton.setIcon(new ImageIcon(getClass().getResource("/common/undo.png")));
        m_undoButton.setRolloverEnabled(true);
        m_undoButton.setText("");
        toolBar2.add(m_undoButton);
        m_redoButton = new JButton();
        m_redoButton.setBorderPainted(false);
        m_redoButton.setFocusPainted(false);
        m_redoButton.setIcon(new ImageIcon(getClass().getResource("/common/redo.png")));
        m_redoButton.setRolloverEnabled(true);
        m_redoButton.setText("");
        toolBar2.add(m_redoButton);
        m_cutButton = new JButton();
        m_cutButton.setBorderPainted(false);
        m_cutButton.setFocusPainted(false);
        m_cutButton.setIcon(new ImageIcon(getClass().getResource("/common/cut.png")));
        m_cutButton.setRolloverEnabled(true);
        m_cutButton.setText("");
        toolBar2.add(m_cutButton);
        m_copyButton = new JButton();
        m_copyButton.setBorderPainted(false);
        m_copyButton.setFocusPainted(false);
        m_copyButton.setIcon(new ImageIcon(getClass().getResource("/common/copy.png")));
        m_copyButton.setRolloverEnabled(true);
        m_copyButton.setText("");
        toolBar2.add(m_copyButton);
        m_pasteButton = new JButton();
        m_pasteButton.setBorderPainted(false);
        m_pasteButton.setFocusPainted(false);
        m_pasteButton.setIcon(new ImageIcon(getClass().getResource("/common/paste.png")));
        m_pasteButton.setRolloverEnabled(true);
        m_pasteButton.setText("");
        toolBar2.add(m_pasteButton);
        final JToolBar toolBar3 = new JToolBar();
        toolBar3.setBorderPainted(false);
        toolBar3.setFloatable(true);
        toolBar3.setRollover(true);
        toolBar3.putClientProperty("JToolBar.isRollover", Boolean.TRUE);
        m_toolbarPanel.add(toolBar3);
        m_addStartPointButton = new JButton();
        m_addStartPointButton.setBorderPainted(false);
        m_addStartPointButton.setFocusPainted(false);
        m_addStartPointButton.setIcon(new ImageIcon(getClass().getResource("/core/EditAddStartPoint.png")));
        m_addStartPointButton.setRolloverEnabled(true);
        m_addStartPointButton.setText("");
        toolBar3.add(m_addStartPointButton);
        m_addPageButton = new JToggleButton();
        m_addPageButton.setBorderPainted(false);
        m_addPageButton.setFocusPainted(false);
        m_addPageButton.setIcon(new ImageIcon(getClass().getResource("/core/EditAddBookPage.png")));
        m_addPageButton.setRolloverEnabled(true);
        m_addPageButton.setText("");
        toolBar3.add(m_addPageButton);
        m_addObjButton = new JToggleButton();
        m_addObjButton.setBorderPainted(false);
        m_addObjButton.setFocusPainted(false);
        m_addObjButton.setIcon(new ImageIcon(getClass().getResource("/core/EditAddObj.png")));
        m_addObjButton.setRolloverEnabled(true);
        m_addObjButton.setText("");
        toolBar3.add(m_addObjButton);
        m_addLinkButton = new JToggleButton();
        m_addLinkButton.setBorderPainted(false);
        m_addLinkButton.setFocusPainted(false);
        m_addLinkButton.setIcon(new ImageIcon(getClass().getResource("/core/EditAddLink.png")));
        m_addLinkButton.setRolloverEnabled(true);
        m_addLinkButton.setSelected(false);
        m_addLinkButton.setText("");
        toolBar3.add(m_addLinkButton);
        m_selectionModeButton = new JToggleButton();
        m_selectionModeButton.setBorderPainted(false);
        m_selectionModeButton.setFocusPainted(false);
        m_selectionModeButton.setIcon(new ImageIcon(getClass().getResource("/core/EditSelectionMode.png")));
        m_selectionModeButton.setRolloverEnabled(true);
        m_selectionModeButton.setSelected(false);
        m_selectionModeButton.setText("");
        toolBar3.add(m_selectionModeButton);
        m_editModuleButton = new JButton();
        m_editModuleButton.setBorderPainted(false);
        m_editModuleButton.setFocusPainted(false);
        m_editModuleButton.setIcon(new ImageIcon(getClass().getResource("/core/EditModule.png")));
        m_editModuleButton.setRolloverEnabled(true);
        m_editModuleButton.setText("");
        toolBar3.add(m_editModuleButton);
        m_editAllPagesButton = new JButton();
        m_editAllPagesButton.setBorderPainted(false);
        m_editAllPagesButton.setFocusPainted(false);
        m_editAllPagesButton.setIcon(new ImageIcon(getClass().getResource("/core/EditChangeAllPages.png")));
        m_editAllPagesButton.setRolloverEnabled(true);
        m_editAllPagesButton.setText("");
        toolBar3.add(m_editAllPagesButton);
        m_editPageButton = new JButton();
        m_editPageButton.setBorderPainted(false);
        m_editPageButton.setFocusPainted(false);
        m_editPageButton.setIcon(new ImageIcon(getClass().getResource("/core/EditPageProperties.png")));
        m_editPageButton.setRolloverEnabled(true);
        m_editPageButton.setText("");
        toolBar3.add(m_editPageButton);
        m_editObjButton = new JButton();
        m_editObjButton.setBorderPainted(false);
        m_editObjButton.setFocusPainted(false);
        m_editObjButton.setIcon(new ImageIcon(getClass().getResource("/core/EditObjProperties.png")));
        m_editObjButton.setRolloverEnabled(true);
        m_editObjButton.setText("");
        toolBar3.add(m_editObjButton);
        m_editLinkButton = new JButton();
        m_editLinkButton.setBorderPainted(false);
        m_editLinkButton.setFocusPainted(false);
        m_editLinkButton.setIcon(new ImageIcon(getClass().getResource("/core/EditLinkProperties.png")));
        m_editLinkButton.setRolloverEnabled(true);
        m_editLinkButton.setText("");
        toolBar3.add(m_editLinkButton);
        m_deletePageButton = new JButton();
        m_deletePageButton.setBorderPainted(false);
        m_deletePageButton.setFocusPainted(false);
        m_deletePageButton.setIcon(new ImageIcon(getClass().getResource("/core/EditDeletePage.png")));
        m_deletePageButton.setRolloverEnabled(true);
        m_deletePageButton.setText("");
        toolBar3.add(m_deletePageButton);
        m_deleteObjButton = new JButton();
        m_deleteObjButton.setBorderPainted(false);
        m_deleteObjButton.setFocusPainted(false);
        m_deleteObjButton.setIcon(new ImageIcon(getClass().getResource("/core/EditDeleteObj.png")));
        m_deleteObjButton.setRolloverEnabled(true);
        m_deleteObjButton.setText("");
        toolBar3.add(m_deleteObjButton);
        m_deleteLinkButton = new JButton();
        m_deleteLinkButton.setBorderPainted(false);
        m_deleteLinkButton.setFocusPainted(false);
        m_deleteLinkButton.setIcon(new ImageIcon(getClass().getResource("/core/EditDeleteLink.png")));
        m_deleteLinkButton.setRolloverEnabled(true);
        m_deleteLinkButton.setText("");
        toolBar3.add(m_deleteLinkButton);
        m_zoomOutButton = new JButton();
        m_zoomOutButton.setBorderPainted(false);
        m_zoomOutButton.setFocusPainted(false);
        m_zoomOutButton.setIcon(new ImageIcon(getClass().getResource("/common/view_min.png")));
        m_zoomOutButton.setRolloverEnabled(true);
        m_zoomOutButton.setText("");
        toolBar3.add(m_zoomOutButton);
        m_zoomInButton = new JButton();
        m_zoomInButton.setBorderPainted(false);
        m_zoomInButton.setFocusPainted(false);
        m_zoomInButton.setIcon(new ImageIcon(getClass().getResource("/common/view_plus.png")));
        m_zoomInButton.setRolloverEnabled(true);
        m_zoomInButton.setText("");
        toolBar3.add(m_zoomInButton);
        final JToolBar toolBar4 = new JToolBar();
        toolBar4.setBorderPainted(false);
        m_toolbarPanel.add(toolBar4);
        m_showLeafsButton = new JButton();
        m_showLeafsButton.setBorderPainted(false);
        m_showLeafsButton.setFocusPainted(false);
        m_showLeafsButton.setIcon(new ImageIcon(getClass().getResource("/core/EditLeafs.png")));
        m_showLeafsButton.setRolloverEnabled(true);
        m_showLeafsButton.setText("");
        toolBar4.add(m_showLeafsButton);
        m_editBookVarsButton = new JButton();
        m_editBookVarsButton.setBorderPainted(false);
        m_editBookVarsButton.setFocusPainted(false);
        m_editBookVarsButton.setIcon(new ImageIcon(getClass().getResource("/core/EditBookVars.png")));
        m_editBookVarsButton.setRolloverEnabled(true);
        m_editBookVarsButton.setText("");
        toolBar4.add(m_editBookVarsButton);
        m_checkBookButton = new JButton();
        m_checkBookButton.setBorderPainted(false);
        m_checkBookButton.setFocusPainted(false);
        m_checkBookButton.setIcon(new ImageIcon(getClass().getResource("/common/check.png")));
        m_checkBookButton.setRolloverEnabled(true);
        m_checkBookButton.setText("");
        toolBar4.add(m_checkBookButton);
        m_startServerButton = new JButton();
        m_startServerButton.setBorderPainted(false);
        m_startServerButton.setFocusPainted(false);
        m_startServerButton.setIcon(new ImageIcon(getClass().getResource("/common/server_run.png")));
        m_startServerButton.setRolloverEnabled(true);
        m_startServerButton.setText("");
        toolBar4.add(m_startServerButton);
        m_stopServerButton = new JButton();
        m_stopServerButton.setBorderPainted(false);
        m_stopServerButton.setEnabled(false);
        m_stopServerButton.setFocusPainted(false);
        m_stopServerButton.setIcon(new ImageIcon(getClass().getResource("/common/server_stop.png")));
        m_stopServerButton.setRolloverEnabled(true);
        m_stopServerButton.setText("");
        toolBar4.add(m_stopServerButton);
        m_findButton = new JButton();
        m_findButton.setBorderPainted(false);
        m_findButton.setFocusPainted(false);
        m_findButton.setIcon(new ImageIcon(getClass().getResource("/common/find.png")));
        m_findButton.setRolloverEnabled(true);
        m_findButton.setText("");
        toolBar4.add(m_findButton);
        final JToolBar toolBar5 = new JToolBar();
        toolBar5.setBorderPainted(false);
        toolBar5.setFloatable(true);
        toolBar5.setOrientation(0);
        m_toolbarPanel.add(toolBar5);
        m_exportToQSPText = new JButton();
        m_exportToQSPText.setBorderPainted(false);
        m_exportToQSPText.setFocusPainted(false);
        m_exportToQSPText.setIcon(new ImageIcon(getClass().getResource("/extras/export/exportqsptxt.png")));
        m_exportToQSPText.setRolloverEnabled(true);
        m_exportToQSPText.setText("");
        toolBar5.add(m_exportToQSPText);
        m_exportToURQText = new JButton();
        m_exportToURQText.setBorderPainted(false);
        m_exportToURQText.setFocusPainted(false);
        m_exportToURQText.setIcon(new ImageIcon(getClass().getResource("/extras/export/exporturqtxt.png")));
        m_exportToURQText.setRolloverEnabled(true);
        m_exportToURQText.setText("");
        toolBar5.add(m_exportToURQText);
        m_exportToPDF = new JButton();
        m_exportToPDF.setBorderPainted(false);
        m_exportToPDF.setFocusPainted(false);
        m_exportToPDF.setIcon(new ImageIcon(getClass().getResource("/extras/export/exportpdf.png")));
        m_exportToPDF.setRolloverEnabled(true);
        m_exportToPDF.setText("");
        toolBar5.add(m_exportToPDF);
        m_exportToHTML = new JButton();
        m_exportToHTML.setBorderPainted(false);
        m_exportToHTML.setFocusPainted(false);
        m_exportToHTML.setIcon(new ImageIcon(getClass().getResource("/extras/export/exporthtml.png")));
        m_exportToHTML.setRolloverEnabled(true);
        m_exportToHTML.setText("");
        toolBar5.add(m_exportToHTML);
        m_exportJSIQ = new JButton();
        m_exportJSIQ.setBorderPainted(false);
        m_exportJSIQ.setFocusPainted(false);
        m_exportJSIQ.setIcon(new ImageIcon(getClass().getResource("/extras/export/exportJSIQ.png")));
        m_exportJSIQ.setRolloverEnabled(true);
        m_exportJSIQ.setText("");
        toolBar5.add(m_exportJSIQ);
        m_exportSTEAD = new JButton();
        m_exportSTEAD.setBorderPainted(false);
        m_exportSTEAD.setFocusPainted(false);
        m_exportSTEAD.setIcon(new ImageIcon(getClass().getResource("/extras/export/exportSTEAD.png")));
        m_exportSTEAD.setRolloverEnabled(true);
        m_exportSTEAD.setText("");
        toolBar5.add(m_exportSTEAD);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return m_mainFramePanel;
    }

    private class PaneEditorInfo {
        public static final int INDEX_UNDEFINED = -1;
        private TabComponent m_tabComponent;
        private final String m_modulePageId;
        private final NonLinearBookFacade m_paneNlbFacade;
        private final GraphEditor m_paneGraphEditor;
        private int m_paneIndex;
        private boolean m_closedManually = false;

        private PaneEditorInfo(
            String modulePageId,
            NonLinearBookFacade paneNlbFacade,
            GraphEditor paneGraphEditor,
            int paneIndex
        ) {
            m_modulePageId = modulePageId;
            m_paneNlbFacade = paneNlbFacade;
            m_paneGraphEditor = paneGraphEditor;
            m_paneIndex = paneIndex;
        }

        private boolean isClosedManually() {
            return m_closedManually;
        }

        private void setClosedManually(boolean closedManually) {
            m_closedManually = closedManually;
        }

        private TabComponent getTabComponent() {
            return m_tabComponent;
        }

        private void setTabComponent(TabComponent tabComponent) {
            m_tabComponent = tabComponent;
        }

        private String getModulePageId() {
            return m_modulePageId;
        }

        private NonLinearBookFacade getPaneNlbFacade() {
            return m_paneNlbFacade;
        }

        private GraphEditor getPaneGraphEditor() {
            return m_paneGraphEditor;
        }

        private int getPaneIndex() {
            return m_paneIndex;
        }

        private void setPaneIndex(int paneIndex) {
            m_paneIndex = paneIndex;
        }
    }

    public MainFrame(@NotNull NonLinearBookFacade nlbFacade, @NotNull Launcher launcher) {
        final MainFrame mainFrame = this;
        m_mainEditorInfo = (
            new PaneEditorInfo(MAIN_PANE_KEY, nlbFacade, new GraphEditor(nlbFacade), 0)
        );
        m_paneEditorInfoMap.put(MAIN_PANE_KEY, m_mainEditorInfo);
        m_launcher = launcher;
        m_dirChooser = new JFileChooser();
        m_dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        m_fileChooser = new JFileChooser();
        $$$setupUI$$$();
        rearrangeCustomComponents();
        disableNonimplementedControls();
        m_graphEditorsPane.setTitleAt(0, "Main");
        m_graphEditorsPane.setComponentAt(0, m_mainEditorInfo.getPaneGraphEditor());
        m_graphEditorsPane.addChangeListener(
            new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    PaneEditorInfo editorInfo = getSelectedPaneInfo();
                    NonLinearBook nlb = editorInfo.getPaneNlbFacade().getNlb();
                    NonLinearBook.BookStatistics bookStats = nlb.getBookStatistics();
                    NonLinearBook.VariableStatistics variableStats = nlb.getVariableStatistics();
                    setBookInfoPaneData(bookStats, variableStats);
                }
            }
        );
        m_newFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearAll();
            }
        });
        m_openFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    int returnVal = m_dirChooser.showOpenDialog(m_mainFramePanel);
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        // Opening the book
                        clearAll();
                        File file = m_dirChooser.getSelectedFile();
                        PaneEditorInfo editorInfo = getMainPaneInfo();
                        editorInfo.getPaneGraphEditor().load(file);
                        openModulesPanes();
                    } else {
                        // Open command cancelled by user
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while loading: " + ex.toString()
                    );
                }
            }
        });
        m_saveFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    PaneEditorInfo editorInfo = getMainPaneInfo();
                    if (editorInfo.getPaneNlbFacade().getNlb().getRootDir() == null) {
                        File saveDir = chooseSaveDir();
                        if (saveDir != null) {
                            editorInfo.getPaneGraphEditor().saveAs(saveDir);
                        }
                    } else {
                        editorInfo.getPaneGraphEditor().save();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while saving: " + ex.toString()
                    );
                }
            }
        });
        m_saveFileAsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    PaneEditorInfo editorInfo = getMainPaneInfo();
                    File saveDir = chooseSaveDir();
                    if (saveDir != null) {
                        editorInfo.getPaneGraphEditor().saveAs(saveDir);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while saving: " + ex.toString()
                    );
                }
            }
        });
        m_commitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    DialogCommit dialog = new DialogCommit();
                    dialog.showDialog();
                    if (dialog.isOk()) {
                        PaneEditorInfo editorInfo = getMainPaneInfo();
                        editorInfo.getPaneNlbFacade().commit(dialog.getCommitMessageText());
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while committing to VCS: " + ex.toString()
                    );
                }
            }
        });
        m_undoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getSelectedPaneInfo().getPaneNlbFacade().undo();
            }
        });
        m_redoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getSelectedPaneInfo().getPaneNlbFacade().redo();
            }
        });
        m_findButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PaneEditorInfo editorInfo = getSelectedPaneInfo();
                DialogSearch dialog = (
                    new DialogSearch(
                        mainFrame,
                        editorInfo.getPaneNlbFacade().getNlb(),
                        editorInfo.getModulePageId()
                    )
                );
                dialog.showDialog();
            }
        });
        m_exportToQSPText.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportFile = chooseExportFile();
                    if (exportFile != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToQSPTextFile(exportFile);
                    }
                } catch (NLBExportException ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while exporting to QSP text file: " + ex.toString()
                    );
                }
            }
        });
        m_exportToURQText.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportFile = chooseExportFile();
                    if (exportFile != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToURQTextFile(exportFile);
                    }
                } catch (NLBExportException ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while exporting to QSP text file: " + ex.toString()
                    );
                }
            }
        });
        m_exportToPDF.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportFile = chooseExportFile();
                    if (exportFile != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToPDFFile(exportFile);
                    }
                } catch (NLBExportException ex) {
                    LOGGER.error("Error while exporting to PDF: ", ex);
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while exporting to PDF: " + ex.toString()
                    );
                }
            }
        });
        m_exportToHTML.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportFile = chooseExportFile();
                    if (exportFile != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToHTMLFile(exportFile);
                    }
                } catch (NLBExportException ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while exporting to HTML: " + ex.toString()
                    );
                }
            }
        });
        m_exportJSIQ.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportFile = chooseExportFile();
                    if (exportFile != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToJSIQFile(exportFile);
                    }
                } catch (NLBExportException ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while exporting to HTML: " + ex.toString()
                    );
                }
            }
        });
        m_exportSTEAD.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportFile = chooseExportFile();
                    if (exportFile != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToSTEADFile(exportFile);
                    }
                } catch (NLBExportException ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while exporting to STEAD: " + ex.toString()
                    );
                }
            }
        });
        m_addStartPointButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getSelectedPaneInfo().getPaneGraphEditor().addStartPoint();
            }
        });
        m_addPageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                enableLinkedButton(
                    m_addPageButton,
                    m_addLinkButton,
                    m_addObjButton,
                    m_selectionModeButton
                );
                final GraphEditor paneGraphEditor = getSelectedPaneInfo().getPaneGraphEditor();
                paneGraphEditor.setAddPageMode(!paneGraphEditor.isAddPageMode());
            }
        });
        m_addObjButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                enableLinkedButton(
                    m_addObjButton,
                    m_addPageButton,
                    m_addLinkButton,
                    m_selectionModeButton
                );
                final GraphEditor paneGraphEditor = getSelectedPaneInfo().getPaneGraphEditor();
                paneGraphEditor.setAddObjMode(!paneGraphEditor.isAddObjMode());
            }
        });
        m_addLinkButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                enableLinkedButton(
                    m_addLinkButton,
                    m_addPageButton,
                    m_addObjButton,
                    m_selectionModeButton
                );
                final GraphEditor paneGraphEditor = getSelectedPaneInfo().getPaneGraphEditor();
                paneGraphEditor.setAddLinkMode(!paneGraphEditor.isAddLinkMode());
            }
        });
        m_selectionModeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                enableLinkedButton(
                    m_selectionModeButton,
                    m_addLinkButton,
                    m_addPageButton,
                    m_addObjButton
                );
                final GraphEditor paneGraphEditor = getSelectedPaneInfo().getPaneGraphEditor();
                paneGraphEditor.setSelectionMode(!paneGraphEditor.isSelectionMode());
            }
        });
        m_editModuleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PaneEditorInfo editorInfo = getSelectedPaneInfo();
                Page page = editorInfo.getPaneGraphEditor().getSelectedPage();
                final PaneEditorInfo paneEditorInfo = createGraphEditorPane(editorInfo, page);
                m_graphEditorsPane.setSelectedIndex(paneEditorInfo.getPaneIndex());
            }
        });
        m_deletePageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getSelectedPaneInfo().getPaneGraphEditor().deleteSelectedPage();
            }
        });
        m_editPageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PaneEditorInfo editorInfo = getSelectedPaneInfo();
                final Page selectedPage = editorInfo.getPaneGraphEditor().getSelectedPage();
                if (selectedPage != null) {
                    DialogPageProperties dialog = (
                        new DialogPageProperties(editorInfo.getPaneNlbFacade(), selectedPage)
                    );
                    dialog.showDialog();
                    editorInfo.getPaneGraphEditor().updatePage(selectedPage);
                }
            }
        });
        m_editObjButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PaneEditorInfo editorInfo = getSelectedPaneInfo();
                final Obj selectedObj = editorInfo.getPaneGraphEditor().getSelectedObj();
                if (selectedObj != null) {
                    DialogObjProperties dialog = (
                        new DialogObjProperties(editorInfo.getPaneNlbFacade(), selectedObj)
                    );
                    dialog.showDialog();
                    editorInfo.getPaneGraphEditor().updateObj(selectedObj);
                }
            }
        });
        m_editAllPagesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PaneEditorInfo editorInfo = getSelectedPaneInfo();
                for (final Page page : editorInfo.getPaneNlbFacade().getNlb().getPages().values()) {
                    if (!page.isDeleted()) {
                        DialogPageProperties dialog = (
                            new DialogPageProperties(editorInfo.getPaneNlbFacade(), page)
                        );
                        dialog.showDialog();
                        editorInfo.getPaneGraphEditor().updatePage(page);
                    }
                }
            }
        });
        m_showLeafsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DialogSearchResults dialog;
                try {
                    PaneEditorInfo editorInfo = getSelectedPaneInfo();
                    dialog = (
                        new DialogSearchResults(
                            mainFrame,
                            editorInfo.getPaneNlbFacade().getNlb(),
                            editorInfo.getModulePageId(),
                            DialogSearchResults.SearchType.Leafs
                        )
                    );
                    dialog.showDialog();
                } catch (NLBConsistencyException ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while querying NLB leafs: " + ex.toString()
                    );
                }
            }
        });
        m_editBookVarsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DialogSearchResults dialog;
                try {
                    PaneEditorInfo editorInfo = getSelectedPaneInfo();
                    dialog = (
                        new DialogSearchResults(
                            mainFrame,
                            editorInfo.getPaneNlbFacade().getNlb(),
                            editorInfo.getModulePageId(),
                            DialogSearchResults.SearchType.Variables
                        )
                    );
                    dialog.showDialog();
                } catch (NLBConsistencyException ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while querying NLB variables: " + ex.toString()
                    );
                }
            }
        });
        m_deleteLinkButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getSelectedPaneInfo().getPaneGraphEditor().deleteSelectedLink();
            }
        });
        m_deleteObjButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getSelectedPaneInfo().getPaneGraphEditor().deleteSelectedObj();
            }
        });
        m_editLinkButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PaneEditorInfo editorInfo = getSelectedPaneInfo();
                final LinkSelectionData selectedLink = (
                    editorInfo.getPaneGraphEditor().getSelectedLink()
                );
                if (selectedLink != null && selectedLink.getLink() != null) {
                    DialogLinkProperties dialog = (
                        new DialogLinkProperties(
                            editorInfo.getPaneNlbFacade(),
                            selectedLink.getLink())
                    );
                    dialog.showDialog();
                    editorInfo.getPaneGraphEditor().updateLink(selectedLink.getLink());
                }
            }
        });
        m_zoomInButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getSelectedPaneInfo().getPaneGraphEditor().zoomIn();
            }
        });
        m_zoomOutButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getSelectedPaneInfo().getPaneGraphEditor().zoomOut();
            }
        });
        m_checkBookButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    PaneEditorInfo editorInfo = getMainPaneInfo();
                    DialogSearchResults dialog = (
                        new DialogSearchResults(
                            mainFrame,
                            editorInfo.getPaneNlbFacade().getNlb(),
                            editorInfo.getModulePageId(),
                            DialogSearchResults.SearchType.CheckBook
                        )
                    );
                    dialog.showDialog();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while checking the NLB: " + ex.toString()
                    );
                }
            }
        });
        m_startServerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (!m_launcher.isRunning()) {
                        PaneEditorInfo editorInfo = getMainPaneInfo();
                        final File rootDir = editorInfo.getPaneNlbFacade().getNlb().getRootDir();
                        if (rootDir != null) {
                            m_launcher.setNLBLibraryRoot(rootDir.getParent());
                            Thread thread = new Thread(m_launcher);
                            thread.start();
                            BareBonesBrowserLaunch.openURL(
                                "http://localhost:8111/nlb/" + rootDir.getName() + "/start"
                            );
                        }
                        m_startServerButton.setEnabled(false);
                        m_stopServerButton.setEnabled(true);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while starting the server: " + ex.toString()
                    );
                }
            }
        });
        m_stopServerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (m_launcher.isRunning()) {
                        m_launcher.stop();
                        m_startServerButton.setEnabled(true);
                        m_stopServerButton.setEnabled(false);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while stopping the server: " + ex.toString()
                    );
                }
            }
        });
        addListenerAndObserver(getMainPaneInfo());
        updateView();
    }

    public void goTo(String modulePageId, String itemId) {
        PaneEditorInfo paneEditorInfo = m_paneEditorInfoMap.get(modulePageId);
        paneEditorInfo.getPaneGraphEditor().goTo(itemId);
        m_graphEditorsPane.setSelectedIndex(paneEditorInfo.getPaneIndex());
    }

    private void clearAll() {
        try {
            for (Map.Entry<String, PaneEditorInfo> entry : m_paneEditorInfoMap.entrySet()) {
                entry.getValue().getPaneGraphEditor().clear();
                entry.getValue().getPaneNlbFacade().clear();

            }
            m_paneEditorInfoMap.clear();
            m_paneEditorInfoMap.put(MAIN_PANE_KEY, m_mainEditorInfo);
            // Remove all tabs except main tab (zero index)
            while (m_graphEditorsPane.getTabCount() > 1) {
                m_graphEditorsPane.remove(1);
            }
        } catch (NLBVCSException ex) {
            JOptionPane.showMessageDialog(
                m_mainFramePanel,
                "Error while NLB clear: " + ex.toString()
            );
        }
    }

    private void openModulesPanes() {
        openModulesPanes(getMainPaneInfo());
    }

    private void openModulesPanes(final PaneEditorInfo paneEditorInfo) {
        for (final Page page : paneEditorInfo.getPaneNlbFacade().getNlb().getPages().values()) {
            if (!page.isDeleted() && !page.getModule().isEmpty()) {
                openModulesPanes(createGraphEditorPane(paneEditorInfo, page));
            }
        }
    }

    private PaneEditorInfo createGraphEditorPane(PaneEditorInfo editorInfo, Page page) {
        final PaneEditorInfo paneEditorInfo;
        final GraphEditor paneGraphEditor;
        if (m_paneEditorInfoMap.containsKey(page.getId())) {
            paneEditorInfo = m_paneEditorInfoMap.get(page.getId());
            if (paneEditorInfo.getPaneIndex() != PaneEditorInfo.INDEX_UNDEFINED) {
                return paneEditorInfo;
            }
        } else {
            NonLinearBookFacade paneNlbFacade = (
                editorInfo.getPaneNlbFacade().createModuleFacade(page.getId())
            );
            paneGraphEditor = new GraphEditor(paneNlbFacade);
            paneEditorInfo = (
                new PaneEditorInfo(
                    page.getId(),
                    paneNlbFacade,
                    paneGraphEditor,
                    m_graphEditorsPane.getTabCount()
                )
            );
            m_paneEditorInfoMap.put(page.getId(), paneEditorInfo);
            addListenerAndObserver(paneEditorInfo);
        }

        // We are opening this tab again, so clear its closed state
        paneEditorInfo.setClosedManually(false);
        addGraphEditorTab(page.getModuleName(), paneEditorInfo);

        return paneEditorInfo;
    }

    private void addGraphEditorTab(final String title, final PaneEditorInfo paneEditorInfo) {
        final int newTabIndex = m_graphEditorsPane.getTabCount();
        paneEditorInfo.setPaneIndex(newTabIndex);
        m_graphEditorsPane.addTab(title, paneEditorInfo.getPaneGraphEditor());

        TabComponent tabComponent = new TabComponent(
            title,
            new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    final int index = paneEditorInfo.getPaneIndex();
                    for (Map.Entry<String, PaneEditorInfo> entry : m_paneEditorInfoMap.entrySet()) {
                        if (entry.getValue().getPaneIndex() > index) {
                            entry.getValue().setPaneIndex(entry.getValue().getPaneIndex() - 1);
                        }
                    }
                    paneEditorInfo.setPaneIndex(PaneEditorInfo.INDEX_UNDEFINED);
                    if (!(TabComponent.CLOSED_IN_CODE.equals(actionEvent.getActionCommand()))) {
                        paneEditorInfo.setClosedManually(true);
                    }
                    m_graphEditorsPane.remove(index);
                }
            }
        );
        paneEditorInfo.setTabComponent(tabComponent);

        m_graphEditorsPane.setTabComponentAt(
            newTabIndex,
            tabComponent.getPnlTab()
        );
    }

    private void addListenerAndObserver(final PaneEditorInfo paneEditorInfo) {
        paneEditorInfo.getPaneGraphEditor().addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                super.mouseMoved(e);
                paneEditorInfo.getPaneGraphEditor().mouseMove(e.getPoint());
                //m_statusBar.setLeadingMessage(e.getPoint().toString());
            }
        });
        paneEditorInfo.getPaneNlbFacade().addObserver(this);
    }

    private void disableNonimplementedControls() {
        m_cutButton.setEnabled(false);
        m_copyButton.setEnabled(false);
        m_pasteButton.setEnabled(false);
    }

    private File chooseSaveDir() {
        int returnVal = m_dirChooser.showSaveDialog(m_mainFramePanel);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            // Opening the book
            return m_dirChooser.getSelectedFile();
        }

        return null;
    }

    private File chooseExportFile() {
        int returnVal = m_fileChooser.showSaveDialog(m_mainFramePanel);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            // Opening the book
            return m_fileChooser.getSelectedFile();
        }

        return null;
    }

    private void enableLinkedButton(JToggleButton pressedButton, JToggleButton... linkedButtons) {
        if (pressedButton.isSelected()) {
            for (final JToggleButton linkedButton : linkedButtons) {
                linkedButton.setEnabled(false);
            }
        } else {
            for (final JToggleButton linkedButton : linkedButtons) {
                linkedButton.setEnabled(true);
            }
        }
    }

    /**
     * Custom components creation
     */
    private void createUIComponents() {
        m_toolbarPanel = new JPanel();
    }

    /**
     * Corrects some properties of the custom components which were incorrectly set by automatic
     * code generation.
     */
    private void rearrangeCustomComponents() {
        m_toolbarPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 5, 5));
        m_toolbarPanel.setSize(new Dimension(300, 1));
    }

    public void serrext() {
        m_statusBar.setVisible(false);
        m_statusBar.setEnabled(false);
        //m_statusBar.setLeadingMessage("jjj");
        //m_statusBar.setTrailingMessage("lkk");
    }

    @Override
    public void updateView() {
        PaneEditorInfo editorInfo = getSelectedPaneInfo();
        m_undoButton.setEnabled(editorInfo.getPaneNlbFacade().canUndo());
        m_redoButton.setEnabled(editorInfo.getPaneNlbFacade().canRedo());
        NonLinearBook nlb = editorInfo.getPaneNlbFacade().getNlb();
        NonLinearBook.BookStatistics bookStats = nlb.getBookStatistics();
        NonLinearBook.VariableStatistics variableStats = nlb.getVariableStatistics();
        for (final NonLinearBook.ModuleInfo info : bookStats.getModuleInfos()) {
            PaneEditorInfo paneEditorInfo = m_paneEditorInfoMap.get(info.getModulePageId());
            if (paneEditorInfo != null) {
                if (paneEditorInfo.getPaneIndex() != PaneEditorInfo.INDEX_UNDEFINED) {
                    paneEditorInfo.getTabComponent().setTabName(info.getModuleName());
                } else {
                    // Do not reopen manually closed tabs
                    if (!paneEditorInfo.isClosedManually()) {
                        addGraphEditorTab(info.getModuleName(), paneEditorInfo);
                    }
                }
            }

        }

        for (final NonLinearBook.ModuleInfo info : bookStats.getModulesToBeDeletedInfos()) {
            PaneEditorInfo paneEditorInfo = m_paneEditorInfoMap.get(info.getModulePageId());
            if (
                paneEditorInfo != null
                    && paneEditorInfo.getPaneIndex() != PaneEditorInfo.INDEX_UNDEFINED
                ) {
                paneEditorInfo.getTabComponent().close();
            }
        }
        setBookInfoPaneData(bookStats, variableStats);
    }

    private void setBookInfoPaneData(
        final NonLinearBook.BookStatistics bookStats,
        final NonLinearBook.VariableStatistics variableStats
    ) {
        StringBuilder builder = new StringBuilder();

        builder.append("Pages count: ").append(bookStats.getPagesCount());
        builder.append("\r\n");
        builder.append("Objs count: ").append(bookStats.getObjsCount());
        builder.append("\r\n");
        builder.append("Unique endings: ").append(bookStats.getUniqueEndings());
        builder.append("\r\n");
        builder.append("Characters count: ").append(bookStats.getCharactersCount());
        builder.append("\r\n");
        builder.append("Modules count: ").append(bookStats.getModulesCount());
        builder.append("\r\n");
        builder.append("Modules: ");
        builder.append("\r\n");
        for (final NonLinearBook.ModuleInfo info : bookStats.getModuleInfos()) {
            builder.append(info.toString());
            builder.append("\r\n");
        }
        builder.append("Modules to be deleted: ");
        builder.append("\r\n");
        for (final NonLinearBook.ModuleInfo info : bookStats.getModulesToBeDeletedInfos()) {
            builder.append(info.toString());
            builder.append("\r\n");
        }
        builder.append("Page variable count: ").append(variableStats.getPageVariablesCount());
        builder.append("\r\n");
        builder.append("Obj variable count: ").append(variableStats.getObjVariablesCount());
        builder.append("\r\n");
        builder.append("Link variable count: ").append(variableStats.getLinkVariablesCount());
        builder.append("\r\n");
        builder.append("Link constraint count: ").append(variableStats.getLinkConstraintVariablesCount());
        builder.append("\r\n");
        builder.append("Plain variables count: ").append(variableStats.getPlainVariablesCount());
        builder.append("\r\n");
        builder.append("Expressions count: ").append(variableStats.getExpressionsCount());
        builder.append("\r\n");
        builder.append("Module constraint count: ").append(variableStats.getModuleConstraintCount());
        builder.append("\r\n");
        m_bookInformationArea.setText(
            builder.toString()
        );
    }

    private PaneEditorInfo getSelectedPaneInfo() {
        final int selectedIndex = m_graphEditorsPane.getSelectedIndex();
        if (selectedIndex == -1) {
            return getMainPaneInfo();
        }
        for (Map.Entry<String, PaneEditorInfo> entry : m_paneEditorInfoMap.entrySet()) {
            if (entry.getValue().getPaneIndex() == selectedIndex) {
                return entry.getValue();
            }
        }
        assert false;
        return null;
    }

    private PaneEditorInfo getMainPaneInfo() {
        return m_paneEditorInfoMap.get(MAIN_PANE_KEY);
    }
}
