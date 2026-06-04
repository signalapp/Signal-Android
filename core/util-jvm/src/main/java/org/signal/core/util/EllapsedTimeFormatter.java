package org.signal.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class EllapsedTimeFormatter {
  private final long hours;
  private final long minutes;
  private final long seconds;

  private EllapsedTimeFormatter(long durationMillis) {
    hours   = durationMillis / 3600;
    minutes = durationMillis % 3600 / 60;
    seconds = durationMillis % 3600 % 60;
  }

  @Override
  public @NotNull String toString() {
    if (hours > 0) {
      return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    } else {
      return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
  }

  public static @Nullable EllapsedTimeFormatter fromDurationMillis(long durationMillis) {
    if (durationMillis == -1) {
      return null;
    }

    return new EllapsedTimeFormatter(durationMillis);
  }
}
