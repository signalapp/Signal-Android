package org.thoughtcrime.securesms.components.spoiler

import android.graphics.Color
import android.text.Annotation
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.View
import android.widget.TextView

/**
 * Helper for applying spans to text that should be rendered as a spoiler. Also
 * tracks spoilers that have been revealed or not.
 */
object SpoilerAnnotation {

  private const val SPOILER_ANNOTATION = "spoiler"
  private val revealedSpoilers = mutableSetOf<String>()

  @JvmStatic
  fun spoilerAnnotation(hash: Int): Annotation {
    return Annotation(SPOILER_ANNOTATION, hash.toString())
  }

  @JvmStatic
  fun isSpoilerAnnotation(annotation: Any): Boolean {
    return SPOILER_ANNOTATION == (annotation as? Annotation)?.key
  }

  fun getSpoilerAndClickAnnotations(spanned: Spanned, start: Int = 0, end: Int = spanned.length): Map<Annotation, SpoilerClickableSpan?> {
    val spoilerAnnotations: Map<Pair<Int, Int>, Annotation> = spanned.getSpans(start, end, Annotation::class.java)
      .filter { isSpoilerAnnotation(it) }
      .associateBy { (spanned.getSpanStart(it) to spanned.getSpanEnd(it)) }

    val spoilerClickSpans: Map<Pair<Int, Int>, SpoilerClickableSpan> = spanned.getSpans(start, end, SpoilerClickableSpan::class.java)
      .associateBy { (spanned.getSpanStart(it) to spanned.getSpanEnd(it)) }

    return spoilerAnnotations
      .map { (position, annotation) ->
        annotation to spoilerClickSpans[position]
      }
      .toMap()
  }

  @JvmStatic
  @JvmOverloads
  fun getSpoilerAnnotations(spanned: Spanned, start: Int, end: Int, unrevealedOnly: Boolean = false): List<Annotation> {
    return spanned
      .getSpans(start, end, Annotation::class.java)
      .filter { isSpoilerAnnotation(it) && !(unrevealedOnly && revealedSpoilers.contains(it.value)) }
  }

  @JvmStatic
  fun resetRevealedSpoilers() {
    revealedSpoilers.clear()
  }

  class SpoilerClickableSpan(private val spoiler: Annotation) : MetricAffectingSpan() {
    val spoilerRevealed
      get() = revealedSpoilers.contains(spoiler.value)

    fun onClick(widget: View) {
      revealedSpoilers.add(spoiler.value)

      if (widget is TextView) {
        val text = widget.text
        if (text is Spannable) {
          Selection.removeSelection(text)
        }
        widget.text = text
      }
    }

    override fun updateDrawState(ds: TextPaint) {
      if (!spoilerRevealed) {
        ds.color = Color.TRANSPARENT
      }
    }

    override fun updateMeasureState(textPaint: TextPaint) = Unit
  }
}
