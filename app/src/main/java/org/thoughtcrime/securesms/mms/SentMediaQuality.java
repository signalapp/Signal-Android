package org.thoughtcrime.securesms.mms;

import androidx.annotation.NonNull;

/**
 * Quality levels to send media at.
 */
public enum SentMediaQuality {
  STANDARD(0),
  HIGH(1);


  private final int code;

  SentMediaQuality(int code) {
    this.code = code;
  }

  public static @NonNull SentMediaQuality fromCode(int code) {
    if (HIGH.code == code) {
      return HIGH;
    }
    return STANDARD;
  }

  public int getCode() {
    return code;
  }
}
