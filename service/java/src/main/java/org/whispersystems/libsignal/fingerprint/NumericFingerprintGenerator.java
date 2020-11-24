/**
 * Copyright (C) 2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.fingerprint;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.ByteUtil;
import org.whispersystems.libsignal.util.IdentityKeyComparator;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class NumericFingerprintGenerator implements FingerprintGenerator {

  private static final int FINGERPRINT_VERSION = 0;

  private final int iterations;

  /**
   * Construct a fingerprint generator for 60 digit numerics.
   *
   * @param iterations The number of internal iterations to perform in the process of
   *                   generating a fingerprint. This needs to be constant, and synchronized
   *                   across all clients.
   *
   *                   The higher the iteration count, the higher the security level:
   *
   *                   - 1024 ~ 109.7 bits
   *                   - 1400 > 110 bits
   *                   - 5200 > 112 bits
   */
  public NumericFingerprintGenerator(int iterations) {
    this.iterations = iterations;
  }

  /**
   * Generate a scannable and displayble fingerprint.
   *
   * @param localStableIdentifier The client's "stable" identifier.
   * @param localIdentityKey The client's identity key.
   * @param remoteStableIdentifier The remote party's "stable" identifier.
   * @param remoteIdentityKey The remote party's identity key.
   * @return A unique fingerprint for this conversation.
   */
  @Override
  public Fingerprint createFor(String localStableIdentifier, final IdentityKey localIdentityKey,
                               String remoteStableIdentifier, final IdentityKey remoteIdentityKey)
  {
    return createFor(localStableIdentifier,
                     new LinkedList<IdentityKey>() {{
                       add(localIdentityKey);
                     }},
                     remoteStableIdentifier,
                     new LinkedList<IdentityKey>() {{
                       add(remoteIdentityKey);
                     }});
  }

  /**
   * Generate a scannable and displayble fingerprint for logical identities that have multiple
   * physical keys.
   *
   * Do not trust the output of this unless you've been through the device consistency process
   * for the provided localIdentityKeys.
   *
   * @param localStableIdentifier The client's "stable" identifier.
   * @param localIdentityKeys The client's collection of physical identity keys.
   * @param remoteStableIdentifier The remote party's "stable" identifier.
   * @param remoteIdentityKeys The remote party's collection of physical identity key.
   * @return A unique fingerprint for this conversation.
   */
  public Fingerprint createFor(String localStableIdentifier, List<IdentityKey> localIdentityKeys,
                               String remoteStableIdentifier, List<IdentityKey> remoteIdentityKeys)
  {
    byte[] localFingerprint  = getFingerprint(iterations, localStableIdentifier, localIdentityKeys);
    byte[] remoteFingerprint = getFingerprint(iterations, remoteStableIdentifier, remoteIdentityKeys);

    DisplayableFingerprint displayableFingerprint = new DisplayableFingerprint(localFingerprint,
                                                                               remoteFingerprint);

    ScannableFingerprint   scannableFingerprint   = new ScannableFingerprint(localFingerprint,
                                                                             remoteFingerprint);

    return new Fingerprint(displayableFingerprint, scannableFingerprint);
  }

  private byte[] getFingerprint(int iterations, String stableIdentifier, List<IdentityKey> unsortedIdentityKeys) {
    try {
      MessageDigest digest    = MessageDigest.getInstance("SHA-512");
      byte[]        publicKey = getLogicalKeyBytes(unsortedIdentityKeys);
      byte[]        hash      = ByteUtil.combine(ByteUtil.shortToByteArray(FINGERPRINT_VERSION),
                                                 publicKey, stableIdentifier.getBytes());

      for (int i=0;i<iterations;i++) {
        digest.update(hash);
        hash = digest.digest(publicKey);
      }

      return hash;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] getLogicalKeyBytes(List<IdentityKey> identityKeys) {
    ArrayList<IdentityKey> sortedIdentityKeys = new ArrayList<IdentityKey>(identityKeys);
    Collections.sort(sortedIdentityKeys, new IdentityKeyComparator());

    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    for (IdentityKey identityKey : sortedIdentityKeys) {
      byte[] publicKeyBytes = identityKey.getPublicKey().serialize();
      baos.write(publicKeyBytes, 0, publicKeyBytes.length);
    }

    return baos.toByteArray();
  }


}
