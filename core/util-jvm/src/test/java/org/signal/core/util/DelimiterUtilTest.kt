package org.signal.core.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DelimiterUtilTest {
  @Test
  fun testEscape() {
    assertEquals("MTV\\ Music", DelimiterUtil.escape("MTV Music", ' '))
    assertEquals("MTV\\ \\ Music", DelimiterUtil.escape("MTV  Music", ' '))

    assertEquals("MTV\\,Music", DelimiterUtil.escape("MTV,Music", ','))
    assertEquals("MTV\\,\\,Music", DelimiterUtil.escape("MTV,,Music", ','))

    assertEquals("MTV Music", DelimiterUtil.escape("MTV Music", '+'))
  }

  @Test
  fun testSplit() {
    assertArrayEquals(
      arrayOf("MTV\\ Music"),
      DelimiterUtil.split("MTV\\ Music", ' ')
    )
    assertArrayEquals(
      arrayOf("MTV", "Music"),
      DelimiterUtil.split("MTV Music", ' ')
    )
  }

  @Test
  fun testEscapeSplit() {
    "MTV Music".let { input ->
      val intermediate = DelimiterUtil.escape(input, ' ')
      val parts = DelimiterUtil.split(intermediate, ' ')
      assertEquals("MTV\\ Music", parts.single())
      assertEquals("MTV Music", DelimiterUtil.unescape(parts.single(), ' '))
    }

    "MTV\\ Music".let { input ->
      val intermediate = DelimiterUtil.escape(input, ' ')
      val parts = DelimiterUtil.split(intermediate, ' ')
      assertEquals("MTV\\\\ Music", parts.single())
      assertEquals("MTV\\ Music", DelimiterUtil.unescape(parts.single(), ' '))
    }
  }
}
