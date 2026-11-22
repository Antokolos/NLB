package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.domain.BookHistory;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.vcs.BookFileChange;
import com.nlbhub.nlb.vcs.BookVersion;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * «History»: сохранённые версии книги (коммиты) и что в каждой изменилось — словами книги.
 * Слева — версии от новых к старым (первой строкой — правки, которые войдут в следующий Commit),
 * с пометкой «только на этом компьютере / на сервере». Справа — изменения выбранной версии по
 * элементам книги ({@link BookHistory}): страница, объект, ссылка… и их поля; перемещения на
 * схеме и порядок — свёрнуты в одну строку, большие группы («Added 120 images») — тоже. Внизу —
 * выбранное поле: текст «было / стало» с подсветкой слов, картинка «было / стало», остальное —
 * таблицей. Только просмотр: ни переключения на старую версию, ни отката.
 */
final class DialogHistory {
    /**
     * Версий за раз (дальше — строка «Show older versions…»)
     */
    private static final int PAGE = 100;
    /**
     * Больше элементов одного вида — одной строкой-группой
     */
    private static final int GROUP = 20;
    private static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;
    private static final int THUMBNAIL = 360;
    private static final String CARD_MESSAGE = "message";
    private static final String CARD_TEXT = "text";
    private static final String CARD_IMAGE = "image";
    private static final String CARD_TABLE = "table";

    /**
     * Строка списка версий: несохранённые правки, версия или «показать старше»
     */
    private static final class Row {
        final BookVersion m_version;
        final boolean m_uncommitted;
        final boolean m_more;

        Row(BookVersion version, boolean uncommitted, boolean more) {
            m_version = version;
            m_uncommitted = uncommitted;
            m_more = more;
        }

        String key() {
            return m_uncommitted ? "" : m_version.getId();
        }
    }

    /**
     * Узел дерева изменений: группа, элемент или поле (поля с одним названием — одним узлом:
     * четыре файла положения на схеме — одна строка)
     */
    private static final class Node {
        final String m_html;
        final BookHistory.ElementChange m_element;
        final List<BookHistory.FieldChange> m_fields;

        Node(String html, BookHistory.ElementChange element, List<BookHistory.FieldChange> fields) {
            m_html = html;
            m_element = element;
            m_fields = fields;
        }

        @Override
        public String toString() {
            return m_html;
        }
    }

    private final NonLinearBookFacade m_facade;
    private final JDialog m_dialog;
    private final DefaultListModel<Row> m_versions = new DefaultListModel<>();
    private final JList<Row> m_versionList = new JList<>(m_versions);
    private final DefaultTreeModel m_treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree m_tree = new JTree(m_treeModel);
    private final JLabel m_status = new JLabel(" ");
    private final CardLayout m_cards = new CardLayout();
    private final JPanel m_detail = new JPanel(m_cards);
    private final JLabel m_message = new JLabel("", SwingConstants.CENTER);
    private final JLabel m_beforeHeader = new JLabel();
    private final JLabel m_afterHeader = new JLabel();
    private final JTextPane m_before = new JTextPane();
    private final JTextPane m_after = new JTextPane();
    private final JLabel m_imageBeforeHeader = new JLabel("Before");
    private final JLabel m_imageAfterHeader = new JLabel("After");
    private final JLabel m_imageBefore = new JLabel("", SwingConstants.CENTER);
    private final JLabel m_imageAfter = new JLabel("", SwingConstants.CENTER);
    private final JEditorPane m_table = new JEditorPane("text/html", "");
    private final Map<String, List<BookHistory.ElementChange>> m_changes = new HashMap<>();
    /**
     * Порядковый номер запроса изменений: ответ на прежний выбор (пользователь уже выбрал другую
     * версию) не показывается
     */
    private int m_request = 0;
    private int m_loaded = 0;

