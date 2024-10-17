package org.signal.core.util;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.BidiFormatter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class StringUtil {

  private static final Set<Character> WHITESPACE = SetUtil.newHashSet('\u200E',  // left-to-right mark
                                                                      '\u200F',  // right-to-left mark
                                                                      '\u2007',  // figure space
                                                                      '\u200B',  // zero-width space
                                                                      '\u2800'); // braille blank

  private static final Pattern ALL_ASCII_PATTERN = Pattern.compile("^[\\x00-\\x7F]*$");

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
   * <p>
   * This method treats a surrogate pair and a grapheme cluster a single character
   * See examples in tests defined in StringUtilText_trimToFit.
   */
  public static @NonNull String trimToFit(@Nullable String name, int maxByteLength) {
    if (TextUtils.isEmpty(name)) {
      return "";
    }

    if (name.getBytes(StandardCharsets.UTF_8).length <= maxByteLength) {
      return name;
    }

    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      for (String graphemeCharacter : new CharacterIterable(name)) {
        byte[] bytes = graphemeCharacter.getBytes(StandardCharsets.UTF_8);

        if (stream.size() + bytes.length <= maxByteLength) {
          stream.write(bytes);
        } else {
          break;
        }
      }
      return stream.toString();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
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
   * @return True if the provided text contains a mix of LTR and RTL characters, otherwise false.
   */
  public static boolean hasMixedTextDirection(@Nullable CharSequence text) {
    if (text == null) {
      return false;
    }

    Boolean isLtr = null;

    for (int i = 0, len = Character.codePointCount(text, 0, text.length()); i < len; i++) {
      int     codePoint = Character.codePointAt(text, i);
      byte    direction = Character.getDirectionality(codePoint);
      boolean isLetter  = Character.isLetter(codePoint);

      if (isLtr != null && isLtr && direction != Character.DIRECTIONALITY_LEFT_TO_RIGHT && isLetter) {
        return true;
      } else if (isLtr != null && !isLtr && direction != Character.DIRECTIONALITY_RIGHT_TO_LEFT && isLetter) {
        return true;
      } else if (isLetter) {
        isLtr = direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT;
      }
    }

    return false;
  }

  /**
   * @return True if the text is null or has a length of 0, otherwise false.
   */
  public static boolean isEmpty(@Nullable String text) {
    return text == null || text.length() == 0;
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

    if (isEmpty(text)) {
      return text;
    }

    if (ALL_ASCII_PATTERN.matcher(text).matches()) {
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

  public static @NonNull String stripBidiIndicator(@NonNull String text) {
    return text.replace("\u200F", "");
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
   * If the {@param text} exceeds the {@param maxChars} it is trimmed in the middle so that the result is exactly {@param maxChars} long including an added
   * ellipsis character.
   * <p>
   * Otherwise the string is returned untouched.
   * <p>
   * When {@param maxChars} is even, one more character is kept from the end of the string than the start.
   */
  public static @Nullable CharSequence abbreviateInMiddle(@Nullable CharSequence text, int maxChars) {
     if (text == null || text.length() <= maxChars) {
      return text;
    }

    int start = (maxChars - 1) / 2;
    int end   = (maxChars - 1) - start;
    return text.subSequence(0, start) + "…" + text.subSequence(text.length() - end, text.length());
  }

  /**
   * @return The number of graphemes in the provided string.
   */
  public static int getGraphemeCount(@NonNull CharSequence text) {
    BreakIteratorCompat iterator = BreakIteratorCompat.getInstance();
    iterator.setText(text);
    return iterator.countBreaks();
  }

  public static String forceLtr(@NonNull CharSequence text) {
    return "\u202a" + text + "\u202c";
  }

  public static @NonNull CharSequence replace(@NonNull CharSequence text, char toReplace, String replacement) {
    SpannableStringBuilder updatedText = null;

    for (int i = text.length() - 1; i >= 0; i--) {
      if (text.charAt(i) == toReplace) {
        if (updatedText == null) {
          updatedText = SpannableStringBuilder.valueOf(text);
        }
        updatedText.replace(i, i + 1, replacement);
      }
    }

    if (updatedText != null) {
      return updatedText;
    } else {
      return text;
    }
  }

  public static boolean startsWith(@NonNull CharSequence text, @NonNull CharSequence substring) {
    if (substring.length() > text.length()) {
      return false;
    }

    for (int i = 0; i < substring.length(); i++) {
      if (text.charAt(i) != substring.charAt(i)) {
        return false;
      }
    }

    return true;
  }

  public static boolean endsWith(@NonNull CharSequence text, @NonNull CharSequence substring) {
    if (substring.length() > text.length()) {
      return false;
    }

    int textIndex = text.length() - 1;
    for (int substringIndex = substring.length() - 1; substringIndex >= 0; substringIndex--, textIndex--) {
      if (text.charAt(textIndex) != substring.charAt(substringIndex)) {
        return false;
      }
    }

    return true;
  }
}
