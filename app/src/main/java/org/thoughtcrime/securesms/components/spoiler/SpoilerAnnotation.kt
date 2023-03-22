package org.thoughtcrime.securesms.components.spoiler

import android.graphics.Color
import android.text.Annotation
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

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
  fun isSpoilerAnnotation(annotation: Annotation): Boolean {
    return SPOILER_ANNOTATION == annotation.key
  }

  @JvmStatic
  fun getSpoilerAnnotations(spanned: Spanned): List<Annotation> {
    val spoilerAnnotations: Map<Pair<Int, Int>, Annotation> = spanned.getSpans(0, spanned.length, Annotation::class.java)
      .filter { isSpoilerAnnotation(it) }
      .associateBy { (spanned.getSpanStart(it) to spanned.getSpanEnd(it)) }

    val spoilerClickSpans: Map<Pair<Int, Int>, SpoilerClickableSpan> = spanned.getSpans(0, spanned.length, SpoilerClickableSpan::class.java)
      .associateBy { (spanned.getSpanStart(it) to spanned.getSpanEnd(it)) }

    return spoilerAnnotations.mapNotNull { (position, annotation) ->
      if (spoilerClickSpans[position]?.spoilerRevealed != true && !revealedSpoilers.contains(annotation.value)) {
        annotation
      } else {
        revealedSpoilers.add(annotation.value)
        null
      }
    }
  }

  @JvmStatic
  fun getSpoilerAnnotations(spanned: Spanned, start: Int, end: Int): List<Annotation> {
    return spanned
      .getSpans(start, end, Annotation::class.java)
      .filter { isSpoilerAnnotation(it) }
  }

  @JvmStatic
  fun resetRevealedSpoilers() {
    revealedSpoilers.clear()
  }

  class SpoilerClickableSpan(spoiler: Annotation) : ClickableSpan() {
    private val spoiler: Annotation
    var spoilerRevealed = false
      private set

    init {
      this.spoiler = spoiler
      spoilerRevealed = revealedSpoilers.contains(spoiler.value)
    }

    override fun onClick(widget: View) {
      spoilerRevealed = true
    }

    override fun updateDrawState(ds: TextPaint) {
      if (!spoilerRevealed) {
        ds.color = Color.TRANSPARENT
      }
    }
  }
}
