package org.thoughtcrime.securesms.components.emoji;

import android.net.Uri;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.util.Util;

import java.util.LinkedList;
import java.util.List;

public class CompositeEmojiPageModel implements EmojiPageModel {
  @AttrRes private final int                  iconAttr;
  @NonNull private final List<EmojiPageModel> models;

  public CompositeEmojiPageModel(@AttrRes int iconAttr, @NonNull List<EmojiPageModel> models) {
    this.iconAttr = iconAttr;
    this.models   = models;
  }

  @Override
  public String getKey() {
    return Util.hasItems(models) ? models.get(0).getKey() : "";
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
  public @Nullable Uri getSpriteUri() {
    return null;
  }

  @Override
  public boolean isDynamic() {
    return false;
  }
}
