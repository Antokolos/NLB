package com.nlbhub.nlb.vcs.secrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Системное хранилище секретов: пароль или токен для git (push, pull) хранится там, где ОС хранит
 * пароли пользователя, — зашифрованным и привязанным к входу в систему; в файлах книги и настройках
 * редактора его нет. Без установки git и других программ:
 * <ul>
 *     <li>Windows — диспетчер учётных данных (Advapi32: CredWrite/CredRead/CredDelete);</li>
 *     <li>macOS — связка ключей (Security.framework);</li>
 *     <li>Linux — Secret Service через libsecret (GNOME Keyring, KWallet).</li>
 * </ul>
 * Нет хранилища (например, Linux без libsecret или без запущенной службы) — {@link #isAvailable()}
 * false: секрет не запоминается, в открытом виде он не хранится нигде.
 * <p>
 * Запись — по паре «служба» (например, «NLB Builder: https://github.com») и «учётная запись» (имя
 * пользователя на сервере).
 */
public interface SecretStore {
    Logger LOGGER = LoggerFactory.getLogger(SecretStore.class);

    /**
     * Хранилище есть и работает
     */
    boolean isAvailable();

    /**
     * Секрет; null — нет такой записи
     */
    String get(String service, String account) throws SecretStoreException;

    /**
     * Записать (существующую запись — заменить)
     */
    void put(String service, String account, String secret) throws SecretStoreException;

    /**
     * Удалить; записи нет — ничего не делать
     */
    void delete(String service, String account) throws SecretStoreException;

    /**
     * Хранилище этой ОС; не работает — заглушка, которая ничего не хранит
     */
    static SecretStore system() {
        final String os = System.getProperty("os.name", "").toLowerCase();
        try {
            final SecretStore store;
            if (os.startsWith("windows")) {
                store = new WindowsCredentialStore();
            } else if (os.startsWith("mac")) {
                store = new MacKeychainStore();
            } else {
                store = new LibSecretStore();
            }
            if (store.isAvailable()) {
                return store;
            }
        } catch (Throwable e) {
            // нет нужной системной библиотеки (UnsatisfiedLinkError и т. п.) — без хранилища
            LOGGER.info("System secret store is not available: {}", e.toString());
        }
        return new NoSecretStore();
    }

    /**
     * Ничего не хранит
     */
    final class NoSecretStore implements SecretStore {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String get(String service, String account) {
            return null;
        }

        @Override
        public void put(String service, String account, String secret) {
        }

        @Override
        public void delete(String service, String account) {
        }
    }
}
