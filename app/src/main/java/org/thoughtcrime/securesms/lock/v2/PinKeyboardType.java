package org.thoughtcrime.securesms.lock.v2;

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;

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

  public int getIconResource() {
    if (this == ALPHA_NUMERIC) return R.drawable.ic_keyboard_24;
    else                       return R.drawable.ic_number_pad_conversation_filter_24;
  }
}
