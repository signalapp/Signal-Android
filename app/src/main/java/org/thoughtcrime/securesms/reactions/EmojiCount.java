package org.thoughtcrime.securesms.reactions;

import androidx.annotation.NonNull;

import java.util.List;

final class EmojiCount {

  static EmojiCount all(@NonNull List<ReactionDetails> reactions) {
    return new EmojiCount("", "", reactions);
  }

  private final String                baseEmoji;
  private final String                displayEmoji;
  private final List<ReactionDetails> reactions;

  EmojiCount(@NonNull String baseEmoji,
             @NonNull String emoji,
             @NonNull List<ReactionDetails> reactions)
  {
    this.baseEmoji    = baseEmoji;
    this.displayEmoji = emoji;
    this.reactions    = reactions;
  }

  public @NonNull String getBaseEmoji() {
    return baseEmoji;
  }

  public @NonNull String getDisplayEmoji() {
    return displayEmoji;
  }

  public int getCount() {
    return reactions.size();
  }

  public @NonNull List<ReactionDetails> getReactions() {
    return reactions;
  }
}