    private DialogHistory(Component parent, NonLinearBookFacade facade) {
        m_facade = facade;
        final Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        m_dialog = new JDialog(owner, "History of the book", Dialog.ModalityType.APPLICATION_MODAL);

        final JLabel intro = new JLabel(intro());
        intro.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        m_versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        m_versionList.setCellRenderer(new VersionRenderer());
        m_versionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectVersion(m_versionList.getSelectedValue());
            }
        });
        final JScrollPane versionScroll = new JScrollPane(m_versionList);
        versionScroll.setPreferredSize(new Dimension(360, 500));

        m_tree.setRootVisible(false);
        m_tree.setShowsRootHandles(true);
        m_tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                setIcon(null);
                return this;
            }
        });
        m_tree.addTreeSelectionListener(e -> showNode(selectedNode()));
        final JScrollPane treeScroll = new JScrollPane(m_tree);
        treeScroll.setPreferredSize(new Dimension(620, 260));

        for (JTextPane pane : new JTextPane[]{m_before, m_after}) {
            pane.setEditable(false);
        }
        final JPanel text = new JPanel(new GridLayout(1, 2, 8, 0));
        text.add(side(m_beforeHeader, new JScrollPane(m_before)));
        text.add(side(m_afterHeader, new JScrollPane(m_after)));
        final JPanel image = new JPanel(new GridLayout(1, 2, 8, 0));
        image.add(side(m_imageBeforeHeader, new JScrollPane(m_imageBefore)));
        image.add(side(m_imageAfterHeader, new JScrollPane(m_imageAfter)));
        m_table.setEditable(false);
        m_message.setForeground(Color.GRAY);
        m_detail.add(m_message, CARD_MESSAGE);
        m_detail.add(text, CARD_TEXT);
        m_detail.add(image, CARD_IMAGE);
        m_detail.add(new JScrollPane(m_table), CARD_TABLE);
        m_detail.setPreferredSize(new Dimension(620, 260));
        message("Select a version on the left, then a change above.");

        final JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScroll, m_detail);
        right.setResizeWeight(0.5);
        final JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, versionScroll, right);
        main.setResizeWeight(0.35);

        final JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadVersions(true));
        final JButton close = new JButton("Close");
        close.addActionListener(e -> m_dialog.dispose());
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(refresh);
        buttons.add(close);
        final JPanel bottom = new JPanel(new BorderLayout());
        m_status.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        bottom.add(m_status, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);

        final JPanel content = new JPanel(new BorderLayout());
        content.add(intro, BorderLayout.NORTH);
        content.add(main, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        m_dialog.setContentPane(content);
        m_dialog.getRootPane().setDefaultButton(close);
        m_dialog.getRootPane().registerKeyboardAction(e -> m_dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        m_dialog.pack();
        m_dialog.setLocationRelativeTo(owner);
        loadVersions(true);
    }

    /**
     * Показать историю книги (модально: пока окно открыто, книга не меняется)
     */
    static void show(Component parent, NonLinearBookFacade facade) {
        new DialogHistory(parent, facade).m_dialog.setVisible(true);
    }

    private String intro() {
        String branch = null;
        try {
            branch = m_facade.getBranchName();
        } catch (Exception e) {
            // нет ветки — нет и предупреждения
        }
        final StringBuilder html = new StringBuilder("<html>Saved versions of the book, newest first. "
                + "<b>Commit</b> saves a version on this computer; <b>Push</b> sends it to the server, "
                + "<b>Pull</b> gets the versions of your co-authors.");
        if (branch != null && !branch.equals("master") && !branch.equals("main")) {
            html.append("<br><font color='#b45309'>&#9888; The book is open on the branch <b>")
                    .append(escape(branch)).append("</b>, not on the main one.</font>");
        }
        return html.append("</html>").toString();
    }

    private static JPanel side(JLabel header, JComponent body) {
        final JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(header, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    // ---------------------------------------------------------------- версии

    /**
     * Загрузить версии: с начала (первой строкой — несохранённые правки, если есть) или
     * следующую порцию
     */
    private void loadVersions(final boolean fromStart) {
        final int offset = fromStart ? 0 : m_loaded;
        m_status.setText("Loading versions…");
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                final boolean uncommitted = fromStart && m_facade.hasUncommittedChanges();
                return new Object[]{uncommitted, m_facade.getHistory(offset, PAGE)};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                try {
                    final Object[] result = get();
                    final List<BookVersion> versions = (List<BookVersion>) result[1];
                    if (fromStart) {
                        m_versions.clear();
                        m_changes.clear();
                        m_loaded = 0;
                        if ((Boolean) result[0]) {
                            m_versions.addElement(new Row(null, true, false));
                        }
                    } else if (!m_versions.isEmpty() && m_versions.lastElement().m_more) {
                        m_versions.removeElementAt(m_versions.size() - 1);
                    }
                    for (BookVersion version : versions) {
                        m_versions.addElement(new Row(version, false, false));
                    }
                    m_loaded += versions.size();
                    if (versions.size() == PAGE) {
                        m_versions.addElement(new Row(null, false, true));
                    }
                    if (m_versions.isEmpty()) {
                        m_status.setText("No saved versions: the book is not under version control or nothing is committed yet.");
                        clearChanges("Nothing to show.");
                        return;
                    }
                    m_status.setText(m_loaded + " version" + (m_loaded == 1 ? "" : "s"));
                    if (fromStart) {
                        m_versionList.setSelectedIndex(0);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    m_status.setText("Cannot read the history: " + cause(e));
                }
            }
        }.execute();
    }

    private void selectVersion(final Row row) {
        if (row == null) {
            return;
        }
        if (row.m_more) {
            loadVersions(false);
            return;
        }
        final String key = row.key();
        final int request = ++m_request;
        final List<BookHistory.ElementChange> cached = m_changes.get(key);
        if (cached != null) {
            showChanges(cached);
            return;
        }
        clearChanges("Reading the changes…");
        new SwingWorker<List<BookHistory.ElementChange>, Void>() {
            @Override
            protected List<BookHistory.ElementChange> doInBackground() throws Exception {
                return m_facade.getVersionChanges(row.m_uncommitted ? null : row.m_version.getId());
            }

            @Override
            protected void done() {
                try {
                    final List<BookHistory.ElementChange> changes = get();
                    m_changes.put(key, changes);
                    if (request == m_request) {
                        showChanges(changes);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    if (request == m_request) {
                        clearChanges("Cannot read the changes: " + cause(e));
                    }
                }
            }
        }.execute();
    }

    // ---------------------------------------------------------------- изменения

    private void clearChanges(String text) {
        m_treeModel.setRoot(new DefaultMutableTreeNode());
        message(text);
    }

    private void showChanges(List<BookHistory.ElementChange> changes) {
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        final List<BookHistory.ElementChange> significant = new ArrayList<>();
        final List<BookHistory.ElementChange> noise = new ArrayList<>();
        for (BookHistory.ElementChange change : changes) {
            (change.isNoiseOnly() ? noise : significant).add(change);
        }
        // большие группы одного вида — одной строкой («Added 120 images»)
        final Map<String, List<BookHistory.ElementChange>> groups = new LinkedHashMap<>();
        for (BookHistory.ElementChange change : significant) {
            groups.computeIfAbsent(change.getType() + " " + change.getKind(), k -> new ArrayList<>()).add(change);
        }
        for (List<BookHistory.ElementChange> group : groups.values()) {
            if (group.size() > GROUP) {
                final BookHistory.ElementChange first = group.get(0);
                final DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Node(
                        "<html>" + kindTag(first.getKind()) + " " + group.size() + " " + plural(first.getType())
                                + "</html>", null, null));
                for (BookHistory.ElementChange change : group) {
                    node.add(elementNode(change));
                }
                root.add(node);
            } else {
                for (BookHistory.ElementChange change : group) {
                    root.add(elementNode(change));
                }
            }
        }
        if (!noise.isEmpty()) {
            final DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Node(
                    "<html><font color='gray'>Moved on the scheme or reordered: " + noise.size() + " item"
                            + (noise.size() == 1 ? "" : "s") + " (does not change the game)</font></html>", null, null));
            for (BookHistory.ElementChange change : noise) {
                node.add(elementNode(change));
            }
            root.add(node);
        }
        m_treeModel.setRoot(root);
        // раскрыть элементы верхнего уровня, если их немного (группы и шум — свёрнуты)
        if (root.getChildCount() <= 30) {
            for (int i = 0; i < root.getChildCount(); i++) {
                final DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
                final Node node = (Node) child.getUserObject();
                if (node.m_element != null) {
                    m_tree.expandPath(new TreePath(child.getPath()));
                }
            }
        }
        if (changes.isEmpty()) {
            message("No changes in this version.");
        } else {
            message("Select a change above to see it.");
            final int count = significant.size();
            m_status.setText(count + " change" + (count == 1 ? "" : "s")
                    + (noise.isEmpty() ? "" : " (+" + noise.size() + " moved on the scheme or reordered)"));
        }
    }

    private DefaultMutableTreeNode elementNode(BookHistory.ElementChange change) {
        final DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Node(
                "<html>" + kindTag(change.getKind()) + " " + escape(change.getTitle()) + "</html>", change,
                change.getFields()));
        // поля с одним названием — одной строкой
        final Map<String, List<BookHistory.FieldChange>> byLabel = new LinkedHashMap<>();
        for (BookHistory.FieldChange field : change.getFields()) {
            byLabel.computeIfAbsent(field.getLabel(), k -> new ArrayList<>()).add(field);
        }
        for (Map.Entry<String, List<BookHistory.FieldChange>> entry : byLabel.entrySet()) {
            final BookHistory.FieldChange first = entry.getValue().get(0);
            final String label = entry.getKey().isEmpty() ? "contents" : entry.getKey();
            final String html = "<html>" + (first.isNoise() ? "<font color='gray'>" : "") + escape(label) + " — "
                    + fieldVerb(first.getKind()) + (first.isNoise() ? "</font>" : "") + "</html>";
            node.add(new DefaultMutableTreeNode(new Node(html, change, entry.getValue())));
        }
        return node;
    }

    private Node selectedNode() {
        final TreePath path = m_tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        final Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return value instanceof Node ? (Node) value : null;
    }

    /**
     * Внизу — выбранное: поле с текстом — «было / стало» со словами; картинка — миниатюры;
     * элемент или несколько полей — таблицей
     */
    private void showNode(Node node) {
        if (node == null || node.m_fields == null) {
            message(node == null ? "Select a change above to see it." : "Expand the group to see its items.");
            return;
        }
        if (node.m_fields.size() == 1) {
            final BookHistory.FieldChange field = node.m_fields.get(0);
            final BookHistory.Type type = node.m_element.getType();
            if (field.isBinary() && field.getFieldPath().isEmpty() && type == BookHistory.Type.IMAGE) {
                showImages(field);
                return;
            }
            if (field.isBinary() || field.getFile().isSubmodule()) {
                message(binaryMessage(node.m_element, field));
                return;
            }
            m_beforeHeader.setText(field.getKind() == BookFileChange.Kind.ADDED ? "Before: (none)" : "Before");
            m_afterHeader.setText(field.getKind() == BookFileChange.Kind.DELETED ? "After: (deleted)" : "After");
            WordDiff.show(m_before, nonNull(field.getDisplayBefore()), m_after, nonNull(field.getDisplayAfter()));
            m_cards.show(m_detail, CARD_TEXT);
            return;
        }
        final StringBuilder html = new StringBuilder("<html><body style='font-family:sans-serif'>");
        html.append("<p><b>").append(escape(node.m_element.getTitle())).append("</b> — ")
                .append(fieldVerb(node.m_element.getKind())).append("</p>");
        html.append("<table border='0' cellspacing='0' cellpadding='3'>")
                .append("<tr><th align='left'>Field</th><th align='left'>Before</th><th align='left'>After</th></tr>");
        for (BookHistory.FieldChange field : node.m_fields) {
            final String color = field.isNoise() ? " style='color:gray'" : "";
            html.append("<tr").append(color).append("><td valign='top'>").append(escape(fieldName(field)))
                    .append("</td><td valign='top'>").append(cell(field, true))
                    .append("</td><td valign='top'>").append(cell(field, false)).append("</td></tr>");
        }
        html.append("</table></body></html>");
        m_table.setText(html.toString());
        m_table.setCaretPosition(0);
        m_cards.show(m_detail, CARD_TABLE);
    }

    private static String fieldName(BookHistory.FieldChange field) {
        final String label = field.getLabel().isEmpty() ? "contents" : field.getLabel();
        // положение на схеме — четыре файла: left, top, width, height
        if (field.getFieldPath().startsWith("coords/")) {
            return label + " (" + field.getFieldPath().substring("coords/".length()) + ")";
        }
        return label;
    }

    private static String cell(BookHistory.FieldChange field, boolean before) {
        if (field.isBinary() || field.getFile().isSubmodule()) {
            final boolean exists = before ? field.getKind() != BookFileChange.Kind.ADDED
                    : field.getKind() != BookFileChange.Kind.DELETED;
            return exists ? "<i>file</i>" : "—";
        }
        final String value = before ? field.getDisplayBefore() : field.getDisplayAfter();
        if (value == null) {
            return "—";
        }
        String text = value.trim();
        if (field.getLabel().endsWith("flag")) {
            text = "true".equals(text) ? "on" : "false".equals(text) ? "off" : text;
        }
        if (text.length() > 300) {
            text = text.substring(0, 299) + "…";
        }
        return escape(text).replace("\n", "<br>");
    }

    private void showImages(BookHistory.FieldChange field) {
        m_imageBefore.setIcon(null);
        m_imageAfter.setIcon(null);
        m_imageBefore.setText("Loading…");
        m_imageAfter.setText("Loading…");
        m_imageBeforeHeader.setText("Before");
        m_imageAfterHeader.setText("After");
        m_cards.show(m_detail, CARD_IMAGE);
        final int request = ++m_request;
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                final BufferedImage before = image(field.getFile().getBeforeObjectId());
                final BufferedImage after = image(field.getFile().getAfterObjectId());
                return new Object[]{before, thumbnail(before), after, thumbnail(after)};
            }

            @Override
            protected void done() {
                if (request != m_request) {
                    return;
                }
                try {
                    final Object[] result = get();
                    // размер — уменьшенная картинка на глаз не отличается от прежней
                    m_imageBeforeHeader.setText("Before" + size((BufferedImage) result[0]));
                    m_imageAfterHeader.setText("After" + size((BufferedImage) result[2]));
                    setImage(m_imageBefore, (Icon) result[1], field.getKind() == BookFileChange.Kind.ADDED ? "(none)" : "(cannot show)");
                    setImage(m_imageAfter, (Icon) result[3], field.getKind() == BookFileChange.Kind.DELETED ? "(deleted)" : "(cannot show)");
                } catch (InterruptedException | ExecutionException e) {
                    message("Cannot read the image: " + cause(e));
                }
            }
        }.execute();
    }

    private static void setImage(JLabel label, Icon icon, String missing) {
        label.setIcon(icon);
        label.setText(icon == null ? missing : null);
    }

    private static String size(BufferedImage image) {
        return image == null ? "" : " (" + image.getWidth() + "×" + image.getHeight() + ")";
    }

    private BufferedImage image(String objectId) throws Exception {
        if (objectId == null) {
            return null;
        }
        final byte[] bytes = m_facade.readVersionObject(objectId, MAX_IMAGE_BYTES);
        return bytes == null ? null : ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static Icon thumbnail(BufferedImage image) {
        if (image == null) {
            return null;
        }
        final double k = Math.min(1.0, (double) THUMBNAIL / Math.max(image.getWidth(), image.getHeight()));
        final int w = Math.max(1, (int) Math.round(image.getWidth() * k));
        final int h = Math.max(1, (int) Math.round(image.getHeight() * k));
        return new ImageIcon(image.getScaledInstance(w, h, Image.SCALE_SMOOTH));
    }

    private static String binaryMessage(BookHistory.ElementChange element, BookHistory.FieldChange field) {
        if (field.getFile().isSubmodule()) {
            return "<html>The external module is updated to another version.<br>"
                    + "Its own history is in the module's repository.</html>";
        }
        final String what = element.getType() == BookHistory.Type.SOUND ? "The sound file" : "The file";
        switch (field.getKind()) {
            case ADDED:
                return what + " is added.";
            case DELETED:
                return what + " is deleted.";
            default:
                return what + " is replaced.";
        }
    }

    private void message(String text) {
        m_message.setText(text);
        m_cards.show(m_detail, CARD_MESSAGE);
    }

    // ---------------------------------------------------------------- слова

    private static String kindTag(BookFileChange.Kind kind) {
        switch (kind) {
            case ADDED:
                return "<font color='#15803d'><b>Added</b></font>";
            case DELETED:
                return "<font color='#b91c1c'><b>Deleted</b></font>";
            default:
                return "<font color='#1d4ed8'><b>Changed</b></font>";
        }
    }

    private static String fieldVerb(BookFileChange.Kind kind) {
        switch (kind) {
            case ADDED:
                return "added";
            case DELETED:
                return "deleted";
            default:
                return "changed";
        }
    }

    private static String plural(BookHistory.Type type) {
        switch (type) {
            case BOOK:
                return "book settings";
            case PAGE:
                return "pages";
            case LINK:
                return "links";
            case OBJECT:
                return "objects";
            case MODIFICATION:
                return "modifications";
            case VARIABLE:
                return "variables and expressions";
            case IMAGE:
                return "images";
            case SOUND:
                return "sounds";
            case MODULE:
                return "external modules";
            default:
                return "files";
        }
    }

    /**
     * «today, 14:32», «yesterday, 09:05», «12 Nov 2026, 18:40»
     */
    static String when(long time, LocalDate today) {
        final Date date = new Date(time);
        final LocalDate day = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        final String clock = new SimpleDateFormat("HH:mm", Locale.ENGLISH).format(date);
        if (day.equals(today)) {
            return "today, " + clock;
        }
        if (day.equals(today.minusDays(1))) {
            return "yesterday, " + clock;
        }
        return new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(date) + ", " + clock;
    }

    /**
     * Строка версии: сообщение и «кто, когда, где»
     */
    static String versionHtml(BookVersion version, LocalDate today) {
        final StringBuilder html = new StringBuilder("<html>");
        if (version.isFromServer()) {
            html.append("<b>Changes from the server</b>");
            final List<String> authors = version.getServerAuthors();
            if (!authors.isEmpty()) {
                html.append(" (").append(escape(String.join(", ", authors))).append(", ")
                        .append(version.getServerVersions()).append(" version")
                        .append(version.getServerVersions() == 1 ? "" : "s").append(")");
            }
        } else {
            final String message = version.getShortMessage();
            html.append("<b>").append(escape(message.isEmpty() ? "(no description)" : message)).append("</b>");
        }
        html.append("<br><font color='gray'>").append(escape(when(version.getTime(), today)));
        if (!version.isFromServer() && !version.getAuthorName().isEmpty()) {
            html.append(" · ").append(escape(version.getAuthorName()));
        }
        html.append("</font>");
        switch (version.getServerState()) {
            case LOCAL_ONLY:
                html.append(" · <font color='#b45309'>only on this computer</font>");
                break;
            case ON_SERVER:
                html.append(" · <font color='#15803d'>on the server</font>");
                break;
            default:
                break;
        }
        return html.append("</html>").toString();
    }

    private final class VersionRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                      boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            final Row row = (Row) value;
            if (row.m_more) {
                setText("<html><i>Show older versions…</i></html>");
            } else if (row.m_uncommitted) {
                setText("<html><b>Not committed yet</b><br><font color='gray'>saved changes that the next "
                        + "<b>Commit</b> will put into a version</font></html>");
            } else {
                setText(versionHtml(row.m_version, LocalDate.now()));
                setToolTipText(row.m_version.getMessage().contains("\n")
                        ? "<html>" + escape(row.m_version.getMessage()).replace("\n", "<br>") + "</html>" : null);
            }
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE5E7EB)),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            return this;
        }
    }

    private static String nonNull(String text) {
        return text == null ? "" : text;
    }

    private static String cause(Exception e) {
        final Throwable cause = e.getCause() != null ? e.getCause() : e;
        return String.valueOf(cause.getMessage());
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
