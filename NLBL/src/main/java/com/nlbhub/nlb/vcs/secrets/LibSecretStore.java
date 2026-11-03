package com.nlbhub.nlb.vcs.secrets;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Linux: Secret Service (GNOME Keyring, KWallet) через libsecret — функции secret_password_*_sync
 * со своей схемой: атрибуты service и account. Нужна библиотека libsecret-1 (есть в обычных
 * настольных системах) и сеанс D-Bus со службой секретов; иначе секрет не запоминается.
 */
final class LibSecretStore implements SecretStore {
    private static final Map<String, Object> UTF8 = Map.of(Library.OPTION_STRING_ENCODING, "UTF-8");
    private static final String SCHEMA_NAME = "com.nlbhub.nlb.Credential";
    private static final int SECRET_SCHEMA_NONE = 0;
    private static final int SECRET_SCHEMA_ATTRIBUTE_STRING = 0;

    /**
     * SecretSchemaAttribute: { const gchar *name; SecretSchemaAttributeType type; }
     */
    public static class SchemaAttribute extends Structure {
        public String name;
        public int type;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("name", "type");
        }
    }

    /**
     * SecretSchema: имя, флаги, 32 атрибута (последний значимый — с name = NULL) и
     * зарезервированные поля
     */
    public static class Schema extends Structure {
        public String name;
        public int flags;
        public SchemaAttribute[] attributes = (SchemaAttribute[]) new SchemaAttribute().toArray(32);
        public int reserved;
        public Pointer reserved1;
        public Pointer reserved2;
        public Pointer reserved3;
        public Pointer reserved4;
        public Pointer reserved5;
        public Pointer reserved6;
        public Pointer reserved7;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("name", "flags", "attributes", "reserved",
                    "reserved1", "reserved2", "reserved3", "reserved4", "reserved5", "reserved6", "reserved7");
        }
    }

    interface LibSecret extends Library {
        int secret_password_store_sync(Schema schema, String collection, String label, String password,
                                       Pointer cancellable, PointerByReference error, Object... attributes);

        Pointer secret_password_lookup_sync(Schema schema, Pointer cancellable, PointerByReference error, Object... attributes);

        int secret_password_clear_sync(Schema schema, Pointer cancellable, PointerByReference error, Object... attributes);

        void secret_password_free(Pointer password);
    }

    interface GLib extends Library {
        void g_error_free(Pointer error);
    }

    private final LibSecret m_lib;
    private final GLib m_glib;
    private final Schema m_schema;

    LibSecretStore() {
        m_lib = load();
        m_glib = Native.load("glib-2.0", GLib.class, UTF8);
        m_schema = new Schema();
        m_schema.name = SCHEMA_NAME;
        m_schema.flags = SECRET_SCHEMA_NONE;
        m_schema.attributes[0].name = "service";
        m_schema.attributes[0].type = SECRET_SCHEMA_ATTRIBUTE_STRING;
        m_schema.attributes[1].name = "account";
        m_schema.attributes[1].type = SECRET_SCHEMA_ATTRIBUTE_STRING;
        m_schema.write();
    }

    /**
     * libsecret-1: в системе без пакетов разработки есть только libsecret-1.so.0
     */
    private static LibSecret load() {
        try {
            return Native.load("secret-1", LibSecret.class, UTF8);
        } catch (UnsatisfiedLinkError e) {
            return Native.load("libsecret-1.so.0", LibSecret.class, UTF8);
        }
    }

    @Override
    public boolean isAvailable() {
        // служба секретов доступна только в сеансе D-Bus (рабочий стол пользователя)
        final String bus = System.getenv("DBUS_SESSION_BUS_ADDRESS");
        return bus != null && !bus.isEmpty();
    }

    @Override
    public String get(String service, String account) throws SecretStoreException {
        final PointerByReference error = new PointerByReference();
        final Pointer secret = m_lib.secret_password_lookup_sync(m_schema, null, error, "service", service, "account", account, null);
        check(error, "read");
        if (secret == null) {
            return null;
        }
        try {
            return secret.getString(0, "UTF-8");
        } finally {
            m_lib.secret_password_free(secret);  // libsecret затирает память секрета
        }
    }

    @Override
    public void put(String service, String account, String secret) throws SecretStoreException {
        final PointerByReference error = new PointerByReference();
        final int ok = m_lib.secret_password_store_sync(m_schema, null /* коллекция по умолчанию */, service + " (" + account + ")",
                secret, null, error, "service", service, "account", account, null);
        check(error, "save");
        if (ok == 0) {
            throw new SecretStoreException("Cannot save the secret to the system keyring");
        }
    }

    @Override
    public void delete(String service, String account) throws SecretStoreException {
        final PointerByReference error = new PointerByReference();
        m_lib.secret_password_clear_sync(m_schema, null, error, "service", service, "account", account, null);
        check(error, "delete");
    }

    /**
     * GError (domain, code, message) — в исключение
     */
    private void check(PointerByReference error, String what) throws SecretStoreException {
        final Pointer gerror = error.getValue();
        if (gerror == null) {
            return;
        }
        final Pointer message = gerror.getPointer(8);  // guint32 domain, gint code, gchar *message
        final String text = message != null ? message.getString(0, "UTF-8") : "unknown error";
        m_glib.g_error_free(gerror);
        throw new SecretStoreException("Cannot " + what + " the secret in the system keyring: " + text);
    }
}
