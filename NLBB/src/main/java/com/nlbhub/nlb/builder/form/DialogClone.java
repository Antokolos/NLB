package com.nlbhub.nlb.builder.form;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * «Clone book»: адрес книги на сервере (https://github.com/… или git@github.com:…) и папка, куда её
 * скачать; имя папки по умолчанию — из адреса, родительская папка — прошлая (настройки редактора).
 */
final class DialogClone {
    private static final Preferences PREFS = Preferences.userRoot().node("com/nlbhub/nlb/git");
    private static final String PARENT_KEY = "cloneParent";

    /**
     * Адрес и папка; null — отмена
     */
    static final class Result {
        final String m_url;
        final File m_dir;

        Result(String url, File dir) {
            m_url = url;
            m_dir = dir;
        }
    }

    private DialogClone() {
    }

    /**
     * Имя папки из адреса: последняя часть пути без «.git»
     */
    static String folderName(String url) {
        String s = url.trim().replace('\\', '/');
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        final int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf(':'));
        String name = slash >= 0 ? s.substring(slash + 1) : s;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    static Result ask(Component parent) {
        final JTextField url = new JTextField(40);
        final File defaultParent = new File(PREFS.get(PARENT_KEY, System.getProperty("user.home")));
        final JTextField folder = new JTextField(new File(defaultParent, "book").getPath(), 40);
        final boolean[] folderEdited = {false};
        url.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                if (!folderEdited[0]) {
                    final String name = folderName(url.getText());
                    final File base = new File(folder.getText()).getParentFile();
                    folder.setText(new File(base != null ? base : defaultParent, name.isEmpty() ? "book" : name).getPath());
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update();
            }
        });
        final JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> {
            final JFileChooser chooser = new JFileChooser(new File(folder.getText()).getParentFile());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Folder to put the book in");
            if (chooser.showDialog(parent, "Select") == JFileChooser.APPROVE_OPTION) {
                folder.setText(new File(chooser.getSelectedFile(), folderName(url.getText()).isEmpty()
                        ? "book" : folderName(url.getText())).getPath());
                folderEdited[0] = true;
            }
        });
        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Book address"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(url, c);
        c.gridx = 1;
        c.gridy = 1;
        panel.add(new JLabel("<html><small>For example https://github.com/nlbproject/Frontier "
                + "or git@github.com:nlbproject/Frontier.git</small></html>"), c);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Folder"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(folder, c);
        c.gridx = 2;
        c.fill = GridBagConstraints.NONE;
        panel.add(browse, c);
        c.gridx = 1;
        c.gridy = 3;
        c.gridwidth = 2;
        panel.add(new JLabel("<html><small>The book is downloaded with its external modules. "
                + "Git does not need to be installed.</small></html>"), c);
        while (true) {
            final int answer = JOptionPane.showConfirmDialog(
                    parent, panel, "Clone book", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
            );
            if (answer != JOptionPane.OK_OPTION) {
                return null;
            }
            final String address = url.getText().trim();
            final File dir = new File(folder.getText().trim());
            final String[] existing = dir.list();
            if (address.isEmpty() || folder.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(parent, "Please enter the book address and the folder");
            } else if (existing != null && existing.length > 0) {
                JOptionPane.showMessageDialog(parent, "The folder " + dir + " is not empty. Please choose another one.");
            } else {
                if (dir.getParentFile() != null) {
                    PREFS.put(PARENT_KEY, dir.getParentFile().getPath());
                }
                return new Result(address, dir);
            }
        }
    }
}
