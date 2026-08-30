/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Test

class TrackingParametersTest {

  @Test
  fun `strips the youtube si parameter`() {
    assertThat(TrackingParameters.strip("https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123"))
      .isEqualTo("https://youtu.be/dQw4w9WgXcQ")
  }

  @Test
  fun `strips si on youtube subdomains`() {
    assertThat(TrackingParameters.strip("https://m.youtube.com/watch?v=dQw4w9WgXcQ&si=AbCdEf123"))
      .isEqualTo("https://m.youtube.com/watch?v=dQw4w9WgXcQ")
  }

  @Test
  fun `strips the youtube is parameter, which replaced si in early 2026`() {
    assertThat(TrackingParameters.strip("https://youtu.be/dQw4w9WgXcQ?is=AbCdEf123"))
      .isEqualTo("https://youtu.be/dQw4w9WgXcQ")
    assertThat(TrackingParameters.strip("https://www.youtube.com/shorts/dQw4w9WgXcQ?is=AbCdEf123"))
      .isEqualTo("https://www.youtube.com/shorts/dQw4w9WgXcQ")
  }

  @Test
  fun `keeps is on hosts that are not in the host-scoped list`() {
    val url = "https://example.com/thing?is=true"

    assertThat(TrackingParameters.strip(url)).isSameInstanceAs(url)
  }

  @Test
  fun `keeps si on hosts that are not in the host-scoped list`() {
    val url = "https://example.com/thing?si=meaningful"
    assertThat(TrackingParameters.strip(url)).isSameInstanceAs(url)
  }

  @Test
  fun `strips the whole utm family anywhere`() {
    val url = "https://example.com/a?utm_source=news&utm_medium=email&utm_campaign=spring&id=7"

    assertThat(TrackingParameters.strip(url)).isEqualTo("https://example.com/a?id=7")
  }

  @Test
  fun `strips fbclid and igshid globally`() {
    assertThat(TrackingParameters.strip("https://www.instagram.com/p/ABC/?igshid=xyz"))
      .isEqualTo("https://www.instagram.com/p/ABC/")
    assertThat(TrackingParameters.strip("https://example.com/article?fbclid=IwAR123"))
      .isEqualTo("https://example.com/article")

    // On a non-Instagram host, so this fails if igshid is ever demoted to a host rule.
    assertThat(TrackingParameters.strip("https://example.com/a?igshid=xyz&keep=1"))
      .isEqualTo("https://example.com/a?keep=1")
  }

  @Test
  fun `strips facebook mibextid`() {
    assertThat(TrackingParameters.strip("https://www.facebook.com/somepage?mibextid=ZbWKwL"))
      .isEqualTo("https://www.facebook.com/somepage")
  }

  @Test
  fun `strips twitter s and t only on twitter and x`() {
    assertThat(TrackingParameters.strip("https://x.com/user/status/123?s=20&t=AbCd"))
      .isEqualTo("https://x.com/user/status/123")
    assertThat(TrackingParameters.strip("https://twitter.com/user/status/123?s=20"))
      .isEqualTo("https://twitter.com/user/status/123")

    val other = "https://example.com/search?s=query&t=1700000000"
    assertThat(TrackingParameters.strip(other)).isSameInstanceAs(other)
  }

  @Test
  fun `matches parameter names case-insensitively`() {
    assertThat(TrackingParameters.strip("https://example.com/a?UTM_Source=x&FBCLID=y&keep=1"))
      .isEqualTo("https://example.com/a?keep=1")
  }

  @Test
  fun `removes a valueless tracking parameter`() {
    assertThat(TrackingParameters.strip("https://example.com/a?fbclid&keep=1"))
      .isEqualTo("https://example.com/a?keep=1")
  }

  @Test
  fun `removes every occurrence of a repeated tracking parameter`() {
    val url = "https://example.com/a?utm_source=one&keep=1&utm_source=two"

    assertThat(TrackingParameters.strip(url)).isEqualTo("https://example.com/a?keep=1")
  }

  @Test
  fun `preserves the order of the remaining parameters`() {
    assertThat(TrackingParameters.strip("https://example.com/a?b=2&utm_source=x&a=1&c=3"))
      .isEqualTo("https://example.com/a?b=2&a=1&c=3")
  }

  @Test
  fun `preserves the fragment`() {
    assertThat(TrackingParameters.strip("https://example.com/a?utm_source=x#section-2"))
      .isEqualTo("https://example.com/a#section-2")
  }

  @Test
  fun `preserves port path and userinfo`() {
    assertThat(TrackingParameters.strip("https://example.com:8443/deep/path.html?gclid=1&q=hello"))
      .isEqualTo("https://example.com:8443/deep/path.html?q=hello")
  }

  @Test
  fun `returns the original instance when there is nothing to strip`() {
    val noQuery = "https://example.com/a"
    val onlyRealParams = "https://example.com/a?q=hello&page=2"

    assertThat(TrackingParameters.strip(noQuery)).isSameInstanceAs(noQuery)
    assertThat(TrackingParameters.strip(onlyRealParams)).isSameInstanceAs(onlyRealParams)
  }

  @Test
  fun `returns the original instance for unparseable or non-http input`() {
    val notAUrl = "this is not a url"
    val mailto = "mailto:someone@example.com?utm_source=x"
    val custom = "signal://example?utm_source=x"

    assertThat(TrackingParameters.strip(notAUrl)).isSameInstanceAs(notAUrl)
    assertThat(TrackingParameters.strip(mailto)).isSameInstanceAs(mailto)
    assertThat(TrackingParameters.strip(custom)).isSameInstanceAs(custom)
  }

  @Test
  fun `works over plain http`() {
    assertThat(TrackingParameters.strip("http://example.com/a?utm_medium=email"))
      .isEqualTo("http://example.com/a")
  }

  /**
   * The security-relevant invariant: stripping can only ever remove query parameters.
   * If it could change the scheme, host, port, path, or fragment then a cleaned link
   * could point somewhere other than what the user was shown.
   */
  @Test
  fun `never changes anything but the query`() {
    val urls = listOf(
      "https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123",
      "https://example.com:8443/deep/path.html?gclid=1&q=hello#frag",
      "https://user.example.com/a/b/c?utm_source=x&utm_content=y",
      "http://example.com/%E2%9C%93?fbclid=1",
      "https://x.com/user/status/123?s=20&t=AbCd"
    )

    for (url in urls) {
      val original = url.toHttpUrlOrNull()!!
      val stripped = TrackingParameters.strip(url).toHttpUrlOrNull()!!

      assertThat(stripped.scheme).isEqualTo(original.scheme)
      assertThat(stripped.host).isEqualTo(original.host)
      assertThat(stripped.port).isEqualTo(original.port)
      assertThat(stripped.encodedPath).isEqualTo(original.encodedPath)
      assertThat(stripped.fragment).isEqualTo(original.fragment)
    }
  }
}
