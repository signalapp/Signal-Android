package org.thoughtcrime.securesms.components.emoji.parsing;


import android.support.annotation.NonNull;

public class EmojiDrawInfo {

  private final EmojiPageBitmap page;
  private final int             index;

  public EmojiDrawInfo(final @NonNull EmojiPageBitmap page, final int index) {
    this.page  = page;
    this.index = index;
  }

  public @NonNull EmojiPageBitmap getPage() {
    return page;
  }

  public int getIndex() {
    return index;
  }

  @Override
  public String toString() {
    return "DrawInfo{" +
        "page=" + page +
        ", index=" + index +
        '}';
  }
}