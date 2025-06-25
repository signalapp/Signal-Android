package org.thoughtcrime.securesms.conversation

import android.view.ViewGroup
import androidx.core.view.doOnNextLayout
import org.signal.core.util.DimensionUnit

/**
 * Adds necessary padding to each side of the given ViewGroup in order to ensure that
 * if all buttons can fit in the visible real-estate on screen, they are centered.
 */
object AttachmentButtonCenterHelper {

  private val itemWidth: Float = DimensionUnit.DP.toPixels(88f)
  private val defaultPadding: Float = DimensionUnit.DP.toPixels(16f)

  fun recenter(container: ViewGroup) {
    val itemCount = container.childCount
    val requiredSpace = itemWidth * itemCount

    container.doOnNextLayout {
      if (it.measuredWidth >= requiredSpace) {
        val extraSpace = it.measuredWidth - requiredSpace
        val availablePadding = extraSpace / 2f
        it.post {
          it.setPadding(availablePadding.toInt(), it.paddingTop, availablePadding.toInt(), it.paddingBottom)
        }
      } else {
        it.setPadding(defaultPadding.toInt(), it.paddingTop, defaultPadding.toInt(), it.paddingBottom)
      }
    }
  }
}
