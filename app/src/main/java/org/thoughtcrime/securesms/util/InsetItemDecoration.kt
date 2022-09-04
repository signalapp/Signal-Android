package org.thoughtcrime.securesms.util

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

private typealias Predicate = (view: View, parent: RecyclerView) -> Boolean
private val ALWAYS_TRUE: Predicate = { _, _ -> true }

/**
 * Externally configurable inset "setter" for recycler views.
 *
 * Primary constructor provides full external control of view insets.
 * Secondary constructors provide basic predicate based insets on the horizontal and vertical.
 */
open class InsetItemDecoration(
  private val setInset: SetInset
) : RecyclerView.ItemDecoration() {

  constructor(horizontalInset: Int = 0, verticalInset: Int = 0) : this(horizontalInset, verticalInset, ALWAYS_TRUE)
  constructor(horizontalInset: Int = 0, verticalInset: Int = 0, predicate: Predicate) : this(horizontalInset, horizontalInset, verticalInset, verticalInset, predicate)
  constructor(leftInset: Int = 0, rightInset: Int = 0, topInset: Int = 0, bottomInset: Int = 0, predicate: Predicate = ALWAYS_TRUE) : this(
    setInset = object : SetInset() {
      override fun setInset(outRect: Rect, view: View, parent: RecyclerView) {
        if (predicate == ALWAYS_TRUE || predicate.invoke(view, parent)) {
          outRect.left = leftInset
          outRect.right = rightInset
          outRect.top = topInset
          outRect.bottom = bottomInset
        }
      }
    }
  )

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    super.getItemOffsets(outRect, view, parent, state)
    setInset.setInset(outRect, view, parent)
  }

  abstract class SetInset {
    abstract fun setInset(outRect: Rect, view: View, parent: RecyclerView)

    fun getPosition(view: View, parent: RecyclerView): Int {
      return parent.getChildAdapterPosition(view)
    }
  }
}
