package org.thoughtcrime.securesms.components.emoji;

public abstract class EmojiPageModel {
  protected OnModelChangedListener listener;

  public abstract int getIconRes();
  public abstract int[] getCodePoints();

  public void setOnModelChangedListener(OnModelChangedListener listener) {
    this.listener = listener;
  }

  interface OnModelChangedListener {
    void onModelChanged();
  }
}
