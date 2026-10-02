package pl.sergeyst.gdanskmonitor;

import java.util.Base64;

/** The existing IV.ciphertext format; no migration or key change. */
final class EncryptedRecord {
    final byte[] iv, ciphertext;
    private EncryptedRecord(byte[] iv, byte[] ciphertext) { this.iv = iv; this.ciphertext = ciphertext; }
    static EncryptedRecord parse(String value) {
        try {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 2) throw new StorageException();
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
            if (iv.length != 12 || ciphertext.length < 16) throw new StorageException();
            return new EncryptedRecord(iv, ciphertext);
        } catch (Exception e) { throw new StorageException(); }
    }
}
