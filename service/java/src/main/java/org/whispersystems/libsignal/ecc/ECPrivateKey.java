/**
 * Copyright (C) 2013-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.libsignal.ecc;

public interface ECPrivateKey {
  public byte[] serialize();
  public int getType();
}
