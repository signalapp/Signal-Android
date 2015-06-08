package org.thoughtcrime.securesms.components.emoji;

public interface EmojiPageModel {
  int getIconAttr();
  String[] getEmoji();
  boolean hasSpriteMap();
  String getSprite();
  boolean isDynamic();
}
