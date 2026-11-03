package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.vcs.VCSPrompts;
import com.nlbhub.nlb.vcs.secrets.RemoteCredentials;
import com.nlbhub.nlb.vcs.secrets.SecretStoreException;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Вопросы pull/push по ssh в окнах редактора (вызываются из фонового потока — окна показываются в
 * потоке Swing): пароль к ключу (можно запомнить в системном хранилище секретов; запомненный не
 * подошёл — забыть и спросить снова), подтверждение незнакомого сервера, сообщения.
 */
final class SwingPrompts implements VCSPrompts {
    private final Component m_parent;
    private final RemoteCredentials m_credentials;

    SwingPrompts(Component parent, RemoteCredentials credentials) {
        m_parent = parent;
        m_credentials = credentials;
    }

    @Override
    public char[] askPassphrase(final String key, final String message, final boolean retry) {
        if (!retry) {
            final String saved = m_credentials.rememberedPassphrase(key);
            if (saved != null) {
                return saved.toCharArray();
            }
        } else {
            m_credentials.forgetPassphrase(key);
        }
        final AtomicReference<char[]> result = new AtomicReference<>();
        onSwing(() -> {
            final JPasswordField field = new JPasswordField(28);
            final boolean canRemember = m_credentials.canRememberSecret();
            final JCheckBox remember = new JCheckBox("Remember the passphrase in the system keychain", canRemember);
            remember.setEnabled(canRemember);
            final JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
            if (retry) {
                final JLabel wrong = new JLabel("The passphrase is not correct.");
                wrong.setForeground(new Color(0xB00020));
                panel.add(wrong);
            }
            panel.add(new JLabel("<html>" + escape(message).replace("\n", "<br>") + "</html>"));
            panel.add(new JLabel("Passphrase of the SSH key"));
            panel.add(field);
            panel.add(remember);
            final int answer = JOptionPane.showConfirmDialog(
                    m_parent, panel, "SSH key", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
            );
            if (answer != JOptionPane.OK_OPTION) {
                return;
            }
            final char[] passphrase = field.getPassword();
            if (remember.isSelected()) {
                try {
                    m_credentials.rememberPassphrase(key, new String(passphrase));
                } catch (SecretStoreException e) {
                    JOptionPane.showMessageDialog(m_parent, "The passphrase was not saved: " + e.getMessage());
                }
            }
            result.set(passphrase);
        });
        return result.get();
    }

    @Override
    public boolean confirm(final String message) {
        final AtomicReference<Boolean> result = new AtomicReference<>(false);
        onSwing(() -> result.set(JOptionPane.showConfirmDialog(
                m_parent, new JLabel("<html>" + escape(message).replace("\n", "<br>") + "</html>"),
                "SSH", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
        ) == JOptionPane.YES_OPTION));
        return result.get();
    }

    @Override
    public void inform(final String message) {
        onSwing(() -> JOptionPane.showMessageDialog(
                m_parent, new JLabel("<html>" + escape(message).replace("\n", "<br>") + "</html>"),
                "SSH", JOptionPane.WARNING_MESSAGE
        ));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void onSwing(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }
}
