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
import com.nlbhub.nlb.builder.view.BulkSelectionHandler;
import com.nlbhub.nlb.builder.view.GraphEditor;
import com.nlbhub.nlb.builder.view.TabComponent;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.exception.*;
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The MainFrame class
 *
 * @author Anton P. Kolosov
 * @version 1.0 7/6/12
 */
public class MainFrame implements PropertyChangeListener, NLBObserver {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = (
            LoggerFactory.getLogger(MainFrame.class)
    );
    private static final String MAIN_PANE_KEY = Constants.MAIN_MODULE_NAME;
    /**
     * The number of ms to wait for server start before launching browser
     */
    private static final long START_SERVER_TIMEOUT = 500;
    private static final String DEFAULT_BOOK_ID = "noname";
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
    private JButton m_commitButton;
    private JButton m_startServerButton;
    private JButton m_stopServerButton;
    private JTextArea m_bookInformationArea;
    private JTabbedPane m_graphEditorsPane;
    private JButton m_editModuleButton;
    private JToggleButton m_selectionModeButton;
    private JPanel m_toolbarPanel;
    private JButton m_exportASM;
    private JButton m_editPropertiesButton;
    private JButton m_editDeleteButton;
    private JButton m_editBookPropertiesButton;
    private JButton m_exportPNG;
    private JButton m_exportTXT;
    private JButton m_pushButton;
    private JButton m_pullButton;
    private JButton m_exportVNSTEAD;
    private JButton m_exportToChoiceScript;
    private final Launcher m_launcher;
    private final JFileChooser m_dirChooser;
    private final JFileChooser m_fileChooser;
    private Map<String, PaneEditorInfo> m_paneEditorInfoMap = new HashMap<>();
    private final PaneEditorInfo m_mainEditorInfo;

    private ProgressMonitor m_progressMonitor;
    private Task m_task;

    // Progress bar handling -- begin
    abstract class Task extends SwingWorker<Void, Void> implements ProgressData {
        @Override
        public void setProgressValue(int progress) {
            setProgress(Math.min(progress, 100));
        }

        @Override
        public void setNoteText(String text) {
            m_progressMonitor.setNote(text);
        }

        @Override
        public void done() {
            Toolkit.getDefaultToolkit().beep();
            m_progressMonitor.setProgress(100);
        }
    }

    class PullTask extends Task {
        private String m_userName;
        private String m_password;
        private PropertyChangeListener m_listener;

        PullTask(String userName, String password, PropertyChangeListener listener) {
            m_userName = userName;
            m_password = password;
            m_listener = listener;
        }

        @Override
        protected Void doInBackground() throws Exception {
            setProgress(0);
            try {
                PaneEditorInfo editorInfo = getMainPaneInfo();
                editorInfo.getPaneNlbFacade().pull(m_userName, m_password, this);
                setProgressValue(100);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while pulling: " + ex.toString()
                );
            }
            return null;
        }

