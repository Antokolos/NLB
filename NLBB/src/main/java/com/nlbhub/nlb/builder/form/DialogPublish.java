package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.vcs.GitHubPublisher;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * «Publish book»: создать новый репозиторий на GitHub (имя, владелец — свой аккаунт или
 * организация, закрытый или открытый) или указать адрес уже созданного пустого репозитория на
 * любом сервере (https или ssh). Если в книге есть незакоммиченные изменения (или коммитов ещё
 * нет) — здесь же сообщение коммита: опубликовать можно только закоммиченное.
 */
final class DialogPublish {
    private static final Preferences PREFS = Preferences.userRoot().node("com/nlbhub/nlb/git");
    private static final String OWNER_KEY = "publishOwner";
    private static final String PRIVATE_KEY = "publishPrivate";

    /**
     * Выбор автора; null — отмена
     */
    static final class Result {
        /**
         * true — создать репозиторий на GitHub, false — адрес готового пустого репозитория
         */
        final boolean m_gitHub;
        final String m_owner;
        final String m_name;
        final String m_description;
        final boolean m_private;
        final String m_url;
        /**
         * Сообщение коммита перед публикацией; null — коммитить не нужно
         */
        final String m_commitMessage;

        Result(boolean gitHub, String owner, String name, String description, boolean privateRepo, String url,
               String commitMessage) {
            m_gitHub = gitHub;
            m_owner = owner;
            m_name = name;
            m_description = description;
            m_private = privateRepo;
            m_url = url;
            m_commitMessage = commitMessage;
        }
    }

    private DialogPublish() {
    }

