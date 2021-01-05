package org.thoughtcrime.securesms.video.videoconverter;

public final class Preconditions {

  public static void checkState(final String errorMessage, final boolean expression) {
    if (!expression) {
      throw new IllegalStateException(errorMessage);
    }
  }
}
