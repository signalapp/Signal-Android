/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.emoji

import android.app.Application
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Annotation
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.core.text.PrecomputedTextCompat
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.endsWith
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isSameInstanceAs
import assertk.assertions.startsWith
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.emoji.EmojiProvider
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmojiTextViewTest {

  companion object {
    private const val LONG_TEXT = "The quick brown fox jumps over the lazy dog, then circles back to jump over it again, and keeps going well past any reasonable number of lines."
    private const val ELLIPSIS = EmojiTextTruncation.ELLIPSIS
  }

  @get:Rule
  val signalStore = MockSignalStoreRule()

  private val context: Application = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    every { signalStore.settings.isPreferSystemEmoji } returns true
    mockkStatic(EmojiProvider::class)
    every { EmojiProvider.getCandidates(any()) } returns null
  }

  @After
  fun tearDown() {
    unmockkStatic(EmojiProvider::class)
  }

  @Test
  fun whenTextFits_showsItUnchanged() {
    val view = emojiTextView(maxLines = 2, text = "Short")

    view.measureAndLayout(atMost(300))

    assertThat(view.text.toString()).isEqualTo("Short")
  }

  @Test
  fun whenTextExceedsMaxLines_ellipsizesWithinMaxLines() {
    val view = emojiTextView(maxLines = 2, text = LONG_TEXT)

    view.measureAndLayout(atMost(300))

    view.assertEllipsizedWithinLimit(maxLines = 2, suffix = "$ELLIPSIS")
    assertThat(LONG_TEXT).startsWith(view.text.toString().removeSuffix("$ELLIPSIS"))
  }

  @Test
  fun whenEllipsizeEndSet_stillEllipsizesItselfWithinMaxLines() {
    for (maxLines in 1..2) {
      val view = emojiTextView(maxLines = maxLines, text = LONG_TEXT)
      view.ellipsize = TextUtils.TruncateAt.END
      view.setOverflowText(" Read more")

      view.measureAndLayout(atMost(300))

      view.assertEllipsizedWithinLimit(maxLines = maxLines, suffix = "$ELLIPSIS Read more")
      assertThat(view.layout.getEllipsisCount(view.layout.lineCount - 1)).isEqualTo(0)
    }
  }

  @Test
  fun whenPrecomputedTextWithEllipsizeEnd_stillEllipsizesItselfWithinMaxLines() {
    val view = emojiTextView(maxLines = 2, text = null)
    view.ellipsize = TextUtils.TruncateAt.END
    view.setOverflowText(" Read more")
    view.setPrecomputedText(PrecomputedTextCompat.create(LONG_TEXT, view.textMetricsParamsCompat))

    view.measureAndLayout(atMost(300))

    view.assertEllipsizedWithinLimit(maxLines = 2, suffix = "$ELLIPSIS Read more")
  }

  @Test
  fun whenSameSpannableIsMutatedAndSetAgain_showsTheNewContent() {
    val text = SpannableStringBuilder("Before")
    val view = emojiTextView(maxLines = null, text = text)

    text.replace(0, text.length, "After")
    view.text = text

    assertThat(view.text.toString()).isEqualTo("After")
  }

  @Test
  fun whenOverflowTextSet_appendsItAfterEllipsisOnLastVisibleLine() {
    val view = emojiTextView(maxLines = 2, text = LONG_TEXT)
    view.setOverflowText(" Read more")

    view.measureAndLayout(atMost(300))

    view.assertEllipsizedWithinLimit(maxLines = 2, suffix = "$ELLIPSIS Read more")
  }

  @Test
  fun whenMeasuredAgainWithSameSpec_changesNothingAndRequestsNoLayout() {
    val view = emojiTextView(maxLines = 2, text = LONG_TEXT)
    view.measureAndLayout(atMost(300))
    val firstText = view.text
    val firstWidth = view.measuredWidth
    val firstHeight = view.measuredHeight

    view.measureAndLayout(atMost(300))

    assertThat(view.text).isSameInstanceAs(firstText)
    assertThat(view.measuredWidth).isEqualTo(firstWidth)
    assertThat(view.measuredHeight).isEqualTo(firstHeight)
    assertThat(view.isLayoutRequested).isFalse()
  }

  @Test
  fun whenOfferedWidthChanges_refitsAndReturnsToSameResult() {
    val view = emojiTextView(maxLines = 2, text = LONG_TEXT)

    view.measureAndLayout(atMost(250))
    val narrow = view.text.toString()

    view.measureAndLayout(atMost(500))
    val wide = view.text.toString()

    view.measureAndLayout(atMost(250))

    assertThat(wide.length).isGreaterThan(narrow.length)
    assertThat(view.text.toString()).isEqualTo(narrow)
  }

  @Test
  fun whenMeasuredAtMost_neverExceedsOfferedWidth() {
    for (width in 120..480 step 40) {
      val view = emojiTextView(maxLines = 2, text = LONG_TEXT)
      view.setOverflowText(" Read more")

      view.measureAndLayout(atMost(width))

      assertThat(view.measuredWidth).isLessThanOrEqualTo(width)
      view.assertEllipsizedWithinLimit(maxLines = 2, suffix = "$ELLIPSIS Read more")
    }
  }

  @Test
  fun whenSettersCalledInAnyOrder_showsSameResult() {
    val maxLinesFirst = emojiTextView(maxLines = 2, text = null).apply {
      setOverflowText(" Read more")
      text = LONG_TEXT
    }
    val textFirst = emojiTextView(maxLines = null, text = LONG_TEXT).apply {
      maxLines = 2
      setOverflowText(" Read more")
    }

    maxLinesFirst.measureAndLayout(atMost(300))
    textFirst.measureAndLayout(atMost(300))

    assertThat(textFirst.text.toString()).isEqualTo(maxLinesFirst.text.toString())
  }

  @Test
  fun whenMaxLengthSetAfterEllipsizing_cutsFromTheOriginalText() {
    val view = emojiTextView(maxLines = 2, text = LONG_TEXT)
    view.setOverflowText(" Read more")
    view.measureAndLayout(atMost(300))

    view.setMaxLength(30)
    view.measureAndLayout(atMost(300))

    val shown = view.text.toString()
    assertThat(shown.count { it == ELLIPSIS }).isEqualTo(1)
    assertThat(shown).isEqualTo(LONG_TEXT.take(30).trimEnd() + "$ELLIPSIS Read more")
  }

  @Test
  fun whenOnlyMaxLengthApplies_cutsImmediatelyWithoutMeasuring() {
    val view = emojiTextView(maxLines = null, text = null)
    view.setMaxLength(20)

    view.text = LONG_TEXT

    assertThat(view.text.toString()).isEqualTo(LONG_TEXT.take(20).trimEnd() + "$ELLIPSIS")
  }

  @Test
  fun whenLineLimitRemoved_showsFullTextAgain() {
    val view = emojiTextView(maxLines = 2, text = LONG_TEXT)
    view.measureAndLayout(atMost(300))

    view.maxLines = Int.MAX_VALUE
    view.measureAndLayout(atMost(300))

    assertThat(view.text.toString()).isEqualTo(LONG_TEXT)
  }

  @Test
  fun whenViewHasHorizontalPadding_ellipsisStaysVisible() {
    val view = emojiTextView(maxLines = 2, text = LONG_TEXT)
    view.setPadding(48, 0, 48, 0)

    view.measureAndLayout(atMost(320))

    view.assertEllipsizedWithinLimit(maxLines = 2, suffix = "$ELLIPSIS")
  }

  @Test
  fun whenHiddenParagraphIsWidest_showsVisibleParagraphsAndStaysStable() {
    val text = "One\nTwo\n" + LONG_TEXT
    val view = emojiTextView(maxLines = 2, text = text)

    view.measureAndLayout(atMost(600))
    val first = view.text.toString()
    view.measureAndLayout(atMost(600))

    assertThat(first).isEqualTo("One\nTwo$ELLIPSIS")
    assertThat(view.text.toString()).isEqualTo(first)
  }

  @Test
  fun whenWidthUnspecified_cutsAtParagraphs() {
    val view = emojiTextView(maxLines = 2, text = "One\nTwo\nThree")

    view.measureAndLayout(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))

    assertThat(view.text.toString()).isEqualTo("One\nTwo$ELLIPSIS")
  }

  @Test
  fun whenCutFallsInsideEmojiSpan_keepsTheWholeSpanOrDropsIt() {
    for (width in 150..400 step 10) {
      val text = SpannableString("Some words before the emoji XY and plenty of words after it to push well past two lines, no matter how wide the view happens to be during this test")
      val emojiStart = text.indexOf("XY")
      text.setSpan(FixedWidthSpan(32), emojiStart, emojiStart + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      val view = emojiTextView(maxLines = 2, text = text)

      view.measureAndLayout(atMost(width))

      val shown = view.text as Spanned
      for (span in shown.getSpans(0, shown.length, FixedWidthSpan::class.java)) {
        assertThat(shown.getSpanEnd(span) - shown.getSpanStart(span)).isEqualTo(2)
      }
      view.assertEllipsizedWithinLimit(maxLines = 2, suffix = "$ELLIPSIS")
    }
  }

  @Test
  fun whenCutFallsInsideMention_keepsTheWholeMentionOrDropsIt() {
    val mentionText = "@Somebody With A Long Name"
    for (width in 150..400 step 10) {
      val text = SpannableString("Hey there, pinging $mentionText about the thing we talked about yesterday afternoon")
      val mentionStart = text.indexOf(mentionText)
      text.setSpan(Annotation(MentionAnnotation.MENTION_ANNOTATION, "1"), mentionStart, mentionStart + mentionText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      val view = emojiTextView(maxLines = 2, text = text)

      view.measureAndLayout(atMost(width))

      val shown = view.text as Spanned
      for (mention in MentionAnnotation.getMentionAnnotations(shown)) {
        assertThat(shown.getSpanEnd(mention) - shown.getSpanStart(mention)).isEqualTo(mentionText.length)
      }
    }
  }

  @Test
  fun whenStyleSpanReachesTheCut_itDoesNotExtendOverTheEllipsis() {
    val text = SpannableString(LONG_TEXT)
    text.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    val view = emojiTextView(maxLines = 2, text = text)

    view.measureAndLayout(atMost(300))

    val shown = view.text as Spanned
    val bold = shown.getSpans(0, shown.length, StyleSpan::class.java).single()
    assertThat(shown.getSpanEnd(bold)).isLessThanOrEqualTo(shown.indexOf(ELLIPSIS))
  }

  @Test
  fun whenShrinkWrapped_staysWithinOfferedWidthAndStable() {
    val attrs = Robolectric.buildAttributeSet()
      .addAttribute(R.attr.emoji_shrinkWrap, "true")
      .build()
    val view = EmojiTextView(context, attrs).apply {
      layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
      textSize = 16f
      maxLines = 2
      text = LONG_TEXT
    }

    view.measureAndLayout(atMost(300))
    val first = view.text
    view.measureAndLayout(atMost(300))

    assertThat(view.measuredWidth).isLessThanOrEqualTo(300)
    assertThat(view.text).isSameInstanceAs(first)
    view.assertEllipsizedWithinLimit(maxLines = 2, suffix = "$ELLIPSIS")
  }

  private fun emojiTextView(maxLines: Int?, text: CharSequence?): EmojiTextView {
    return EmojiTextView(context).apply {
      layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
      textSize = 16f
      if (maxLines != null) {
        this.maxLines = maxLines
      }
      if (text != null) {
        this.text = text
      }
    }
  }

  private fun atMost(width: Int): Int = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST)

  private fun EmojiTextView.measureAndLayout(widthSpec: Int) {
    measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
    layout(0, 0, measuredWidth, measuredHeight)
  }

  /** The text ends with [suffix], fits in [maxLines], and nothing (including the suffix) is clipped off the end. */
  private fun EmojiTextView.assertEllipsizedWithinLimit(maxLines: Int, suffix: String) {
    val shown = text.toString()
    assertThat(shown).endsWith(suffix)
    assertThat(layout.lineCount).isLessThanOrEqualTo(maxLines)
    assertThat(layout.getLineEnd(layout.lineCount - 1)).isEqualTo(shown.length)
  }

  private class FixedWidthSpan(private val width: Int) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = width
    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) = Unit
  }
}
