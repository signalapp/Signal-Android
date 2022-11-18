package org.thoughtcrime.securesms.mediapreview.mediarail

import android.graphics.Rect
import android.view.View
import androidx.annotation.Px
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * From: <a href="https://stackoverflow.com/a/53510142">https://stackoverflow.com/a/53510142</a>
 */
class CenterDecoration(@Px private val spacing: Int) : RecyclerView.ItemDecoration() {

  private var firstViewWidth = -1
  private var lastViewWidth = -1

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    super.getItemOffsets(outRect, view, parent, state)
    val adapterPosition = (view.layoutParams as RecyclerView.LayoutParams).absoluteAdapterPosition
    val layoutManager = parent.layoutManager as LinearLayoutManager
    if (adapterPosition == 0) {
      if (view.width != firstViewWidth) {
        view.doOnPreDraw { parent.invalidateItemDecorations() }
      }
      firstViewWidth = view.width
      outRect.left = parent.width / 2 - view.width / 2
      if (layoutManager.itemCount > 1) {
        outRect.right = spacing / 2
      } else {
        outRect.right = outRect.left
      }
    } else if (adapterPosition == layoutManager.itemCount - 1) {
      if (view.width != lastViewWidth) {
        view.doOnPreDraw { parent.invalidateItemDecorations() }
      }
      lastViewWidth = view.width
      outRect.right = parent.width / 2 - view.width / 2
      outRect.left = spacing / 2
    } else {
      outRect.left = spacing / 2
      outRect.right = spacing / 2
    }
  }
}
