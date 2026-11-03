package com.nlbhub.nlb.builder.form;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * Имя пользователя и пароль или токен для pull/push по https. GitHub (и другие серверы) пароль
 * учётной записи для git не принимают — нужен токен доступа; он может храниться в системном
 * хранилище секретов («Запомнить»), тогда следующий раз редактор не спросит.
 */
final class DialogGitCredentials {
    /**
     * Что ввёл автор; пароль или токен — массивом символов, его затирают после использования
     */
    static final class Result {
        final String m_user;
        final char[] m_secret;
        final boolean m_remember;

        Result(String user, char[] secret, boolean remember) {
            m_user = user;
            m_secret = secret;
            m_remember = remember;
        }

        String secret() {
            return new String(m_secret);
        }

        void wipe() {
            Arrays.fill(m_secret, '\0');
        }
    }

    private DialogGitCredentials() {
    }

    /**
     * @param action     «Push» или «Pull»
     * @param server     сервер книги (https://github.com) или null
     * @param user       имя по умолчанию (последнее на этом сервере) или null
     * @param message    пояснение сверху (сервер не принял токен) или null
     * @param canRemember есть системное хранилище секретов
     * @return null — отмена
     */
    static Result ask(Component parent, String action, String server, String user, String message, boolean canRemember) {
        final JTextField userField = new JTextField(user == null ? "" : user, 28);
        final JPasswordField secretField = new JPasswordField(28);
        final JCheckBox remember = new JCheckBox("Remember the password or token in the system keychain", canRemember);
        remember.setEnabled(canRemember);
        remember.setToolTipText(canRemember
                ? "Stored encrypted by your operating system (Windows Credential Manager, macOS Keychain, Linux Secret Service), not in the book or editor files"
                : "No system keychain is available on this computer: the password or token is not stored");
        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 2, 2, 2);
        int row = 0;
        if (message != null) {
            c.gridy = row++;
            final JLabel label = new JLabel(message);
            label.setForeground(new Color(0xB00020));
            panel.add(label, c);
        }
        if (server != null) {
            c.gridy = row++;
            panel.add(new JLabel("Server: " + server), c);
        }
        c.gridwidth = 1;
        c.gridy = row;
        panel.add(new JLabel("User name"), c);
        c.gridx = 1;
        panel.add(userField, c);
        c.gridx = 0;
        c.gridy = ++row;
        panel.add(new JLabel("Password or token"), c);
        c.gridx = 1;
        panel.add(secretField, c);
        c.gridx = 0;
        c.gridwidth = 2;
        c.gridy = ++row;
        panel.add(new JLabel("<html><small>GitHub does not accept the account password for Git: create a personal access token<br>"
                + "(Settings -> Developer settings -> Personal access tokens -> Fine-grained tokens,<br>"
                + "access to the book repositories, permission Contents: Read and write) and paste it here.</small></html>"), c);
        c.gridy = ++row;
        panel.add(remember, c);
        while (true) {
            final int answer = JOptionPane.showConfirmDialog(
                    parent, panel, action + " — " + (server != null ? server : "Git"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
            );
            if (answer != JOptionPane.OK_OPTION) {
                return null;
            }
            final String name = userField.getText().trim();
            final char[] secret = secretField.getPassword();
            if (!name.isEmpty() && secret.length > 0) {
                return new Result(name, secret, remember.isSelected());
            }
            Arrays.fill(secret, '\0');
            JOptionPane.showMessageDialog(parent, "Please enter the user name and the password or token");
        }
    }
}
