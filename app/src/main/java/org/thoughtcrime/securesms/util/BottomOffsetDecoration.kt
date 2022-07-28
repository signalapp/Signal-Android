package org.thoughtcrime.securesms.util

import android.graphics.Rect
import android.view.View
import androidx.annotation.Px
import androidx.recyclerview.widget.RecyclerView

/**
 * Adds some empty space to the bottom of a recyclerview. Useful if you need some "dead space" at the bottom of the list to account for floating menus that may
 * otherwise cover up the bottom entries of the list.
 */
class BottomOffsetDecoration(@Px private val bottomOffset: Int) : RecyclerView.ItemDecoration() {

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    super.getItemOffsets(outRect, view, parent, state)
    if (parent.getChildAdapterPosition(view) == state.itemCount - 1) {
      outRect.set(0, 0, 0, bottomOffset)
    } else {
      outRect.set(0, 0, 0, 0)
    }
  }
}
