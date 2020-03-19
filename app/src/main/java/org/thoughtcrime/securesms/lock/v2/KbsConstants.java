package org.thoughtcrime.securesms.lock.v2;

public final class KbsConstants {

  public static final int MINIMUM_PIN_LENGTH        = 6;
  public static final int LEGACY_MINIMUM_PIN_LENGTH = 4;

  private KbsConstants() { }

  public static int minimumPossiblePinLength() {
    return LEGACY_MINIMUM_PIN_LENGTH;
  }
}
