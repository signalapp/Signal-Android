package org.signal.core.util

import okio.utf8Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Suppress("ClassName")
@RunWith(Parameterized::class)
class StringExtensions_splitByByteLength(
  private val testInput: String,
  private val byteLength: Int,
  private val expected: Pair<String, String?>
) {

  @Test
  fun testModelInList() {
    val actual = testInput.splitByByteLength(byteLength)
    assertEquals(expected, actual)
    assertTrue(actual.first.utf8Size() <= byteLength)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: splitByByteLength(input={0}, byteLength={1})")
    fun data(): List<Array<Any>> {
      return listOf<Array<Any>>(
        arrayOf("1234567890", 0, "" to "1234567890"),
        arrayOf("1234567890", 3, "123" to "4567890"),
        arrayOf("1234567890", 10, "1234567890" to null),
        arrayOf("1234567890", 15, "1234567890" to null),
        arrayOf("大いなる力には大いなる責任が伴う", 0, "" to "大いなる力には大いなる責任が伴う"),
        arrayOf("大いなる力には大いなる責任が伴う", 8, "大い" to "なる力には大いなる責任が伴う"),
        arrayOf("大いなる力には大いなる責任が伴う", 47, "大いなる力には大いなる責任が伴" to "う"),
        arrayOf("大いなる力には大いなる責任が伴う", 48, "大いなる力には大いなる責任が伴う" to null),
        arrayOf("大いなる力には大いなる責任が伴う", 100, "大いなる力には大いなる責任が伴う" to null)
      ).toList()
    }
  }
}
