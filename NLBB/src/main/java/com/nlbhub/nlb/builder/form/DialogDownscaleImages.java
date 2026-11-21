package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.api.MediaFile;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.util.ImageDownscaler;
import com.nlbhub.nlb.util.StringHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * «Downscale…» в Media Library: уменьшение выделенных картинок с одним общим коэффициентом
 * (ядро — {@link ImageDownscaler}). Показывает план «было -> станет», добавленные в группу кадры
 * анимации и цели переадресаций, предупреждения о позициях объектов в пикселях; файлы
 * перезаписываются только после «Downscale».
 */
public final class DialogDownscaleImages {
    /** Экран VN по умолчанию (тема NLB); позже — размер экрана из свойств книги */
    static final int DEFAULT_MAX_WIDTH = 1920;
    static final int DEFAULT_MAX_HEIGHT = 1080;
    /**
     * Качество JPG при перезаписи: это исходник книги, сжимает его потом экспорт (пресеты
     * .preset относятся к PNG -> JPG при экспорте), поэтому — почти без потерь
     */
    static final int JPEG_QUALITY = 95;

    private final NonLinearBookFacade m_facade;
    private final JDialog m_dialog;
    private final List<String> m_group;
    private final Map<String, String> m_notes = new HashMap<>();
    private final JSpinner m_maxWidth = new JSpinner(new SpinnerNumberModel(DEFAULT_MAX_WIDTH, 0, 100000, 10));
    private final JSpinner m_maxHeight = new JSpinner(new SpinnerNumberModel(DEFAULT_MAX_HEIGHT, 0, 100000, 10));
    private final DefaultTableModel m_model = new DefaultTableModel(new Object[]{"File", "Size", "New size", "Note"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel m_status = new JLabel();
    private final JButton m_downscale = new JButton("Downscale");
    private final JButton m_cancel = new JButton("Cancel");
    private ImageDownscaler.Plan m_plan;
    private boolean m_applied = false;

    /**
     * @param facade   главный фасад книги (getMainFacade())
     * @param selected выделенные в библиотеке файлы
     * @return true — файлы уменьшены (библиотеке нужно обновить таблицу и предпросмотр)
     */
    public static boolean show(Component parent, NonLinearBookFacade facade, List<String> selected) {
        DialogDownscaleImages dialog = new DialogDownscaleImages(parent, facade, selected);
        dialog.m_dialog.setVisible(true);
        return dialog.m_applied;
    }

    private DialogDownscaleImages(Component parent, NonLinearBookFacade facade, List<String> selected) {
        m_facade = facade;
        final NonLinearBook book = facade.getNlb();
        final List<MediaFile> imageFiles = book.getImageFiles();
        m_group = ImageDownscaler.group(selected, imageFiles, ImageDownscaler.animatedImageNames(book));
        describeAdded(selected, imageFiles);

        final Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        m_dialog = new JDialog(owner, "Downscale images", Dialog.ModalityType.APPLICATION_MODAL);

        final JPanel limits = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        limits.add(new JLabel("Fit into: width"));
        limits.add(m_maxWidth);
        limits.add(new JLabel("height"));
        limits.add(m_maxHeight);
        limits.add(new JLabel("(0 — no limit; images are only made smaller, all by the same scale)"));
        // без разделителя разрядов: «1920», а не «1,920»
        m_maxWidth.setEditor(new JSpinner.NumberEditor(m_maxWidth, "#"));
        m_maxHeight.setEditor(new JSpinner.NumberEditor(m_maxHeight, "#"));
        m_maxWidth.addChangeListener(e -> replan());
        m_maxHeight.addChangeListener(e -> replan());

        final JTable table = new JTable(m_model);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(260);
        table.getColumnModel().getColumn(3).setPreferredWidth(260);
        final JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(760, 260));

        final List<String> warnings = ImageDownscaler.offsetWarnings(book, m_group, imageFiles);
        final JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(tableScroll, BorderLayout.CENTER);
        if (!warnings.isEmpty()) {
            final JTextArea text = new JTextArea(String.join("\n", warnings));
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setRows(Math.min(4, warnings.size() + 1));
            text.setForeground(new Color(150, 80, 0));
            final JScrollPane warningsScroll = new JScrollPane(text);
            warningsScroll.setBorder(BorderFactory.createTitledBorder("Warnings"));
            center.add(warningsScroll, BorderLayout.SOUTH);
        }

        final JLabel overwrite = new JLabel("<html><b>Files are overwritten on disk</b> (names and formats are kept)."
                + " Use VCS (Git) to restore the originals.</html>");
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(m_downscale);
        buttons.add(m_cancel);
        final JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(m_status, BorderLayout.NORTH);
        south.add(overwrite, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        final JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(limits, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        m_dialog.setContentPane(content);

        m_downscale.addActionListener(e -> apply());
        m_cancel.addActionListener(e -> m_dialog.dispose());
        m_dialog.getRootPane().registerKeyboardAction(
                e -> m_dialog.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        replan();
        m_dialog.pack();
        m_dialog.setLocationRelativeTo(owner);
    }

    /** Пояснения к файлам, которые добавлены в группу сверх выделения */
    private void describeAdded(List<String> selected, List<MediaFile> imageFiles) {
        Set<String> selectedSet = new HashSet<>(selected);
        Map<String, String> redirectedFrom = new HashMap<>();
        for (MediaFile file : imageFiles) {
            if (selectedSet.contains(file.getFileName()) && StringHelper.notEmpty(file.getRedirect())) {
                redirectedFrom.put(file.getRedirect(), file.getFileName());
            }
        }
        for (String name : m_group) {
            if (redirectedFrom.containsKey(name)) {
                m_notes.put(name, "redirect target of " + redirectedFrom.get(name));
            } else if (!selectedSet.contains(name)) {
                m_notes.put(name, "animation frame");
            }
        }
    }

    private void replan() {
        final int maxWidth = (Integer) m_maxWidth.getValue();
        final int maxHeight = (Integer) m_maxHeight.getValue();
        if (m_plan == null) {
            // размеры читаются один раз, дальше план пересчитывается без диска
            final File imagesDir = m_facade.getNlb().getImagesDir();
            final List<File> files = new ArrayList<>();
            for (String name : m_group) {
                files.add(new File(imagesDir, name));
            }
            m_plan = ImageDownscaler.plan(files, maxWidth, maxHeight);
        } else {
            m_plan = ImageDownscaler.replan(m_plan, maxWidth, maxHeight);
        }
        m_model.setRowCount(0);
        for (int i = 0; i < m_plan.getItems().size(); i++) {
            final ImageDownscaler.Item item = m_plan.getItems().get(i);
            final String name = m_group.get(i);
            final String note = m_notes.getOrDefault(name, "");
            if (item.getProblem() != null) {
                m_model.addRow(new Object[]{name, "", "", join(note, item.getProblem())});
            } else {
                final String size = item.getWidth() + "×" + item.getHeight();
                m_model.addRow(new Object[]{
                        name,
                        size,
                        item.isChanged() ? item.getNewWidth() + "×" + item.getNewHeight() : "unchanged",
                        note
                });
            }
        }
        if (m_plan.hasChanges()) {
            m_status.setText(String.format("Scale: %.1f %%", m_plan.getScale() * 100));
            m_downscale.setEnabled(true);
        } else {
            m_status.setText("All images already fit — nothing to downscale.");
            m_downscale.setEnabled(false);
        }
    }

    private static String join(String a, String b) {
        return a.isEmpty() ? b : a + "; " + b;
    }

    private void apply() {
        final ImageDownscaler.Plan plan = m_plan;
        // книга только для чтения — сказать здесь, в потоке Swing (фасад сообщил бы из фонового)
        final String readOnly = m_facade.getReadOnlyReason();
        if (readOnly != null) {
            JOptionPane.showMessageDialog(m_dialog, readOnly);
            return;
        }
        setBusy(true);
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return m_facade.downscaleImageFiles(plan, JPEG_QUALITY);
            }

            @Override
            protected void done() {
                try {
                    m_applied = get();
                    m_dialog.dispose();
                } catch (InterruptedException | ExecutionException e) {
                    final Throwable cause = e.getCause() != null ? e.getCause() : e;
                    setBusy(false);
                    m_plan = null;  // часть файлов могла уже уменьшиться — размеры прочитать заново
                    replan();
                    JOptionPane.showMessageDialog(m_dialog, "Error while downscaling: " + cause);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        m_downscale.setEnabled(!busy);
        m_cancel.setEnabled(!busy);
        m_maxWidth.setEnabled(!busy);
        m_maxHeight.setEnabled(!busy);
        m_dialog.setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        if (busy) {
            m_status.setText("Downscaling…");
        }
    }
}
