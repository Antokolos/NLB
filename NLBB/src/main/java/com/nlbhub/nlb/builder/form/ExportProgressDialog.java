package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.domain.export.ExportProgress;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Окно прогресса экспорта: экспорт идёт в фоновом потоке (SwingWorker), окно показывает формат,
 * этап (страницы и объекты, запись файла, картинки, звук), полосу «сделано из скольких» и кнопку
 * Cancel. Окно модальное — пока идёт экспорт, книгу не правят. Отмена срабатывает на следующем
 * шаге экспорта ({@link ExportProgress.Cancelled}); уже записанные файлы остаются.
 */
final class ExportProgressDialog {
    /** Работа в фоне: один или несколько экспортов */
    interface Work {
        void run() throws Exception;
    }

    private static final class Report {
        final String format;
        final String stage;
        final int done;
        final int total;

        Report(String format, String stage, int done, int total) {
            this.format = format;
            this.stage = stage;
            this.done = done;
            this.total = total;
        }
    }

    private ExportProgressDialog() {
    }

    /**
     * Выполнить работу с окном прогресса.
     *
     * @param parent      окно, над которым показать
     * @param title       заголовок («Export to QSP»)
     * @param errorPrefix начало сообщения об ошибке («Error while exporting to QSP: »)
     * @param work        работа (в фоновом потоке)
     * @param onSuccess   что сделать в потоке Swing после удачного завершения (может быть null)
     */
    static void run(Component parent, String title, String errorPrefix, Work work, Runnable onSuccess) {
        final Window owner = (parent == null) ? null : SwingUtilities.getWindowAncestor(parent);
        final JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        final JLabel formatLabel = new JLabel(" ");
        final JLabel stageLabel = new JLabel("Preparing...");
        final JProgressBar bar = new JProgressBar(0, 100);
        bar.setIndeterminate(true);
        bar.setStringPainted(true);
        bar.setString("");
        final JButton cancel = new JButton("Cancel");
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel labels = new JPanel(new GridLayout(2, 1, 0, 4));
        labels.add(formatLabel);
        labels.add(stageLabel);
        panel.add(labels, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(cancel);
        panel.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        Runnable doCancel = () -> {
            cancelled.set(true);
            cancel.setEnabled(false);
            stageLabel.setText("Cancelling...");
        };
        cancel.addActionListener(e -> doCancel.run());
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doCancel.run();
            }
        });
        dialog.setSize(new Dimension(420, 150));
        dialog.setLocationRelativeTo(owner);

        SwingWorker<Void, Report> worker = new SwingWorker<Void, Report>() {
            @Override
            protected Void doInBackground() throws Exception {
                ExportProgress.setListener(new ExportProgress.Listener() {
                    @Override
                    public void progress(String format, String stage, int done, int total) {
                        publish(new Report(format, stage, done, total));
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }
                });
                try {
                    work.run();
                } finally {
                    ExportProgress.setListener(null);
                }
                return null;
            }

            @Override
            protected void process(List<Report> chunks) {
                if (cancelled.get()) {
                    return;
                }
                Report last = chunks.get(chunks.size() - 1);
                formatLabel.setText(last.format.isEmpty() ? " " : last.format);
                if (last.total > 0) {
                    bar.setIndeterminate(false);
                    bar.setValue((int) Math.min(100L, 100L * last.done / last.total));
                    bar.setString(last.done + " / " + last.total);
                    stageLabel.setText(capitalize(last.stage));
                } else {
                    bar.setIndeterminate(true);
                    bar.setString("");
                    stageLabel.setText(capitalize(last.stage) + (last.done > 0 ? " (" + last.done + ")" : ""));
                }
            }

            @Override
            protected void done() {
                dialog.dispose();
                try {
                    get();
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = (e.getCause() == null) ? e : e.getCause();
                    if (!(cause instanceof ExportProgress.Cancelled)) {
                        JOptionPane.showMessageDialog(parent, errorPrefix + cause);
                    }
                }
            }
        };
        worker.execute();
        // модальное окно блокирует здесь, пока done() его не закроет; если работа закончилась
        // раньше показа, done() уже вызвал dispose(), и окно не появится
        if (!worker.isDone()) {
            dialog.setVisible(true);
        }
    }

    private static String capitalize(String text) {
        return (text == null || text.isEmpty()) ? "" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
