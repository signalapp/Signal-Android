package org.thoughtcrime.securesms.reactions;

import androidx.annotation.NonNull;

final class EmojiCount {
  private final String baseEmoji;
  private final String displayEmoji;
  private final int    count;

  EmojiCount(@NonNull String baseEmoji, @NonNull String emoji, int count) {
    this.baseEmoji    = baseEmoji;
    this.displayEmoji = emoji;
    this.count        = count;
  }

  public @NonNull String getBaseEmoji() {
    return baseEmoji;
  }

  public @NonNull String getDisplayEmoji() {
    return displayEmoji;
  }

  public int getCount() {
    return count;
  }
}
