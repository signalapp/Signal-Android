package org.thoughtcrime.securesms.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Suppress("ClassName")
@RunWith(Parameterized::class)
class LinkUtilTest_toDisplayUrl(private val input: String, private val expected: String) {

  @Test
  fun toDisplayUrl() {
    assertEquals(expected, LinkUtil.toDisplayUrl(input))
  }

  companion object {
    @Parameterized.Parameters(name = "{0}")
    @JvmStatic
    fun data(): Collection<Array<Any>> {
      return listOf(
        // Punycode host → Unicode when the decoded domain is valid
        arrayOf("https://xn--gr-zia.org",           "https://grå.org"),
        arrayOf("https://xn--gr-zia.org/some/path", "https://grå.org/some/path"),

        // Multi-byte UTF-8 path decoding: %C3%A5 is the UTF-8 encoding of å (U+00E5)
        arrayOf("https://xn--gr-zia.org/gr%C3%A5.html", "https://grå.org/grå.html"),

        // ASCII-range single-byte decoding
        arrayOf("https://example.com/%61%62%63",        "https://example.com/abc"),   // letters
        arrayOf("https://example.com/%31%32%33",        "https://example.com/123"),   // digits
        arrayOf("https://example.com/Signal%2DAndroid", "https://example.com/Signal-Android"), // hyphen %2D

        // Characters that must NOT be decoded
        arrayOf("https://example.com/some%20path",  "https://example.com/some%20path"), // space
        arrayOf("https://example.com/a%2Fb",        "https://example.com/a%2Fb"),       // slash
        arrayOf("https://example.com/a%40b",        "https://example.com/a%40b"),       // @

        // Plain ASCII host — unchanged
        arrayOf("https://google.com",                          "https://google.com"),
        arrayOf("https://google.com/path",                     "https://google.com/path"),

        // Port is preserved
        arrayOf("https://example.com:8080/path%2Dname", "https://example.com:8080/path-name"),

        // No scheme — returned as-is (java.net.URI sees no host; fallback regex requires https?://)
        arrayOf("xn--gr-zia.org", "xn--gr-zia.org"),

        // Query and fragment are left untouched
        arrayOf("https://example.com/path?q=%61", "https://example.com/path?q=%61"),
        arrayOf("https://example.com/path%2Dname?q=1#sec%2D1",
                 "https://example.com/path-name?q=1#sec%2D1"),

        // Invalid UTF-8 when fully decoded — path left entirely unchanged
        // %C3 is a 2-byte lead byte; %61 ('a') is not a continuation byte
        arrayOf("https://example.com/%C3%61",    "https://example.com/%C3%61"),
        // %80 is a continuation byte with no preceding lead byte
        arrayOf("https://example.com/%80",       "https://example.com/%80"),
        // %FF is never a valid UTF-8 byte
        arrayOf("https://example.com/%FF",       "https://example.com/%FF"),
        // valid å (%C3%A5) followed by a lone lead byte (%C3) with no continuation
        arrayOf("https://example.com/%C3%A5%C3", "https://example.com/%C3%A5%C3"),

        // Valid UTF-8 when fully decoded — selective decoding still applies
        // å decoded as a letter, space (%20) stays encoded
        arrayOf("https://example.com/%C3%A5%20path", "https://example.com/å%20path"),
      )
    }
  }
}
