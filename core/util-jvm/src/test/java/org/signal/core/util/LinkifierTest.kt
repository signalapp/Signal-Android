/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Each row supplies an input string and the exact list of [Expected] detections in left-to-right
 * order. An empty list means we expect no detections (i.e. a false-positive guard).
 */
@RunWith(Parameterized::class)
class LinkifierTest(private val case: Case) {

  data class Case(
    val name: String,
    val input: String,
    val expected: List<Expected>
  ) {
    override fun toString(): String = name
  }

  /**
   * @property spanText  The substring of [Case.input] that should be marked as a link.
   * @property url       The normalized URL the [Linkifier.DetectedLink] should expose. Defaults to
   *                     [spanText] for cases where the input already includes a scheme.
   */
  data class Expected(
    val spanText: String,
    val url: String = spanText
  )

  @Test
  fun findLinks() {
    val actual: List<Linkifier.DetectedLink> = Linkifier.findLinks(case.input)

    assertThat(actual).hasSize(case.expected.size)

    // Walk forward through the input as we go, so that repeated spans are matched in order.
    var cursor = 0
    for ((i, expected) in case.expected.withIndex()) {
      val expectedStart = case.input.indexOf(expected.spanText, cursor)
      if (expectedStart < 0) {
        throw AssertionError("expected span <${expected.spanText}> not found in <${case.input}> at or after index $cursor")
      }
      val expectedEnd = expectedStart + expected.spanText.length

      val link = actual[i]
      assertThat(link.start).isEqualTo(expectedStart)
      assertThat(link.end).isEqualTo(expectedEnd)
      assertThat(link.url).isEqualTo(expected.url)

      cursor = expectedEnd
    }
  }

