package org.thoughtcrime.securesms.reactions.any;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.components.emoji.EmojiPageModel;

import java.util.Objects;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReactWithAnyEmojiPageBlock that = (ReactWithAnyEmojiPageBlock) o;
    return label == that.label                                     &&
           pageModel.getIconAttr() == that.pageModel.getIconAttr() &&
           Objects.equals(pageModel.getEmoji(), that.pageModel.getEmoji());
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, pageModel.getEmoji());
  }
}
