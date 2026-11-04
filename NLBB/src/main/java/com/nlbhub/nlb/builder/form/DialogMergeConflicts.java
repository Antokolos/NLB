package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.domain.BookFilePaths;
import com.nlbhub.nlb.vcs.ConflictField;
import com.nlbhub.nlb.vcs.ConflictResolution;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «Слияние правок»: pull нашёл поля книги, изменённые и у автора, и на сервере. Слева — список
 * полей словами книги (с отметкой, решено ли), справа — два варианта целиком с подсвеченными
 * различиями слов и итог, который можно править: «моё», «с сервера», «оба подряд» или свой
 * текст. Удалено на одной стороне — «оставить» или «удалить» (элемент целиком). Пока не решено
 * всё, слить нельзя; «Cancel pull» — ничего не менять.
 */
final class DialogMergeConflicts {
    private static final Color REMOVED = new Color(0xFFD6D6);
    private static final Color ADDED = new Color(0xD3F5D3);

    /**
     * Решение по полю и итоговый текст (для правки)
     */
    private static final class Item {
        final ConflictField m_field;
        final String m_label;
        ConflictResolution m_resolution;
        String m_result;

        Item(ConflictField field, String label) {
            m_field = field;
            m_label = label;
        }

        boolean textual() {
            return !m_field.isBinary() && !m_field.isDeletion();
        }

        @Override
        public String toString() {
            final String mark;
            if (m_resolution == null) {
                mark = "?";
            } else if (m_resolution.getKind() == ConflictResolution.Kind.MINE) {
                mark = "mine";
            } else if (m_resolution.getKind() == ConflictResolution.Kind.THEIRS) {
                mark = "server";
            } else {
                mark = "edited";
            }
            return "[" + mark + "] " + m_label;
        }
    }

    private final JDialog m_dialog;
    private final List<Item> m_items = new ArrayList<>();
    private final DefaultListModel<Item> m_model = new DefaultListModel<>();
    private final JList<Item> m_list = new JList<>(m_model);
    private final JLabel m_title = new JLabel();
    private final JLabel m_mineHeader = new JLabel();
    private final JLabel m_theirsHeader = new JLabel();
    private final JTextPane m_mine = new JTextPane();
    private final JTextPane m_theirs = new JTextPane();
    private final JTextArea m_result = new JTextArea(5, 40);
    private final JButton m_useMine = new JButton();
    private final JButton m_useTheirs = new JButton();
    private final JButton m_useBoth = new JButton("Both (mine, then server's)");
    private final JButton m_merge = new JButton("Merge");
    private final JLabel m_progress = new JLabel();
    private boolean m_updating = false;
    private boolean m_ok = false;

