package org.thoughtcrime.securesms;

import android.os.Build;

import java.util.HashSet;
import java.util.Set;

/**
 * Device hardware capability lists.
 * <p>
 * Moved outside of ApplicationContext as the indirection was important for API19 support with desugaring: https://issuetracker.google.com/issues/183419297
 */
final class RtcDeviceLists {

  private RtcDeviceLists() {}

  static Set<String> hardwareAECBlockList() {
    return new HashSet<String>() {{
      add("Pixel");
      add("Pixel XL");
      add("Moto G5");
      add("Moto G (5S) Plus");
      add("Moto G4");
      add("TA-1053");
      add("Mi A1");
      add("Mi A2");
      add("E5823"); // Sony z5 compact
      add("Redmi Note 5");
      add("FP2"); // Fairphone FP2
      add("MI 5");
    }};
  }

  static Set<String> openSlEsAllowList() {
    return new HashSet<String>() {{
      add("Pixel");
      add("Pixel XL");
    }};
  }

  static boolean hardwareAECBlocked() {
    return hardwareAECBlockList().contains(Build.MODEL);
  }

  static boolean openSLESAllowed() {
    return openSlEsAllowList().contains(Build.MODEL);
  }
}
