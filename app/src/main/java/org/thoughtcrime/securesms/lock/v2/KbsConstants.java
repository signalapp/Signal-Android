package org.thoughtcrime.securesms.lock.v2;

import org.thoughtcrime.securesms.keyvalue.SignalStore;

public final class KbsConstants {

  public static final int MINIMUM_PIN_LENGTH        = 6;
  public static final int LEGACY_MINIMUM_PIN_LENGTH = 4;

  private KbsConstants() { }

  public static int minimumPossiblePinLength() {
    return SignalStore.kbsValues().hasMigratedToPinsForAll() ? MINIMUM_PIN_LENGTH : LEGACY_MINIMUM_PIN_LENGTH;
  }
}