    /**
     * @param folder        папка книги (имя репозитория по умолчанию)
     * @param title         название книги (описание репозитория)
     * @param commitMessage сообщение коммита по умолчанию; null — всё уже закоммичено
     */
    static Result ask(Component parent, String folder, String title, String commitMessage) {
        final JRadioButton gitHub = new JRadioButton("Create a new repository on GitHub", true);
        final JRadioButton existing = new JRadioButton("Use an existing empty repository (any Git server)");
        final ButtonGroup group = new ButtonGroup();
        group.add(gitHub);
        group.add(existing);
        final JTextField name = new JTextField(GitHubPublisher.repositoryName(folder), 30);
        final JTextField owner = new JTextField(PREFS.get(OWNER_KEY, ""), 30);
        final JTextField description = new JTextField(title == null ? "" : title, 30);
        final JCheckBox privateRepo = new JCheckBox("Private (only you and the people you invite can see it)",
                PREFS.getBoolean(PRIVATE_KEY, true));
        final JTextField url = new JTextField(30);
        final JTextField commit = new JTextField(commitMessage == null ? "" : commitMessage, 30);

        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        c.gridx = 0;
        c.gridwidth = 2;
        c.gridy = row++;
        panel.add(gitHub, c);
        c.gridwidth = 1;
        final JComponent[] gitHubFields = {name, owner, description, privateRepo};
        row = addRow(panel, c, row, "Repository name", name);
        row = addRow(panel, c, row, "Owner", owner);
        row = addHint(panel, c, row, "Empty — your own account; or the name of an organization you belong to");
        row = addRow(panel, c, row, "Description", description);
        c.gridx = 1;
        c.gridy = row++;
        panel.add(privateRepo, c);
        row = addHint(panel, c, row, "A GitHub token is needed: classic with the \"repo\" scope, or fine-grained with "
                + "\"Administration\" and \"Contents\": Read and write");
        c.gridx = 0;
        c.gridwidth = 2;
        c.gridy = row++;
        panel.add(existing, c);
        c.gridwidth = 1;
        row = addRow(panel, c, row, "Repository address", url);
        row = addHint(panel, c, row, "For example https://gitlab.com/me/book.git or git@github.com:me/book.git; "
                + "the repository must be empty");
        if (commitMessage != null) {
            c.gridx = 0;
            c.gridwidth = 2;
            c.gridy = row++;
            panel.add(new JLabel(" "), c);
            c.gridy = row++;
            panel.add(new JLabel("The book has changes that are not committed yet; they will be committed first."), c);
            c.gridwidth = 1;
            row = addRow(panel, c, row, "Commit message", commit);
        }
        final Runnable sync = () -> {
            for (JComponent field : gitHubFields) {
                field.setEnabled(gitHub.isSelected());
            }
            url.setEnabled(existing.isSelected());
        };
        gitHub.addActionListener(e -> sync.run());
        existing.addActionListener(e -> sync.run());
        sync.run();

        while (true) {
            final int answer = JOptionPane.showConfirmDialog(
                    parent, panel, "Publish book", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
            );
            if (answer != JOptionPane.OK_OPTION) {
                return null;
            }
            final String message = commitMessage == null ? null : commit.getText().trim();
            if (message != null && message.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "Please enter the commit message");
                continue;
            }
            if (gitHub.isSelected()) {
                final String repoName = name.getText().trim();
                if (repoName.isEmpty() || !repoName.equals(GitHubPublisher.repositoryName(repoName))) {
                    JOptionPane.showMessageDialog(parent, "The repository name may contain only Latin letters, digits, "
                            + "'-', '_' and '.'. For example: " + GitHubPublisher.repositoryName(repoName));
                    continue;
                }
                PREFS.put(OWNER_KEY, owner.getText().trim());
                PREFS.putBoolean(PRIVATE_KEY, privateRepo.isSelected());
                return new Result(true, owner.getText().trim(), repoName, description.getText().trim(),
                        privateRepo.isSelected(), null, message);
            }
            if (url.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(parent, "Please enter the address of the repository");
                continue;
            }
            return new Result(false, null, null, null, false, url.getText().trim(), message);
        }
    }

    /**
     * Токен GitHub для создания репозитория (и затем push)
     */
    static final class Token {
        final char[] m_token;
        final boolean m_remember;

        Token(char[] token, boolean remember) {
            m_token = token;
            m_remember = remember;
        }

        String token() {
            return new String(m_token);
        }

        void wipe() {
            java.util.Arrays.fill(m_token, '\0');
        }
    }

    /**
     * @param message пояснение сверху (GitHub не принял токен) или null
     * @return null — отмена
     */
    static Token askToken(Component parent, String server, String message, boolean canRemember) {
        final JPasswordField field = new JPasswordField(36);
        final JCheckBox remember = new JCheckBox("Remember the token in the system keychain", canRemember);
        remember.setEnabled(canRemember);
        final JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        if (message != null) {
            final JLabel label = new JLabel(message);
            label.setForeground(new Color(0xB00020));
            panel.add(label);
        }
        panel.add(new JLabel("GitHub access token (" + server + ")"));
        panel.add(field);
        panel.add(new JLabel("<html><small>Create it on GitHub: Settings -&gt; Developer settings -&gt; Personal access tokens.<br>"
                + "Tokens (classic): the \"repo\" scope. Fine-grained tokens: repository access \"All repositories\",<br>"
                + "permissions \"Administration\" and \"Contents\": Read and write. The same token is then used for push and pull.</small></html>"));
        panel.add(remember);
        while (true) {
            final int answer = JOptionPane.showConfirmDialog(
                    parent, panel, "Publish book — GitHub token", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
            );
            if (answer != JOptionPane.OK_OPTION) {
                return null;
            }
            final char[] token = field.getPassword();
            if (token.length > 0) {
                return new Token(token, remember.isSelected());
            }
            JOptionPane.showMessageDialog(parent, "Please enter the token");
        }
    }

    private static int addRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = row;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        panel.add(field, c);
        return row + 1;
    }

    private static int addHint(JPanel panel, GridBagConstraints c, int row, String text) {
        c.gridx = 1;
        c.gridy = row;
        panel.add(new JLabel("<html><small>" + text + "</small></html>"), c);
        return row + 1;
    }
}
