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
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.secrets.RemoteCredentials;
import com.nlbhub.nlb.vcs.secrets.SecretStoreException;
import com.nlbhub.nlb.util.BareBonesBrowserLaunch;
import com.nlbhub.nlb.web.Launcher;
import com.nlbhub.nlb.web.player.BookLibrary;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private JButton m_cloneFileButton;
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

    /**
     * Pull или push по https с учётными данными: сохранённые (имя — в настройках, пароль или токен —
     * в системном хранилище секретов) или введённые автором. Сервер их не принял — сохранённый
     * токен забывается, и автора спрашивают снова
     */
    class TransferTask extends Task {
        private final boolean m_push;
        private final String m_server;
        private final String m_userName;
        private final String m_password;
        private final boolean m_fromStore;
        private final boolean m_remember;
        private final PropertyChangeListener m_listener;
        private final boolean m_ssh;
        private Exception m_error = null;

        TransferTask(boolean push, String server, String userName, String password, boolean fromStore, boolean remember,
                     PropertyChangeListener listener, boolean ssh) {
            m_ssh = ssh;
            m_push = push;
            m_server = server;
            m_userName = userName;
            m_password = password;
            m_fromStore = fromStore;
            m_remember = remember;
            m_listener = listener;
        }

        @Override
        protected Void doInBackground() throws Exception {
            setProgress(0);
            try {
                final NonLinearBookFacade facade = getMainPaneInfo().getPaneNlbFacade();
                if (m_push) {
                    facade.push(m_userName, m_password, this);
                } else {
                    facade.pull(m_userName, m_password, this);
                }
                setProgressValue(100);
            } catch (Exception ex) {
                m_error = ex;
            }
            return null;
        }

        @Override
        public void done() {
            super.done();
            (m_push ? m_pushButton : m_pullButton).setEnabled(true);
            final String action = m_push ? "Push" : "Pull";
            if (m_error instanceof NLBVCSAuthenticationException && m_ssh) {
                // ssh: токен ни при чём — ключ не подошёл или его нет
                JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "<html>The server did not accept your SSH key.<br><br>"
                                + "Make sure the public key (for example ~/.ssh/id_ed25519.pub) is added to your account on the server<br>"
                                + "(GitHub: Settings -&gt; SSH and GPG keys -&gt; New SSH key), or that the key is loaded into ssh-agent.<br>"
                                + "No key yet? Create one: ssh-keygen -t ed25519 (Windows, macOS and Linux have it).</html>"
                );
            } else if (m_error instanceof NLBVCSAuthenticationException) {
                // сервер не принял: сохранённый токен — забыть; спросить снова
                if (m_fromStore) {
                    credentials().forgetSecret(m_server, m_userName);
                }
                startTransfer(m_push, m_fromStore
                        ? "The server did not accept the saved password or token. Please enter it again."
                        : "The server did not accept the user name and password or token. Please check them.");
                return;
            }
            if (m_error == null && m_server != null) {
                try {
                    credentials().remember(m_server, m_userName, m_remember ? m_password : null);
                } catch (SecretStoreException ex) {
                    JOptionPane.showMessageDialog(
                            m_mainFramePanel,
                            action + " is done, but the password or token was not saved: " + ex.getMessage()
                    );
                }
            }
            if (m_error != null && !(m_error instanceof NLBVCSAuthenticationException)) {
                JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while " + (m_push ? "pushing" : "pulling") + ": " + m_error.toString()
                );
            }
            if (!m_push) {
                // книга после pull — заново с диска
                m_task = new OpenTask(getMainPaneInfo().getPaneNlbFacade().getNlb().getRootDir());
                m_task.addPropertyChangeListener(m_listener);
                m_openFileButton.setEnabled(false);
                m_task.execute();
            }
        }
    }

    /**
     * Скачивание книги с сервера (clone) — без установки git. По https сначала с сохранённым токеном
     * этого сервера или без учётных данных (открытый репозиторий); сервер их потребовал или не
     * принял — диалог и повтор. По ssh — ключи и вопросы, как при pull/push. Затем книга открывается
     */
    class CloneTask extends Task {
        private final DialogClone.Result m_target;
        private final String m_server;
        private final String m_userName;
        private final String m_password;
        private final boolean m_fromStore;
        private final boolean m_remember;
        private final boolean m_ssh;
        private final PropertyChangeListener m_listener;
        private Exception m_error = null;

        CloneTask(DialogClone.Result target, String server, String userName, String password, boolean fromStore,
                  boolean remember, boolean ssh, PropertyChangeListener listener) {
            m_target = target;
            m_server = server;
            m_userName = userName;
            m_password = password;
            m_fromStore = fromStore;
            m_remember = remember;
            m_ssh = ssh;
            m_listener = listener;
        }

        @Override
        protected Void doInBackground() throws Exception {
            setProgress(0);
            try {
                getMainPaneInfo().getPaneNlbFacade().cloneBook(
                        m_target.m_url, m_target.m_dir.getPath(), m_userName, m_password, this
                );
                setProgressValue(100);
            } catch (Exception ex) {
                m_error = ex;
            }
            return null;
        }

        @Override
        public void done() {
            super.done();
            m_cloneFileButton.setEnabled(true);
            if (m_error instanceof NLBVCSAuthenticationException && !m_ssh) {
                if (m_fromStore) {
                    credentials().forgetSecret(m_server, m_userName);
                }
                startClone(m_target, m_userName == null
                        ? "The server needs a user name and a password or token to download this book."
                        : "The server did not accept the user name and password or token. Please check them.");
                return;
            }
            if (m_error instanceof NLBVCSAuthenticationException) {
                JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "<html>The server did not accept your SSH key.<br><br>"
                                + "Make sure the public key (for example ~/.ssh/id_ed25519.pub) is added to your account on the server<br>"
                                + "(GitHub: Settings -&gt; SSH and GPG keys -&gt; New SSH key), or that the key is loaded into ssh-agent.<br>"
                                + "Or clone the book by its https:// address.</html>"
                );
                return;
            }
            if (m_error != null) {
                JOptionPane.showMessageDialog(m_mainFramePanel, "Error while cloning: " + m_error.toString());
                return;
            }
            if (m_server != null && m_userName != null) {
                try {
                    credentials().remember(m_server, m_userName, m_remember ? m_password : null);
                } catch (SecretStoreException ex) {
                    JOptionPane.showMessageDialog(
                            m_mainFramePanel, "The book is cloned, but the password or token was not saved: " + ex.getMessage()
                    );
                }
            }
            // скачанная книга — открыть
            m_progressMonitor = new ProgressMonitor(m_mainFramePanel, "Opening Non-Linear Book", "Initializing...", 0, 100);
            m_progressMonitor.setProgress(0);
            m_progressMonitor.setMillisToDecideToPopup(200);
            m_progressMonitor.setMillisToPopup(200);
            m_task = new OpenTask(m_target.m_dir);
            m_task.addPropertyChangeListener(m_listener);
            m_openFileButton.setEnabled(false);
            m_task.execute();
        }
    }

    /**
     * Clone: message == null — первая попытка (с сохранённым токеном сервера или без учётных
     * данных); иначе — спросить их с этим пояснением
     */
    private void startClone(final DialogClone.Result target, final String message) {
        final String url = target.m_url;
        final boolean ssh = RemoteCredentials.isSsh(url);
        getMainPaneInfo().getPaneNlbFacade().setVcsPrompts(new SwingPrompts(m_mainFramePanel, credentials()));
        final String server = ssh ? null : RemoteCredentials.server(url);
        String user = null;
        String secret = null;
        boolean fromStore = false;
        boolean remember = false;
        if (!ssh) {
            user = credentials().rememberedUser(server);
            if (user == null) {
                user = RemoteCredentials.userFromUrl(url);
            }
            if (message == null) {
                secret = credentials().rememberedSecret(server, user);
                fromStore = secret != null;
                if (!fromStore) {
                    user = null;  // открытый репозиторий — без учётных данных
                }
            } else {
                final DialogGitCredentials.Result result = DialogGitCredentials.ask(
                        m_mainFramePanel, "Clone", server, user, message, credentials().canRememberSecret()
                );
                if (result == null) {
                    return;
                }
                user = result.m_user;
                secret = result.secret();
                remember = result.m_remember;
                result.wipe();
            }
        }
        m_progressMonitor = new ProgressMonitor(m_mainFramePanel, "Cloning Non-Linear Book", "Initializing...", 0, 100);
        m_progressMonitor.setProgress(0);
        m_progressMonitor.setMillisToDecideToPopup(200);
        m_progressMonitor.setMillisToPopup(200);
        m_task = new CloneTask(target, server, user, secret, fromStore, remember, ssh, this);
        m_task.addPropertyChangeListener(this);
        m_cloneFileButton.setEnabled(false);
        m_task.execute();
    }

    /**
     * Хранилище учётных данных git (создаётся при первом pull/push: подгружает системную библиотеку)
     */
    private RemoteCredentials m_credentials = null;

    private RemoteCredentials credentials() {
        if (m_credentials == null) {
            m_credentials = RemoteCredentials.system();
        }
        return m_credentials;
    }

    /**
     * Pull или push: с сохранёнными учётными данными, если они есть и не было отказа (message ==
     * null), иначе — спросить
     */
    private void startTransfer(final boolean push, final String message) {
        final String action = push ? "Push" : "Pull";
        final String url = getMainPaneInfo().getPaneNlbFacade().getRemoteUrl();
        final boolean ssh = RemoteCredentials.isSsh(url);
        // ssh: ключи из ~/.ssh и ssh-agent; пароль к ключу и незнакомый сервер — вопросами в окнах
        getMainPaneInfo().getPaneNlbFacade().setVcsPrompts(new SwingPrompts(m_mainFramePanel, credentials()));
        final String server = RemoteCredentials.server(url);
        String user = credentials().rememberedUser(server);
        if (user == null && url != null) {
            user = RemoteCredentials.userFromUrl(url);
        }
        String secret = message == null && !ssh ? credentials().rememberedSecret(server, user) : null;
        final boolean fromStore = secret != null;
        boolean remember = false;
        if (!fromStore && !ssh) {
            final DialogGitCredentials.Result result = DialogGitCredentials.ask(
                    m_mainFramePanel, action, server, user, message, credentials().canRememberSecret()
            );
            if (result == null) {
                return;
            }
            user = result.m_user;
            secret = result.secret();
            remember = result.m_remember;
            result.wipe();
        }
        m_progressMonitor = new ProgressMonitor(
                m_mainFramePanel,
                push ? "Pushing Non-Linear Book" : "Pulling Non-Linear Book",
                "Initializing...",
                0,
                100
        );
        m_progressMonitor.setProgress(0);
        m_progressMonitor.setMillisToDecideToPopup(200);
        m_progressMonitor.setMillisToPopup(200);
        m_task = new TransferTask(push, server, user, secret, fromStore, remember, this, ssh);
        m_task.addPropertyChangeListener(this);
        (push ? m_pushButton : m_pullButton).setEnabled(false);
        m_task.execute();
    }

    /**
     * Сколько раз книга открывалась в этом запуске: первое открытие — холодное (JVM не
     * разогрета, файлы книги не в кэше диска), его время в логе отличается от повторных
     */
    private int m_openCount = 0;
    /**
     * Сообщение об отказе в правке уже на экране — не показывать второе поверх
     */
    private boolean m_refusedChangeShown = false;

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
                final long start = System.nanoTime();
                clearAll();
                PaneEditorInfo editorInfo = getMainPaneInfo();
                editorInfo.getPaneGraphEditor().load(m_file, this);
                setProgressValue(85);
                setNoteText("Opening panes...");
                final long panesStart = System.nanoTime();
                openModulesPanes();
                final long done = System.nanoTime();
                LOGGER.info(
                        "Book {} opened in {} ms (module panes: {} ms), opening #{} in this run",
                        m_file, (done - start) / 1000000, (done - panesStart) / 1000000, ++m_openCount
                );
                setNoteText("All done!");
                setProgressValue(100);
            } catch (NLBVCSException | NLBConsistencyException | NLBIOException ex) {
                JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "Error while loading: " + ex.toString()
                );
            } catch (RuntimeException ex) {
                // иначе исключение пропадает в SwingWorker без следа
                LOGGER.error("Error while loading " + m_file, ex);
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
        // правка во вкладке внешнего модуля (копия, не сохраняется) не принимается — объяснить почему
        NonLinearBookFacade.setRefusedChangeNotifier(reason -> SwingUtilities.invokeLater(() -> {
            if (!m_refusedChangeShown) {
                m_refusedChangeShown = true;
                JOptionPane.showMessageDialog(
                        m_mainFramePanel,
                        "<html><body style='width: 420px'>" + reason + "</body></html>",  // с переносом строк
                        "Read-only module",
                        JOptionPane.WARNING_MESSAGE
                );
                m_refusedChangeShown = false;
            }
        }));
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
        m_cloneFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    final DialogClone.Result target = DialogClone.ask(m_mainFramePanel);
                    if (target != null) {
                        startClone(target, null);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(m_mainFramePanel, "Error while cloning: " + ex.toString());
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
                    startTransfer(false, null);
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
                    // от чьего имени коммит: не задано ни в репозитории книги, ни в общих
                    // настройках git — спросить один раз (запишется в .git/config книги)
                    final NonLinearBookFacade facade = getMainPaneInfo().getPaneNlbFacade();
                    if (facade.canSetCommitter() && facade.getCommitter() == null && !askCommitter(facade)) {
                        return;
                    }
                    DialogCommit dialog = new DialogCommit();
                    dialog.showDialog();
                    if (dialog.isOk()) {
                        facade.commit(dialog.getCommitMessageText());
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
                    startTransfer(true, null);
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
                        String bookId = (rootDir != null) ? rootDir.getName() : DEFAULT_BOOK_ID;
                        m_launcher.clearNLBCache();
                        // Always prefer in-memory data
                        m_launcher.putNLBInMemoryToCache(bookId, mainNLB);
                        Thread thread = new Thread(m_launcher);
                        thread.start();
                        Thread.sleep(START_SERVER_TIMEOUT);
                        // веб-плеер: в списке книг сервера редактора — только эта книга
                        BareBonesBrowserLaunch.openURL(
                                "http://localhost:8111/play/?book=" + URLEncoder.encode(bookId, StandardCharsets.UTF_8)
                                        + "&mode=" + BookLibrary.defaultMode(mainNLB)
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
            // Сначала — на главную вкладку: при удалении остальных вкладок слушатель смены вкладки
            // ищет выбранную в m_paneEditorInfoMap, а та уже очищена. С вкладки модуля это давало
            // NullPointerException посреди очистки (открытие книги молча обрывалось: граф вкладки
            // пустел, а новая книга не открывалась)
            m_graphEditorsPane.setSelectedIndex(0);
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

    /**
     * Спросить имя и e-mail для коммитов и записать их в настройки репозитория книги
     * (.git/config — у каждого соавтора свои; их же можно поменять в свойствах книги)
     */
    private boolean askCommitter(final NonLinearBookFacade facade) throws NLBVCSException {
        final JTextField name = new JTextField(30);
        final JTextField email = new JTextField(30);
        final JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.add(new JLabel("Commits need your name and e-mail. They are stored in the book's .git/config"));
        panel.add(new JLabel("(only in your copy of the book) and can be changed in the book properties."));
        panel.add(new JLabel("Name"));
        panel.add(name);
        panel.add(new JLabel("E-mail"));
        panel.add(email);
        while (true) {
            final int answer = JOptionPane.showConfirmDialog(
                    m_mainFramePanel, panel, "Commit author", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
            );
            if (answer != JOptionPane.OK_OPTION) {
                return false;
            }
            if (!name.getText().trim().isEmpty() && !email.getText().trim().isEmpty()) {
                facade.setCommitter(new Author(name.getText().trim(), email.getText().trim()));
                return true;
            }
            JOptionPane.showMessageDialog(m_mainFramePanel, "Please enter both name and e-mail");
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
        addGraphEditorTab(tabTitle(page.getModuleName(), paneEditorInfo), paneEditorInfo);

        return paneEditorInfo;
    }

    /**
     * Название вкладки модуля: внешний модуль, который правится прямо в книге (сохраняется в
     * modules/<имя>), — «(external)»; копия только для чтения — «(read-only)»
     */
    private static String tabTitle(final String moduleName, final PaneEditorInfo paneEditorInfo) {
        final NonLinearBookFacade facade = paneEditorInfo.getPaneNlbFacade();
        if (facade.getReadOnlyReason() != null) {
            return moduleName + " (read-only)";
        }
        return moduleName + (facade.getEditedExternalModuleName() != null ? " (external)" : "");
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
                    paneEditorInfo.getTabComponent().setTabName(tabTitle(info.getModuleName(), paneEditorInfo));
                } else {
                    // Do not reopen manually closed tabs
                    if (!paneEditorInfo.isClosedManually()) {
                        addGraphEditorTab(tabTitle(info.getModuleName(), paneEditorInfo), paneEditorInfo);
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
        // вкладки уже нет в таблице (книга закрывается или открывается другая) — главная
        return getMainPaneInfo();
    }

    private PaneEditorInfo getMainPaneInfo() {
        return m_paneEditorInfoMap.get(MAIN_PANE_KEY);
    }
}
