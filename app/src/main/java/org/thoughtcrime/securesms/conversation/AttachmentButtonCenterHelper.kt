package org.thoughtcrime.securesms.conversation

import android.view.View
import androidx.core.view.doOnLayout
import org.signal.core.util.DimensionUnit
import org.signal.core.util.logging.Log

/**
 * Adds necessary padding to each side of the given ViewGroup in order to ensure that
 * if all buttons can fit in the visible real-estate as defined by the wrapper view,
 * then they are centered. However if there are too many buttons to fit on the screen,
 * then put a basic amount of padding on each side so that it looks nice when scrolling
 * to either end.
 */
object AttachmentButtonCenterHelper {

  private val DEFAULT_PADDING = DimensionUnit.DP.toPixels(16f).toInt()

  fun recenter(buttonHolder: View, wrapper: View) {
    buttonHolder.let {
      it.post {
        it.setPadding(DEFAULT_PADDING, it.paddingTop, DEFAULT_PADDING, it.paddingBottom)
      }
      it.post {
        val extraSpace = wrapper.measuredWidth - it.measuredWidth
        val horizontalPadding = when {
          extraSpace > 0 -> (extraSpace / 2f).toInt()
          else -> DEFAULT_PADDING
        }
        it.setPadding(horizontalPadding, it.paddingTop, horizontalPadding, it.paddingBottom)
      }
    }
  }
}
