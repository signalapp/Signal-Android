package org.thoughtcrime.securesms.payments.preferences;

import androidx.annotation.NonNull;

public enum PaymentType {
  REQUEST("request"),
  PAYMENT("payment");

  private final String code;

  PaymentType(@NonNull String code) {
    this.code = code;
  }

  @NonNull String getCode() {
    return code;
  }
}
