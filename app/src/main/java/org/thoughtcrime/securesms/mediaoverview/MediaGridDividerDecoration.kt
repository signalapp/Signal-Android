package org.thoughtcrime.securesms.mediaoverview

import android.graphics.Rect
import android.view.View
import androidx.annotation.Px
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.util.ViewUtil

internal class MediaGridDividerDecoration(
  private val spanCount: Int,
  @Px private val space: Int,
  private val adapter: MediaGalleryAllAdapter
) : RecyclerView.ItemDecoration() {

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    val holder = parent.getChildViewHolder(view)

    val adapterPosition = holder.adapterPosition
    val section = adapter.getAdapterPositionSection(adapterPosition)
    val itemSectionOffset = adapter.getItemSectionOffset(section, adapterPosition)

    if (itemSectionOffset == -1) {
      return
    }

    val sectionItemViewType = adapter.getSectionItemViewType(section, itemSectionOffset)
    if (sectionItemViewType != MediaGalleryAllAdapter.GALLERY) {
      return
    }

    val column = itemSectionOffset % spanCount
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
