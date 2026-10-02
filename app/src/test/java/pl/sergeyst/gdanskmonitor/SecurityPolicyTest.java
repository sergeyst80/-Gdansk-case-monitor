package pl.sergeyst.gdanskmonitor;

import org.junit.Test;
import static org.junit.Assert.*;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.AEADBadTagException;
import javax.crypto.spec.GCMParameterSpec;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class SecurityPolicyTest {
    @Test public void portalAllowsOnlyExactHttpsOrigin() {
        for (String url : new String[]{"https://klient.gdansk.uw.gov.pl/", "https://klient.gdansk.uw.gov.pl:443/path?q=1", "HTTPS://KLIENT.GDANSK.UW.GOV.PL/"}) assertTrue(url, PortalPolicy.trusted(url));
        for (String url : new String[]{null, "", "http://klient.gdansk.uw.gov.pl/", "https://klient.gdansk.uw.gov.pl.evil.test/", "https://evil.test/klient.gdansk.uw.gov.pl", "https://user@klient.gdansk.uw.gov.pl/", "https://klient.gdansk.uw.gov.pl:8443/", "https://klient.gdansk.uw.gov.pl./", "file:///data/data/private", "content://private", "javascript:alert(1)", "//klient.gdansk.uw.gov.pl/", "https://klient.gdansk.uw.gov.pl\\@evil.test/"}) assertFalse(String.valueOf(url), PortalPolicy.trusted(url));
    }
    @Test public void malformedRecordsFailClosed() {
        for (String value : new String[]{null, "", ".", "invalid", "a.b.c", "@@@.@@@", "AA==.AA=="}) {
            try { EncryptedRecord.parse(value); fail("Malformed record accepted"); }
            catch (StorageException expected) { assertFalse(expected.getMessage().contains("@@@")); }
        }
    }
    @Test public void existingFormatRoundTripsAndRejectsTampering() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256);
        SecretKey key = generator.generateKey();
        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding"); encrypt.init(Cipher.ENCRYPT_MODE, key);
        byte[] plain = "fictional account data".getBytes(StandardCharsets.UTF_8);
        String existing = Base64.getEncoder().encodeToString(encrypt.getIV()) + "." + Base64.getEncoder().encodeToString(encrypt.doFinal(plain));
        EncryptedRecord record = EncryptedRecord.parse(existing);
        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
        decrypt.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, record.iv));
        assertArrayEquals(plain, decrypt.doFinal(record.ciphertext));
        record.ciphertext[0] ^= 1;
        decrypt.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, record.iv));
        try { decrypt.doFinal(record.ciphertext); fail("Tampered ciphertext accepted"); }
        catch (AEADBadTagException expected) { /* authentication is mandatory */ }
    }
}
