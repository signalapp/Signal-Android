package org.whispersystems.libaxolotl.util;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.state.PreKeyRecord;

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

  /**
   * Generate an identity key pair.  Clients should only do this once,
   * at install time.
   *
   * @return the generated IdentityKeyPair.
   */
  public static IdentityKeyPair generateIdentityKeyPair() {
    ECKeyPair   keyPair   = Curve.generateKeyPair(false);
    IdentityKey publicKey = new IdentityKey(keyPair.getPublicKey());
    return new IdentityKeyPair(publicKey, keyPair.getPrivateKey());
  }

  /**
   * Generate a registration ID.  Clients should only do this once,
   * at install time.
   *
   * @return the generated registration ID.
   */
  public static int generateRegistrationId() {
    try {
      return SecureRandom.getInstance("SHA1PRNG").nextInt(16380) + 1;
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
  public List<PreKeyRecord> generatePreKeys(int start, int count) {
    List<PreKeyRecord> results = new LinkedList<>();

    for (int i=0;i<count;i++) {
      results.add(new PreKeyRecord((start + i) % Medium.MAX_VALUE, Curve.generateKeyPair(true)));
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
    ECKeyPair keyPair = Curve.generateKeyPair(true);
    return new PreKeyRecord(Medium.MAX_VALUE, keyPair);
  }

}
