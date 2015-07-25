package org.thoughtcrime.securesms.components.emoji;

import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class StaticEmojiPageModel implements EmojiPageModel {
  @AttrRes  private final int      iconAttr;
  @NonNull  private final String[] emoji;
  @Nullable private final String   sprite;

  public StaticEmojiPageModel(@AttrRes int iconAttr, @NonNull String[] emoji, @Nullable String sprite) {
    this.iconAttr  = iconAttr;
    this.emoji     = emoji;
    this.sprite    = sprite;
  }

  public int getIconAttr() {
    return iconAttr;
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
