package org.thoughtcrime.securesms.conversation.mutiselect

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * Adjusts touch events when child is in Multiselect mode so that we can
 * touch within the offset region and still select / deselect content.
 */
class MultiselectRecyclerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

  override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
    val child: View? = children.firstOrNull { it is Multiselectable && e.y.toInt() in it.top..it.bottom }
    if (child != null) {
      child.getHitRect(rect)

      if (ViewUtil.isLtr(child) && rect.left != 0 && e.x < rect.left) {
        e.offsetLocation(rect.left - e.x, 0f)
      } else if (ViewUtil.isRtl(child) && rect.right < right && e.x > rect.right) {
        e.offsetLocation(-(right - rect.right).toFloat(), 0f)
      }
    }

    return super.onInterceptTouchEvent(e)
  }

  companion object {
    private val rect = Rect()
  }
}
