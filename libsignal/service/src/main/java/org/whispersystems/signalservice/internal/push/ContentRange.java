package org.whispersystems.signalservice.internal.push;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContentRange {
  private static final Pattern PATTERN = Pattern.compile("versions (\\d+)-(\\d+)\\/(\\d+)");

  private final int rangeStart;
  private final int rangeEnd;
  private final int totalSize;

  /**
   * Parses a content range header.
   */
  public static Optional<ContentRange> parse(String header) {
    if (header != null) {
      Matcher matcher = PATTERN.matcher(header);

      if (matcher.matches()) {
        return Optional.of(new ContentRange(Integer.parseInt(matcher.group(1)),
                                            Integer.parseInt(matcher.group(2)),
                                            Integer.parseInt(matcher.group(3))));
      }
    }

    return Optional.absent();
  }

  private ContentRange(int rangeStart, int rangeEnd, int totalSize) {
    this.rangeStart = rangeStart;
    this.rangeEnd   = rangeEnd;
    this.totalSize  = totalSize;
  }

  public int getRangeStart() {
    return rangeStart;
  }

  public int getRangeEnd() {
    return rangeEnd;
  }

  public int getTotalSize() {
    return totalSize;
  }
}
