package org.thoughtcrime.securesms.logsubmit;

import androidx.annotation.NonNull;

/**
 * A {@link LogLine} that doesn't worry about IDs.
 */
class SimpleLogLine implements LogLine {

  static final SimpleLogLine EMPTY = new SimpleLogLine("", Style.NONE, Placeholder.NONE);

  private final String      text;
  private final Style       style;
  private final Placeholder placeholder;

  SimpleLogLine(@NonNull String text, @NonNull Style style, @NonNull Placeholder placeholder) {
    this.text        = text;
    this.style       = style;
    this.placeholder = placeholder;
  }

  @Override
  public long getId() {
    return -1;
  }

  @Override
  public @NonNull String getText() {
    return text;
  }

  @Override
  public @NonNull Style getStyle() {
    return style;
  }

  @Override
  public @NonNull Placeholder getPlaceholderType() {
    return placeholder;
  }
}
