package org.thoughtcrime.securesms.components.emoji;

import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class StaticEmojiPageModel implements EmojiPageModel {
  @DrawableRes private final int      icon;
  @NonNull     private final String[] emoji;
  @Nullable    private final String   sprite;

  public StaticEmojiPageModel(@DrawableRes int icon, @NonNull String[] emoji, @Nullable String sprite) {
    this.icon   = icon;
    this.emoji  = emoji;
    this.sprite = sprite;
  }

  public int getIconRes() {
    return icon;
  }

  @NonNull public String[] getEmoji() {
    return emoji;
  }

  @Override public boolean hasSpriteMap() {
    return sprite != null;
  }

  @Override @Nullable public String getSprite() {
    return sprite;
  }

  @Override public boolean isDynamic() {
    return false;
  }
}
