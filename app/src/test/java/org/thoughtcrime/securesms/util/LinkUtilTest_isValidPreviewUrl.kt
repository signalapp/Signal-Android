package org.thoughtcrime.securesms.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Suppress("ClassName")
@RunWith(Parameterized::class)
class LinkUtilTest_isValidPreviewUrl(private val input: String, private val output: Boolean) {

  @Test
  fun isLegal() {
    assertEquals(output, LinkUtil.isValidPreviewUrl(input))
  }

  companion object {
    @Parameterized.Parameters
    @JvmStatic
    fun data(): Collection<Array<Any>> {
      return listOf(
        arrayOf("google.com", false),
        arrayOf("foo.google.com", false),
        arrayOf("https://foo.google.com", true),
        arrayOf("https://foo.google.com.", true),
        arrayOf("https://foo.google.com/some/path.html", true),
        arrayOf("кц.рф", false),
        arrayOf("https://кц.рф/some/path", true),
        arrayOf("https://abcdefg.onion", false),
        arrayOf("https://abcdefg.i2p", false),
        arrayOf("http://кц.com", false),
        arrayOf("кц.com", false),
        arrayOf("http://asĸ.com", false),
        arrayOf("http://foo.кц.рф", false),
        arrayOf("кц.рф\u202C", false),
        arrayOf("кц.рф\u202D", false),
        arrayOf("кц.рф\u202E", false),
        arrayOf("кц.рф\u2500", false),
        arrayOf("кц.рф\u25AA", false),
        arrayOf("кц.рф\u25FF", false),
        arrayOf("", false),
        arrayOf("https://…", false),
        arrayOf("https://...", false),
        arrayOf("https://cool.example", false),
        arrayOf("https://cool.example.com", false),
        arrayOf("https://cool.example.net", false),
        arrayOf("https://cool.example.org", false),
        arrayOf("https://cool.invalid", false),
        arrayOf("https://cool.localhost", false),
        arrayOf("https://localhost", false),
        arrayOf("https://cool.test", false),
        arrayOf("https://cool.invalid.com", true),
        arrayOf("https://cool.localhost.signal.org", true),
        arrayOf("https://cool.test.blarg.gov", true),
        arrayOf("https://github.com/signalapp/Signal-Android/compare/v6.23.2...v6.23.3", true)
      )
    }
  }
}
