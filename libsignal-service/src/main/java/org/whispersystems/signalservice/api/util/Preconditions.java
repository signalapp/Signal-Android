package org.whispersystems.signalservice.api.util;

/**
 * Convenient ways to assert expected state.
 */
public final class Preconditions {

  private Preconditions() {}

  public static void checkArgument(boolean state) {
    checkArgument(state, "Condition must be true!");
  }

  public static void checkArgument(boolean state, String message) {
    if (!state) {
      throw new IllegalArgumentException(message);
    }
  }

  public static void checkState(boolean state) {
    checkState(state, "Condition must be true!");
  }

  public static void checkState(boolean state, String message) {
    if (!state) {
      throw new IllegalStateException(message);
    }
  }

  public static <E> E checkNotNull(E object) {
    return checkNotNull(object, "Must not be null!");
  }

  public static <E> E checkNotNull(E object, String message) {
    if (object == null) {
      throw new NullPointerException(message);
    } else {
      return object;
    }
  }
}
