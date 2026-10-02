package pl.sergeyst.gdanskmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureStore {
    private static final String PREFS = "secure_store";
    private static final String KEY_ALIAS = "gdansk_case_monitor_aes";
    private final SharedPreferences prefs;
    private static final Object KEY_LOCK = new Object();

    public SecureStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private SecretKey key(boolean create) throws Exception {
        synchronized (KEY_LOCK) {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        if (!create || prefs.contains("accounts") || prefs.contains("statuses")) throw new StorageException();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
        }
    }

    public void putEncrypted(String name, String plain) throws Exception {
        // Authenticate existing ciphertext before allowing any replacement.
        if (prefs.contains(name)) getEncrypted(name, null);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(true));
        byte[] cipher = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        String value = Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + "." + Base64.encodeToString(cipher, Base64.NO_WRAP);
        if (!prefs.edit().putString(name, value).commit()) throw new StorageException();
    }

    public String getEncrypted(String name, String fallback) {
        try {
            if (!prefs.contains(name)) return fallback;
            String value = prefs.getString(name, null);
            if (value == null || value.isEmpty()) throw new StorageException();
            EncryptedRecord record = EncryptedRecord.parse(value);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, record.iv));
            return new String(c.doFinal(record.ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new StorageException();
        }
    }

    public void putPlain(String name, String value) { prefs.edit().putString(name, value).apply(); }
    public String getPlain(String name, String fallback) { return prefs.getString(name, fallback); }
    public void putBool(String name, boolean value) { prefs.edit().putBoolean(name, value).apply(); }
    public boolean getBool(String name, boolean fallback) { return prefs.getBoolean(name, fallback); }
    public void putLong(String name, long value) { prefs.edit().putLong(name, value).apply(); }
    public long getLong(String name, long fallback) { return prefs.getLong(name, fallback); }
}
