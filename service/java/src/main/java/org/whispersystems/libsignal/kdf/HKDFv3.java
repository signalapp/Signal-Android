/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.kdf;

public class HKDFv3 extends HKDF {
  @Override
  protected int getIterationStartOffset() {
    return 1;
  }
}
