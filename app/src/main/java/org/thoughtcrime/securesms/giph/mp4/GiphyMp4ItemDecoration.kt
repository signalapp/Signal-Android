package org.thoughtcrime.securesms.giph.mp4

import android.graphics.Canvas
import android.graphics.Rect
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.v2.ConversationAdapterV2
import kotlin.math.min

/**
 * Decoration that will make the video display params update on each recycler redraw.
 */
class GiphyMp4ItemDecoration(
  private val callback: GiphyMp4PlaybackController.Callback,
  private val onRecyclerVerticalTranslationSet: ((Float) -> Unit)? = null
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
      onRecyclerVerticalTranslationSet?.invoke(parent.translationY)
    } else {
      val threadHeaderViewHolder = parent.children
        .map { parent.getChildViewHolder(it) }
        .filter { it is ConversationAdapter.FooterViewHolder || it is ConversationAdapterV2.ThreadHeaderViewHolder }
        .firstOrNull()

      if (threadHeaderViewHolder == null) {
        parent.translationY = 0f
        onRecyclerVerticalTranslationSet?.invoke(parent.translationY)
        return
      }

      val toolbarMargin = if (threadHeaderViewHolder is ConversationAdapterV2.ThreadHeaderViewHolder) {
        // A decorator adds the margin for the toolbar, margin is difference of the bounds "height" and the view height
        val bounds = Rect()
        parent.getDecoratedBoundsWithMargins(threadHeaderViewHolder.itemView, bounds)
        bounds.bottom - bounds.top - threadHeaderViewHolder.itemView.height
      } else {
        // Deprecated not needed for CFv2
        0
      }

      val childTop: Int = threadHeaderViewHolder.itemView.top - toolbarMargin
      parent.translationY = min(0, -childTop).toFloat()
      onRecyclerVerticalTranslationSet?.invoke(parent.translationY)
    }
  }
}
