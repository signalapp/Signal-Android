package org.thoughtcrime.securesms.components.emoji

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter.EmojiModel
import org.thoughtcrime.securesms.util.InsetItemDecoration
import org.thoughtcrime.securesms.util.ViewUtil

private val EDGE_LENGTH: Int = ViewUtil.dpToPx(7)
private val HORIZONTAL_INSET: Int = ViewUtil.dpToPx(8)
private val VERTICAL_INSET: Int = ViewUtil.dpToPx(8)

/**
 * Use super class to add insets to the emojis and use the [onDrawOver] to draw the variation
 * hint if the emoji has more than one variation.
 */
class EmojiItemDecoration(private val allowVariations: Boolean, private val variationsDrawable: Drawable) : InsetItemDecoration(SetInset()) {

  override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    super.onDrawOver(canvas, parent, state)

    val adapter: EmojiPageViewGridAdapter? = parent.adapter as? EmojiPageViewGridAdapter
    if (allowVariations && adapter != null) {
      for (i in 0 until parent.childCount) {
        val child: View = parent.getChildAt(i)
        val position: Int = parent.getChildAdapterPosition(child)
        if (position >= 0 && position <= adapter.itemCount) {
          val model = adapter.currentList[position]
          if (model is EmojiModel && model.emoji.hasMultipleVariations()) {
            variationsDrawable.setBounds(child.right, child.bottom - EDGE_LENGTH, child.right + EDGE_LENGTH, child.bottom)
            variationsDrawable.draw(canvas)
          }
        }
      }
    }
  }

  private class SetInset : InsetItemDecoration.SetInset() {
    override fun setInset(outRect: Rect, view: View, parent: RecyclerView) {
      val isFirstHeader = view.javaClass == AppCompatTextView::class.java && getPosition(view, parent) == 0
      outRect.left = HORIZONTAL_INSET
      outRect.right = HORIZONTAL_INSET
      outRect.top = if (isFirstHeader) 0 else VERTICAL_INSET
      outRect.bottom = VERTICAL_INSET
    }
  }
}
