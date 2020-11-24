/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.kdf;

import org.whispersystems.libsignal.util.ByteUtil;

public class DerivedRootSecrets {

  public static final int SIZE = 64;

  private final byte[] rootKey;
  private final byte[] chainKey;

  public DerivedRootSecrets(byte[] okm) {
    byte[][] keys = ByteUtil.split(okm, 32, 32);
    this.rootKey  = keys[0];
    this.chainKey = keys[1];
  }

  public byte[] getRootKey() {
    return rootKey;
  }

  public byte[] getChainKey() {
    return chainKey;
  }

}
