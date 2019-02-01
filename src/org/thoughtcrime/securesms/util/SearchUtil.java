package org.thoughtcrime.securesms.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
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
    String       normalizedText      = text.toLowerCase(locale);
    String       normalizedHighlight = highlight.toLowerCase(locale);
    List<String> highlightTokens     = Stream.of(normalizedHighlight.split("\\s")).filter(s -> s.trim().length() > 0).toList();
    List<String> textTokens          = Stream.of(normalizedText.split("\\s")).filter(s -> s.trim().length() > 0).toList();

    List<Pair<Integer, Integer>> ranges = new LinkedList<>();

    int textListIndex = 0;
    int textCharIndex = 0;

    for (String highlightToken : highlightTokens) {
      for (int i = textListIndex; i < textTokens.size(); i++) {
        if (textTokens.get(i).startsWith(highlightToken)) {
          textListIndex = i + 1;
          ranges.add(new Pair<>(textCharIndex, textCharIndex + highlightToken.length()));
          textCharIndex += textTokens.get(i).length() + 1;
          break;
        }
        textCharIndex += textTokens.get(i).length() + 1;
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
