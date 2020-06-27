package org.whispersystems.signalservice.api.crypto;

import junit.framework.TestCase;

import org.conscrypt.Conscrypt;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.kdf.HKDFv3;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.util.Arrays;

public class AttachmentCipherTest extends TestCase {

  static {
    Security.insertProviderAt(Conscrypt.newProvider(), 1);
  }

  public void test_attachment_encryptDecrypt() throws IOException, InvalidMessageException {
    byte[]        key             = Util.getSecretBytes(64);
    byte[]        plaintextInput  = "Peter Parker".getBytes();
    EncryptResult encryptResult   = encryptData(plaintextInput, key);
    File          cipherFile      = writeToFile(encryptResult.ciphertext);
    InputStream   inputStream     = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.length, key, encryptResult.digest);
    byte[]        plaintextOutput = readInputStreamFully(inputStream);

    assertTrue(Arrays.equals(plaintextInput, plaintextOutput));

    cipherFile.delete();
  }

  public void test_attachment_encryptDecryptEmpty() throws IOException, InvalidMessageException {
    byte[]        key             = Util.getSecretBytes(64);
    byte[]        plaintextInput  = "".getBytes();
    EncryptResult encryptResult   = encryptData(plaintextInput, key);
    File          cipherFile      = writeToFile(encryptResult.ciphertext);
    InputStream   inputStream     = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.length, key, encryptResult.digest);
    byte[]        plaintextOutput = readInputStreamFully(inputStream);

    assertTrue(Arrays.equals(plaintextInput, plaintextOutput));

    cipherFile.delete();
  }

  public void test_attachment_decryptFailOnBadKey() throws IOException{
    File    cipherFile          = null;
    boolean hitCorrectException = false;

    try {
      byte[]        key             = Util.getSecretBytes(64);
      byte[]        plaintextInput  = "Gwen Stacy".getBytes();
      EncryptResult encryptResult   = encryptData(plaintextInput, key);
      byte[]        badKey          = new byte[64];

      cipherFile = writeToFile(encryptResult.ciphertext);

      AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.length, badKey, encryptResult.digest);
    } catch (InvalidMessageException e) {
      hitCorrectException = true;
    } finally {
      if (cipherFile != null) {
        cipherFile.delete();
      }
    }

    assertTrue(hitCorrectException);
  }

  public void test_attachment_decryptFailOnBadDigest() throws IOException{
    File    cipherFile          = null;
    boolean hitCorrectException = false;

    try {
      byte[]        key             = Util.getSecretBytes(64);
      byte[]        plaintextInput  = "Mary Jane Watson".getBytes();
      EncryptResult encryptResult   = encryptData(plaintextInput, key);
      byte[]        badDigest       = new byte[32];

      cipherFile = writeToFile(encryptResult.ciphertext);

      AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.length, key, badDigest);
    } catch (InvalidMessageException e) {
      hitCorrectException = true;
    } finally {
      if (cipherFile != null) {
        cipherFile.delete();
      }
    }

    assertTrue(hitCorrectException);
  }

  public void test_attachment_decryptFailOnNullDigest() throws IOException{
    File    cipherFile          = null;
    boolean hitCorrectException = false;

    try {
      byte[]        key             = Util.getSecretBytes(64);
      byte[]        plaintextInput  = "Aunt May".getBytes();
      EncryptResult encryptResult   = encryptData(plaintextInput, key);

      cipherFile = writeToFile(encryptResult.ciphertext);

      AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.length, key, null);
    } catch (InvalidMessageException e) {
      hitCorrectException = true;
    } finally {
      if (cipherFile != null) {
        cipherFile.delete();
      }
    }

    assertTrue(hitCorrectException);
  }

  public void test_attachment_decryptFailOnBadMac() throws IOException {
    File    cipherFile          = null;
    boolean hitCorrectException = false;

    try {
      byte[]        key              = Util.getSecretBytes(64);
      byte[]        plaintextInput   = "Uncle Ben".getBytes();
      EncryptResult encryptResult    = encryptData(plaintextInput, key);
      byte[]        badMacCiphertext = Arrays.copyOf(encryptResult.ciphertext, encryptResult.ciphertext.length);

      badMacCiphertext[badMacCiphertext.length - 1] += 1;

      cipherFile = writeToFile(badMacCiphertext);

      AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.length, key, encryptResult.digest);
    } catch (InvalidMessageException e) {
      hitCorrectException = true;
    } finally {
      if (cipherFile != null) {
        cipherFile.delete();
      }
    }

    assertTrue(hitCorrectException);
  }

  public void test_sticker_encryptDecrypt() throws IOException, InvalidMessageException {
    byte[]        packKey         = Util.getSecretBytes(32);
    byte[]        plaintextInput  = "Peter Parker".getBytes();
    EncryptResult encryptResult   = encryptData(plaintextInput, expandPackKey(packKey));
    InputStream   inputStream     = AttachmentCipherInputStream.createForStickerData(encryptResult.ciphertext, packKey);
    byte[]        plaintextOutput = readInputStreamFully(inputStream);

    assertTrue(Arrays.equals(plaintextInput, plaintextOutput));
  }

  public void test_sticker_encryptDecryptEmpty() throws IOException, InvalidMessageException {
    byte[]        packKey         = Util.getSecretBytes(32);
    byte[]        plaintextInput  = "".getBytes();
    EncryptResult encryptResult   = encryptData(plaintextInput, expandPackKey(packKey));
    InputStream   inputStream     = AttachmentCipherInputStream.createForStickerData(encryptResult.ciphertext, packKey);
    byte[]        plaintextOutput = readInputStreamFully(inputStream);

    assertTrue(Arrays.equals(plaintextInput, plaintextOutput));
  }

  public void test_sticker_decryptFailOnBadKey() throws IOException{
    boolean hitCorrectException = false;

    try {
      byte[]        packKey         = Util.getSecretBytes(32);
      byte[]        plaintextInput  = "Gwen Stacy".getBytes();
      EncryptResult encryptResult   = encryptData(plaintextInput, expandPackKey(packKey));
      byte[]        badPackKey      = new byte[32];

      AttachmentCipherInputStream.createForStickerData(encryptResult.ciphertext, badPackKey);
    } catch (InvalidMessageException e) {
      hitCorrectException = true;
    }

    assertTrue(hitCorrectException);
  }

  public void test_sticker_decryptFailOnBadMac() throws IOException {
    boolean hitCorrectException = false;

    try {
      byte[]        packKey          = Util.getSecretBytes(32);
      byte[]        plaintextInput   = "Uncle Ben".getBytes();
      EncryptResult encryptResult    = encryptData(plaintextInput, expandPackKey(packKey));
      byte[]        badMacCiphertext = Arrays.copyOf(encryptResult.ciphertext, encryptResult.ciphertext.length);

      badMacCiphertext[badMacCiphertext.length - 1] += 1;

      AttachmentCipherInputStream.createForStickerData(badMacCiphertext, packKey);
    } catch (InvalidMessageException e) {
      hitCorrectException = true;
    }

    assertTrue(hitCorrectException);
  }

  private static EncryptResult encryptData(byte[] data, byte[] keyMaterial) throws IOException {
    ByteArrayOutputStream        outputStream  = new ByteArrayOutputStream();
    AttachmentCipherOutputStream encryptStream = new AttachmentCipherOutputStream(keyMaterial, null, outputStream);

    encryptStream.write(data);
    encryptStream.flush();
    encryptStream.close();

    return new EncryptResult(outputStream.toByteArray(), encryptStream.getTransmittedDigest());
  }

  private static File writeToFile(byte[] data) throws IOException {
    File         file         = File.createTempFile("temp", ".data");
    OutputStream outputStream = new FileOutputStream(file);

    outputStream.write(data);
    outputStream.close();

    return file;
  }

  private static byte[] readInputStreamFully(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Util.copy(inputStream, outputStream);
    return outputStream.toByteArray();
  }

  private static byte[] expandPackKey(byte[] shortKey) {
    return new HKDFv3().deriveSecrets(shortKey, "Sticker Pack".getBytes(), 64);
  }

  private static class EncryptResult {
    final byte[] ciphertext;
    final byte[] digest;

    private EncryptResult(byte[] ciphertext, byte[] digest) {
      this.ciphertext = ciphertext;
      this.digest     = digest;
    }
  }
}
