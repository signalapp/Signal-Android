package org.thoughtcrime.securesms.components.emoji.parsing;


import androidx.annotation.NonNull;

public class EmojiDrawInfo {

  private final EmojiPageBitmap page;
  private final int             index;

  /**
   * Creates a blank EmojiDrawInfo, indicating that this character is an emoji but no font bitmap exists for this character.
   */
  public EmojiDrawInfo() {
    this.page = null;
    this.index = 0;
  }

  public EmojiDrawInfo(final @NonNull EmojiPageBitmap page, final int index) {
    this.page  = page;
    this.index = index;
  }

  public EmojiPageBitmap getPage() {
    return page;
  }

  public int getIndex() {
    return index;
  }

  @Override
  public @NonNull String toString() {
    return "DrawInfo{" +
        "page=" + page +
        ", index=" + index +
        '}';
  }
}