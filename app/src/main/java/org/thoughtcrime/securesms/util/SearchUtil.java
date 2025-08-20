package org.thoughtcrime.securesms.util;

import android.text.Annotation;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.core.util.StringUtil;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation;

import java.security.InvalidParameterException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class SearchUtil {

  public static final int STRICT    = 0;
  public static final int MATCH_ALL = 1;

  public static Spannable getHighlightedSpan(@NonNull Locale locale,
                                             @NonNull StyleFactory styleFactory,
                                             @Nullable CharSequence text,
                                             @Nullable String highlight,
                                             int matchMode)
  {
    if (TextUtils.isEmpty(text)) {
      return new SpannableString("");
    }

    text = StringUtil.replace(text, '\n', " ");

    return getHighlightedSpan(locale, styleFactory, SpannableString.valueOf(text), highlight, matchMode);
  }

  public static Spannable getHighlightedSpan(@NonNull Locale locale,
                                             @NonNull StyleFactory styleFactory,
                                             @Nullable Spannable text,
                                             @Nullable String highlight,
                                             int matchMode)
  {
    if (TextUtils.isEmpty(text)) {
      return new SpannableString("");
    }


    if (TextUtils.isEmpty(highlight)) {
      return text;
    }

    SpannableString              spanned = new SpannableString(text);
    List<Pair<Integer, Integer>> ranges;

    switch (matchMode) {
      case STRICT:
        ranges = getStrictHighlightRanges(locale, text.toString(), highlight);
        break;
      case MATCH_ALL:
        ranges = getHighlightRanges(locale, text.toString(), highlight);
        break;
      default:
        throw new InvalidParameterException("match mode must be STRICT or MATCH_ALL: " + matchMode);
    }

    for (Pair<Integer, Integer> range : ranges) {
      CharacterStyle[] styles = styleFactory.createStyles();
      for (CharacterStyle style : styles) {
        List<Annotation> annotations = SpoilerAnnotation.getSpoilerAnnotations(spanned, range.first(), range.second());
        if (annotations.isEmpty()) {
          spanned.setSpan(style, range.first(), range.second(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
    }

    return spanned;
  }

  static List<Pair<Integer, Integer>> getStrictHighlightRanges(@NonNull Locale locale,
                                                               @NonNull String text,
                                                               @NonNull String highlight) {
    String       normalizedText      = text.toLowerCase(locale);
    String       normalizedHighlight = highlight.toLowerCase(locale);
    List<String> highlightTokens     = Stream.of(normalizedHighlight.split("\\s"))
                                             .filter(s -> !s.trim().isEmpty())
                                             .toList();

    int[]                        indexMap = buildIndexMap(text, normalizedText, locale);
    List<Pair<Integer, Integer>> ranges   = new LinkedList<>();

    int lastHighlightEndIndex = 0;

    for (String highlightToken : highlightTokens) {
      int index;

      do {
        index                 = normalizedText.indexOf(highlightToken, lastHighlightEndIndex);
        lastHighlightEndIndex = index + highlightToken.length();
      } while (index > 0 && !Character.isWhitespace(normalizedText.charAt(index - 1)));

      if (index >= 0) {
        // Map normalized range back to original text indices
        int start = indexMap[index];
        int end   = indexMap[Math.min(index + highlightToken.length() - 1, indexMap.length - 1)] + 1;
        ranges.add(new Pair<>(start, end));
      }

      if (index < 0 || lastHighlightEndIndex >= normalizedText.length()) {
        break;
      }
    }

    if (ranges.size() != highlightTokens.size()) {
      return Collections.emptyList();
    }

    return ranges;
  }


  private static int[] buildIndexMap(@NonNull String original, @NonNull String normalized, @NonNull Locale locale) {
    int[] indexMap = new int[normalized.length()];

    int originalCharIndex   = 0;
    int normalizedCharIndex = 0;

    while (originalCharIndex < original.length() && normalizedCharIndex < normalized.length()) {
      String originalCharacter   = String.valueOf(original.charAt(originalCharIndex));
      String normalizedCharacter = originalCharacter.toLowerCase(locale);

      for (int i = 0; i < normalizedCharacter.length() && normalizedCharIndex < indexMap.length; i++, normalizedCharIndex++) {
        indexMap[normalizedCharIndex] = originalCharIndex;
      }

      originalCharIndex++;
    }

    return indexMap;
  }

  static List<Pair<Integer, Integer>> getHighlightRanges(@NonNull Locale locale,
                                                         @NonNull String text,
                                                         @NonNull String highlight) {
    String       normalizedText      = text.toLowerCase(locale);
    String       normalizedHighlight = highlight.toLowerCase(locale);
    List<String> highlightTokens     = Stream.of(normalizedHighlight.split("\\s"))
                                             .filter(s -> !s.trim().isEmpty())
                                             .toList();

    int[] indexMap = buildIndexMap(text, normalizedText, locale);

    List<Pair<Integer, Integer>> ranges    = new LinkedList<>();
    int                          lastIndex = 0;

    for (String highlightToken : highlightTokens) {
      int index = 0;
      lastIndex = 0;

      while (index != -1) {
        index = normalizedText.indexOf(highlightToken, lastIndex);
        if (index != -1) {
          // Map normalized range back to original text indices
          int start = indexMap[index];
          int end   = indexMap[Math.min(index + highlightToken.length() - 1, indexMap.length - 1)] + 1;
          ranges.add(new Pair<>(start, end));
          lastIndex = index + highlightToken.length();
        }
      }
    }

    return ranges;
  }

  public interface StyleFactory {
    CharacterStyle[] createStyles();
  }
}
