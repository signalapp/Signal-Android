package org.thoughtcrime.securesms.components.emoji;

import java.util.List;

public interface EmojiPageModel {
  int getIconAttr();
  List<String> getEmoji();
  List<Emoji> getDisplayEmoji();
  boolean hasSpriteMap();
  String getSprite();
  boolean isDynamic();
}
