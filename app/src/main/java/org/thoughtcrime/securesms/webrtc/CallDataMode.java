package org.thoughtcrime.securesms.webrtc;

/**
 * Represents the user's desired data mode for calls.
 */
public enum CallDataMode {
  LOW_ALWAYS(0),
  HIGH_ON_WIFI(1),
  HIGH_ALWAYS(2);

  private final int code;

  CallDataMode(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public static CallDataMode fromCode(int code) {
    switch (code) {
      case 1:
        return HIGH_ON_WIFI;
      case 2:
        return HIGH_ALWAYS;
      default:
        return LOW_ALWAYS;
    }
  }
}
