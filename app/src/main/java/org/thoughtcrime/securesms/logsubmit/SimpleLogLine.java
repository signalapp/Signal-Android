package org.thoughtcrime.securesms.logsubmit;

import androidx.annotation.NonNull;

/**
 * A {@link LogLine} that doesn't worry about IDs.
 */
class SimpleLogLine implements LogLine {

  static final SimpleLogLine EMPTY = new SimpleLogLine("", Style.NONE);

  private final String text;
  private final Style  style;

  SimpleLogLine(@NonNull String text, @NonNull Style style) {
    this.text = text;
    this.style = style;
  }

  @Override
  public long getId() {
    return -1;
  }

  public @NonNull String getText() {
    return text;
  }

  public @NonNull Style getStyle() {
    return style;
  }
}
