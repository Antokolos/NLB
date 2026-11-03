package com.nlbhub.nlb.vcs;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

import java.util.HashMap;
import java.util.Map;

/**
 * Вопросы JGit при соединении по ssh — к автору ({@link VCSPrompts}): пароль к зашифрованному ключу
 * (повторный вопрос о том же ключе — прежний не подошёл), подтверждение незнакомого сервера (JGit
 * присылает сообщение с отпечатком ключа и вопрос «да/нет»), сообщения. Имя и пароль для https
 * отсюда не даются.
 */
final class SshCredentialsProvider extends CredentialsProvider {
    private final VCSPrompts m_prompts;
    /**
     * Сколько раз в этой операции уже спрашивали пароль к ключу
     */
    private final Map<String, Integer> m_attempts = new HashMap<>();

    SshCredentialsProvider(VCSPrompts prompts) {
        m_prompts = prompts;
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    public boolean supports(CredentialItem... items) {
        for (CredentialItem item : items) {
            if (!(item instanceof CredentialItem.YesNoType)
                    && !(item instanceof CredentialItem.CharArrayType)
                    && !(item instanceof CredentialItem.InformationalMessage)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Путь к ключу из сообщения JGit (первое в одинарных кавычках); нет — весь текст
     */
    static String keyPath(final String info, final String fallback) {
        final int start = info.indexOf('\'');
        final int end = start >= 0 ? info.indexOf('\'', start + 1) : -1;
        return end > start + 1 ? info.substring(start + 1, end) : fallback;
    }

    @Override
    public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
        if (m_prompts == null) {
            return false;
        }
        final StringBuilder info = new StringBuilder();
        for (CredentialItem item : items) {
            if (item instanceof CredentialItem.InformationalMessage) {
                if (info.length() > 0) {
                    info.append('\n');
                }
                info.append(item.getPromptText());
            }
        }
        boolean asked = false;
        for (CredentialItem item : items) {
            if (item instanceof CredentialItem.YesNoType) {
                final String question = (info.length() > 0 ? info + "\n\n" : "") + item.getPromptText();
                ((CredentialItem.YesNoType) item).setValue(m_prompts.confirm(question));
                asked = true;
            } else if (item instanceof CredentialItem.CharArrayType) {
                // JGit спрашивает просто «Passphrase», а какой ключ — пишет в сообщении рядом
                // («'<путь>' needs a passphrase…», при повторе — «'<путь>' could not be read…»):
                // ключ — путь в кавычках, он один и тот же в первом и повторном вопросе
                final String message = (info.length() > 0 ? info + "\n" : "") + item.getPromptText();
                final String key = keyPath(info.toString(), message);
                final int attempt = m_attempts.merge(key, 1, Integer::sum);
                final char[] passphrase = m_prompts.askPassphrase(key, message, attempt > 1);
                if (passphrase == null) {
                    return false;
                }
                ((CredentialItem.CharArrayType) item).setValueNoCopy(passphrase);
                asked = true;
            } else if (!(item instanceof CredentialItem.InformationalMessage)) {
                throw new UnsupportedCredentialItem(uri, item.getClass().getSimpleName() + ": " + item.getPromptText());
            }
        }
        if (!asked && info.length() > 0) {
            m_prompts.inform(info.toString());
        }
        return true;
    }
}
