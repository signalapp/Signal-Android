package org.thoughtcrime.securesms.components.emoji.parsing;


import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.emoji.EmojiPageReference;

public class EmojiDrawInfo {

  private final EmojiPageReference page;
  private final int                index;

  public EmojiDrawInfo(final @NonNull EmojiPageReference page, final int index) {
    this.page  = page;
    this.index = index;
  }

  public @NonNull EmojiPageReference getPage() {
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