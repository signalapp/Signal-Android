package org.thoughtcrime.securesms.logsubmit;

import androidx.annotation.NonNull;

/**
 * A {@link LogLine} with proper IDs.
 */
public class CompleteLogLine implements LogLine {

  private final long    id;
  private final LogLine line;

  public CompleteLogLine(long id, @NonNull LogLine line) {
    this.id   = id;
    this.line = line;
  }

  @Override
  public long getId() {
    return id;
  }

  @Override
  public @NonNull String getText() {
    return line.getText();
  }

  @Override
  public @NonNull Style getStyle() {
    return line.getStyle();
  }

  @Override
  public @NonNull Placeholder getPlaceholderType() {
    return line.getPlaceholderType();
  }
}
