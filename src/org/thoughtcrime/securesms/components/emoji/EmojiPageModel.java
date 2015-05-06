package org.thoughtcrime.securesms.components.emoji;

public interface EmojiPageModel {
  int getIconRes();
  int[] getCodePoints();
  void onCodePointSelected(int codePoint);
}
