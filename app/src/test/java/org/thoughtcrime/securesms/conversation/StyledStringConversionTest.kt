/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.app.Application
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.view.View
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.signal.uicomponents.styledtext.LinkRange
import org.signal.uicomponents.styledtext.StyleRange
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StyledStringConversionTest {

  @After
  fun tearDown() {
    SpoilerAnnotation.resetRevealedSpoilers()
  }

  @Test
  fun `text styles carry over`() {
    val body = spannable("bold italic both strike mono") {
      setSpan(StyleSpan(Typeface.BOLD), 0, 4)
      setSpan(StyleSpan(Typeface.ITALIC), 5, 11)
      setSpan(StyleSpan(Typeface.BOLD_ITALIC), 12, 16)
      setSpan(StrikethroughSpan(), 17, 23)
      setSpan(TypefaceSpan(MessageStyler.MONOSPACE), 24, 28)
    }

    assertThat(body.toStyledString().styles).containsExactlyInAnyOrder(
      StyleRange(0, 4, StyleRange.Style.BOLD),
      StyleRange(5, 11, StyleRange.Style.ITALIC),
      StyleRange(12, 16, StyleRange.Style.BOLD),
      StyleRange(12, 16, StyleRange.Style.ITALIC),
      StyleRange(17, 23, StyleRange.Style.STRIKETHROUGH),
      StyleRange(24, 28, StyleRange.Style.MONOSPACE)
    )
  }

  @Test
  fun `links carry over`() {
    val body = spannable("see example.com") {
      setSpan(URLSpan("https://example.com"), 4, 15)
    }

    assertThat(body.toStyledString().links).containsExactly(LinkRange(4, 15, "https://example.com"))
  }

  @Test
  fun `mentions render as plain text`() {
    val body = spannable("hi @Miles") {
      setSpan(MentionAnnotation.mentionAnnotationForRecipientId(RecipientId.from(1)), 3, 9)
    }

    val caption = body.toStyledString()

    assertThat(caption.text).isEqualTo("hi @Miles")
    assertThat(caption.styles).isEmpty()
  }

  @Test
  fun `spoilers are hidden by default`() {
    val body = spannable("a secret") {
      setSpan(SpoilerAnnotation.spoilerAnnotation(1), 2, 8)
    }

    assertThat(body.toStyledString().styles).containsExactly(StyleRange(2, 8, StyleRange.Style.SPOILER))
  }

  @Test
  fun `spoilers already revealed elsewhere stay revealed`() {
    val spoiler = SpoilerAnnotation.spoilerAnnotation(2)
    val clickSpan = SpoilerAnnotation.SpoilerClickableSpan(spoiler)
    val body = spannable("a secret") {
      setSpan(spoiler, 2, 8)
      setSpan(clickSpan, 2, 8)
    }

    clickSpan.onClick(View(ApplicationProvider.getApplicationContext()))

    assertThat(body.toStyledString().styles).isEmpty()
  }

  private fun spannable(text: String, block: SpanBuilder.() -> Unit): SpannableString {
    val spannable = SpannableString(text)
    SpanBuilder(spannable).block()
    return spannable
  }

  private class SpanBuilder(private val spannable: SpannableString) {
    fun setSpan(span: Any, start: Int, end: Int) {
      spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
  }
}
