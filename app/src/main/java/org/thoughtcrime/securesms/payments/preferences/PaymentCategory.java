package org.thoughtcrime.securesms.payments.preferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum PaymentCategory {
  ALL("all"),
  SENT("sent"),
  RECEIVED("received");

  private final String code;

  PaymentCategory(@NonNull String code) {
    this.code = code;
  }

  @NonNull String getCode() {
    return code;
  }

  static @NonNull PaymentCategory forCode(@Nullable String code) {
    for (PaymentCategory type : values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }

    return ALL;
  }
}
