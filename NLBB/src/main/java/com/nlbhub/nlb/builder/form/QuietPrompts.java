package com.nlbhub.nlb.builder.form;

import com.nlbhub.nlb.vcs.VCSPrompts;
import com.nlbhub.nlb.vcs.secrets.RemoteCredentials;

/**
 * Вопросы ssh для фоновой проверки сервера: ничего не спрашивать (автор работает над книгой) —
 * пароль к ключу только из системного хранилища, незнакомый сервер — не принимать (проверка
 * просто не удастся; при Pull/Push спросят как обычно)
 */
final class QuietPrompts implements VCSPrompts {
    private final RemoteCredentials m_credentials;

    QuietPrompts(RemoteCredentials credentials) {
        m_credentials = credentials;
    }

    @Override
    public char[] askPassphrase(String key, String message, boolean retry) {
        if (retry) {
            return null;
        }
        final String saved = m_credentials.rememberedPassphrase(key);
        return saved == null ? null : saved.toCharArray();
    }

    @Override
    public boolean confirm(String message) {
        return false;
    }

    @Override
    public void inform(String message) {
    }
}
