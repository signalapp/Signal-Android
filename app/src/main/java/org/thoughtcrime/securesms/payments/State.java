package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

public enum State {
  // These are serialized into the database, do not change the values.
  INITIAL(0),
  SUBMITTED(1),
  SUCCESSFUL(2),
  FAILED(3);

  private final int value;

  State(int value) {
    this.value = value;
  }

  public int serialize() {
    return value;
  }

  public static @NonNull State deserialize(int value) {
    if      (value == State.INITIAL.value)    return State.INITIAL;
    else if (value == State.SUBMITTED.value)  return State.SUBMITTED;
    else if (value == State.SUCCESSFUL.value) return State.SUCCESSFUL;
    else if (value == State.FAILED.value)     return State.FAILED;
    else                                      throw new AssertionError("" + value);
  }

  public boolean isInProgress() {
    return this == INITIAL || this == SUBMITTED;
  }
}
