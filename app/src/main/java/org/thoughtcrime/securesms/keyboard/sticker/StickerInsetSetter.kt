package org.thoughtcrime.securesms.keyboard.sticker

import android.graphics.Rect
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.util.InsetItemDecoration
import org.thoughtcrime.securesms.util.ViewUtil

private val horizontalInset: Int = ViewUtil.dpToPx(8)
private val verticalInset: Int = ViewUtil.dpToPx(8)

/**
 * Set insets for sticker items in a [RecyclerView]. For use in [InsetItemDecoration].
 */
class StickerInsetSetter : InsetItemDecoration.SetInset() {
  override fun setInset(outRect: Rect, view: View, parent: RecyclerView) {
    val isHeader = view.javaClass == AppCompatTextView::class.java

    outRect.left = horizontalInset
    outRect.right = horizontalInset
    outRect.top = verticalInset
    outRect.bottom = if (isHeader) 0 else verticalInset
  }
}
