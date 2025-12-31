package org.whispersystems.signalservice.internal.push

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ContentRange_parse_Test(
  private val input: String?,
  private val expectedRangeStart: Int,
  private val expectedRangeEnd: Int,
  private val expectedSize: Int
) {
  @Test
  fun rangeStart() {
    assertThat(ContentRange.parse(input).get().rangeStart).isEqualTo(expectedRangeStart)
  }

  @Test
  fun rangeEnd() {
    assertThat(ContentRange.parse(input).get().rangeEnd).isEqualTo(expectedRangeEnd)
  }

  @Test
  fun totalSize() {
    assertThat(ContentRange.parse(input).get().totalSize).isEqualTo(expectedSize)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "Content-Range: \"{0}\"")
    fun data(): Collection<Array<Any>> {
      return listOf(
        arrayOf("versions 1-2/3", 1, 2, 3),
        arrayOf("versions 23-45/67", 23, 45, 67)
      )
    }
  }
}
