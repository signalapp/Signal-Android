package org.thoughtcrime.securesms.conversation

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.ContextMenuList

/**
 * The context menu shown after long pressing a message in ConversationActivity.
 */
class ConversationContextMenu(private val anchor: View, items: List<ActionItem>) : PopupWindow(
  LayoutInflater.from(anchor.context).inflate(R.layout.signal_context_menu, null),
  ViewGroup.LayoutParams.WRAP_CONTENT,
  ViewGroup.LayoutParams.WRAP_CONTENT,
) {

  val context: Context = anchor.context

  private val contextMenuList = ContextMenuList(
    recyclerView = contentView.findViewById(R.id.signal_context_menu_list),
    onItemClick = { dismiss() },
  )

  init {
    setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.signal_context_menu_background))
    animationStyle = R.style.ConversationContextMenuAnimation

    isFocusable = false
    isOutsideTouchable = true

    elevation = 20f

    setTouchInterceptor { _, event ->
      event.action == MotionEvent.ACTION_OUTSIDE
    }

    contextMenuList.setItems(items)

    contentView.measure(
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
  }

  fun getMaxWidth(): Int = contentView.measuredWidth
  fun getMaxHeight(): Int = contentView.measuredHeight

  fun show(offsetX: Int, offsetY: Int) {
    showAsDropDown(anchor, offsetX, offsetY, Gravity.TOP or Gravity.START)
  }
}
