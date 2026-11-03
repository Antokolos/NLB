package com.nlbhub.nlb.vcs.secrets;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.nio.charset.StandardCharsets;

/**
 * macOS: связка ключей входа (Security.framework) — «общий пароль» (generic password) с полями
 * «служба» и «учётная запись»; виден в «Связке ключей», там же его можно удалить.
 */
final class MacKeychainStore implements SecretStore {
    private static final int ERR_SEC_SUCCESS = 0;
    private static final int ERR_SEC_ITEM_NOT_FOUND = -25300;

    interface Security extends Library {
        int SecKeychainAddGenericPassword(Pointer keychain, int serviceNameLength, byte[] serviceName,
                                          int accountNameLength, byte[] accountName,
                                          int passwordLength, byte[] passwordData, Pointer itemRef);

        int SecKeychainFindGenericPassword(Pointer keychainOrArray, int serviceNameLength, byte[] serviceName,
                                           int accountNameLength, byte[] accountName,
                                           IntByReference passwordLength, PointerByReference passwordData,
                                           PointerByReference itemRef);

        int SecKeychainItemModifyAttributesAndData(Pointer itemRef, Pointer attrList, int length, byte[] data);

        int SecKeychainItemDelete(Pointer itemRef);

        int SecKeychainItemFreeContent(Pointer attrList, Pointer data);
    }

    interface CoreFoundation extends Library {
        void CFRelease(Pointer cf);
    }

    private final Security m_security;
    private final CoreFoundation m_cf;

    MacKeychainStore() {
        m_security = Native.load("Security", Security.class);
        m_cf = Native.load("CoreFoundation", CoreFoundation.class);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String get(String service, String account) throws SecretStoreException {
        final byte[] s = bytes(service);
        final byte[] a = bytes(account);
        final IntByReference length = new IntByReference();
        final PointerByReference data = new PointerByReference();
        final int status = m_security.SecKeychainFindGenericPassword(null, s.length, s, a.length, a, length, data, null);
        if (status == ERR_SEC_ITEM_NOT_FOUND) {
            return null;
        }
        check(status, "read");
        try {
            return new String(data.getValue().getByteArray(0, length.getValue()), StandardCharsets.UTF_8);
        } finally {
            m_security.SecKeychainItemFreeContent(null, data.getValue());
        }
    }

    @Override
    public void put(String service, String account, String secret) throws SecretStoreException {
        final byte[] s = bytes(service);
        final byte[] a = bytes(account);
        final byte[] p = bytes(secret);
        final PointerByReference item = new PointerByReference();
        final int found = m_security.SecKeychainFindGenericPassword(null, s.length, s, a.length, a, null, null, item);
        if (found == ERR_SEC_SUCCESS) {
            // запись уже есть — заменить пароль в ней
            try {
                check(m_security.SecKeychainItemModifyAttributesAndData(item.getValue(), null, p.length, p), "save");
            } finally {
                m_cf.CFRelease(item.getValue());
            }
            return;
        }
        if (found != ERR_SEC_ITEM_NOT_FOUND) {
            check(found, "save");
        }
        check(m_security.SecKeychainAddGenericPassword(null, s.length, s, a.length, a, p.length, p, null), "save");
    }

    @Override
    public void delete(String service, String account) throws SecretStoreException {
        final byte[] s = bytes(service);
        final byte[] a = bytes(account);
        final PointerByReference item = new PointerByReference();
        final int found = m_security.SecKeychainFindGenericPassword(null, s.length, s, a.length, a, null, null, item);
        if (found == ERR_SEC_ITEM_NOT_FOUND) {
            return;
        }
        check(found, "delete");
        try {
            check(m_security.SecKeychainItemDelete(item.getValue()), "delete");
        } finally {
            m_cf.CFRelease(item.getValue());
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void check(int status, String what) throws SecretStoreException {
        if (status != ERR_SEC_SUCCESS) {
            throw new SecretStoreException("Cannot " + what + " the secret in the macOS keychain (OSStatus " + status + ")");
        }
    }
}
