package org.thoughtcrime.securesms.components.emoji;

public abstract class EmojiPageModel {
  public abstract int getIconRes();
  public abstract int[] getCodePoints();
  public void onCodePointSelected(int codePoint) { }
  public void setOnModelChangedListener(OnModelChangedListener listener) { }

  interface OnModelChangedListener {
    void onModelChanged();
  }
}
