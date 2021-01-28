package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.BidiFormatter;

import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class StringUtil {

  private static final Set<Character> WHITESPACE = SetUtil.newHashSet('\u200E',  // left-to-right mark
                                                                      '\u200F',  // right-to-left mark
                                                                      '\u2007'); // figure space

  private static final class Bidi {
    /** Override text direction  */
    private static final Set<Integer> OVERRIDES = SetUtil.newHashSet("\u202a".codePointAt(0), /* LRE */
                                                                     "\u202b".codePointAt(0), /* RLE */
                                                                     "\u202d".codePointAt(0), /* LRO */
                                                                     "\u202e".codePointAt(0)  /* RLO */);

    /** Set direction and isolate surrounding text */
    private static final Set<Integer> ISOLATES = SetUtil.newHashSet("\u2066".codePointAt(0), /* LRI */
                                                                    "\u2067".codePointAt(0), /* RLI */
                                                                    "\u2068".codePointAt(0)  /* FSI */);
    /** Closes things in {@link #OVERRIDES} */
    private static final int PDF = "\u202c".codePointAt(0);

    /** Closes things in {@link #ISOLATES} */
    private static final int PDI = "\u2069".codePointAt(0);

    /** Auto-detecting isolate */
    private static final int FSI = "\u2068".codePointAt(0);
  }

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
   * @return A charsequence with no leading or trailing whitespace. Only creates a new charsequence
   *         if it has to.
   */
  public static @NonNull CharSequence trim(@NonNull CharSequence charSequence) {
    if (charSequence.length() == 0) {
      return charSequence;
    }

    int start = 0;
    int end   = charSequence.length() - 1;

    while (start < charSequence.length() && Character.isWhitespace(charSequence.charAt(start))) {
      start++;
    }

    while (end >= 0 && end > start && Character.isWhitespace(charSequence.charAt(end))) {
      end--;
    }

    if (start > 0 || end < charSequence.length() - 1) {
      return charSequence.subSequence(start, end + 1);
    } else {
      return charSequence;
    }
  }

  /**
   * @return True if the string is empty, or if it contains nothing but whitespace characters.
   *         Accounts for various unicode whitespace characters.
   */
  public static boolean isVisuallyEmpty(@Nullable String value) {
    if (value == null || value.length() == 0) {
      return true;
    }

    return indexOfFirstNonEmptyChar(value) == -1;
  }

  /**
   * @return String without any leading or trailing whitespace.
   *         Accounts for various unicode whitespace characters.
   */
  public static String trimToVisualBounds(@NonNull String value) {
    int start = indexOfFirstNonEmptyChar(value);

    if (start == -1) {
      return "";
    }

    int end = indexOfLastNonEmptyChar(value);

    return value.substring(start, end + 1);
  }

  private static int indexOfFirstNonEmptyChar(@NonNull String value) {
    int length = value.length();

    for (int i = 0; i < length; i++) {
      if (!isVisuallyEmpty(value.charAt(i))) {
        return i;
      }
    }

    return -1;
  }

  private static int indexOfLastNonEmptyChar(@NonNull String value) {
    for (int i = value.length() - 1; i >= 0; i--) {
      if (!isVisuallyEmpty(value.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  /**
   * @return True if the character is invisible or whitespace. Accounts for various unicode
   *         whitespace characters.
   */
  public static boolean isVisuallyEmpty(char c) {
    return Character.isWhitespace(c) || WHITESPACE.contains(c);
  }

  /**
   * @return A string representation of the provided unicode code point.
   */
  public static @NonNull String codePointToString(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  /**
   * Isolates bi-directional text from influencing surrounding text. You should use this whenever
   * you're injecting user-generated text into a larger string.
   *
   * You'd think we'd be able to trust {@link BidiFormatter}, but unfortunately it just misses some
   * corner cases, so here we are.
   *
   * The general idea is just to balance out the opening and closing codepoints, and then wrap the
   * whole thing in FSI/PDI to isolate it.
   *
   * For more details, see:
   * https://www.w3.org/International/questions/qa-bidi-unicode-controls
   */
  public static @NonNull String isolateBidi(@Nullable String text) {
    if (text == null) {
      return "";
    }

    if (Util.isEmpty(text)) {
      return text;
    }

    int overrideCount      = 0;
    int overrideCloseCount = 0;
    int isolateCount       = 0;
    int isolateCloseCount  = 0;

    for (int i = 0, len = text.codePointCount(0, text.length()); i < len; i++) {
      int codePoint = text.codePointAt(i);

      if (Bidi.OVERRIDES.contains(codePoint)) {
        overrideCount++;
      } else if (codePoint == Bidi.PDF) {
        overrideCloseCount++;
      } else if (Bidi.ISOLATES.contains(codePoint)) {
        isolateCount++;
      } else if (codePoint == Bidi.PDI) {
        isolateCloseCount++;
      }
    }

    StringBuilder suffix = new StringBuilder();

    while (overrideCount > overrideCloseCount) {
      suffix.appendCodePoint(Bidi.PDF);
      overrideCloseCount++;
    }

    while (isolateCount > isolateCloseCount) {
      suffix.appendCodePoint(Bidi.FSI);
      isolateCloseCount++;
    }

    StringBuilder out = new StringBuilder();

    return out.appendCodePoint(Bidi.FSI)
              .append(text)
              .append(suffix)
              .appendCodePoint(Bidi.PDI)
              .toString();
  }

  public static @Nullable String stripBidiProtection(@Nullable String text) {
    if (text == null) return null;

    return text.replaceAll("[\\u2068\\u2069\\u202c]", "");
  }

  /**
   * Trims a {@link CharSequence} of starting and trailing whitespace. Behavior matches
   * {@link String#trim()} to preserve expectations around results.
   */
  public static CharSequence trimSequence(CharSequence text) {
    int length     = text.length();
    int startIndex = 0;

    while ((startIndex < length) && (text.charAt(startIndex) <= ' ')) {
      startIndex++;
    }
    while ((startIndex < length) && (text.charAt(length - 1) <= ' ')) {
      length--;
    }
    return (startIndex > 0 || length < text.length()) ? text.subSequence(startIndex, length) : text;
  }
}
