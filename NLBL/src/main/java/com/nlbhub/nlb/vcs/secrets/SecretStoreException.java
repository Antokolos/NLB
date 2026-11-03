package com.nlbhub.nlb.vcs.secrets;

/**
 * Ошибка системного хранилища секретов (отказ ОС, хранилище заблокировано и т. п.)
 */
public class SecretStoreException extends Exception {
    public SecretStoreException(String message) {
        super(message);
    }

    public SecretStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
