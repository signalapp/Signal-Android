package org.thoughtcrime.securesms.giph.mp4

import android.graphics.Canvas
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min

/**
 * Decoration that will make the video display params update on each recycler redraw.
 */
class GiphyMp4ItemDecoration(val callback: GiphyMp4PlaybackController.Callback) : RecyclerView.ItemDecoration() {
  override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    setParentRecyclerTranslationY(parent)

    parent.children.map { parent.getChildViewHolder(it) }.filterIsInstance(GiphyMp4Playable::class.java).forEach {
      callback.updateVideoDisplayPositionAndSize(parent, it)
    }
  }

  private fun setParentRecyclerTranslationY(parent: RecyclerView) {
    if (parent.childCount == 0 || parent.canScrollVertically(-1) || parent.canScrollVertically(1)) {
      parent.translationY = 0f
    } else {
      val childTop = parent.children.last().top
      parent.translationY = min(0, -childTop).toFloat()
    }
  }
}
