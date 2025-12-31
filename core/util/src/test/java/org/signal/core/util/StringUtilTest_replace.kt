package org.signal.core.util

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config

@Suppress("ClassName")
@RunWith(value = ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StringUtilTest_replace {

  @Parameter(0)
  lateinit var text: CharSequence

  @Parameter(1)
  lateinit var charToReplace: Character

  @Parameter(2)
  lateinit var replacement: String

  @Parameter(3)
  lateinit var expected: CharSequence

  companion object {
    @JvmStatic
    @Parameters
    fun data(): Collection<Array<Any>> {
      return listOf(
        arrayOf("Replace\nme", '\n', " ", "Replace me"),
        arrayOf("Replace me", '\n', " ", "Replace me"),
        arrayOf("\nReplace me", '\n', " ", " Replace me"),
        arrayOf("Replace me\n", '\n', " ", "Replace me "),
        arrayOf("Replace\n\nme", '\n', " ", "Replace  me"),
        arrayOf("Replace\nme\n", '\n', " ", "Replace me "),
        arrayOf("\n\nReplace\n\nme\n", '\n', " ", "  Replace  me ")
      )
    }
  }

  @Test
  fun replace() {
    val result = StringUtil.replace(text, charToReplace.charValue(), replacement)

    assertEquals(expected.toString(), result.toString())
  }
}
