/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.Application
import android.text.SpannableString
import android.text.Spanned
import android.text.style.URLSpan
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LinkifierSpannableExtensionsTest {

  @Test
  fun `empty text returns false and adds nothing`() {
    val spannable = SpannableString("")
    assertThat(spannable.addDetectedLinks()).isFalse()
    assertThat(spannable.urlSpans()).isEmpty()
  }

  @Test
  fun `text with no urls returns false`() {
    val spannable = SpannableString("just words and punctuation, nothing more.")
    assertThat(spannable.addDetectedLinks()).isFalse()
    assertThat(spannable.urlSpans()).isEmpty()
  }

  @Test
  fun `single url is added with correct range and url`() {
    val text = "visit https://signal.org for more"
    val spannable = SpannableString(text)

    assertThat(spannable.addDetectedLinks()).isTrue()

    val spans = spannable.urlSpans()
    assertThat(spans).hasSize(1)
    val span = spans.single()
    assertThat(spannable.getSpanStart(span)).isEqualTo(text.indexOf("https"))
    assertThat(spannable.getSpanEnd(span)).isEqualTo(text.indexOf("https") + "https://signal.org".length)
    assertThat(span.url).isEqualTo("https://signal.org")
  }

  @Test
  fun `bare domain gets http scheme prefix`() {
    val spannable = SpannableString("see signal.org today")
    assertThat(spannable.addDetectedLinks()).isTrue()

    val span = spannable.urlSpans().single()
    assertThat(span.url).isEqualTo("http://signal.org")
  }

  @Test
  fun `multiple urls each get their own span`() {
    val spannable = SpannableString("first https://a.com then https://b.com")
    assertThat(spannable.addDetectedLinks()).isTrue()

    val urls = spannable.urlSpans().map { it.url }
    assertThat(urls).containsExactly("https://a.com", "https://b.com")
  }

  @Test
  fun `filter excludes links and returns false when all rejected`() {
    val spannable = SpannableString("https://a.com and https://b.com")
    val added = spannable.addDetectedLinks(filter = { false })
    assertThat(added).isFalse()
    assertThat(spannable.urlSpans()).isEmpty()
  }

  @Test
  fun `filter excludes only some links`() {
    val spannable = SpannableString("https://a.com and https://b.com")
    spannable.addDetectedLinks(filter = { it.url == "https://b.com" })

    val urls = spannable.urlSpans().map { it.url }
    assertThat(urls).containsExactly("https://b.com")
  }

  @Test
  fun `custom spanFactory is used for each accepted link`() {
    val spannable = SpannableString("https://a.com plus https://b.com")
    val tagged = mutableListOf<String>()
    spannable.addDetectedLinks(spanFactory = { link ->
      tagged += link.url
      URLSpan("custom:${link.url}")
    })

    val urls = spannable.urlSpans().map { it.url }
    assertThat(urls).containsExactly("custom:https://a.com", "custom:https://b.com")
    assertThat(tagged).containsExactly("https://a.com", "https://b.com")
  }

  @Test
  fun `longer existing URLSpan wins over shorter overlapping detection`() {
    val text = "call tel://+15551234.com for help"
    val spannable = SpannableString(text)

    val phoneStart = text.indexOf("tel://")
    val phoneEnd = text.indexOf(" for")
    spannable.setSpan(URLSpan("tel:+15551234"), phoneStart, phoneEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    spannable.addDetectedLinks()

    val spans = spannable.urlSpans()
    assertThat(spans).hasSize(1)
    assertThat(spans.single().url).isEqualTo("tel:+15551234")
  }

  @Test
  fun `longer detection wins over shorter existing URLSpan`() {
    val text = "see https://example.com/long/path here"
    val spannable = SpannableString(text)

    // Pre-existing short span covering only the host portion.
    val shortStart = text.indexOf("example")
    val shortEnd = shortStart + "example.com".length
    spannable.setSpan(URLSpan("http://shortwins"), shortStart, shortEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    spannable.addDetectedLinks()

    val spans = spannable.urlSpans()
    assertThat(spans).hasSize(1)
    assertThat(spans.single().url).isEqualTo("https://example.com/long/path")
  }

  @Test
  fun `non-overlapping detection is added even when other URLSpan exists`() {
    val text = "call mailto and visit https://signal.org now"
    val spannable = SpannableString(text)

    val mailStart = text.indexOf("mailto")
    val mailEnd = mailStart + "mailto".length
    spannable.setSpan(URLSpan("mailto:foo@bar.com"), mailStart, mailEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    spannable.addDetectedLinks()

    val urls = spannable.urlSpans().map { it.url }.sorted()
    assertThat(urls).containsExactly("https://signal.org", "mailto:foo@bar.com")
  }

  private fun SpannableString.urlSpans(): Array<URLSpan> = getSpans(0, length, URLSpan::class.java)
}
