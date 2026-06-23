@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package org.signal.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.lang.Boolean as JavaBoolean

@Suppress("ClassName")
@RunWith(value = Parameterized::class)
class StringUtilTest_endsWith {

  @Parameter(0)
  lateinit var text: CharSequence

  @Parameter(1)
  lateinit var substring: CharSequence

  @Parameter(2)
  lateinit var expected: JavaBoolean

  companion object {
    @JvmStatic
    @Parameters
    fun data(): Collection<Array<Any>> {
      return listOf(
        arrayOf("Text", "xt", true),
        arrayOf("Text", "", true),
        arrayOf("Text", "XT", false),
        arrayOf("Text…", "xt…", true),
        arrayOf("", "Te", false),
        arrayOf("Text", "Text", true),
        arrayOf("Text", "2Text", false),
        arrayOf("\uD83D\uDC64Text", "Te", false),
        arrayOf("Text text text\uD83D\uDC64", "\uD83D\uDC64", true),
        arrayOf("Text\uD83D\uDC64Text", "\uD83D\uDC64Text", true)
      )
    }
  }

  @Test
  fun replace() {
    val result = StringUtil.endsWith(text, substring)

    assertEquals(expected, result)
  }
}
