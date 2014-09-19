package org.whispersystems.libaxolotl.util;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper class for generating keys of different types.
 *
 * @author Moxie Marlinspike
 */
public class KeyHelper {

  private KeyHelper() {}

  /**
   * Generate an identity key pair.  Clients should only do this once,
   * at install time.
   *
   * @return the generated IdentityKeyPair.
   */
  public static IdentityKeyPair generateIdentityKeyPair() {
    ECKeyPair   keyPair   = Curve.generateKeyPair();
    IdentityKey publicKey = new IdentityKey(keyPair.getPublicKey());
    return new IdentityKeyPair(publicKey, keyPair.getPrivateKey());
  }

  /**
   * Generate a registration ID.  Clients should only do this once,
   * at install time.
   *
   * @param extendedRange By default (false), the generated registration
   *                      ID is sized to require the minimal possible protobuf
   *                      encoding overhead. Specify true if the caller needs
   *                      the full range of MAX_INT at the cost of slightly
   *                      higher encoding overhead.
   * @return the generated registration ID.
   */
  public static int generateRegistrationId(boolean extendedRange) {
    try {
      SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
      if (extendedRange) return secureRandom.nextInt(Integer.MAX_VALUE - 1) + 1;
      else               return secureRandom.nextInt(16380) + 1;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static int getRandomSequence(int max) {
    try {
      return SecureRandom.getInstance("SHA1PRNG").nextInt(max);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Generate a list of PreKeys.  Clients should do this at install time, and
   * subsequently any time the list of PreKeys stored on the server runs low.
   * <p>
   * PreKey IDs are shorts, so they will eventually be repeated.  Clients should
   * store PreKeys in a circular buffer, so that they are repeated as infrequently
   * as possible.
   *
   * @param start The starting PreKey ID, inclusive.
   * @param count The number of PreKeys to generate.
   * @return the list of generated PreKeyRecords.
   */
  public static List<PreKeyRecord> generatePreKeys(int start, int count) {
    List<PreKeyRecord> results = new LinkedList<>();

    start--;

    for (int i=0;i<count;i++) {
      results.add(new PreKeyRecord(((start + i) % (Medium.MAX_VALUE-1)) + 1, Curve.generateKeyPair()));
    }

    return results;
  }

  /**
   * Generate the last resort PreKey.  Clients should do this only once, at install
   * time, and durably store it for the length of the install.
   *
   * @return the generated last resort PreKeyRecord.
   */
  public static PreKeyRecord generateLastResortPreKey() {
    ECKeyPair keyPair = Curve.generateKeyPair();
    return new PreKeyRecord(Medium.MAX_VALUE, keyPair);
  }

  /**
   * Generate a signed PreKey
   *
   * @param identityKeyPair The local client's identity key pair.
   * @param signedPreKeyId The PreKey id to assign the generated signed PreKey
   *
   * @return the generated signed PreKey
   * @throws InvalidKeyException when the provided identity key is invalid
   */
  public static SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair, int signedPreKeyId)
      throws InvalidKeyException
  {
    ECKeyPair keyPair   = Curve.generateKeyPair();
    byte[]    signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());

    return new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);
  }


  public static ECKeyPair generateSenderSigningKey() {
    return Curve.generateKeyPair();
  }

  public static byte[] generateSenderKey() {
    try {
      byte[] key = new byte[32];
      SecureRandom.getInstance("SHA1PRNG").nextBytes(key);

      return key;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static int generateSenderKeyId() {
    try {
      return SecureRandom.getInstance("SHA1PRNG").nextInt(Integer.MAX_VALUE);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

}
