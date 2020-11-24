package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;

import com.annimon.stream.Stream;

import org.whispersystems.libsignal.util.Pair;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class SearchUtil {

  public static Spannable getHighlightedSpan(@NonNull Locale locale,
                                             @NonNull StyleFactory styleFactory,
                                             @Nullable String text,
                                             @Nullable String highlight)
  {
    if (TextUtils.isEmpty(text)) {
      return new SpannableString("");
    }

    text = text.replaceAll("\n", " ");

    return getHighlightedSpan(locale, styleFactory, new SpannableString(text), highlight);
  }

  public static Spannable getHighlightedSpan(@NonNull Locale locale,
                                             @NonNull StyleFactory styleFactory,
                                             @Nullable Spannable text,
                                             @Nullable String highlight)
  {
    if (TextUtils.isEmpty(text)) {
      return new SpannableString("");
    }


    if (TextUtils.isEmpty(highlight)) {
      return text;
    }

    List<Pair<Integer, Integer>> ranges  = getHighlightRanges(locale, text.toString(), highlight);
    SpannableString              spanned = new SpannableString(text);

    for (Pair<Integer, Integer> range : ranges) {
      spanned.setSpan(styleFactory.create(), range.first(), range.second(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    return spanned;
  }

  static List<Pair<Integer, Integer>> getHighlightRanges(@NonNull Locale locale,
                                                         @NonNull String text,
                                                         @NonNull String highlight)
  {
    if (text.length() == 0) {
      return Collections.emptyList();
    }

    String       normalizedText      = text.toLowerCase(locale);
    String       normalizedHighlight = highlight.toLowerCase(locale);
    List<String> highlightTokens     = Stream.of(normalizedHighlight.split("\\s")).filter(s -> s.trim().length() > 0).toList();

    List<Pair<Integer, Integer>> ranges = new LinkedList<>();

    int lastHighlightEndIndex = 0;

    for (String highlightToken : highlightTokens) {
      int index;

      do {
        index = normalizedText.indexOf(highlightToken, lastHighlightEndIndex);
        lastHighlightEndIndex = index + highlightToken.length();
      } while (index > 0 && !Character.isWhitespace(normalizedText.charAt(index - 1)));

      if (index >= 0) {
        ranges.add(new Pair<>(index, lastHighlightEndIndex));
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

  public interface StyleFactory {
    CharacterStyle create();
  }
}
