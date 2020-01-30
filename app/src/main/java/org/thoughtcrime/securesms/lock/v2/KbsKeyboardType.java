package org.thoughtcrime.securesms.lock.v2;

import androidx.annotation.Nullable;

public enum KbsKeyboardType {
  NUMERIC("numeric"),
  ALPHA_NUMERIC("alphaNumeric");

  private final String code;

  KbsKeyboardType(String code) {
    this.code = code;
  }

  KbsKeyboardType getOther() {
    if (this == NUMERIC) return ALPHA_NUMERIC;
    else                 return NUMERIC;
  }

  public String getCode() {
    return code;
  }

  public static KbsKeyboardType fromCode(@Nullable String code) {
    for (KbsKeyboardType type : KbsKeyboardType.values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }

    return NUMERIC;
  }
}
