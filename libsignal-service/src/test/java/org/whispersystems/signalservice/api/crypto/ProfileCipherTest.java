package org.whispersystems.signalservice.api.crypto;


import org.conscrypt.Conscrypt;
import org.junit.Test;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.internal.util.Util;
import org.signal.core.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.whispersystems.signalservice.testutil.LibSignalLibraryUtil.assumeLibSignalSupportedOnOS;

public class ProfileCipherTest {

  private class TestByteArrayInputStream extends ByteArrayInputStream {
    TestByteArrayInputStream(byte[] buffer) {
      super(buffer);
    }

    int getPos() {
      return this.pos;
    }
  }

  static {
    // https://github.com/google/conscrypt/issues/1034
    if (!System.getProperty("os.arch").equals("aarch64")) {
      Security.insertProviderAt(Conscrypt.newProvider(), 1);
    }
  }

  @Test
  public void testEncryptDecrypt() throws InvalidCiphertextException, InvalidInputException {
    ProfileKey    key       = new ProfileKey(Util.getSecretBytes(32));
    ProfileCipher cipher    = new ProfileCipher(key);
    byte[]        name      = cipher.encrypt("Clement\0Duval".getBytes(), 53);
    String        plaintext = cipher.decryptString(name);
    assertEquals(plaintext, "Clement\0Duval");
  }

  @Test
  public void testEmpty() throws Exception {
    ProfileKey    key       = new ProfileKey(Util.getSecretBytes(32));
    ProfileCipher cipher    = new ProfileCipher(key);
    byte[]        name      = cipher.encrypt("".getBytes(), 26);
    String        plaintext = cipher.decryptString(name);

    assertEquals(plaintext.length(), 0);
  }

  private byte[] readStream(byte[] input, ProfileKey key, int bufferSize) throws Exception {
    TestByteArrayInputStream bais = new TestByteArrayInputStream(input);
    assertEquals(0, bais.getPos());

    ProfileCipherInputStream in   = new ProfileCipherInputStream(bais, key);
    assertEquals(12 + 16, bais.getPos()); // initial read of nonce + tag-sized buffer

    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[]                buffer = new byte[bufferSize];

    int pos = bais.getPos();
    int read;
    while ((read = in.read(buffer)) != -1) {
      assertEquals(pos + read, bais.getPos());
      pos += read;
      result.write(buffer, 0, read);
    }

    assertEquals(pos, input.length);
    return result.toByteArray();
  }

  @Test
  public void testStreams() throws Exception {
    assumeLibSignalSupportedOnOS();

    ProfileKey                key  = new ProfileKey(Util.getSecretBytes(32));
    ByteArrayOutputStream     baos = new ByteArrayOutputStream();
    ProfileCipherOutputStream out  = new ProfileCipherOutputStream(baos, key);

    out.write("This is an avatar".getBytes());
    out.flush();
    out.close();

    byte[] encrypted = baos.toByteArray();

    assertEquals(new String(readStream(encrypted, key, 2048)), "This is an avatar");
    assertEquals(new String(readStream(encrypted, key, 16 /* == block size */)), "This is an avatar");
    assertEquals(new String(readStream(encrypted, key, 5)), "This is an avatar");
  }

  @Test
  public void testStreamBadAuthentication() throws Exception {
    assumeLibSignalSupportedOnOS();

    ProfileKey                key  = new ProfileKey(Util.getSecretBytes(32));
    ByteArrayOutputStream     baos = new ByteArrayOutputStream();
    ProfileCipherOutputStream out  = new ProfileCipherOutputStream(baos, key);

    out.write("This is an avatar".getBytes());
    out.flush();
    out.close();

    byte[] encrypted = baos.toByteArray();
    encrypted[encrypted.length - 1] ^= 1;
    try {
      readStream(encrypted, key, 2048);
      fail("failed to verify authenticate tag");
    } catch (IOException e) {
    }
  }

  @Test
  public void testEncryptLengthBucket1() throws InvalidInputException {
    ProfileKey    key       = new ProfileKey(Util.getSecretBytes(32));
    ProfileCipher cipher    = new ProfileCipher(key);
    byte[]        name      = cipher.encrypt("Peter\0Parker".getBytes(), 53);

    String encoded = Base64.encodeWithPadding(name);

    assertEquals(108, encoded.length());
  }

  @Test
  public void testEncryptLengthBucket2() throws InvalidInputException {
    ProfileKey    key       = new ProfileKey(Util.getSecretBytes(32));
    ProfileCipher cipher    = new ProfileCipher(key);
    byte[]        name      = cipher.encrypt("Peter\0Parker".getBytes(), 257);

    String encoded = Base64.encodeWithPadding(name);

    assertEquals(380, encoded.length());
  }

  @Test
  public void testTargetNameLength() {
    assertEquals(53, ProfileCipher.getTargetNameLength("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    assertEquals(53, ProfileCipher.getTargetNameLength("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1"));
    assertEquals(257, ProfileCipher.getTargetNameLength("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ12"));
  }
}
