package org.signal.core.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public final class BreakIteratorCompatTest {

  @Test
  public void empty() {
    BreakIteratorCompat breakIterator = BreakIteratorCompat.getInstance();
    breakIterator.setText("");

    assertEquals(BreakIteratorCompat.DONE, breakIterator.next());
  }

  @Test
  public void single() {
    BreakIteratorCompat breakIterator = BreakIteratorCompat.getInstance();
    breakIterator.setText("a");

    assertEquals(1, breakIterator.next());
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next());
  }

  @Test
  public void count_empty() {
    BreakIteratorCompat breakIterator = BreakIteratorCompat.getInstance();
    breakIterator.setText("");

    assertEquals(0, breakIterator.countBreaks());
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next());
  }

  @Test
  public void count_simple_text() {
    BreakIteratorCompat breakIterator = BreakIteratorCompat.getInstance();
    breakIterator.setText("abc");

    assertEquals(3, breakIterator.countBreaks());
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next());
  }

  @Test
  public void two_counts() {
    BreakIteratorCompat breakIterator = BreakIteratorCompat.getInstance();
    breakIterator.setText("abc");

    assertEquals(3, breakIterator.countBreaks());
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next());
    assertEquals(3, breakIterator.countBreaks());
  }

  @Test
  public void count_multi_character_graphemes() {
    String hindi = "समाजो गयेग";

    BreakIteratorCompat breakIterator = BreakIteratorCompat.getInstance();
    breakIterator.setText(hindi);

    assertEquals(7, breakIterator.countBreaks());
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next());
  }

  @Test
  public void iterate_multi_character_graphemes() {
    String hindi = "समाजो गयेग";

    BreakIteratorCompat breakIterator = BreakIteratorCompat.getInstance();
    breakIterator.setText(hindi);

    assertEquals(asList("स", "मा", "जो", " ", "ग", "ये", "ग"), toList(breakIterator));
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next());
  }

  @Test
  public void split_multi_character_graphemes() {
    String hindi = "समाजो गयेग";

    BreakIteratorCompat breakIterator = BreakIteratorCompat.getInstance();
    breakIterator.setText(hindi);

    assertEquals("समाजो गयेग", breakIterator.take(8));
    assertEquals("समाजो गयेग", breakIterator.take(7));
    assertEquals("समाजो गये", breakIterator.take(6));
    assertEquals("समाजो ग", breakIterator.take(5));
    assertEquals("समाजो ", breakIterator.take(4));
    assertEquals("समाजो", breakIterator.take(3));
    assertEquals("समा", breakIterator.take(2));
    assertEquals("स", breakIterator.take(1));
    assertEquals("", breakIterator.take(0));
    assertEquals("", breakIterator.take(-1));
  }

  private List<CharSequence> toList(BreakIteratorCompat breakIterator) {
    List<CharSequence> list = new ArrayList<>();
    breakIterator.forEach(list::add);
    return list;
  }
}
