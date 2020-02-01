package org.thoughtcrime.securesms.lock.v2;

import androidx.annotation.Nullable;

public enum PinKeyboardType {
  NUMERIC("numeric"),
  ALPHA_NUMERIC("alphaNumeric");

  private final String code;

  PinKeyboardType(String code) {
    this.code = code;
  }

  public PinKeyboardType getOther() {
    if (this == NUMERIC) return ALPHA_NUMERIC;
    else                 return NUMERIC;
  }

  public String getCode() {
    return code;
  }

  public static PinKeyboardType fromCode(@Nullable String code) {
    for (PinKeyboardType type : PinKeyboardType.values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }

    return NUMERIC;
  }
}