    private DialogMergeConflicts(Component parent, List<ConflictField> fields, NonLinearBook book) {
        final Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        m_dialog = new JDialog(owner, "Merge changes", Dialog.ModalityType.APPLICATION_MODAL);
        String coauthor = null;
        for (ConflictField field : fields) {
            final Item item = new Item(field, BookFilePaths.describe(book, field.getPath()));
            m_items.add(item);
            m_model.addElement(item);
            if (coauthor == null) {
                coauthor = field.getTheirAuthor();
            }
        }
        final JLabel intro = new JLabel("<html>You and " + escape(coauthor != null ? coauthor : "your co-author")
                + " changed the same parts of the book. <b>Nothing is changed yet.</b><br>"
                + "Choose a version for each part (or edit the result). All other changes are merged as usual.</html>");
        intro.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        m_list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        m_list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                show(m_list.getSelectedValue());
            }
        });
        final JScrollPane listScroll = new JScrollPane(m_list);
        listScroll.setPreferredSize(new Dimension(340, 400));

        for (JTextPane pane : new JTextPane[]{m_mine, m_theirs}) {
            pane.setEditable(false);
        }
        m_result.setLineWrap(true);
        m_result.setWrapStyleWord(true);
        m_result.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                edited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                edited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                edited();
            }
        });
        m_useMine.addActionListener(e -> decide(ConflictResolution.mine()));
        m_useTheirs.addActionListener(e -> decide(ConflictResolution.theirs()));
        m_useBoth.addActionListener(e -> {
            final Item item = m_list.getSelectedValue();
            if (item != null) {
                setResult(item.m_field.getMine().trim() + "\n" + item.m_field.getTheirs().trim());
            }
        });

        final JPanel sides = new JPanel(new GridLayout(1, 2, 8, 0));
        sides.add(side(m_mineHeader, m_mine, m_useMine));
        sides.add(side(m_theirsHeader, m_theirs, m_useTheirs));
        final JPanel resultPanel = new JPanel(new BorderLayout(4, 4));
        final JPanel resultTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        resultTop.add(new JLabel("Result (you can edit it):  "));
        resultTop.add(m_useBoth);
        resultPanel.add(resultTop, BorderLayout.NORTH);
        resultPanel.add(new JScrollPane(m_result), BorderLayout.CENTER);
        final JPanel detail = new JPanel(new BorderLayout(0, 8));
        m_title.setFont(m_title.getFont().deriveFont(Font.BOLD));
        detail.add(m_title, BorderLayout.NORTH);
        final JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sides, resultPanel);
        vertical.setResizeWeight(0.6);
        detail.add(vertical, BorderLayout.CENTER);
        detail.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        final JButton allMine = new JButton("Keep all mine");
        allMine.addActionListener(e -> decideAll(ConflictResolution.mine()));
        final JButton allTheirs = new JButton("Take all from the server");
        allTheirs.addActionListener(e -> decideAll(ConflictResolution.theirs()));
        final JButton cancel = new JButton("Cancel pull");
        cancel.addActionListener(e -> m_dialog.dispose());
        m_merge.addActionListener(e -> {
            m_ok = true;
            m_dialog.dispose();
        });
        final JPanel buttons = new JPanel(new BorderLayout());
        final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(allMine);
        left.add(allTheirs);
        left.add(m_progress);
        final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(cancel);
        right.add(m_merge);
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        final JPanel content = new JPanel(new BorderLayout());
        content.add(intro, BorderLayout.NORTH);
        listScroll.setMinimumSize(new Dimension(200, 100));
        final JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, detail);
        horizontal.setDividerLocation(340);
        horizontal.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        content.add(horizontal, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        m_dialog.setContentPane(content);
        m_dialog.setSize(1150, 640);
        m_dialog.setLocationRelativeTo(parent);
        m_dialog.getRootPane().setDefaultButton(m_merge);
        updateProgress();
        m_list.setSelectedIndex(0);
    }

    /**
     * @return решение для каждого поля; null — отмена (ничего не менять)
     */
    static Map<String, ConflictResolution> ask(Component parent, List<ConflictField> fields, NonLinearBook book) {
        final DialogMergeConflicts dialog = new DialogMergeConflicts(parent, fields, book);
        dialog.m_dialog.setVisible(true);
        if (!dialog.m_ok) {
            return null;
        }
        final Map<String, ConflictResolution> result = new LinkedHashMap<>();
        for (Item item : dialog.m_items) {
            result.put(item.m_field.getPath(), item.m_resolution);
        }
        return result;
    }

    private static JPanel side(JLabel header, JTextPane pane, JButton use) {
        final JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(pane), BorderLayout.CENTER);
        final JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bottom.add(use);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void show(Item item) {
        if (item == null) {
            return;
        }
        final ConflictField field = item.m_field;
        m_title.setText("<html>" + escape(item.m_label) + "</html>");
        m_mineHeader.setText("Your version");
        final StringBuilder theirs = new StringBuilder("Version on the server");
        if (field.getTheirAuthor() != null) {
            theirs.append(" — ").append(field.getTheirAuthor());
        }
        if (field.getTheirTime() > 0) {
            theirs.append(", ").append(new SimpleDateFormat("d MMM HH:mm").format(new Date(field.getTheirTime())));
        }
        if (field.getTheirMessage() != null && !field.getTheirMessage().isEmpty()) {
            theirs.append(", \"").append(field.getTheirMessage()).append("\"");
        }
        m_theirsHeader.setText(theirs.toString());
        if (field.isBinary()) {
            setPlain(m_mine, field.isMineExists() ? "(a file: image, sound or other — not shown)" : "(deleted)");
            setPlain(m_theirs, field.isTheirsExists() ? "(a file: image, sound or other — not shown)" : "(deleted)");
        } else if (field.isDeletion()) {
            setPlain(m_mine, field.isMineExists() ? field.getMine() : "(deleted by you)");
            setPlain(m_theirs, field.isTheirsExists() ? field.getTheirs() : "(deleted on the server)");
        } else {
            highlight(field.getMine(), field.getTheirs());
        }
        m_useMine.setText(field.isMineExists() ? (field.isDeletion() ? "Keep it (yours)" : "Use mine") : "Delete it");
        m_useTheirs.setText(field.isTheirsExists() ? (field.isDeletion() ? "Keep it (server's)" : "Use server's")
                : "Delete it");
        final boolean textual = item.textual();
        m_useBoth.setEnabled(textual);
        m_result.setEnabled(textual);
        m_updating = true;
        if (!textual) {
            m_result.setText(field.isDeletion()
                    ? "Deleted on one side and changed on the other. Keeping it restores the whole element "
                    + "(page, link, object...); deleting removes it whole."
                    : "");
        } else {
            m_result.setText(item.m_result != null ? item.m_result : "");
        }
        m_result.setCaretPosition(0);
        m_updating = false;
    }

    private void decide(ConflictResolution resolution) {
        final Item item = m_list.getSelectedValue();
        if (item == null) {
            return;
        }
        item.m_resolution = resolution;
        if (item.textual()) {
            item.m_result = resolution.getKind() == ConflictResolution.Kind.MINE ? item.m_field.getMine() : item.m_field.getTheirs();
            m_updating = true;
            m_result.setText(item.m_result);
            m_result.setCaretPosition(0);
            m_updating = false;
        }
        changed();
        // следующее нерешённое
        for (int i = 0; i < m_items.size(); i++) {
            if (m_items.get(i).m_resolution == null) {
                m_list.setSelectedIndex(i);
                return;
            }
        }
    }

    private void decideAll(ConflictResolution resolution) {
        for (Item item : m_items) {
            item.m_resolution = resolution;
            if (item.textual()) {
                item.m_result = resolution.getKind() == ConflictResolution.Kind.MINE ? item.m_field.getMine() : item.m_field.getTheirs();
            }
        }
        changed();
        show(m_list.getSelectedValue());
    }

    private void setResult(String text) {
        m_result.setText(text);
        m_result.setCaretPosition(0);
    }

    /**
     * Автор правит итог: совпал с одной из версий — это она, иначе — свой текст
     */
    private void edited() {
        if (m_updating) {
            return;
        }
        final Item item = m_list.getSelectedValue();
        if (item == null || !item.textual()) {
            return;
        }
        final String text = m_result.getText();
        item.m_result = text;
        if (text.equals(item.m_field.getMine())) {
            item.m_resolution = ConflictResolution.mine();
        } else if (text.equals(item.m_field.getTheirs())) {
            item.m_resolution = ConflictResolution.theirs();
        } else {
            item.m_resolution = ConflictResolution.text(text);
        }
        changed();
    }

    private void changed() {
        m_list.repaint();
        updateProgress();
    }

    private void updateProgress() {
        int decided = 0;
        for (Item item : m_items) {
            if (item.m_resolution != null) {
                decided++;
            }
        }
        m_progress.setText("  Decided " + decided + " of " + m_items.size());
        m_merge.setEnabled(decided == m_items.size());
    }

    private static void setPlain(JTextPane pane, String text) {
        pane.setText(text);
        pane.setCaretPosition(0);
    }

    private static final Pattern TOKEN = Pattern.compile("\\s+|[\\p{L}\\p{N}_]+|[^\\s\\p{L}\\p{N}_]");

    /**
     * Обе версии с подсветкой различий по словам: в своей — чего нет на сервере, в версии
     * сервера — чего нет в своей (общая часть — наибольшая общая подпоследовательность слов)
     */
    private void highlight(String mine, String theirs) {
        final List<String> a = tokens(mine);
        final List<String> b = tokens(theirs);
        final boolean[] commonA = new boolean[a.size()];
        final boolean[] commonB = new boolean[b.size()];
        if ((long) a.size() * b.size() <= 4_000_000L) {
            final int[][] lcs = new int[a.size() + 1][b.size() + 1];
            for (int i = a.size() - 1; i >= 0; i--) {
                for (int j = b.size() - 1; j >= 0; j--) {
                    lcs[i][j] = a.get(i).equals(b.get(j)) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            int i = 0;
            int j = 0;
            while (i < a.size() && j < b.size()) {
                if (a.get(i).equals(b.get(j))) {
                    commonA[i++] = true;
                    commonB[j++] = true;
                } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                    i++;
                } else {
                    j++;
                }
            }
        }
        fill(m_mine, a, commonA, REMOVED);
        fill(m_theirs, b, commonB, ADDED);
    }

    private static void fill(JTextPane pane, List<String> tokens, boolean[] common, Color color) {
        pane.setText("");
        final StyledDocument doc = pane.getStyledDocument();
        final SimpleAttributeSet plain = new SimpleAttributeSet();
        final SimpleAttributeSet marked = new SimpleAttributeSet();
        StyleConstants.setBackground(marked, color);
        try {
            for (int i = 0; i < tokens.size(); i++) {
                final String token = tokens.get(i);
                final boolean space = token.trim().isEmpty();
                doc.insertString(doc.getLength(), token, common[i] || space ? plain : marked);
            }
        } catch (BadLocationException e) {
            pane.setText(String.join("", tokens));
        }
        pane.setCaretPosition(0);
    }

    private static List<String> tokens(String text) {
        final List<String> result = new ArrayList<>();
        final Matcher matcher = TOKEN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
