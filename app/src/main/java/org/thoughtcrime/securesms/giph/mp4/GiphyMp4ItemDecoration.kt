package org.thoughtcrime.securesms.giph.mp4

import android.graphics.Canvas
import android.graphics.Rect
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.conversation.ConversationHeaderView
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
      val threadHeaderView: ConversationHeaderView? = parent.children
        .filterIsInstance<ConversationHeaderView>()
        .firstOrNull()

      if (threadHeaderView == null) {
        parent.translationY = 0f
        onRecyclerVerticalTranslationSet?.invoke(parent.translationY)
        return
      }

      // A decorator adds the margin for the toolbar, margin is difference of the bounds "height" and the view height
      val bounds = Rect()
      parent.getDecoratedBoundsWithMargins(threadHeaderView, bounds)
      val toolbarMargin = bounds.bottom - bounds.top - threadHeaderView.height

      val childTop: Int = threadHeaderView.top - toolbarMargin
      parent.translationY = min(0, -childTop).toFloat()
      onRecyclerVerticalTranslationSet?.invoke(parent.translationY)
    }
  }
}
