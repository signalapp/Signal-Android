package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

public enum Direction {
  // These are serialized into the database, do not change the values.
  SENT(0),
  RECEIVED(1);

  private final int value;

  Direction(int value) {
    this.value = value;
  }

  public int serialize() {
    return value;
  }

  public static @NonNull Direction deserialize(int value) {
    if      (value == Direction.SENT.value)     return Direction.SENT;
    else if (value == Direction.RECEIVED.value) return Direction.RECEIVED;
    else                                        throw new AssertionError("" + value);
  }

  public boolean isReceived() {
    return this == RECEIVED;
  }

  public boolean isSent() {
    return this == SENT;
  }
}
