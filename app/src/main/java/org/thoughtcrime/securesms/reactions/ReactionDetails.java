package org.thoughtcrime.securesms.reactions;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.Recipient;

public class ReactionDetails {
  private final Recipient sender;
  private final String    baseEmoji;
  private final String    displayEmoji;
  private final long      timestamp;

  ReactionDetails(@NonNull Recipient sender, @NonNull String baseEmoji, @NonNull String displayEmoji, long timestamp) {
    this.sender       = sender;
    this.baseEmoji    = baseEmoji;
    this.displayEmoji = displayEmoji;
    this.timestamp    = timestamp;
  }

  public @NonNull Recipient getSender() {
    return sender;
  }

  public @NonNull String getBaseEmoji() {
    return baseEmoji;
  }

  public @NonNull String getDisplayEmoji() {
    return displayEmoji;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
