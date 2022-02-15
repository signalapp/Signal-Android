package org.thoughtcrime.securesms.giph.mp4

import android.graphics.Canvas
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import kotlin.math.min

/**
 * Decoration that will make the video display params update on each recycler redraw.
 */
class GiphyMp4ItemDecoration(
  val callback: GiphyMp4PlaybackController.Callback,
  val onRecyclerVerticalTranslationSet: (Float) -> Unit
) : RecyclerView.ItemDecoration() {
  override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    setParentRecyclerTranslationY(parent)

    parent.children.map { parent.getChildViewHolder(it) }.filterIsInstance(GiphyMp4Playable::class.java).forEach {
      callback.updateVideoDisplayPositionAndSize(parent, it)
    }
  }

  private fun setParentRecyclerTranslationY(parent: RecyclerView) {
    if (parent.childCount == 0 || parent.canScrollVertically(-1) || parent.canScrollVertically(1)) {
      parent.translationY = 0f
      onRecyclerVerticalTranslationSet(parent.translationY)
    } else {
      val footerViewHolder = parent.children
        .map { parent.getChildViewHolder(it) }
        .filterIsInstance(ConversationAdapter.FooterViewHolder::class.java)
        .firstOrNull()

      if (footerViewHolder == null) {
        parent.translationY = 0f
        onRecyclerVerticalTranslationSet(parent.translationY)
        return
      }

      val childTop: Int = footerViewHolder.itemView.top
      parent.translationY = min(0, -childTop).toFloat()
      onRecyclerVerticalTranslationSet(parent.translationY)
    }
  }
}