        @Override
        public void done() {
            super.done();
            m_pullButton.setEnabled(true);
            m_task = new OpenTask(getMainPaneInfo().getPaneNlbFacade().getNlb().getRootDir());
            m_task.addPropertyChangeListener(m_listener);
            m_openFileButton.setEnabled(false);
            m_task.execute();
        }
    }

    class PushTask extends Task {
        private String m_userName;
        private String m_password;

        PushTask(String userName, String password) {
            m_userName = userName;
            m_password = password;
        }

        @Override
        protected Void doInBackground() throws Exception {
            setProgress(0);
            try {
                PaneEditorInfo editorInfo = getMainPaneInfo();
                editorInfo.getPaneNlbFacade().push(m_userName, m_password, this);
                setProgressValue(100);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while pushing: " + ex.toString()
                );
            }
            return null;
        }

        @Override
        public void done() {
            super.done();
            m_pushButton.setEnabled(true);
        }
    }

    class OpenTask extends Task {
        private File m_file;

        OpenTask(File file) {
            m_file = file;
        }

        @Override
        public Void doInBackground() {
            setProgress(0);
            try {
                // Opening the book
                clearAll();
                PaneEditorInfo editorInfo = getMainPaneInfo();
                editorInfo.getPaneGraphEditor().load(m_file, this);
                setProgressValue(85);
                setNoteText("Opening panes...");
                openModulesPanes();
                setNoteText("All done!");
                setProgressValue(100);
            } catch (NLBVCSException | NLBConsistencyException | NLBIOException ex) {
                JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while loading: " + ex.toString()
                );
            }
            return null;
        }

        @Override
        public void done() {
            super.done();
            m_openFileButton.setEnabled(true);
        }
    }

    class SaveAsTask extends Task {
        File m_saveDir;

        SaveAsTask(File saveDir) {
            m_saveDir = saveDir;
        }

        @Override
        protected Void doInBackground() {
            try {
                PaneEditorInfo editorInfo = getMainPaneInfo();
                editorInfo.getPaneGraphEditor().saveAs(m_saveDir, this);
            } catch (NLBVCSException | NLBConsistencyException | NLBIOException | NLBFileManipulationException ex) {
                JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while saving: " + ex.toString()
                );
            }
            return null;
        }

        @Override
        public void done() {
            super.done();
            m_saveFileButton.setEnabled(true);
            m_saveFileAsButton.setEnabled(true);
        }
    }

    class SaveTask extends Task {
        @Override
        protected Void doInBackground() {
            try {
                PaneEditorInfo editorInfo = getMainPaneInfo();
                editorInfo.getPaneGraphEditor().save(this);
            } catch (NLBVCSException | NLBConsistencyException | NLBIOException | NLBFileManipulationException ex) {
                JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while saving: " + ex.toString()
                );
            }
            return null;
        }

        @Override
        public void done() {
            super.done();
            m_saveFileButton.setEnabled(true);
            m_saveFileAsButton.setEnabled(true);
        }
    }

    class SaveAsImageTask extends Task {
        File m_imageFile;

        SaveAsImageTask(File imageFile) {
            m_imageFile = imageFile;
        }

        @Override
        protected Void doInBackground() throws Exception {
            try {
                getSelectedPaneInfo().getPaneGraphEditor().saveAsImage(m_imageFile, this);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while exporting to PNG: " + ex.toString()
                );
            }
            return null;
        }

        @Override
        public void done() {
            super.done();
            m_exportPNG.setEnabled(true);
        }
    }

    /**
     * Invoked when task's progress property changes.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if ("progress".equals(evt.getPropertyName())) {
            int progress = (Integer) evt.getNewValue();
            m_progressMonitor.setProgress(progress);
            if (m_progressMonitor.isCanceled() || m_task.isDone()) {
                Toolkit.getDefaultToolkit().beep();
                if (m_progressMonitor.isCanceled()) {
                    m_task.cancel(true);
                }
                m_saveFileButton.setEnabled(true);
                m_saveFileAsButton.setEnabled(true);
                m_openFileButton.setEnabled(true);
            }
        }
    }
    // Progress bar handling -- end

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
                new PaneEditorInfo(MAIN_PANE_KEY, nlbFacade, new GraphEditor(this, nlbFacade), 0)
        );
        m_paneEditorInfoMap.put(MAIN_PANE_KEY, m_mainEditorInfo);
        m_launcher = launcher;
        m_dirChooser = new JFileChooser();
        m_dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        m_fileChooser = new JFileChooser();
        //$$$setupUI$$$();
        rearrangeCustomComponents();
        m_graphEditorsPane.setTitleAt(0, "Main");
        m_graphEditorsPane.setComponentAt(0, m_mainEditorInfo.getPaneGraphEditor());
        m_graphEditorsPane.addChangeListener(
                new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        PaneEditorInfo editorInfo = getSelectedPaneInfo();
                        m_undoButton.setEnabled(editorInfo.getPaneNlbFacade().canUndo());
                        m_redoButton.setEnabled(editorInfo.getPaneNlbFacade().canRedo());
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
                        m_progressMonitor = new ProgressMonitor(
                                m_mainFramePanel, //$$$getRootComponent$$$()
                                "Opening Non-Linear Book",
                                "Initializing...",
                                0,
                                100
                        );
                        m_progressMonitor.setProgress(0);
                        m_progressMonitor.setMillisToDecideToPopup(200);
                        m_progressMonitor.setMillisToPopup(200);
                        m_task = new OpenTask(m_dirChooser.getSelectedFile());
                        m_task.addPropertyChangeListener(mainFrame);
                        m_openFileButton.setEnabled(false);
                        m_task.execute();
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
                            initSaveProgress();
                            m_task = new SaveAsTask(saveDir);
                        } else {
                            return;
                        }
                    } else {
                        initSaveProgress();
                        m_task = new SaveTask();
                    }

                    m_task.addPropertyChangeListener(mainFrame);
                    m_saveFileButton.setEnabled(false);
                    m_saveFileAsButton.setEnabled(false);
                    m_task.execute();
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
                    File saveDir = chooseSaveDir();
                    if (saveDir != null) {
                        initSaveProgress();
                        m_task = new SaveAsTask(saveDir);
                        m_task.addPropertyChangeListener(mainFrame);
                        m_saveFileButton.setEnabled(false);
                        m_saveFileAsButton.setEnabled(false);
                        m_task.execute();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            m_mainFramePanel,
                            "Error while saving: " + ex.toString()
                    );
                }
            }
        });
        m_pullButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    DialogPull dialog = new DialogPull();
                    dialog.showDialog();
                    if (dialog.isOk()) {
                        m_progressMonitor = new ProgressMonitor(
                                m_mainFramePanel, //$$$getRootComponent$$$()
                                "Pulling Non-Linear Book",
                                "Initializing...",
                                0,
                                100
                        );
                        m_progressMonitor.setProgress(0);
                        m_progressMonitor.setMillisToDecideToPopup(200);
                        m_progressMonitor.setMillisToPopup(200);
                        m_task = new PullTask(dialog.getUserName(), dialog.getPassword(), mainFrame);
                        m_task.addPropertyChangeListener(mainFrame);
                        m_pullButton.setEnabled(false);
                        m_task.execute();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            m_mainFramePanel,
                            "Error while pulling: " + ex.toString()
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
        m_pushButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    DialogPush dialog = new DialogPush();
                    dialog.showDialog();
                    if (dialog.isOk()) {
                        m_progressMonitor = new ProgressMonitor(
                                m_mainFramePanel, //$$$getRootComponent$$$()
                                "Pushing Non-Linear Book",
                                "Initializing...",
                                0,
                                100
                        );
                        m_progressMonitor.setProgress(0);
                        m_progressMonitor.setMillisToDecideToPopup(200);
                        m_progressMonitor.setMillisToPopup(200);
                        m_task = new PushTask(dialog.getUserName(), dialog.getPassword());
                        m_task.addPropertyChangeListener(mainFrame);
                        m_pushButton.setEnabled(false);
                        m_task.execute();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            m_mainFramePanel,
                            "Error while pushing: " + ex.toString()
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
        m_cutButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PaneEditorInfo paneInfo = getSelectedPaneInfo();
                BulkSelectionHandler bulkSelectionHandler = paneInfo.getPaneGraphEditor().getBulkSelectionHandler();
                if (bulkSelectionHandler.hasSelection()) {
                    paneInfo.getPaneNlbFacade().cut(
                            bulkSelectionHandler.getSelectedPagesIds(),
                            bulkSelectionHandler.getSelectedObjsIds()
                    );
                }
            }
        });
        m_copyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PaneEditorInfo paneInfo = getSelectedPaneInfo();
                BulkSelectionHandler bulkSelectionHandler = paneInfo.getPaneGraphEditor().getBulkSelectionHandler();
                if (bulkSelectionHandler.hasSelection()) {
                    paneInfo.getPaneNlbFacade().copy(
                            bulkSelectionHandler.getSelectedPagesIds(),
                            bulkSelectionHandler.getSelectedObjsIds()
                    );
                }
            }
        });
        m_pasteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PaneEditorInfo paneInfo = getSelectedPaneInfo();
                paneInfo.getPaneNlbFacade().paste();
                paneInfo.getPaneGraphEditor().init();
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
                                editorInfo.getModulePageId(),
                                Constants.EMPTY_STRING
                        )
                );
                dialog.showDialog();
            }
        });
        m_exportToChoiceScript.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportDir = chooseExportDir();
                    if (exportDir != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToChoiceScript(exportDir);
                    }
                } catch (NLBExportException ex) {
                    JOptionPane.showMessageDialog(
                            m_mainFramePanel,
                            "Error while exporting to Choice Script file: " + ex.toString()
                    );
                }
            }
        });
        m_exportToQSPText.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportDir = chooseExportDir();
                    if (exportDir != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToQSPTextFile(exportDir);
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
                    File exportDir = chooseExportDir();
                    if (exportDir != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToURQTextFile(exportDir);
                    }
                } catch (NLBExportException ex) {
                    JOptionPane.showMessageDialog(
                            m_mainFramePanel,
                            "Error while exporting to URQ file: " + ex.toString()
                    );
                }
            }
        });
        m_exportToPDF.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportFile = chooseExportFile("book.pdf");
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
        m_exportTXT.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportDir = chooseExportDir();
                    if (exportDir != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToTXTFile(exportDir);
                    }
                } catch (NLBExportException ex) {
                    JOptionPane.showMessageDialog(
                            m_mainFramePanel,
                            "Error while exporting to TXT: " + ex.toString()
                    );
                }
            }
        });
        m_exportToHTML.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportDir = chooseExportDir();
                    if (exportDir != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToHTMLFile(exportDir);
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
                    File exportDir = chooseExportDir();
                    if (exportDir != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToJSIQFile(exportDir);
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
                    File exportDir = chooseExportDir();
                    if (exportDir != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToSTEADFile(exportDir);
                    }
                } catch (NLBExportException | NLBIOException ex) {
                    JOptionPane.showMessageDialog(
                            m_mainFramePanel,
                            "Error while exporting to STEAD: " + ex.toString()
                    );
                }
            }
        });
        m_exportVNSTEAD.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportDir = chooseExportDir();
                    if (exportDir != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToVNSTEADFile(exportDir);
                    }
                } catch (NLBExportException | NLBIOException ex) {
                    JOptionPane.showMessageDialog(
                            m_mainFramePanel,
                            "Error while exporting to VNSTEAD: " + ex.toString()
                    );
                }
            }
        });
        m_exportASM.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    File exportDir = chooseExportDir();
                    if (exportDir != null) {
                        getMainPaneInfo().getPaneNlbFacade().exportToASMFile(exportDir);
                    }
                } catch (NLBExportException ex) {
                    JOptionPane.showMessageDialog(
                            m_mainFramePanel,
                            "Error while exporting to ASM: " + ex.toString()
                    );
                }
            }
        });
        m_exportPNG.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                File exportFile = chooseExportFile("book.png");
                if (exportFile != null) {
                    m_progressMonitor = new ProgressMonitor(
                            m_mainFramePanel, //$$$getRootComponent$$$()
                            "Exporting Non-Linear Book to PNG",
                            "Initializing...",
                            0,
                            100
                    );
                    m_progressMonitor.setProgress(0);
                    m_progressMonitor.setMillisToDecideToPopup(200);
                    m_progressMonitor.setMillisToPopup(200);
                    m_task = new SaveAsImageTask(exportFile);
                    m_task.addPropertyChangeListener(mainFrame);
                    m_exportPNG.setEnabled(false);
                    m_task.execute();
                }
            }
        });
        m_editBookPropertiesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DialogBookProperties dialog;
                PaneEditorInfo editorInfo = getSelectedPaneInfo();
                dialog = new DialogBookProperties(editorInfo.getPaneNlbFacade());
                dialog.showDialog();
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
        m_editDeleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PaneEditorInfo editorInfo = getSelectedPaneInfo();
                GraphEditor paneGraphEditor = editorInfo.getPaneGraphEditor();
                final Page selectedPage = paneGraphEditor.getSelectedPage();
                final Obj selectedObj = paneGraphEditor.getSelectedObj();
                if (selectedPage != null) {
                    paneGraphEditor.deleteSelectedPage();
                } else if (selectedObj != null) {
                    paneGraphEditor.deleteSelectedObj();
                } else {
                    final LinkSelectionData selectedLink = paneGraphEditor.getSelectedLink();
                    if (selectedLink != null && selectedLink.getLink() != null) {
                        paneGraphEditor.deleteSelectedLink();
                    }
                }
            }
        });
        m_editPropertiesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                GraphEditor graphEditor = getSelectedPaneInfo().getPaneGraphEditor();
                graphEditor.editSelectedItemProperties(mainFrame);
            }
        });
        m_editAllPagesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PaneEditorInfo editorInfo = getSelectedPaneInfo();
                for (final Page page : editorInfo.getPaneNlbFacade().getNlb().getPages().values()) {
                    if (!page.isDeleted()) {
                        DialogPageProperties dialog = (
                                new DialogPageProperties(mainFrame, editorInfo.getPaneNlbFacade(), page)
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
                        NonLinearBook mainNLB = editorInfo.getPaneNlbFacade().getNlb();
                        final File rootDir = mainNLB.getRootDir();
                        if (rootDir != null) {
                            m_launcher.setNLBLibraryRoot(rootDir.getParent());
                        }
                        String bookId = (rootDir != null) ? rootDir.getName() : DEFAULT_BOOK_ID;
                        m_launcher.clearNLBCache();
                        // Always prefer in-memory data
                        m_launcher.putNLBInMemoryToCache(bookId, mainNLB);
                        Thread thread = new Thread(m_launcher);
                        thread.start();
                        Thread.sleep(START_SERVER_TIMEOUT);
                        BareBonesBrowserLaunch.openURL(
                                "http://localhost:8111/nlb/" + bookId + "/start"
                        );
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

    public JPanel getMainFramePanel() {
        return m_mainFramePanel;
    }

    private void initSaveProgress() {
        m_progressMonitor = new ProgressMonitor(
                m_mainFramePanel, //$$$getRootComponent$$$()
                "Saving Non-Linear Book",
                "Initializing...",
                0,
                100
        );
        m_progressMonitor.setProgress(0);
        m_progressMonitor.setMillisToDecideToPopup(200);
        m_progressMonitor.setMillisToPopup(200);
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
            paneGraphEditor = new GraphEditor(this, paneNlbFacade);
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

    private File chooseSaveDir() {
        int returnVal = m_dirChooser.showSaveDialog(m_mainFramePanel);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            // Opening the book
            return m_dirChooser.getSelectedFile();
        }

        return null;
    }

    private File chooseExportDir() {
        int returnVal = m_dirChooser.showSaveDialog(m_mainFramePanel);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            return m_dirChooser.getSelectedFile();
        }

        return null;
    }

    private File chooseExportFile(final String defaultName) {
        m_fileChooser.setSelectedFile(new File(defaultName));
        int returnVal = m_fileChooser.showSaveDialog(m_mainFramePanel);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
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
        NonLinearBookFacade facade = editorInfo.getPaneNlbFacade();
        m_undoButton.setEnabled(facade.canUndo());
        m_redoButton.setEnabled(facade.canRedo());
        m_pullButton.setEnabled(!facade.hasChanges());
        NonLinearBook nlb = facade.getNlb();
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
        builder.append("Page timer variable count: ").append(variableStats.getPageTimerVariablesCount());
        builder.append("\r\n");
        builder.append("Obj variable count: ").append(variableStats.getObjVariablesCount());
        builder.append("\r\n");
        builder.append("Obj constraints count: ").append(variableStats.getObjConstraintsCount());
        builder.append("\r\n");
        builder.append("Obj refs count: ").append(variableStats.getObjRefsCount());
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
        builder.append("Autowire constraint count: ").append(variableStats.getAutowireConstraintCount());
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
