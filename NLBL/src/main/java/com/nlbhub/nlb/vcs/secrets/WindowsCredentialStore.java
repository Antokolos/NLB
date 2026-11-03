package com.nlbhub.nlb.vcs.secrets;

import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Windows: диспетчер учётных данных (Credential Manager, «Учётные данные Windows») — общая
 * учётная запись (CRED_TYPE_GENERIC) с сохранением для пользователя на этом компьютере; видна и
 * удаляется в «Панель управления -> Диспетчер учётных данных».
 */
final class WindowsCredentialStore implements SecretStore {
    private static final int CRED_TYPE_GENERIC = 1;
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
    private static final int ERROR_NOT_FOUND = 1168;

    /**
     * CREDENTIALW
     */
    public static class Credential extends Structure {
        public int Flags;
        public int Type;
        public WString TargetName;
        public WString Comment;
        public int LastWrittenLow;
        public int LastWrittenHigh;
        public int CredentialBlobSize;
        public Pointer CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public Pointer Attributes;
        public WString TargetAlias;
        public WString UserName;

        public Credential() {
            super();
        }

        public Credential(Pointer p) {
            super(p);
            read();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("Flags", "Type", "TargetName", "Comment", "LastWrittenLow", "LastWrittenHigh",
                    "CredentialBlobSize", "CredentialBlob", "Persist", "AttributeCount", "Attributes",
                    "TargetAlias", "UserName");
        }
    }

    interface Advapi32 extends StdCallLibrary {
        boolean CredWriteW(Credential credential, int flags) throws LastErrorException;

        boolean CredReadW(WString targetName, int type, int flags, PointerByReference credential) throws LastErrorException;

        boolean CredDeleteW(WString targetName, int type, int flags) throws LastErrorException;

        void CredFree(Pointer buffer);
    }

    private final Advapi32 m_advapi;

    WindowsCredentialStore() {
        m_advapi = Native.load("Advapi32", Advapi32.class);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Имя записи в диспетчере: служба и учётная запись
     */
    private static WString target(String service, String account) {
        return new WString(service + " (" + account + ")");
    }

    @Override
    public String get(String service, String account) throws SecretStoreException {
        final PointerByReference out = new PointerByReference();
        try {
            m_advapi.CredReadW(target(service, account), CRED_TYPE_GENERIC, 0, out);
        } catch (LastErrorException e) {
            if (e.getErrorCode() == ERROR_NOT_FOUND) {
                return null;
            }
            throw new SecretStoreException("Cannot read the secret from Windows Credential Manager (error " + e.getErrorCode() + ")", e);
        }
        try {
            final Credential credential = new Credential(out.getValue());
            if (credential.CredentialBlob == null || credential.CredentialBlobSize == 0) {
                return "";
            }
            return new String(credential.CredentialBlob.getByteArray(0, credential.CredentialBlobSize), StandardCharsets.UTF_8);
        } finally {
            m_advapi.CredFree(out.getValue());
        }
    }

    @Override
    public void put(String service, String account, String secret) throws SecretStoreException {
        final byte[] blob = secret.getBytes(StandardCharsets.UTF_8);
        final Memory memory = new Memory(Math.max(1, blob.length));
        memory.write(0, blob, 0, blob.length);
        final Credential credential = new Credential();
        credential.Type = CRED_TYPE_GENERIC;
        credential.TargetName = target(service, account);
        credential.Comment = new WString("NLB Builder: password or token for Git");
        credential.CredentialBlobSize = blob.length;
        credential.CredentialBlob = memory;
        credential.Persist = CRED_PERSIST_LOCAL_MACHINE;
        credential.UserName = new WString(account);
        try {
            m_advapi.CredWriteW(credential, 0);
        } catch (LastErrorException e) {
            throw new SecretStoreException("Cannot save the secret to Windows Credential Manager (error " + e.getErrorCode() + ")", e);
        } finally {
            memory.clear();  // секрет в памяти — затереть
        }
    }

    @Override
    public void delete(String service, String account) throws SecretStoreException {
        try {
            m_advapi.CredDeleteW(target(service, account), CRED_TYPE_GENERIC, 0);
        } catch (LastErrorException e) {
            if (e.getErrorCode() != ERROR_NOT_FOUND) {
                throw new SecretStoreException("Cannot delete the secret from Windows Credential Manager (error " + e.getErrorCode() + ")", e);
            }
        }
    }
}
