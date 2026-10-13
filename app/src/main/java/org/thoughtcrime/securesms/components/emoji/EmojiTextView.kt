/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.emoji

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Layout
import android.text.PrecomputedText
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.method.TransformationMethod
import android.text.style.CharacterStyle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.text.PrecomputedTextCompat
import androidx.core.view.GestureDetectorCompat
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.emoji.EmojiProvider
import org.signal.emoji.JumboEmoji
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.mention.MentionRendererDelegate
import org.thoughtcrime.securesms.components.spoiler.SpoilerRendererDelegate
import org.thoughtcrime.securesms.conversation.MessageStyler
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor
import java.lang.ref.WeakReference
import java.text.BreakIterator
import java.util.concurrent.Executor

/**
 * A TextView that renders Signal emoji, mentions and spoilers, and ellipsizes styled text itself (with optional
 * trailing overflow text, e.g. "Read more"), since the platform can't ellipsize spans reliably.
 *
 * What's displayed is always derived from the text most recently set on the view, the length/line limits, and the
 * width offered by the parent. It never depends on the view's own previous size, so measuring is stable and the text
 * can't oscillate between lengths.
 */
open class EmojiTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

  companion object {
    private const val JUMBOMOJI_SCALE = 0.8f

    /** Past this many emoji, laying out the text is expensive enough to do off the main thread. */
    private const val ASYNC_EMOJI_THRESHOLD = 100

    /** Line breaking can round differently than a single-line width measurement, so leave a pixel of slack. */
    private const val FIT_SLOP_PX = 1f

    /** Upper bound on backing off a cut when the line breaker still produces too many lines. */
    private const val MAX_CUT_BACKOFF_ATTEMPTS = 8
  }

  private var scaleEmojis = false
  private var maxLength = -1
  private var measureLastLine = false
  private var forceJumboEmoji = false
  private var shrinkWrap = false
  private var originalFontSize = 0f

  private var mentionRendererDelegate: MentionRendererDelegate? = null
  private var spoilerRendererDelegate: SpoilerRendererDelegate? = null

  private lateinit var textDirection: TextDirectionHeuristic

  private var overflowText: CharSequence? = null

  var isJumbomoji = false
    private set

  var lastLineWidth = -1
    private set

  // What the caller last asked us to show, used to skip re-emojifying identical text.
  private var requestedText: CharSequence? = null
  private var requestedType: BufferType? = null
  private var requestedOverflowText: CharSequence? = null
  private var requestedTransformationMethod: TransformationMethod? = null
  private var requestedWithSystemEmoji = false

  /** The emojified text most recently set. Everything displayed is derived from this, never from [getText]. */
  private var sourceText: CharSequence? = null
  private var sourceRevision = 0
  private var displayedText: CharSequence? = null
  private var displayOutdated = false
  private var lastLineFit: LineFitInputs? = null
  private var suppressRequestLayout = false

  private val asyncExecutor: Executor = SerialMonoLifoExecutor(SignalExecutors.UNBOUNDED)
  private var asyncTaskNumber = 0
  private var pendingAsyncText: CharSequence? = null

  init {
    context.withStyledAttributes(attrs, R.styleable.EmojiTextView) {
      scaleEmojis = getBoolean(R.styleable.EmojiTextView_scaleEmojis, false)
      maxLength = getInteger(R.styleable.EmojiTextView_emoji_maxLength, -1)
      measureLastLine = getBoolean(R.styleable.EmojiTextView_measureLastLine, false)
      forceJumboEmoji = getBoolean(R.styleable.EmojiTextView_emoji_forceJumbo, false)
      shrinkWrap = getBoolean(R.styleable.EmojiTextView_emoji_shrinkWrap, false)

      if (getBoolean(R.styleable.EmojiTextView_emoji_renderMentions, true)) {
        mentionRendererDelegate = MentionRendererDelegate(context, ContextCompat.getColor(context, R.color.transparent_black_20))
      }

      if (getBoolean(R.styleable.EmojiTextView_emoji_renderSpoilers, false)) {
        spoilerRendererDelegate = SpoilerRendererDelegate(this@EmojiTextView)
      }
    }

    context.withStyledAttributes(attrs, intArrayOf(android.R.attr.textSize)) {
      originalFontSize = getDimensionPixelSize(0, 0).toFloat()
    }

    if (layoutDirection == LAYOUT_DIRECTION_LTR) {
      textDirection = TextDirectionHeuristics.FIRSTSTRONG_RTL
      if (getTextDirection() == TEXT_DIRECTION_INHERIT) {
        setTextDirection(TEXT_DIRECTION_FIRST_STRONG_RTL)
      }
    } else {
      textDirection = TextDirectionHeuristics.ANYRTL_LTR
      if (getTextDirection() == TEXT_DIRECTION_INHERIT) {
        setTextDirection(TEXT_DIRECTION_ANY_RTL)
      }
    }

    setEmojiCompatEnabled(useSystemEmoji())
  }

  // region Configuration

  /** Caps the text at [maxLength] characters, followed by an ellipsis and the overflow text. -1 for no limit. */
  fun setMaxLength(maxLength: Int) {
    this.maxLength = maxLength
    onDisplayInputsChanged()
  }

  /** Text shown after the ellipsis whenever the text is cut short, e.g. "Read more". */
  fun setOverflowText(overflowText: CharSequence?) {
    this.overflowText = overflowText
    onDisplayInputsChanged()
  }

  fun setMentionBackgroundTint(@ColorInt mentionBackgroundTint: Int) {
    mentionRendererDelegate?.setTint(mentionBackgroundTint)
  }

  fun enableRenderSpoilers() {
    if (spoilerRendererDelegate == null) {
      spoilerRendererDelegate = SpoilerRendererDelegate(this)
    }
  }

  override fun isSingleLine(): Boolean {
    return layout?.lineCount == 1
  }

  override fun setTextSize(size: Float) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
  }

  override fun setTextSize(unit: Int, size: Float) {
    originalFontSize = TypedValue.applyDimension(unit, size, resources.displayMetrics)
    super.setTextSize(unit, size)
  }

  override fun setTextColor(color: Int) {
    super.setTextColor(color)
    spoilerRendererDelegate?.updateFromTextColor()
  }

  // endregion

  // region Setting text

  fun setTextAsync(text: CharSequence?) {
    setTextAsync(text, BufferType.SPANNABLE)
  }

  /**
   * Sets the text, preparing its layout off the main thread when it contains enough emoji to be expensive.
   */
  fun setTextAsync(text: CharSequence?, type: BufferType) {
    val taskNumber = ++asyncTaskNumber

    val candidates = if (isInEditMode) null else EmojiProvider.getCandidates(text)
    if (candidates == null || candidates.size() <= ASYNC_EMOJI_THRESHOLD) {
      setText(text, type)
      return
    }

    val params = textMetricsParamsCompat
    val viewRef = WeakReference(this)

    asyncExecutor.execute {
      val view = viewRef.get() ?: return@execute
      val emojified = synchronized(view) { view.emojify(text, type) } ?: return@execute
      val precomputed = PrecomputedTextCompat.create(emojified, params)

      view.post {
        if (view.asyncTaskNumber != taskNumber) {
          return@post
        }

        view.pendingAsyncText = text
        try {
          view.setPrecomputedText(precomputed)
        } catch (e: IllegalArgumentException) {
          view.setText(text, type)
        } finally {
          view.pendingAsyncText = null
        }
      }
    }
  }

  /**
   * Prefer [setTextAsync] when the text may contain a large number of emoji.
   */
  override fun setText(text: CharSequence?, type: BufferType?) {
    val isPrecomputed = text is PrecomputedTextCompat || (Build.VERSION.SDK_INT >= 28 && text is PrecomputedText)

    val requested = if (isPrecomputed) pendingAsyncText ?: text else text
    val prepared = if (isPrecomputed) text else emojify(text, type)

    if (prepared == null) {
      return
    }

    // A copy, so a caller mutating and re-setting the same Spannable isn't mistaken for unchanged text.
    requestedText = requested?.let { if (it is Spanned) SpannableString(it) else it.toString() }
    requestedType = type
    requestedOverflowText = overflowText
    requestedTransformationMethod = transformationMethod
    requestedWithSystemEmoji = useSystemEmoji()
    sourceText = prepared

    onDisplayInputsChanged()
    showLengthLimitedTextIfOutdated()
  }

  /**
   * [text] with Signal emoji applied, or null if it's identical to what's already set. Also applies jumbomoji sizing.
   */
  private fun emojify(text: CharSequence?, type: BufferType?): CharSequence? {
    if (text == null) {
      return ""
    }

    val candidates = if (isInEditMode) null else EmojiProvider.getCandidates(text)

    if (scaleEmojis) {
      val scale = if (candidates != null && candidates.allEmojis && (candidates.hasJumboForAll() || JumboEmoji.canDownloadJumbo()) && text is Spanned && !MessageStyler.hasStyling(text)) {
        jumbomojiScale(candidates.size())
      } else {
        1f
      }
      isJumbomoji = scale > 1f
      super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize * scale)
    }

    if (isUnchanged(text, type)) {
      return null
    }

    return if (useSystemEmoji() || candidates == null || candidates.size() == 0) {
      SpannableStringBuilder(text)
    } else {
      SpannableStringBuilder(EmojiProvider.emojify(candidates, text, this, isJumbomoji || forceJumboEmoji) ?: text)
    }
  }

  private fun jumbomojiScale(emojiCount: Int): Float {
    var scale = 1f
    if (emojiCount <= 5) scale += JUMBOMOJI_SCALE
    if (emojiCount <= 4) scale += JUMBOMOJI_SCALE
    if (emojiCount <= 2) scale += JUMBOMOJI_SCALE
    return scale
  }

  private fun isUnchanged(text: CharSequence, type: BufferType?): Boolean {
    return requestedText == text &&
      requestedOverflowText == overflowText &&
      requestedType == type &&
      requestedWithSystemEmoji == useSystemEmoji() &&
      requestedTransformationMethod == transformationMethod
  }

  private fun useSystemEmoji(): Boolean {
    return isInEditMode || SignalStore.settings.isPreferSystemEmoji
  }

  // endregion

  // region Deriving the displayed text

  private fun onDisplayInputsChanged() {
    if (sourceText == null) {
      return
    }

    sourceRevision++
    displayOutdated = true
    lastLineFit = null
    requestLayout()
  }

  private fun showLengthLimitedTextIfOutdated() {
    val source = sourceText ?: return
    if (displayOutdated) {
      displayOutdated = false
      showText(lengthLimitedText(source))
    }
  }

  private fun exceedsMaxLength(text: CharSequence): Boolean {
    return maxLength > 0 && text.length > maxLength + 1
  }

  private fun lengthLimitedText(source: CharSequence): CharSequence {
    return if (exceedsMaxLength(source)) EmojiTextTruncation.truncate(source, maxLength, ellipsisSuffix()) else source
  }

  private fun lengthLimitedPrefix(source: CharSequence): CharSequence {
    return if (exceedsMaxLength(source)) source.subSequence(0, EmojiTextTruncation.snapCut(source, maxLength)) else source
  }

  private fun ellipsisSuffix(): CharSequence {
    return SpannableStringBuilder().append(EmojiTextTruncation.ELLIPSIS).append(overflowText ?: "")
  }

  private fun hasLineLimit(): Boolean {
    return maxLines > 0 && maxLines != Int.MAX_VALUE
  }

  /**
   * Chooses the text to show within [getMaxLines] for the width the parent is offering, using this view's own layout
   * so line breaks match exactly. A repeat measure with the same inputs does nothing.
   */
  @SuppressLint("WrongCall")
  private fun fitToLineLimit(source: CharSequence, widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val inputs = LineFitInputs(this, widthMeasureSpec)
    if (inputs == lastLineFit) {
      return
    }
    lastLineFit = inputs

    showText(lengthLimitedText(source))
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val fullLayout = layout ?: return
    if (fullLayout.lineCount <= maxLines) {
      return
    }

    val prefix = lengthLimitedPrefix(source)
    val suffix = ellipsisSuffix()
    val availableWidth = availableTextWidth(widthMeasureSpec)
    val lastLineStart = fullLayout.getLineStart(maxLines - 1).coerceAtMost(prefix.length)
    val lastLineEnd = fullLayout.getLineEnd(maxLines - 1).coerceAtMost(prefix.length)
    val characters = EmojiTextTruncation.characterIterator(prefix)

    var cut = EmojiTextTruncation.largestFittingCut(prefix, lastLineStart, lastLineEnd) { candidate ->
      lastLineFits(prefix, lastLineStart, candidate, suffix, availableWidth, characters)
    }

    showText(EmojiTextTruncation.truncate(prefix, cut, suffix))
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    var attempts = 0
    while (attempts++ < MAX_CUT_BACKOFF_ATTEMPTS && cut > 0 && (layout?.lineCount ?: 0) > maxLines) {
      cut = EmojiTextTruncation.previousWordBoundary(prefix, cut)
      showText(EmojiTextTruncation.truncate(prefix, cut, suffix))
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
  }

  private fun lastLineFits(prefix: CharSequence, lineStart: Int, cut: Int, suffix: CharSequence, availableWidth: Float, characters: BreakIterator): Boolean {
    val end = EmojiTextTruncation.trimTrailingWhitespace(prefix, lineStart, EmojiTextTruncation.snapCut(prefix, cut, characters))
    val line = SpannableStringBuilder(prefix, lineStart, end.coerceAtLeast(lineStart)).append(suffix)
    return Layout.getDesiredWidth(line, paint) <= availableWidth - FIT_SLOP_PX
  }

  private fun availableTextWidth(widthMeasureSpec: Int): Float {
    var available = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) Float.MAX_VALUE else MeasureSpec.getSize(widthMeasureSpec).toFloat()

    if (maxWidth > 0 && maxWidth != Int.MAX_VALUE) {
      available = minOf(available, maxWidth.toFloat())
    }

    return available - compoundPaddingLeft - compoundPaddingRight
  }

  private fun showText(text: CharSequence) {
    if (text === displayedText) {
      return
    }
    displayedText = text

    // We're either inside onMeasure or about to be measured, so the relayout TextView requests is redundant.
    suppressRequestLayout = true
    try {
      super.setText(text, BufferType.SPANNABLE)
    } catch (e: IllegalArgumentException) {
      // Precomputed text whose params no longer match this view.
      super.setText(SpannableStringBuilder(text), BufferType.SPANNABLE)
    } finally {
      suppressRequestLayout = false
    }
  }

  override fun requestLayout() {
    if (!suppressRequestLayout) {
      super.requestLayout()
    }
  }

  // endregion

  // region Measuring

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    showLengthLimitedTextIfOutdated()

    val source = sourceText
    if (source != null && source.isNotEmpty() && hasLineLimit()) {
      fitToLineLimit(source, widthMeasureSpec, heightMeasureSpec)
    } else if (source != null && lastLineFit != null) {
      lastLineFit = null
      showText(lengthLimitedText(source))
    }

    val widthSpec = applyLetterSpacingWidthFix(widthMeasureSpec)
    super.onMeasure(widthSpec, heightMeasureSpec)

    if (shrinkWrap && MeasureSpec.getMode(widthSpec) == MeasureSpec.AT_MOST) {
      shrinkToLongestLine(widthSpec, heightMeasureSpec)
    }

    lastLineWidth = if (measureLastLine) measureLastLineWidth() else -1
  }

  @SuppressLint("WrongCall")
  private fun shrinkToLongestLine(widthSpec: Int, heightMeasureSpec: Int) {
    val layout = layout ?: return
    val longestLine = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
    val desiredWidth = longestLine.toInt() + paddingLeft + paddingRight

    if (measuredWidth > desiredWidth) {
      super.onMeasure(MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.AT_MOST), heightMeasureSpec)
    }
  }

  /**
   * How wide the last line is, so a host can decide whether a footer fits beside it. Text laid out against the
   * view's direction is treated as filling the whole width.
   */
  private fun measureLastLineWidth(): Int {
    val layout = layout ?: return -1
    val text = layout.text
    if (text.isEmpty()) {
      return -1
    }

    val isRtl = textDirection.isRtl(text, 0, text.length)
    val runsAgainstLayout = (layoutDirection == LAYOUT_DIRECTION_LTR) == isRtl

    return if (runsAgainstLayout) {
      measuredWidth
    } else {
      paint.measureText(text, layout.getLineStart(layout.lineCount - 1), text.length).toInt()
    }
  }

  /**
   * Starting from API 30, there can be a rounding error in text layout when a non-zero letter spacing is used, which
   * inserts a line break where there shouldn't be one. Give the text a little extra room to avoid it.
   * https://issuetracker.google.com/issues/173574230
   */
  private fun applyLetterSpacingWidthFix(widthMeasureSpec: Int): Int {
    if (Build.VERSION.SDK_INT < 30 || letterSpacing <= 0 || MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.AT_MOST) {
      return widthMeasureSpec
    }

    val text = text ?: return widthMeasureSpec
    val textWidth = if (hasMetricAffectingSpan(text)) Layout.getDesiredWidth(text, paint) else longestParagraphWidth(text)
    val desiredWidth = textWidth.toInt() + paddingLeft + paddingRight

    return if (desiredWidth < MeasureSpec.getSize(widthMeasureSpec)) {
      MeasureSpec.makeMeasureSpec(desiredWidth + 3, MeasureSpec.EXACTLY)
    } else {
      widthMeasureSpec
    }
  }

  private fun hasMetricAffectingSpan(text: CharSequence): Boolean {
    return text is Spanned && text.nextSpanTransition(-1, text.length, CharacterStyle::class.java) != text.length
  }

  private fun longestParagraphWidth(text: CharSequence): Float {
    if (text.isEmpty()) {
      return 0f
    }

    val paragraphLimit = if (maxLines > 0) maxLines else Int.MAX_VALUE
    return text.toString()
      .split("\n")
      .take(paragraphLimit)
      .maxOfOrNull { paint.measureText(it) } ?: 0f
  }

  // endregion

  // region Drawing and touch

  override fun onDraw(canvas: Canvas) {
    val text = text
    val hadLayout = layout != null

    if (text is Spanned && hadLayout) {
      drawSpecialRenderers(canvas, text, mentionRendererDelegate, spoilerRendererDelegate)
    }

    super.onDraw(canvas)

    if (text is Spanned && !hadLayout && layout != null) {
      drawSpecialRenderers(canvas, text, null, spoilerRendererDelegate)
    }
  }

  private fun drawSpecialRenderers(canvas: Canvas, text: Spanned, mentionDelegate: MentionRendererDelegate?, spoilerDelegate: SpoilerRendererDelegate?) {
    val checkpoint = canvas.save()
    canvas.translate(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
    try {
      mentionDelegate?.draw(canvas, text, layout)
      spoilerDelegate?.draw(canvas, text, layout)
    } finally {
      canvas.restoreToCount(checkpoint)
    }
  }

  override fun invalidateDrawable(drawable: Drawable) {
    if (drawable is EmojiProvider.EmojiDrawable) {
      invalidate()
    } else {
      super.invalidateDrawable(drawable)
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  fun bindGestureListener() {
    val gestureDetector = GestureDetectorCompat(context, ScrollAndTapListener())
    setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
  }

  /**
   * TextView makes it easy to trigger a click by accident (say, when trying to scroll while already at the bottom),
   * so scrolling and tapping are handled manually.
   */
  private inner class ScrollAndTapListener : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent): Boolean = true

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
      if (!canScrollVertically(distanceY.toInt())) {
        return true
      }

      val maxScrollDistance = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)
      scrollTo(0, (scrollY + distanceY.toInt()).coerceIn(0, maxScrollDistance))
      return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean = performClick()
  }

  // endregion

  /** Everything that affects which text fits within [getMaxLines]. */
  private data class LineFitInputs(
    val sourceRevision: Int,
    val widthMeasureSpec: Int,
    val maxLines: Int,
    val textSize: Float,
    val textScaleX: Float,
    val letterSpacing: Float,
    val typeface: Typeface?,
    val horizontalPadding: Int,
    val maxWidth: Int,
    val breakStrategy: Int,
    val hyphenationFrequency: Int
  ) {
    constructor(view: EmojiTextView, widthMeasureSpec: Int) : this(
      sourceRevision = view.sourceRevision,
      widthMeasureSpec = widthMeasureSpec,
      maxLines = view.maxLines,
      textSize = view.paint.textSize,
      textScaleX = view.paint.textScaleX,
      letterSpacing = view.paint.letterSpacing,
      typeface = view.paint.typeface,
      horizontalPadding = view.compoundPaddingLeft + view.compoundPaddingRight,
      maxWidth = view.maxWidth,
      breakStrategy = view.breakStrategy,
      hyphenationFrequency = view.hyphenationFrequency
    )
  }
}
