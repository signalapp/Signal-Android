/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.app.Application
import android.text.SpannableString
import android.text.style.URLSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.util.UrlClickHandler

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class V2ConversationItemUtilsTest {

  private val clickHandler = UrlClickHandler { false }

  @Test
  fun `linkifyUrlLinks - url in middle of body - linkified`() {
    val body = SpannableString("see https://signal.org/foo for details")

    V2ConversationItemUtils.linkifyUrlLinks(body, true, clickHandler)

    assertEquals(listOf("https://signal.org/foo"), body.urls())
  }

  @Test
  fun `linkifyUrlLinks - url at end of complete body - linkified`() {
    val body = SpannableString("see https://signal.org/foo")

    V2ConversationItemUtils.linkifyUrlLinks(body, true, clickHandler, isBodyTruncated = false)

    assertEquals(listOf("https://signal.org/foo"), body.urls())
  }

  @Test
  fun `linkifyUrlLinks - url at end of truncated body - not linkified`() {
    val body = SpannableString("see https://signal.org/foo")

    V2ConversationItemUtils.linkifyUrlLinks(body, true, clickHandler, isBodyTruncated = true)

    assertEquals(emptyList<String>(), body.urls())
  }

  @Test
  fun `linkifyUrlLinks - truncated body - only trailing url is skipped`() {
    val body = SpannableString("https://signal.org/one and https://signal.org/two")

    V2ConversationItemUtils.linkifyUrlLinks(body, true, clickHandler, isBodyTruncated = true)

    assertEquals(listOf("https://signal.org/one"), body.urls())
  }

  @Test
  fun `linkifyUrlLinks - truncated body with trailing text after url - linkified`() {
    val body = SpannableString("see https://signal.org/foo and more")

    V2ConversationItemUtils.linkifyUrlLinks(body, true, clickHandler, isBodyTruncated = true)

    assertEquals(listOf("https://signal.org/foo"), body.urls())
  }

  @Test
  fun `linkifyUrlLinks - truncated body cut after trailing punctuation - not linkified`() {
    val body = SpannableString("see https://signal.org/foo?a=1,")

    V2ConversationItemUtils.linkifyUrlLinks(body, true, clickHandler, isBodyTruncated = true)

    assertEquals(emptyList<String>(), body.urls())
  }

  @Test
  fun `linkifyUrlLinks - truncated body cut after unbalanced closing bracket - not linkified`() {
    val body = SpannableString("see https://signal.org/foo?a=1)")

    V2ConversationItemUtils.linkifyUrlLinks(body, true, clickHandler, isBodyTruncated = true)

    assertEquals(emptyList<String>(), body.urls())
  }

  @Test
  fun `linkifyUrlLinks - complete body ending in punctuation after url - linkified`() {
    val body = SpannableString("see https://signal.org/foo.")

    V2ConversationItemUtils.linkifyUrlLinks(body, true, clickHandler, isBodyTruncated = false)

    assertEquals(listOf("https://signal.org/foo"), body.urls())
  }

  private fun SpannableString.urls(): List<String> {
    return getSpans(0, length, URLSpan::class.java).sortedBy { getSpanStart(it) }.map { it.url }
  }
}
