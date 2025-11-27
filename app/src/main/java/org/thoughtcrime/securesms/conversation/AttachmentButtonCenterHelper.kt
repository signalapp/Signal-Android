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
    // Story time - as far as I can remember, the width attribute included padding, so calculating
    // the core width required doing myView.run { width - (paddingLeft + paddingRight) } to account
    // for that. This is weird if you're used to CSS.
    // However something changed between 7.58 and 7.66, but the width attribute no longer includes
    // padding at all, which mirrors the behaviour of CSS. So, yay, I guess?
    // To be clear, I have no clue why this behaviour changed. My current suspects are Signal (doubt),
    // Android Studio (maybe), and Google silently changing upstream dependencies (maybe).
    val extraSpace = wrapper.width - buttonHolder.width
    val horizontalPadding = if (extraSpace >= 0)
      (extraSpace / 2f).toInt()
    else
      DEFAULT_PADDING
    Log.d(TAG, "will add $horizontalPadding px on either side")
    buttonHolder.apply { setPadding(horizontalPadding, paddingTop, horizontalPadding, paddingBottom) }
  }
}
