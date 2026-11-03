package com.nlbhub.nlb.vcs;

import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Вопросы JGit при соединении по ssh — к автору: пароль к ключу (ключ — путь из сообщения JGit,
 * одинаковый в первом и повторном вопросе; повтор — значит, прежний не подошёл), незнакомый сервер
 * (сообщение с отпечатком и вопрос «да/нет»), отмена.
 */
public class SshCredentialsProviderTest {
    private final List<String> m_log = new ArrayList<>();
    private boolean m_answer = true;
    private String m_passphrase = "secret";

    private final VCSPrompts m_prompts = new VCSPrompts() {
        @Override
        public char[] askPassphrase(String key, String message, boolean retry) {
            m_log.add("passphrase " + key + " retry=" + retry);
            return m_passphrase == null ? null : m_passphrase.toCharArray();
        }

        @Override
        public boolean confirm(String message) {
            m_log.add("confirm " + message);
            return m_answer;
        }

        @Override
        public void inform(String message) {
            m_log.add("inform " + message);
        }
    };

    @Test
    public void passphraseIsAskedPerKeyAndRetryIsRecognized() throws Exception {
        SshCredentialsProvider provider = new SshCredentialsProvider(m_prompts);
        URIish uri = new URIish("ssh://git@example.com/book.git");
        CredentialItem.Password first = new CredentialItem.Password("Passphrase");
        assertTrue(provider.supports(new CredentialItem.InformationalMessage("x"), first));
        assertTrue(provider.get(uri,
                new CredentialItem.InformationalMessage("'/home/anna/.ssh/id_ed25519' needs a passphrase to be read."), first));
        assertArrayEquals("secret".toCharArray(), first.getValue());
        // повторный вопрос JGit — другим текстом, но о том же ключе
        CredentialItem.Password second = new CredentialItem.Password("Passphrase");
        assertTrue(provider.get(uri,
                new CredentialItem.InformationalMessage("'/home/anna/.ssh/id_ed25519' could not be read. Enter the passphrase again."), second));
        assertEquals("passphrase /home/anna/.ssh/id_ed25519 retry=false", m_log.get(0));
        assertEquals("passphrase /home/anna/.ssh/id_ed25519 retry=true", m_log.get(1));

        // отмена — JGit прекращает попытки
        m_passphrase = null;
        assertFalse(provider.get(uri,
                new CredentialItem.InformationalMessage("'/home/anna/.ssh/id_rsa' needs a passphrase to be read."),
                new CredentialItem.Password("Passphrase")));
    }

    @Test
    public void unknownHostIsConfirmedWithFingerprint() throws Exception {
        SshCredentialsProvider provider = new SshCredentialsProvider(m_prompts);
        URIish uri = new URIish("ssh://git@example.com/book.git");
        CredentialItem.YesNoType accept = new CredentialItem.YesNoType("Accept and store this key, and continue connecting?");
        assertTrue(provider.get(uri,
                new CredentialItem.InformationalMessage("The authenticity of host 'example.com' cannot be established."),
                new CredentialItem.InformationalMessage("SHA256:AL521Hz7Sfwus9U0UG9IV7Z"),
                accept));
        assertTrue(accept.getValue());
        assertTrue(m_log.get(0), m_log.get(0).contains("SHA256:AL521Hz7Sfwus9U0UG9IV7Z"));
        assertTrue(m_log.get(0), m_log.get(0).contains("Accept and store this key"));

        m_answer = false;
        CredentialItem.YesNoType refuse = new CredentialItem.YesNoType("Accept?");
        assertTrue(provider.get(uri, refuse));
        assertFalse(refuse.getValue());

        // только сообщение (ключ сервера изменился) — показать
        assertTrue(provider.get(uri, new CredentialItem.InformationalMessage("Host key for 'example.com' has changed")));
        assertTrue(m_log.get(m_log.size() - 1).startsWith("inform Host key"));
        // имя и пароль https отсюда не даются
        assertFalse(provider.supports(new CredentialItem.Username()));
    }
}
