package org.signal.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakIteratorCompatTest {
  @Test
  fun empty() {
    val breakIterator = BreakIteratorCompat.getInstance()
    breakIterator.setText("")

    assertEquals(BreakIteratorCompat.DONE, breakIterator.next())
  }

  @Test
  fun single() {
    val breakIterator = BreakIteratorCompat.getInstance()
    breakIterator.setText("a")

    assertEquals(1, breakIterator.next())
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next())
  }

  @Test
  fun count_empty() {
    val breakIterator = BreakIteratorCompat.getInstance()
    breakIterator.setText("")

    assertEquals(0, breakIterator.countBreaks())
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next())
  }

  @Test
  fun count_simple_text() {
    val breakIterator = BreakIteratorCompat.getInstance()
    breakIterator.setText("abc")

    assertEquals(3, breakIterator.countBreaks())
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next())
  }

  @Test
  fun two_counts() {
    val breakIterator = BreakIteratorCompat.getInstance()
    breakIterator.setText("abc")

    assertEquals(3, breakIterator.countBreaks())
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next())
    assertEquals(3, breakIterator.countBreaks())
  }

  @Test
  fun count_multi_character_graphemes() {
    val hindi = "समाजो गयेग"

    val breakIterator = BreakIteratorCompat.getInstance()
    breakIterator.setText(hindi)

    assertEquals(7, breakIterator.countBreaks())
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next())
  }

  @Test
  fun iterate_multi_character_graphemes() {
    val hindi = "समाजो गयेग"

    val breakIterator = BreakIteratorCompat.getInstance()
    breakIterator.setText(hindi)

    assertEquals(listOf("स", "मा", "जो", " ", "ग", "ये", "ग"), breakIterator.toList())
    assertEquals(BreakIteratorCompat.DONE, breakIterator.next())
  }

  @Test
  fun split_multi_character_graphemes() {
    val hindi = "समाजो गयेग"

    val breakIterator = BreakIteratorCompat.getInstance()
    breakIterator.setText(hindi)

    assertEquals("समाजो गयेग", breakIterator.take(8))
    assertEquals("समाजो गयेग", breakIterator.take(7))
    assertEquals("समाजो गये", breakIterator.take(6))
    assertEquals("समाजो ग", breakIterator.take(5))
    assertEquals("समाजो ", breakIterator.take(4))
    assertEquals("समाजो", breakIterator.take(3))
    assertEquals("समा", breakIterator.take(2))
    assertEquals("स", breakIterator.take(1))
    assertEquals("", breakIterator.take(0))
    assertEquals("", breakIterator.take(-1))
  }
}
