package org.thoughtcrime.securesms.components.emoji;

import android.net.Uri;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class StaticEmojiPageModel implements EmojiPageModel {
  @AttrRes  private final int         iconAttr;
  @NonNull  private final List<Emoji> emoji;
  @Nullable private final Uri         sprite;

  public StaticEmojiPageModel(@AttrRes int iconAttr, @NonNull String[] strings, @Nullable Uri sprite) {
    List<Emoji> emoji = new ArrayList<>(strings.length);
    for (String s : strings) {
      emoji.add(new Emoji(Collections.singletonList(s)));
    }

    this.iconAttr = iconAttr;
    this.emoji    = emoji;
    this.sprite   = sprite;
  }

  public StaticEmojiPageModel(@AttrRes int iconAttr, @NonNull List<Emoji> emoji, @Nullable Uri sprite) {
    this.iconAttr     = iconAttr;
    this.emoji        = Collections.unmodifiableList(emoji);
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
  public @Nullable Uri getSpriteUri() {
    return sprite;
  }

  @Override
  public boolean isDynamic() {
    return false;
  }
}
