package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView
import org.thoughtcrime.securesms.R

/**
 * A small card with a circular progress indicator in it. Usable in place
 * of a ProgressDialog, which is deprecated.
 *
 * Remember to add this as the last UI element in your XML hierarchy so it'll
 * draw over top of other elements.
 */
class ProgressCard @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : MaterialCardView(context, attrs) {
  init {
    inflate(context, R.layout.progress_card, this)
  }
}
