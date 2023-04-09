package org.thoughtcrime.securesms.util

import android.text.Annotation
import android.text.Spannable
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation

/**
 * Filters the results of [getSpans] to exclude spans covered by an unrevealed spoiler when drawing or
 * processing clicks. Since [getSpans] can also be called when making copies of spannables, we do not filter
 * the call unless we know we are drawing or getting click spannables.
 */
class SpoilerFilteringSpannable(private val spannable: Spannable, private val inOnDrawProvider: InOnDrawProvider) : Spannable by spannable {

  override fun <T : Any> getSpans(start: Int, end: Int, type: Class<T>): Array<T> {
    val spans: Array<T> = spannable.getSpans(start, end, type)

    if (spans.isEmpty() || !(inOnDrawProvider.isInOnDraw() || type == LongClickCopySpan::class.java)) {
      return spans
    }

    if (spannable.getSpans(0, spannable.length, Annotation::class.java).none { SpoilerAnnotation.isSpoilerAnnotation(it) }) {
      return spans
    }

    val spansToExclude = HashSet<Any>()
    val spoilers: Map<Annotation, SpoilerAnnotation.SpoilerClickableSpan?> = SpoilerAnnotation.getSpoilerAndClickAnnotations(spannable, start, end)
    val allOtherTheSpans: Map<T, Pair<Int, Int>> = spans
      .filterNot { SpoilerAnnotation.isSpoilerAnnotation(it) || it is SpoilerAnnotation.SpoilerClickableSpan }
      .associateWith { (spannable.getSpanStart(it) to spannable.getSpanEnd(it)) }

    spoilers.forEach { (spoiler, click) ->
      if (click?.spoilerRevealed == true) {
        spansToExclude += spoiler
        spansToExclude += click
      } else {
        val spoilerStart = spannable.getSpanStart(spoiler)
        val spoilerEnd = spannable.getSpanEnd(spoiler)

        for ((span, position) in allOtherTheSpans) {
          if (position.first in spoilerStart..spoilerEnd) {
            spansToExclude += span
          } else if (position.second in spoilerStart..spoilerEnd) {
            spansToExclude += span
          }
        }
      }
    }

    return spans.filter(spansToExclude)
  }

  /**
   * Kotlin does not handle generic JVM arrays well so instead of using all the nice collection functions
   * we do a move desired objects down and overwrite undesired objects and then copy the array to trim
   * it to the correct length. For our use case, it's okay to modify the original array.
   */
  private fun <T : Any> Array<T>.filter(set: Set<Any>): Array<T> {
    var index = 0
    for (i in this.indices) {
      this[index] = this[i]
      if (!set.contains(this[index])) {
        index++
      }
    }
    return copyOfRange(0, index)
  }

  override fun toString(): String = spannable.toString()
  override fun hashCode(): Int = spannable.hashCode()
  override fun equals(other: Any?): Boolean = spannable == other

  interface InOnDrawProvider {
    fun isInOnDraw(): Boolean
  }
}
