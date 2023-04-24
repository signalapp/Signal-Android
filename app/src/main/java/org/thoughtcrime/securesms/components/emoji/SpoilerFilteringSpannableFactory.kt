package org.thoughtcrime.securesms.components.emoji

import android.text.Spannable
import org.thoughtcrime.securesms.util.SpoilerFilteringSpannable
import org.thoughtcrime.securesms.util.SpoilerFilteringSpannable.InOnDrawProvider

/**
 * Spannable factory used to help ensure spans are copied/maintained properly through the
 * Android text handling system.
 *
 * @param inOnDraw Used by [SpoilerFilteringSpannable] to remove spans when being called from onDraw
 */
class SpoilerFilteringSpannableFactory(private val inOnDraw: InOnDrawProvider) : Spannable.Factory() {
  override fun newSpannable(source: CharSequence): Spannable {
    return wrap(super.newSpannable(source))
  }

  fun wrap(source: Spannable): SpoilerFilteringSpannable {
    return SpoilerFilteringSpannable(source, inOnDraw)
  }
}
