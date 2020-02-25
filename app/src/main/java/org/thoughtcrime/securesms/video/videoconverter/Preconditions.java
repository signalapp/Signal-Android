package org.thoughtcrime.securesms.video.videoconverter;

final class Preconditions {

  static void checkState(final Object errorMessage, final boolean expression) {
    if (!expression) {
      throw new IllegalStateException(String.valueOf(errorMessage));
    }
  }
}
