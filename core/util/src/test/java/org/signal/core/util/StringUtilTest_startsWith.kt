@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package org.signal.core.util

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import java.lang.Boolean as JavaBoolean

@Suppress("ClassName")
@RunWith(value = ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StringUtilTest_startsWith {

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
        arrayOf("Text", "Te", true),
        arrayOf("Text", "", true),
        arrayOf("Text", "te", false),
        arrayOf("…Text", "…Te", true),
        arrayOf("", "Te", false),
        arrayOf("Text", "Text", true),
        arrayOf("Text", "Text2", false),
        arrayOf("\uD83D\uDC64Text", "Te", false),
        arrayOf("Text text text\uD83D\uDC64", "\uD83D\uDC64", false),
        arrayOf("\uD83D\uDC64Text", "\uD83D\uDC64Te", true)
      )
    }
  }

  @Test
  fun replace() {
    val result = StringUtil.startsWith(text, substring)

    assertEquals(expected, result)
  }
}
