package org.whispersystems.signalservice.internal.push;

import java.util.Objects;

/**
 * Represents the processor being used for a given payment, required when accessing
 * receipt credentials.
 */
public enum DonationProcessor {
  STRIPE("STRIPE"),
  PAYPAL("BRAINTREE");

  private final String code;

  DonationProcessor(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public static DonationProcessor fromCode(String code) {
    for (final DonationProcessor value : values()) {
      if (Objects.equals(code, value.code)) {
        return value;
      }
    }

    throw new IllegalArgumentException(code);
  }
}
