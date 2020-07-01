package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.collect.Sets;

import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class StringUtil {

  private static final Set<Character> WHITESPACE = Sets.newHashSet('\u200E',  // left-to-right mark
                                                                   '\u200F',  // right-to-left mark
                                                                   '\u2007'); // figure space

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

  /**
   * @return True if the string is empty, or if it contains nothing but whitespace characters.
   *         Accounts for various unicode whitespace characters.
   */
  public static boolean isVisuallyEmpty(@Nullable String value) {
    if (value == null || value.length() == 0) {
      return true;
    }

    for (int i = 0; i < value.length(); i++) {
      if (!isVisuallyEmpty(value.charAt(i))) {
        return false;
      }
    }

    return true;
  }

  /**
   * @return True if the character is invisible or whitespace. Accounts for various unicode
   *         whitespace characters.
   */
  public static boolean isVisuallyEmpty(char c) {
    return Character.isWhitespace(c) || WHITESPACE.contains(c);
  }
}
