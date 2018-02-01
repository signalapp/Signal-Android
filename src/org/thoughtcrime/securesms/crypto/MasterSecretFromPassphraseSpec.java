package org.thoughtcrime.securesms.crypto;

import org.thoughtcrime.securesms.util.Util;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class MasterSecretFromPassphraseSpec {

  public static final int ENCRYPTION_KEY_LENGTH = 128; // bits
  public static final int MAC_KEY_LENGTH = 128; // bits
  public static final int SALT_LENGTH = 16; // bytes
  public static final int ITERATION_LENGTH = 4; // bytes

  private String passphrase;
  private byte[] salt;
  private int iterations;

  public MasterSecretFromPassphraseSpec(String passphrase) throws NoSuchAlgorithmException {
    this.passphrase = passphrase;
    this.salt = MasterSecretUtil.generateSalt();
    this.iterations = MasterSecretUtil.generateIterationCount(passphrase, salt);
  }

  public MasterSecretFromPassphraseSpec(String passphrase,
                                        byte[] salt,
                                        byte[] iterations) {
    this.passphrase = passphrase;
    this.salt = salt;
    this.iterations = convertIterations(iterations);
  }

  public String passphrase() {
    return passphrase;
  }

  public byte[] salt() {
    return salt;
  }

  public int iterations() {
    return this.iterations;
  }

  public byte[] saltAndIterationBytes() {
    return Util.combine(
            salt,
            convertIterations(iterations()));
  }

  public static byte[] convertIterations(int iterations) {
    return new byte[]{
            (byte) (iterations >>> 24),
            (byte) (iterations >>> 16),
            (byte) (iterations >>> 8),
            (byte) iterations
    };
  }

  public static int convertIterations(byte[] iterations) {
    if (iterations.length != 4) {
      throw new AssertionError();
    }
    return ByteBuffer.wrap(iterations).getInt();
  }

  public MasterSecret deriveMasterSecret() throws InvalidKeySpecException, NoSuchAlgorithmException {
    int combinedKeyLength = ENCRYPTION_KEY_LENGTH + MAC_KEY_LENGTH;
    PBEKeySpec keySpec = new PBEKeySpec(passphrase.toCharArray(), salt, iterations, combinedKeyLength);
    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
    byte[] combinedKey = keyFactory.generateSecret(keySpec).getEncoded();
    byte[] encryptionKey = Arrays.copyOfRange(combinedKey, 0, ENCRYPTION_KEY_LENGTH / 8);
    byte[] macKey = Arrays.copyOfRange(combinedKey, ENCRYPTION_KEY_LENGTH / 8, combinedKey.length);
    SecretKeySpec encryptionKeySpec = new SecretKeySpec(encryptionKey, "AES");
    SecretKeySpec macKeySpec = new SecretKeySpec(macKey, "HmacSHA1");
    return new MasterSecret(encryptionKeySpec, macKeySpec);
  }
}
