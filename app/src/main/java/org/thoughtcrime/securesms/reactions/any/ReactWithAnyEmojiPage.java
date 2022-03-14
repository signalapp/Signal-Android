package org.thoughtcrime.securesms.reactions.any;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.List;
import java.util.Objects;

/**
 * Represents a swipeable page in the ReactWithAnyEmoji dialog fragment, encapsulating any
 * {@link ReactWithAnyEmojiPageBlock}s contained on that page. It is assumed that there is at least
 * one page present.
 *
 * This class also exposes several properties based off of that list, in order to allow the ReactWithAny
 * bottom sheet to properly lay out its tabs and assign labels as the user moves between pages.
 */
class ReactWithAnyEmojiPage {

  private final List<ReactWithAnyEmojiPageBlock> pageBlocks;

  ReactWithAnyEmojiPage(@NonNull List<ReactWithAnyEmojiPageBlock> pageBlocks) {
    Preconditions.checkArgument(!pageBlocks.isEmpty());

    this.pageBlocks = pageBlocks;
  }

  public @NonNull String getKey() {
    return pageBlocks.get(0).getPageModel().getKey();
  }

  public @StringRes int getLabel() {
    return pageBlocks.get(0).getLabel();
  }

  public boolean hasEmoji() {
    return !pageBlocks.get(0).getPageModel().getEmoji().isEmpty();
  }

  public List<ReactWithAnyEmojiPageBlock> getPageBlocks() {
    return pageBlocks;
  }

  public @AttrRes int getIconAttr() {
    return pageBlocks.get(0).getPageModel().getIconAttr();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReactWithAnyEmojiPage that = (ReactWithAnyEmojiPage) o;
    return pageBlocks.equals(that.pageBlocks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pageBlocks);
  }
}
