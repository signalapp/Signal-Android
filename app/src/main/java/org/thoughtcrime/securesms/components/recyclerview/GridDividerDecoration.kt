package org.thoughtcrime.securesms.components.recyclerview

import android.graphics.Rect
import android.view.View
import androidx.annotation.Px
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * Decoration which will add an equal amount of space between each item in a grid.
 */
open class GridDividerDecoration(
  private val spanCount: Int,
  @Px private val space: Int
) : RecyclerView.ItemDecoration() {

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    return setItemOffsets(parent.getChildAdapterPosition(view), view, outRect)
  }

  protected fun setItemOffsets(position: Int, view: View, outRect: Rect) {
    val column = position % spanCount
    val isRtl = ViewUtil.isRtl(view)

    val distanceFromEnd = spanCount - 1 - column

    val spaceStart = (column / spanCount.toFloat()) * space
    val spaceEnd = (distanceFromEnd / spanCount.toFloat()) * space

    outRect.setStart(spaceStart.toInt(), isRtl)
    outRect.setEnd(spaceEnd.toInt(), isRtl)
    outRect.bottom = space
  }

  private fun Rect.setEnd(end: Int, isRtl: Boolean) {
    if (isRtl) {
      left = end
    } else {
      right = end
    }
  }

  private fun Rect.setStart(start: Int, isRtl: Boolean) {
    if (isRtl) {
      right = start
    } else {
      left = start
    }
  }
}
