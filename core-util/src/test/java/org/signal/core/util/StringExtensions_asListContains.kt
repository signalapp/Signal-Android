package org.signal.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class StringExtensions_asListContains(
  private val model: String,
  private val serializedList: String,
  private val expected: Boolean
) {

  @Test
  fun testModelInList() {
    val actual = serializedList.asListContains(model)
    assertEquals(expected, actual)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: modelInList(model={0}, list={1})={2}")
    fun data(): List<Array<Any>> {
      return listOf<Array<Any>>(
        arrayOf("a", "a", true),
        arrayOf("a", "a,b", true),
        arrayOf("a", "c,a,b", true),
        arrayOf("ab", "a*", true),
        arrayOf("ab", "c,a*,b", true),
        arrayOf("abc", "c,ab*,b", true),

        arrayOf("a", "b", false),
        arrayOf("a", "abc", false),
        arrayOf("b", "a*", false)
      ).toList()
    }
  }
}
