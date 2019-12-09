package org.thoughtcrime.securesms.registration;

import androidx.annotation.NonNull;

public final class ReceivedSmsEvent {

  private final @NonNull String code;

  public ReceivedSmsEvent(@NonNull String code) {
    this.code = code;
  }

  public @NonNull String getCode() {
    return code;
  }
}
