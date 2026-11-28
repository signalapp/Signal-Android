package org.thoughtcrime.securesms.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Suppress("ClassName")
@RunWith(value = Parameterized::class)
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
    @Parameterized.Parameters(name = "{index}: getAbbreviation({0})={1}")
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
      arrayOf("(Gwen) [Stacy]", "GS"),
      arrayOf("- Gwen", "G"),
      arrayOf("Gwen \uD83C\uDFF3\uFE0F\u200D\u26A7\uFE0F", "G\uD83C\uDFF3\uFE0F\u200D\u26A7\uFE0F"),
      arrayOf("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67 Stacy", "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67S"),
      arrayOf("\u2764\uFE0F", "\u2764\uFE0F"),
      arrayOf("", null)
    )
  }
}
