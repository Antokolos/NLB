package com.nlbhub.nlb.exception;

/**
 * Сервер git не принял имя пользователя и пароль (токен) или токену не хватает прав: редактор
 * забывает сохранённый токен и спрашивает снова
 */
public class NLBVCSAuthenticationException extends NLBVCSException {
    public NLBVCSAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
