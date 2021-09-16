package org.thoughtcrime.securesms.mediaoverview

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.components.recyclerview.GridDividerDecoration

internal class MediaGridDividerDecoration(
  spanCount: Int,
  space: Int,
  private val adapter: MediaGalleryAllAdapter
) : GridDividerDecoration(spanCount, space) {

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    val holder = parent.getChildViewHolder(view)

    val adapterPosition = holder.bindingAdapterPosition
    val section = adapter.getAdapterPositionSection(adapterPosition)
    val itemSectionOffset = adapter.getItemSectionOffset(section, adapterPosition)

    if (itemSectionOffset == -1) {
      return
    }

    val sectionItemViewType = adapter.getSectionItemViewType(section, itemSectionOffset)
    if (sectionItemViewType != MediaGalleryAllAdapter.GALLERY) {
      return
    }

    setItemOffsets(itemSectionOffset, view, outRect)
  }
}
