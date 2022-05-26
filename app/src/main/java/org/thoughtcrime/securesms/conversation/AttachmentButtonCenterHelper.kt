package org.thoughtcrime.securesms.conversation

import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.RecyclerView
import org.signal.core.util.DimensionUnit

/**
 * Adds necessary padding to each side of the given RecyclerView in order to ensure that
 * if all buttons can fit in the visible real-estate on screen, they are centered.
 */
class AttachmentButtonCenterHelper(private val recyclerView: RecyclerView) : RecyclerView.AdapterDataObserver() {

  private val itemWidth: Float = DimensionUnit.DP.toPixels(88f)
  private val defaultPadding: Float = DimensionUnit.DP.toPixels(16f)

  override fun onChanged() {
    val itemCount = recyclerView.adapter?.itemCount ?: return
    val requiredSpace = itemWidth * itemCount

    recyclerView.doOnNextLayout {
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
