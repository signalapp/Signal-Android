package org.thoughtcrime.securesms.components.emoji;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedList;
import java.util.List;

public class CompositeEmojiPageModel implements EmojiPageModel {
  @AttrRes  private final int         iconAttr;
  @NonNull  private final EmojiPageModel[] models;

  public CompositeEmojiPageModel(@AttrRes int iconAttr, @NonNull EmojiPageModel... models) {
    this.iconAttr = iconAttr;
    this.models   = models;
  }

  public int getIconAttr() {
    return iconAttr;
  }

  @Override
  public @NonNull List<String> getEmoji() {
    List<String> emojis = new LinkedList<>();
    for (EmojiPageModel model : models) {
      emojis.addAll(model.getEmoji());
    }
    return emojis;
  }

  @Override
  public @NonNull List<Emoji> getDisplayEmoji() {
    List<Emoji> emojis = new LinkedList<>();
    for (EmojiPageModel model : models) {
      emojis.addAll(model.getDisplayEmoji());
    }
    return emojis;
  }

  @Override
  public boolean hasSpriteMap() {
    return false;
  }

  @Override
  public @Nullable String getSprite() {
    return null;
  }

  @Override
  public boolean isDynamic() {
    return false;
  }
}
