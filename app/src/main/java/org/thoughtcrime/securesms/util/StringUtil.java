package org.thoughtcrime.securesms.util;

import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.text.BidiFormatter;

import com.google.android.collect.Sets;

import org.thoughtcrime.securesms.logging.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class StringUtil {
  private static final String         TAG        = StringUtil.class.getSimpleName();
  private static final Set<Character> WHITESPACE = Sets.newHashSet('\u200E',  // left-to-right mark
                                                                   '\u200F',  // right-to-left mark
                                                                   '\u2007'); // figure space

  private static final class Bidi {
    /** Override text direction  */
    private static final Set<Integer> OVERRIDES = Sets.newHashSet("\u202a".codePointAt(0), /* LRE */
                                                                  "\u202b".codePointAt(0), /* RLE */
                                                                  "\u202d".codePointAt(0), /* LRO */
                                                                  "\u202e".codePointAt(0)  /* RLO */);

    /** Set direction and isolate surrounding text */
    private static final Set<Integer> ISOLATES = Sets.newHashSet("\u2066".codePointAt(0), /* LRI */
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

  private static BreakIteratorCompat getBreakIteratorInstance() {
    if (Build.VERSION.SDK_INT < 24) {
      return new FallbackBreakIterator();
    }
    return new AndroidIcuBreakIterator();
  }

  /**
   * Trims a name string to fit into the byte length requirement.
   *
   * This method treats a surrogate pair and a grapheme cluster a single character
   * See examples in tests defined in `StringUtilText_trimToFit`.
   */
  public static @NonNull String trimToFit(@Nullable String name, int maxByteLength) {
    if (name == null) return "";
    if (name.length() == 0) return "";
    if (name.getBytes(StandardCharsets.UTF_8).length <= maxByteLength) return name;

    BreakIteratorCompat breakIterator = getBreakIteratorInstance();
    breakIterator.setText(name);

    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try {
      int endIndex = breakIterator.first();
      while (!breakIterator.wasDone(endIndex)) {
        int startIndex = endIndex;
        endIndex = breakIterator.next();
        if (breakIterator.wasDone(endIndex)) break;

        String chr = name.substring(startIndex, endIndex);
        byte[] bytes = chr.getBytes(StandardCharsets.UTF_8);
        if (stream.size() + bytes.length > maxByteLength) break;

        stream.write(bytes);
      }
      String trimmed = stream.toString();
      stream.close();
      return trimmed;
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      // Fall back to legacy code for now
      return legacyTrimToFit(name, maxByteLength);
    }
  }

  private static @NonNull String legacyTrimToFit(@Nullable String name, int maxLength) {
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
  public static @NonNull String isolateBidi(@NonNull String text) {
    if (text.isEmpty()) {
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

  /**
   * An interface to abstract away two different BreakIterator implementations.
   */
  private interface BreakIteratorCompat {
    int first();
    int next();
    void setText(String text);
    boolean wasDone(int index);
  }

  /**
   * An BreakIteratorCompat implementation that delegates calls to `android.icu.text.BreakIterator`.
   * This class handles grapheme clusters fine but requires Android API >= 24.
   */
  @RequiresApi(24)
  private static class AndroidIcuBreakIterator implements BreakIteratorCompat {
    private android.icu.text.BreakIterator breakIterator = android.icu.text.BreakIterator.getCharacterInstance();

    @Override
    public int first() {
      return breakIterator.first();
    }

    @Override
    public int next() {
      return breakIterator.next();
    }

    @Override
    public void setText(String text) {
      breakIterator.setText(text);
    }

    @Override
    public boolean wasDone(int index) {
      return index == android.icu.text.BreakIterator.DONE;
    }
  }

  /**
   * An BreakIteratorCompat implementation that delegates calls to `java.text.BreakIterator`.
   * This class may or may not handle grapheme clusters well depending on the underlying implementation.
   * In the emulator, API 23 implements ICU version of the BreakIterator so that it handles grapheme
   * clusters fine. But API 21 implements RuleBasedIterator which does not handle grapheme clusters.
   *
   * If it doesn't handle grapheme clusters correctly, in most cases the combined characters are
   * broken up into pieces when the code tries to trim a string. For example, an emoji that is
   * a combination of a person, gender and skin tone, trimming the character using this class may result
   * in trimming the parts of the character, e.g. a dark skin frowning woman emoji may result in
   * a neutral skin frowning woman emoji.
   */
  private static class FallbackBreakIterator implements BreakIteratorCompat {
    private java.text.BreakIterator breakIterator = java.text.BreakIterator.getCharacterInstance();

    @Override
    public int first() {
      return breakIterator.first();
    }

    @Override
    public int next() {
      return breakIterator.next();
    }

    @Override
    public void setText(String text) {
      breakIterator.setText(text);
    }

    @Override
    public boolean wasDone(int index) {
      return index == java.text.BreakIterator.DONE;
    }
  }
}
