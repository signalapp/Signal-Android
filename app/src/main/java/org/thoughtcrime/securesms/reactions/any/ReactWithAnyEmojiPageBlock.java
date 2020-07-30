package org.thoughtcrime.securesms.reactions.any;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.components.emoji.EmojiPageModel;

/**
 * Wraps a single "class" of Emojis, be it a predefined category, recents, etc. and provides
 * a label for that "class".
 */
class ReactWithAnyEmojiPageBlock {

  private final int            label;
  private final EmojiPageModel pageModel;

  ReactWithAnyEmojiPageBlock(@StringRes int label, @NonNull EmojiPageModel pageModel) {
    this.label     = label;
    this.pageModel = pageModel;
  }

  public @StringRes int getLabel() {
    return label;
  }

  public EmojiPageModel getPageModel() {
    return pageModel;
  }
}