  companion object {

    private fun web(spanText: String, url: String = spanText) = Expected(spanText, url)
    private fun geo(spanText: String) = Expected(spanText, url = spanText)

    @Parameterized.Parameters(name = "{0}")
    @JvmStatic
    fun cases(): Collection<Array<Any>> = listOf(

      // ----- empty / no-match -----
      Case("empty text", "", emptyList()),
      Case("plain text with no urls", "just some words, nothing fancy.", emptyList()),

      // ----- basic web URLs -----
      Case("https url", "https://signal.org", listOf(web("https://signal.org"))),
      Case("http url", "http://signal.org", listOf(web("http://signal.org"))),
      Case("bare domain gets http prefix", "signal.org", listOf(web("signal.org", url = "http://signal.org"))),
      Case("www-prefixed url gets http prefix", "www.signal.org", listOf(web("www.signal.org", url = "http://www.signal.org"))),
      Case("url with path query and fragment", "https://example.com/foo/bar?x=1&y=2#frag", listOf(web("https://example.com/foo/bar?x=1&y=2#frag"))),
      Case("url with port", "http://localhost:8080/path", listOf(web("http://localhost:8080/path"))),
      Case("unicode IDN domain", "https://кц.рф/path", listOf(web("https://кц.рф/path"))),
      Case("url embedded in sentence", "Check out https://signal.org for details", listOf(web("https://signal.org"))),

      // ----- the trailing-hyphen bug fix -----
      Case("trailing hyphen before space is preserved", "see https://example.com/foo- and bar", listOf(web("https://example.com/foo-"))),
      Case("trailing hyphen at end of string is preserved", "https://example.com/foo-", listOf(web("https://example.com/foo-"))),
      Case("multiple trailing hyphens are preserved", "https://example.com/foo--- end", listOf(web("https://example.com/foo---"))),
      Case("bare domain followed by trailing hyphen excludes the hyphen", "see signal.com- today", listOf(web("signal.com", url = "http://signal.com"))),
      Case("hyphen in middle of url is preserved", "https://example-site.com/a-b-c", listOf(web("https://example-site.com/a-b-c"))),
      Case("url ending in dash followed by another url", "https://example.com/foo- https://other.com", listOf(web("https://example.com/foo-"), web("https://other.com"))),

      // ----- trailing punctuation trimming -----
      Case("trailing period is trimmed", "Visit https://signal.org.", listOf(web("https://signal.org"))),
      Case("trailing comma is trimmed", "https://signal.org, then go", listOf(web("https://signal.org"))),
      Case("trailing exclamation is trimmed", "https://signal.org!", listOf(web("https://signal.org"))),
      Case("trailing question mark is trimmed", "Have you tried https://signal.org?", listOf(web("https://signal.org"))),
      Case("trailing semicolon is trimmed", "Go to https://signal.org; thanks", listOf(web("https://signal.org"))),
      Case("trailing colon is trimmed", "https://signal.org: nice", listOf(web("https://signal.org"))),
      Case("multiple trailing punctuation chars are all trimmed", "Wow https://signal.org!!! amazing", listOf(web("https://signal.org"))),
      Case("trailing slash is preserved", "https://signal.org/ end", listOf(web("https://signal.org/"))),
      Case("trailing underscore is preserved", "https://example.com/path_ tail", listOf(web("https://example.com/path_"))),
      Case("comma in url path is preserved", "Go to https://example.com/a,b/c", listOf(web("https://example.com/a,b/c"))),
      Case("comma in url query is preserved", "Go to https://example.com/search?q=a,b", listOf(web("https://example.com/search?q=a,b"))),

      // ----- bracket / paren handling -----
      Case("trailing closing paren without opener is trimmed", "(see https://signal.org)", listOf(web("https://signal.org"))),
      Case("trailing closing paren with matching opener inside is preserved", "https://en.wikipedia.org/wiki/Foo_(bar) and more", listOf(web("https://en.wikipedia.org/wiki/Foo_(bar)"))),
      Case("trailing closing bracket without opener is trimmed", "[https://signal.org]", listOf(web("https://signal.org"))),
      Case("trailing closing brace without opener is trimmed", "{https://signal.org}", listOf(web("https://signal.org"))),
      Case("parenthesized url with trailing punctuation has both trimmed", "(see https://signal.org).", listOf(web("https://signal.org"))),

      // ----- multiple URLs in one input -----
      Case("two urls separated by text", "First https://a.com then https://b.com", listOf(web("https://a.com"), web("https://b.com"))),
      Case("two urls separated only by comma", "https://a.com,https://b.com", listOf(web("https://a.com"), web("https://b.com"))),
      Case(
        name = "multi-line text with several urls per line",
        input = """
          Line one https://a.com
          Line two https://b.com and https://c.com
          Line three: nothing
        """.trimIndent(),
        expected = listOf(web("https://a.com"), web("https://b.com"), web("https://c.com"))
      ),
      Case(
        name = "github compare url with triple-dot path is captured fully",
        input = "diff: https://github.com/signalapp/Signal-Android/compare/v6.23.2...v6.23.3",
        expected = listOf(web("https://github.com/signalapp/Signal-Android/compare/v6.23.2...v6.23.3"))
      ),

      // ----- boundary handling -----
      Case("explicit-scheme url glued to a leading word is rejected", "foohttps://signal.org", emptyList()),
      Case("url after newline is matched", "first line\nhttps://signal.org", listOf(web("https://signal.org"))),
      Case("url after open paren is matched", "(https://signal.org", listOf(web("https://signal.org"))),
      Case("url after digit-only token keeps boundary", "12345 https://signal.org", listOf(web("https://signal.org"))),
      Case("email's host half is not also matched as a url", "Hi user@example.com bye", emptyList()),

      // ----- false-positive guards -----
      Case("single dot in a sentence is not a url", "Hello. World", emptyList()),
      Case("decimal number is not a url", "value is 3.14", emptyList()),
      Case("version string is not a url", "v1.2.3 release", emptyList()),
      Case("single-letter TLD is rejected", "abc.x next", emptyList()),
      Case("bare scheme is not a url", "https:// is not a url", emptyList()),

      // ----- TLD validation against the IANA list -----
      Case("Mr.Smith style false positive is rejected", "Mr.Smith said hello", emptyList()),
      Case("bare domain with unknown TLD is rejected", "see signal.notatld today", emptyList()),
      Case("bare domain with valid IDN TLD is accepted", "visit example.рф", listOf(web("example.рф", url = "http://example.рф"))),
      Case("bare domain with punycode TLD is accepted", "visit example.xn--p1ai", listOf(web("example.xn--p1ai", url = "http://example.xn--p1ai"))),
      Case("fake punycode-shaped TLD is rejected", "see signal.xn--notarealtld today", emptyList()),
      Case("scheme with unknown TLD is still accepted", "https://buildbot.notatld/", listOf(web("https://buildbot.notatld/"))),
      Case("www-prefixed unknown TLD is still accepted", "www.foo.notatld", listOf(web("www.foo.notatld", url = "http://www.foo.notatld"))),
      Case("e.g. abbreviation is not a url", "e.g. you can do this", emptyList()),

      // ----- additional cases mirroring AOSP LinkifyTest -----
      Case("mixed-case scheme is matched", "hTtP://android.com", listOf(web("hTtP://android.com"))),
      Case("punycode url with scheme is matched", "http://xn--fsqu00a.xn--unup4y", listOf(web("http://xn--fsqu00a.xn--unup4y"))),
      Case("bare domain with leading dash on TLD label is rejected", "xn--fsqu00a.-xn--unup4y next", emptyList()),
      Case("tilde in path is preserved", "http://www.example.com:8080/~user/?foo=bar", listOf(web("http://www.example.com:8080/~user/?foo=bar"))),
      Case("dollar sign in path is preserved", "http://android.com/path\$?v=\$val", listOf(web("http://android.com/path\$?v=\$val"))),
      Case("port plus query is matched", "http://www.example.com:8080/?foo=bar", listOf(web("http://www.example.com:8080/?foo=bar"))),
      Case("schemed url with empty path and query is matched", "http://android.com?q=v", listOf(web("http://android.com?q=v"))),
      Case("bare domain with empty path and query is matched", "android.com?q=v", listOf(web("android.com?q=v", url = "http://android.com?q=v"))),
      Case("bare domain with internal dashes is matched", "see a-nd.r-oid.com today", listOf(web("a-nd.r-oid.com", url = "http://a-nd.r-oid.com"))),
      Case("bare domain with underscores is rejected", "a_nd.r_oid.com next", emptyList()),
      Case("schemed url with underscores in host is matched", "http://a_nd.r_oid.com/x", listOf(web("http://a_nd.r_oid.com/x"))),

      // ----- unicode whitespace acts as a URL boundary -----
      Case("en-space terminates url", "http://and rest", listOf(web("http://and"))),
      Case("ideographic space terminates url", "http://and　rest", listOf(web("http://and"))),
      Case("nbsp terminates url", "http://and rest", listOf(web("http://and"))),

      // ----- bidi/zero-width format chars on either side reject the url -----
      Case("leading bidi override rejects url", "‬moc.diordna.com next", emptyList()),
      Case("trailing bidi override rejects url", "moc.diordna.com‭ next", emptyList()),
      Case("internal bidi override rejects both halves", "moc.diordna.com‮moc.diordna.com", emptyList()),
      Case("zero-width space adjacent to url rejects it", "see signal.org​ next", emptyList()),

      // ----- geo URIs -----
      Case("geo uri with basic coords", "geo:48.2010,16.3695", listOf(geo("geo:48.2010,16.3695"))),
      Case("geo uri with altitude", "geo:48.2010,16.3695,123", listOf(geo("geo:48.2010,16.3695,123"))),
      Case("geo uri with uncertainty param", "geo:48.2010,16.3695;u=40", listOf(geo("geo:48.2010,16.3695;u=40"))),
      Case("geo uri with google query and label", "geo:0,0?q=37.786971,-122.399677(Treasure%20Island)", listOf(geo("geo:0,0?q=37.786971,-122.399677(Treasure%20Island)"))),
      Case("geo uri with google search query", "geo:0,0?q=Eiffel+Tower", listOf(geo("geo:0,0?q=Eiffel+Tower"))),
      Case("geo uri with negative coords", "geo:-33.86,151.21", listOf(geo("geo:-33.86,151.21"))),
      Case("geo uri at origin", "geo:0,0", listOf(geo("geo:0,0"))),
      Case("geo uri scheme is case insensitive", "GEO:48.2,16.3", listOf(geo("GEO:48.2,16.3"))),

      // ----- geo URI in sentence context -----
      Case("geo uri followed by sentence period", "Meet at geo:48.2,16.3.", listOf(geo("geo:48.2,16.3"))),
      Case("geo uri followed by sentence comma", "From geo:48.2,16.3, walk north", listOf(geo("geo:48.2,16.3"))),
      Case("geo uri inside parens is detected without them", "(geo:48.2,16.3)", listOf(geo("geo:48.2,16.3"))),
      Case("balanced parens inside geo query are preserved", "place geo:0,0?q=37.78,-122.4(Treasure%20Island) end", listOf(geo("geo:0,0?q=37.78,-122.4(Treasure%20Island)"))),
      Case("unmatched close paren after geo query is trimmed", "(see geo:0,0?q=foo)", listOf(geo("geo:0,0?q=foo"))),
      Case("non-numeric altitude falls back to coord-only match", "geo:48,16,foo", listOf(geo("geo:48,16"))),

      // ----- geo URI false-positive guards -----
      Case("geo with lat over 90 is rejected", "geo:91.0,0", emptyList()),
      Case("geo with lon over 180 is rejected", "geo:0,181.0", emptyList()),
      Case("geo with lat below -90 is rejected", "geo:-91,0", emptyList()),
      Case("geo with non-numeric coords is not matched", "geo:foo,bar", emptyList()),
      Case("geo glued to a leading letter is rejected", "xgeo:48.2,16.3", emptyList()),
      Case("geo missing lon is not matched", "geo:48.2", emptyList()),
      Case("bare geo scheme is not matched", "geo:", emptyList()),
      Case("whitespace between geo coords breaks the uri", "geo:48.2, 16.3", emptyList()),

      // ----- geo + web mixed -----
      Case("geo uri then web url ordered left-to-right", "see geo:48.2,16.3 and https://signal.org", listOf(geo("geo:48.2,16.3"), web("https://signal.org"))),
      Case("web url then geo uri ordered left-to-right", "https://signal.org then geo:48.2,16.3", listOf(web("https://signal.org"), geo("geo:48.2,16.3"))),
      Case("two geo uris in the same string", "from geo:48.2,16.3 to geo:51.5,-0.1", listOf(geo("geo:48.2,16.3"), geo("geo:51.5,-0.1")))
    ).map { arrayOf<Any>(it) }
  }
}

/**
 * Tests for behavior that doesn't fit the row-based shape of [LinkifierTest] — exception contracts
 * on the public API.
 */
class LinkifierContractTest {

  @Test
  fun `DetectedLink with negative start throws`() {
    assertFailure { Linkifier.DetectedLink(-1, 0, "x") }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun `DetectedLink with end before start throws`() {
    assertFailure { Linkifier.DetectedLink(5, 4, "x") }.isInstanceOf<IllegalArgumentException>()
  }
}
