package org.thoughtcrime.securesms.components.emoji;

public interface EmojiPageModel {
  int getIconRes();
  String[] getEmoji();
  boolean hasSpriteMap();
  String getSprite();
  boolean isDynamic();
}
