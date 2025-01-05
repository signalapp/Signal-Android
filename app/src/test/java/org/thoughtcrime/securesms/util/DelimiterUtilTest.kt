package org.thoughtcrime.securesms.util

import android.text.TextUtils
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DelimiterUtilTest {
  @Before
  fun setup() {
    mockkStatic(TextUtils::class)
    every { TextUtils.isEmpty(any()) } answers {
      (invocation.args.first() as? String)?.isEmpty() ?: true
    }
  }

  @After
  fun cleanup() {
    unmockkStatic(TextUtils::class)
  }

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
