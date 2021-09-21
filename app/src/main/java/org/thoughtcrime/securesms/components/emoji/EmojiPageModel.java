package org.thoughtcrime.securesms.components.emoji;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.List;

public interface EmojiPageModel {
  String getKey();
  int getIconAttr();
  List<String> getEmoji();
  List<Emoji> getDisplayEmoji();
  @Nullable Uri getSpriteUri();
  boolean isDynamic();
}
