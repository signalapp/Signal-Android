package org.thoughtcrime.securesms.components.spoiler

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.Annotation
import android.text.Layout
import android.text.Spanned
import android.view.animation.LinearInterpolator
import android.widget.TextView
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation.SpoilerClickableSpan
import org.thoughtcrime.securesms.components.spoiler.SpoilerRenderer.MultiLineSpoilerRenderer
import org.thoughtcrime.securesms.components.spoiler.SpoilerRenderer.SingleLineSpoilerRenderer

/**
 * Performs initial calculation on how to render spoilers and then delegates to the single line or
 * multi-line version of actually drawing the spoiler sparkles.
 */
class SpoilerRendererDelegate @JvmOverloads constructor(private val view: TextView, private val renderForComposing: Boolean = false) {

  private val single: SpoilerRenderer
  private val multi: SpoilerRenderer
  private var animatorRunning = false
  private var textColor: Int

  private var spoilerDrawablePool = mutableMapOf<Annotation, List<SpoilerDrawable>>()
  private var nextSpoilerDrawablePool = mutableMapOf<Annotation, List<SpoilerDrawable>>()

  private val cachedAnnotations = HashMap<Int, Map<Annotation, SpoilerClickableSpan?>>()
  private val cachedMeasurements = HashMap<Int, SpanMeasurements>()

  private val animator = ValueAnimator.ofInt(0, 100).apply {
    duration = 1000
    interpolator = LinearInterpolator()
    addUpdateListener { view.invalidate() }
    repeatCount = ValueAnimator.INFINITE
    repeatMode = ValueAnimator.REVERSE
  }

  init {
    single = SingleLineSpoilerRenderer()
    multi = MultiLineSpoilerRenderer()
    textColor = view.textColors.defaultColor
  }

  fun updateFromTextColor() {
    val color = view.textColors.defaultColor
    if (color != textColor) {
      spoilerDrawablePool
        .values
        .flatten()
        .forEach { it.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }
      textColor = color
    }
  }

  fun draw(canvas: Canvas, text: Spanned, layout: Layout) {
    var hasSpoilersToRender = false
    val annotations: Map<Annotation, SpoilerClickableSpan?> = cachedAnnotations.getFromCache(text) { SpoilerAnnotation.getSpoilerAndClickAnnotations(text) }

    nextSpoilerDrawablePool.clear()
    for ((annotation, clickSpan) in annotations.entries) {
      if (clickSpan?.spoilerRevealed == true) {
        continue
      }

      val spanStart: Int = text.getSpanStart(annotation)
      val spanEnd: Int = text.getSpanEnd(annotation)
      if (spanStart >= spanEnd) {
        continue
      }

      val measurements = cachedMeasurements.getFromCache(annotation.value, layout) {
        val startLine = layout.getLineForOffset(spanStart)
        val endLine = layout.getLineForOffset(spanEnd)
        SpanMeasurements(
          startLine = startLine,
          endLine = endLine,
          startOffset = (layout.getPrimaryHorizontal(spanStart) + -1 * layout.getParagraphDirection(startLine)).toInt(),
          endOffset = (layout.getPrimaryHorizontal(spanEnd) + layout.getParagraphDirection(endLine)).toInt()
        )
      }

      val renderer: SpoilerRenderer = if (measurements.startLine == measurements.endLine) single else multi
      val drawables: List<SpoilerDrawable> = spoilerDrawablePool[annotation] ?: listOf(SpoilerDrawable(textColor), SpoilerDrawable(textColor), SpoilerDrawable(textColor))

      renderer.draw(canvas, layout, measurements.startLine, measurements.endLine, measurements.startOffset, measurements.endOffset, drawables)
      nextSpoilerDrawablePool[annotation] = drawables
      hasSpoilersToRender = true
    }

    val temporaryPool = spoilerDrawablePool
    spoilerDrawablePool = nextSpoilerDrawablePool
    nextSpoilerDrawablePool = temporaryPool

    if (hasSpoilersToRender) {
      if (!animatorRunning) {
        animator.start()
        animatorRunning = true
      }
    } else {
      animator.pause()
      animatorRunning = false
    }
  }

  private inline fun <V> MutableMap<Int, V>.getFromCache(vararg keys: Any, default: () -> V): V {
    if (renderForComposing) {
      return default()
    }
    return getOrPut(keys.contentHashCode(), default)
  }

  private data class SpanMeasurements(
    val startLine: Int,
    val endLine: Int,
    val startOffset: Int,
    val endOffset: Int
  )
}
