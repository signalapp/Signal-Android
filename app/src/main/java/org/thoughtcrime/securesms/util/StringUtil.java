package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;

public final class StringUtil {

  private StringUtil() {
  }

  /**
   * Trims a name string to fit into the byte length requirement.
   */
  public static @NonNull String trimToFit(@Nullable String name, int maxLength) {
    if (name == null) return "";

    // At least one byte per char, so shorten string to reduce loop
    if (name.length() > maxLength) {
      name = name.substring(0, maxLength);
    }

    // Remove one char at a time until fits in byte allowance
    while (name.getBytes(StandardCharsets.UTF_8).length > maxLength) {
      name = name.substring(0, name.length() - 1);
    }

    return name;
  }
}
