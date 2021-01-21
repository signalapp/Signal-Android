package org.whispersystems.signalservice.api.crypto;


import junit.framework.TestCase;

import org.conscrypt.Conscrypt;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;

public class ProfileCipherTest extends TestCase {

  static {
    Security.insertProviderAt(Conscrypt.newProvider(), 1);
  }

  public void testEncryptDecrypt() throws InvalidCiphertextException, InvalidInputException {
    ProfileKey    key       = new ProfileKey(Util.getSecretBytes(32));
    ProfileCipher cipher    = new ProfileCipher(key);
    byte[]        name      = cipher.encryptName("Clement\0Duval".getBytes(), 53);
    byte[]        plaintext = cipher.decryptName(name);
    assertEquals(new String(plaintext), "Clement\0Duval");
  }

  public void testEmpty() throws Exception {
    ProfileKey    key       = new ProfileKey(Util.getSecretBytes(32));
    ProfileCipher cipher    = new ProfileCipher(key);
    byte[]        name      = cipher.encryptName("".getBytes(), 26);
    byte[]        plaintext = cipher.decryptName(name);

    assertEquals(plaintext.length, 0);
  }

  public void testStreams() throws Exception {
    ProfileKey                key  = new ProfileKey(Util.getSecretBytes(32));
    ByteArrayOutputStream     baos = new ByteArrayOutputStream();
    ProfileCipherOutputStream out  = new ProfileCipherOutputStream(baos, key);

    out.write("This is an avatar".getBytes());
    out.flush();
    out.close();

    ByteArrayInputStream     bais = new ByteArrayInputStream(baos.toByteArray());
    ProfileCipherInputStream in   = new ProfileCipherInputStream(bais, key);

    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[]                buffer = new byte[2048];

    int read;

    while ((read = in.read(buffer)) != -1) {
      result.write(buffer, 0, read);
    }

    assertEquals(new String(result.toByteArray()), "This is an avatar");
  }

  public void testEncryptLengthBucket1() throws InvalidInputException {
    ProfileKey    key       = new ProfileKey(Util.getSecretBytes(32));
    ProfileCipher cipher    = new ProfileCipher(key);
    byte[]        name      = cipher.encryptName("Peter\0Parker".getBytes(), 53);

    String encoded = Base64.encodeBytes(name);

    assertEquals(108, encoded.length());
  }

  public void testEncryptLengthBucket2() throws InvalidInputException {
    ProfileKey    key       = new ProfileKey(Util.getSecretBytes(32));
    ProfileCipher cipher    = new ProfileCipher(key);
    byte[]        name      = cipher.encryptName("Peter\0Parker".getBytes(), 257);

    String encoded = Base64.encodeBytes(name);

    assertEquals(380, encoded.length());
  }

  public void testTargetNameLength() {
    assertEquals(53, ProfileCipher.getTargetNameLength("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    assertEquals(53, ProfileCipher.getTargetNameLength("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1"));
    assertEquals(257, ProfileCipher.getTargetNameLength("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ12"));
  }
}
