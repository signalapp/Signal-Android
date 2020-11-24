package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.whispersystems.libsignal.util.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SearchUtilTest {

  private static final Locale LOCALE = Locale.ENGLISH;

  @Test
  public void getHighlightRanges_singleHighlightToken() {
    String                       text      = "abc";
    String                       highlight = "a";
    List<Pair<Integer, Integer>> result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(0, 1)), result);
  }

  @Test
  public void getHighlightRanges_singleHighlightTokenWithNewLines() {
    String                       text      = "123\n\n\nabc";
    String                       highlight = "a";
    List<Pair<Integer, Integer>> result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(6, 7)), result);
  }

  @Test
  public void getHighlightRanges_multipleHighlightTokens() {
    String                       text      = "a bc";
    String                       highlight = "a b";
    List<Pair<Integer, Integer>> result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(0, 1), new Pair<>(2, 3)), result);


    text      = "abc def";
    highlight = "ab de";
    result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(0, 2), new Pair<>(4, 6)), result);
  }

  @Test
  public void getHighlightRanges_onlyHighlightPrefixes() {
    String                       text      = "abc";
    String                       highlight = "b";
    List<Pair<Integer, Integer>> result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertTrue(result.isEmpty());

    text      = "abc";
    highlight = "c";
    result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertTrue(result.isEmpty());
  }

  @Test
  public void getHighlightRanges_resultNotInFirstToken() {
    String                       text      = "abc def ghi";
    String                       highlight = "gh";
    List<Pair<Integer, Integer>> result    = SearchUtil.getHighlightRanges(LOCALE, text, highlight);

    assertEquals(Arrays.asList(new Pair<>(8, 10)), result);
  }
}
