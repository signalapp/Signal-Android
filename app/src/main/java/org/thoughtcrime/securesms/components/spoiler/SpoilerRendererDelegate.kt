package org.thoughtcrime.securesms.components.spoiler

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.text.Annotation
import android.text.Layout
import android.text.Spanned
import android.view.View
import android.view.View.OnAttachStateChangeListener
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
  private val spoilerDrawable: SpoilerDrawable
  private var animatorRunning = false
  private var textColor: Int

  private val cachedAnnotations = HashMap<Int, Map<Annotation, SpoilerClickableSpan?>>()
  private val cachedMeasurements = HashMap<Int, SpanMeasurements>()

  private val animator = ValueAnimator.ofInt(0, 100).apply {
    duration = 1000
    interpolator = LinearInterpolator()
    addUpdateListener {
      SpoilerPaint.update()
      view.invalidate()
    }
    repeatCount = ValueAnimator.INFINITE
    repeatMode = ValueAnimator.REVERSE
  }

  init {
    textColor = view.textColors.defaultColor
    spoilerDrawable = SpoilerDrawable(textColor)
    single = SingleLineSpoilerRenderer(spoilerDrawable)
    multi = MultiLineSpoilerRenderer(spoilerDrawable)

    view.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
      override fun onViewDetachedFromWindow(v: View) = stopAnimating()
      override fun onViewAttachedToWindow(v: View) = Unit
    })
  }

  fun updateFromTextColor() {
    val color = view.textColors.defaultColor
    if (color != textColor) {
      spoilerDrawable.setTintColor(color)
      textColor = color
    }
  }

  fun draw(canvas: Canvas, text: Spanned, layout: Layout) {
    var hasSpoilersToRender = false
    val annotations: Map<Annotation, SpoilerClickableSpan?> = cachedAnnotations.getFromCache(text) { SpoilerAnnotation.getSpoilerAndClickAnnotations(text) }

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

      renderer.draw(canvas, layout, measurements.startLine, measurements.endLine, measurements.startOffset, measurements.endOffset)
      hasSpoilersToRender = true
    }

    if (hasSpoilersToRender) {
      if (!animatorRunning) {
        animator.start()
        animatorRunning = true
      }
    } else {
      stopAnimating()
    }
  }

  private fun stopAnimating() {
    animator.pause()
    animatorRunning = false
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
