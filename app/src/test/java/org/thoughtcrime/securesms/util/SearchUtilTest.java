package org.thoughtcrime.securesms.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import kotlin.Pair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SearchUtilTest {

  private static final Locale LOCALE = Locale.ENGLISH;

  @Test
  public void getStrictHighlightRanges_singleHighlightToken() {
    String                       text      = "abc";
    String                       highlight = "a";
    List<Pair<Integer, Integer>> result    = SearchUtil.getStrictHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(0, 1)), result);
  }

  @Test
  public void getStrictHighlightRanges_singleHighlightTokenWithNewLines() {
    String                       text      = "123\n\n\nabc";
    String                       highlight = "a";
    List<Pair<Integer, Integer>> result    = SearchUtil.getStrictHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(6, 7)), result);
  }

  @Test
  public void getStrictHighlightRanges_multipleHighlightTokens() {
    String                       text      = "a bc";
    String                       highlight = "a b";
    List<Pair<Integer, Integer>> result    = SearchUtil.getStrictHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(0, 1), new Pair<>(2, 3)), result);


    text      = "abc def";
    highlight = "ab de";
    result    = SearchUtil.getStrictHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(0, 2), new Pair<>(4, 6)), result);
  }

  @Test
  public void getStrictHighlightRanges_onlyHighlightPrefixes() {
    String                       text      = "abc";
    String                       highlight = "b";
    List<Pair<Integer, Integer>> result    = SearchUtil.getStrictHighlightRanges(LOCALE, text, highlight);

    assertTrue(result.isEmpty());

    text      = "abc";
    highlight = "c";
    result    = SearchUtil.getStrictHighlightRanges(LOCALE, text, highlight);

    assertTrue(result.isEmpty());
  }

  @Test
  public void getStrictHighlightRanges_resultNotInFirstToken() {
    String                       text      = "abc def ghi";
    String                       highlight = "gh";
    List<Pair<Integer, Integer>> result    = SearchUtil.getStrictHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(8, 10)), result);
  }

  @Test
  public void getHighlightRanges_singleHighlightToken() {
    String                       text      = "abc";
    String                       highlight = "a";
    List<Pair<Integer, Integer>> result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(0, 1)), result);
  }

  @Test
  public void getHighlightRanges_singleHighlightTokenMultipleMatches_turkish_text() {
    String                       text      = "İaİ";
    String                       highlight = "i";
    List<Pair<Integer, Integer>> result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertEquals(List.of(new Pair<>(0, 1), new Pair<>(2, 3)), result);
  }

  @Test
  public void getHighlightRanges_singleHighlightTokenMultipleMatches_turkish_both() {
    String                       text      = "İaİ";
    String                       highlight = "İaİ";
    List<Pair<Integer, Integer>> result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertEquals(List.of(new Pair<>(0, 3)), result);
  }

  @Test
  public void getHighlightRanges_singleHighlightTokenMultipleMatches_turkish_highlight() {
    String                       text      = "iai";
    String                       highlight = "İaİ";
    List<Pair<Integer, Integer>> result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertEquals(Collections.emptyList(), result);
  }

  @Test
  public void getStrictHighlightRanges_singleHighlightToken_turkish_text() {
    String                       text      = "İaİ";
    String                       highlight = "i";
    List<Pair<Integer, Integer>> result    = SearchUtil.getStrictHighlightRanges(LOCALE, text, highlight);

    assertEquals(List.of(new Pair<>(0, 1)), result);
  }

  @Test
  public void getStrictHighlightRanges_singleHighlightToken_turkish_highlight() {
    String                       text      = "iai";
    String                       highlight = "İaİ";
    List<Pair<Integer, Integer>> result    = SearchUtil.getStrictHighlightRanges(LOCALE, text, highlight);

    assertEquals(Collections.emptyList(), result);
  }

  @Test
  public void getStrictHighlightRanges_singleHighlightToken_turkish_both() {
    String                       text      = "İaİ";
    String                       highlight = "İaİ";
    List<Pair<Integer, Integer>> result    = SearchUtil.getStrictHighlightRanges(LOCALE, text, highlight);

    assertEquals(List.of(new Pair<>(0, 3)), result);
  }

  @Test
  public void getHighlightRanges_singleHighlightTokenMultipleMatches() {
    String                       text      = "blabla";
    String                       highlight = "a";
    List<Pair<Integer, Integer>> result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(2, 3), new Pair<>(5, 6)), result);
  }

  @Test
  public void getHighlightRanges_multipleHighlightTokenMultipleMatches() {
    String                       text      = "test search test string";
    String                       highlight = "test str";
    List<Pair<Integer, Integer>> result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(0, 4), new Pair<>(12, 16), new Pair<>(17,20)), result);
  }
}
