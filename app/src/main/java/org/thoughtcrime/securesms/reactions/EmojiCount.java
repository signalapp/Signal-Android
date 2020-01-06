package org.thoughtcrime.securesms.reactions;

import androidx.annotation.NonNull;

final class EmojiCount {
  private final String  emoji;
  private final int     count;

  EmojiCount(@NonNull String emoji, int count) {
    this.emoji = emoji;
    this.count = count;
  }

  public @NonNull String getEmoji() {
    return emoji;
  }

  public int getCount() {
    return count;
  }
}
