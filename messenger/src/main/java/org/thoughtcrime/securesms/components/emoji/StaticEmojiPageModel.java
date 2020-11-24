package org.thoughtcrime.securesms.components.emoji;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class StaticEmojiPageModel implements EmojiPageModel {
  @AttrRes  private final int         iconAttr;
  @NonNull  private final List<Emoji> emoji;
  @Nullable private final String      sprite;

  public StaticEmojiPageModel(@AttrRes int iconAttr, @NonNull String[] strings, @Nullable String sprite) {
    List<Emoji> emoji = new ArrayList<>(strings.length);
    for (String s : strings) {
      emoji.add(new Emoji(s));
    }

    this.iconAttr = iconAttr;
    this.emoji    = emoji;
    this.sprite   = sprite;
  }

  public StaticEmojiPageModel(@AttrRes int iconAttr, @NonNull Emoji[] emoji, @Nullable String sprite) {
    this.iconAttr     = iconAttr;
    this.emoji        = Arrays.asList(emoji);
    this.sprite       = sprite;
  }

  public int getIconAttr() {
    return iconAttr;
  }

  @Override
  public @NonNull List<String> getEmoji() {
    List<String> emojis = new LinkedList<>();
    for (Emoji e : emoji) {
      emojis.addAll(e.getVariations());
    }
    return emojis;
  }

  @Override
  public @NonNull List<Emoji> getDisplayEmoji() {
    return emoji;
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
