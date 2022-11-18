package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

public enum FailureReason {
  // These are serialized into the database, do not change the values.
  UNKNOWN(0),
  INSUFFICIENT_FUNDS(1),
  NETWORK(2);

  private final int value;

  FailureReason(int value) {
    this.value = value;
  }

  public int serialize() {
    return value;
  }

  public static @NonNull FailureReason deserialize(int value) {
    if      (value == FailureReason.UNKNOWN.value)            return FailureReason.UNKNOWN;
    else if (value == FailureReason.INSUFFICIENT_FUNDS.value) return FailureReason.INSUFFICIENT_FUNDS;
    else if (value == FailureReason.NETWORK.value)            return FailureReason.NETWORK;
    else                                                      throw new AssertionError("" + value);
  }
}
