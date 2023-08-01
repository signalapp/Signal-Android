package org.thoughtcrime.securesms.components.spoiler

import android.animation.TimeAnimator
import android.graphics.Canvas
import android.text.Annotation
import android.text.Layout
import android.text.Spanned
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation.SpoilerClickableSpan
import org.thoughtcrime.securesms.util.AccessibilityUtil
import org.thoughtcrime.securesms.util.getLifecycle

/**
 * Performs initial calculation on how to render spoilers and then delegates to actually drawing the spoiler sparkles.
 */
class SpoilerRendererDelegate @JvmOverloads constructor(
  private val view: TextView,
  private val renderForComposing: Boolean = false
) {

  private val renderer: SpoilerRenderer
  private val spoilerDrawable: SpoilerDrawable
  private var animatorRunning = false
  private var textColor: Int
  private var canAnimate = false

  private val cachedAnnotations = HashMap<Int, Map<Annotation, SpoilerClickableSpan?>>()
  private val cachedMeasurements = HashMap<Int, SpanMeasurements>()

  private var systemAnimationsEnabled = !AccessibilityUtil.areAnimationsDisabled(view.context)

  private val animator = TimeAnimator().apply {
    setTimeListener { _, _, _ ->
      SpoilerPaint.update()
      view.invalidate()
    }
  }

  init {
    textColor = view.textColors.defaultColor
    spoilerDrawable = SpoilerDrawable(textColor)
    renderer = SpoilerRenderer(
      spoilerDrawable = spoilerDrawable,
      renderForComposing = renderForComposing,
      padding = 2.dp,
      composeBackgroundColor = ContextCompat.getColor(view.context, R.color.signal_colorOnSurfaceVariant1)
    )

    view.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
      override fun onViewDetachedFromWindow(v: View) = stopAnimating()
      override fun onViewAttachedToWindow(v: View) {
        view.getLifecycle()?.addObserver(object : DefaultLifecycleObserver {
          override fun onResume(owner: LifecycleOwner) {
            canAnimate = true
            systemAnimationsEnabled = !AccessibilityUtil.areAnimationsDisabled(view.context)
            view.invalidate()
          }

          override fun onPause(owner: LifecycleOwner) {
            canAnimate = false
            stopAnimating()
          }
        })
      }
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
    if (!canAnimate) {
      return
    }

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

      renderer.draw(canvas, layout, measurements.startLine, measurements.endLine, measurements.startOffset, measurements.endOffset)
      hasSpoilersToRender = true
    }

    if (hasSpoilersToRender && systemAnimationsEnabled) {
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
