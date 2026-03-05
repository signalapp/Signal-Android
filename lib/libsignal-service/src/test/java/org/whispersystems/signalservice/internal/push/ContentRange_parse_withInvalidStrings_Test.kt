package org.whispersystems.signalservice.internal.push

import assertk.assertThat
import assertk.assertions.isFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ContentRange_parse_withInvalidStrings_Test(private val input: String?) {
  @Test
  fun parse_should_be_absent() {
    assertThat(ContentRange.parse(input).isPresent).isFalse()
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "Content-Range: \"{0}\"")
    fun data(): List<Array<String?>> {
      return listOf(
        arrayOf(null),
        arrayOf(""),
        arrayOf("23-45/67"),
        arrayOf("ersions 23-45/67"),
        arrayOf("versions 23-45"),
        arrayOf("versions a-b/c")
      )
    }
  }
}
