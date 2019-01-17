package org.thoughtcrime.securesms.components.emoji;

import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class StaticEmojiPageModel implements EmojiPageModel {
  @AttrRes  private final int      iconAttr;
  @NonNull  private final String[] emoji;
  @NonNull  private final String[] displayEmoji;
  @Nullable private final String   sprite;

  public StaticEmojiPageModel(@AttrRes int iconAttr, @NonNull String[] emoji, @Nullable String sprite) {
    this(iconAttr, emoji, emoji, sprite);
  }

  public StaticEmojiPageModel(@AttrRes int iconAttr, @NonNull String[] emoji, @NonNull String[] displayEmoji, @Nullable String sprite) {
    this.iconAttr     = iconAttr;
    this.emoji        = emoji;
    this.displayEmoji = displayEmoji;
    this.sprite       = sprite;
  }

  public int getIconAttr() {
    return iconAttr;
  }

  @Override
  public @NonNull String[] getEmoji() {
    return emoji;
  }

  @Override
  public @NonNull String[] getDisplayEmoji() {
    return displayEmoji;
  }

  @Override
  public boolean hasSpriteMap() {
    return sprite != null;
  }

  @Override
  public @Nullable String getSprite() {
    return sprite;
  }

  @Override
  public boolean isDynamic() {
    return false;
  }
}
