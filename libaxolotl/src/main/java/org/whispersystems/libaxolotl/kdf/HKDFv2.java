package org.whispersystems.libaxolotl.kdf;

public class HKDFv2 extends HKDF {
  @Override
  protected int getIterationStartOffset() {
    return 0;
  }
}
