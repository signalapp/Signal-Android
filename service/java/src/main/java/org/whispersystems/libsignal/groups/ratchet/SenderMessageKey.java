/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.libsignal.groups.ratchet;

import org.whispersystems.libsignal.kdf.HKDFv3;
import org.whispersystems.libsignal.util.ByteUtil;

/**
 * The final symmetric material (IV and Cipher Key) used for encrypting
 * individual SenderKey messages.
 *
 * @author Moxie Marlinspike
 */
public class SenderMessageKey {

  private final int    iteration;
  private final byte[] iv;
  private final byte[] cipherKey;
  private final byte[] seed;

  public SenderMessageKey(int iteration, byte[] seed) {
    byte[] derivative = new HKDFv3().deriveSecrets(seed, "WhisperGroup".getBytes(), 48);
    byte[][] parts    = ByteUtil.split(derivative, 16, 32);

    this.iteration = iteration;
    this.seed      = seed;
    this.iv        = parts[0];
    this.cipherKey = parts[1];
  }

  public int getIteration() {
    return iteration;
  }

  public byte[] getIv() {
    return iv;
  }

  public byte[] getCipherKey() {
    return cipherKey;
  }

  public byte[] getSeed() {
    return seed;
  }
}
