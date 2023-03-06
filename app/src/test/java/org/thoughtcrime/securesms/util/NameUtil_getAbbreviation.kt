package org.thoughtcrime.securesms.util

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(value = ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class NameUtil_getAbbreviation(
  private val name: String,
  private val expected: String?
) {

  @Test
  fun test_getAbbreviation() {
    val actual: String? = NameUtil.getAbbreviation(name)
    assertEquals(expected, actual)
  }

  companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: getAbbreviation({0})={1}")
    fun params() = listOf(
      arrayOf("Gwen Stacy", "GS"),
      arrayOf("Gwen", "G"),
      arrayOf("gwen stacy", "gs"),
      arrayOf("Mary Jane Watson", "MJ"),
      arrayOf("Mary-Jane Watson", "MW"),
      arrayOf("αlpha Ωmega", "αΩ"),
      arrayOf("љabc ђ123", "љђ"),
      // Works on device, but for whatever reason doesn't work in robolectric
//      arrayOf("Bob \uD83C\uDDE8\uD83C\uDDFF", "B\uD83C\uDDE8\uD83C\uDDFF"),
      arrayOf("", null)
    )
  }
}
