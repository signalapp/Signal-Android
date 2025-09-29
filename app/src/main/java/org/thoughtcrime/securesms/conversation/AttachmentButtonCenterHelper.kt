package org.thoughtcrime.securesms.conversation

import android.view.View
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

  private val TAG = Log.tag(AttachmentButtonCenterHelper::class)
  private val DEFAULT_PADDING = DimensionUnit.DP.toPixels(16f).toInt()

  fun recenter(buttonHolder: View, wrapper: View) {
    // The core width of the list of buttons not include any padding.
    val buttonHolderCoreWidth = buttonHolder.run { width - (paddingLeft + paddingRight) }
    val extraSpace = wrapper.width - buttonHolderCoreWidth
    val horizontalPadding = if (extraSpace >= 0)
      (extraSpace / 2f).toInt()
    else
      DEFAULT_PADDING
    Log.d(TAG, "will add $horizontalPadding px on either side")
    buttonHolder.apply { setPadding(horizontalPadding, paddingTop, horizontalPadding, paddingBottom) }
  }
}
