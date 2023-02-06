package org.thoughtcrime.securesms.components.menu

import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * A custom context menu that will show next to an anchor view and display several options. Basically a PopupMenu with custom UI and positioning rules.
 *
 * This will prefer showing the menu underneath the anchor, but if there's not enough space in the container, it will show it above the anchor and reverse the
 * order of the menu items. If there's not enough room for either, it'll show it centered above the anchor. If there's not enough room then, it'll center it,
 * chop off the part that doesn't fit, and make the menu scrollable.
 */
class SignalContextMenu private constructor(
  val anchor: View,
  val container: ViewGroup,
  val items: List<ActionItem>,
  val baseOffsetX: Int = 0,
  val baseOffsetY: Int = 0,
  val horizontalPosition: HorizontalPosition = HorizontalPosition.START,
  val verticalPosition: VerticalPosition = VerticalPosition.BELOW,
  val onDismiss: Runnable? = null
) : PopupWindow(
  LayoutInflater.from(anchor.context).inflate(R.layout.signal_context_menu, null),
  ViewGroup.LayoutParams.WRAP_CONTENT,
  ViewGroup.LayoutParams.WRAP_CONTENT
) {

  val context: Context = anchor.context

  private val contextMenuList = ContextMenuList(
    recyclerView = contentView.findViewById(R.id.signal_context_menu_list),
    onItemClick = { dismiss() }
  )

  init {
    setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.signal_context_menu_background))
    inputMethodMode = INPUT_METHOD_NOT_NEEDED

    isFocusable = true

    if (onDismiss != null) {
      setOnDismissListener { onDismiss.run() }
    }

    elevation = 20f

    contextMenuList.setItems(items)
  }

  private fun show(): SignalContextMenu {
    if (anchor.width == 0 || anchor.height == 0) {
      anchor.post(this::show)
      return this
    }

    contentView.measure(
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )

    val anchorRect = Rect(anchor.left, anchor.top, anchor.right, anchor.bottom).also {
      if (anchor.parent != container) {
        container.offsetDescendantRectToMyCoords(anchor, it)
      }
    }

    val menuBottomBound = anchorRect.bottom + contentView.measuredHeight + baseOffsetY
    val menuTopBound = anchorRect.top - contentView.measuredHeight - baseOffsetY

    val screenBottomBound = container.height
    val screenTopBound = container.y

    val offsetY: Int

    if (verticalPosition == VerticalPosition.ABOVE && menuTopBound > screenTopBound) {
      offsetY = -(anchorRect.height() + contentView.measuredHeight + baseOffsetY)
      contextMenuList.setItems(items.reversed())
    } else if (menuBottomBound < screenBottomBound) {
      offsetY = baseOffsetY
    } else if (menuTopBound > screenTopBound) {
      offsetY = -(anchorRect.height() + contentView.measuredHeight + baseOffsetY)
      contextMenuList.setItems(items.reversed())
    } else {
      offsetY = -((anchorRect.height() / 2) + (contentView.measuredHeight / 2) + baseOffsetY)
    }

    val offsetX: Int = when (horizontalPosition) {
      HorizontalPosition.START -> {
        if (ViewUtil.isLtr(context)) {
          baseOffsetX
        } else {
          -(baseOffsetX + contentView.measuredWidth)
        }
      }
      HorizontalPosition.END -> {
        if (ViewUtil.isLtr(context)) {
          -(baseOffsetX + contentView.measuredWidth - anchorRect.width())
        } else {
          baseOffsetX - anchorRect.width()
        }
      }
    }

    showAsDropDown(anchor, offsetX, offsetY)

    return this
  }

  enum class HorizontalPosition {
    START, END
  }

  enum class VerticalPosition {
    ABOVE, BELOW
  }

  /**
   * @param anchor The view to put the pop-up on
   * @param container A parent of [anchor] that represents the acceptable boundaries of the popup
   */
  class Builder(
    val anchor: View,
    val container: ViewGroup
  ) {

    private var onDismiss: Runnable? = null
    private var offsetX = 0
    private var offsetY = 0
    private var horizontalPosition = HorizontalPosition.START
    private var verticalPosition = VerticalPosition.BELOW

    fun onDismiss(onDismiss: Runnable): Builder {
      this.onDismiss = onDismiss
      return this
    }

    fun offsetX(offsetPx: Int): Builder {
      this.offsetX = offsetPx
      return this
    }

    fun offsetY(offsetPx: Int): Builder {
      this.offsetY = offsetPx
      return this
    }

    fun preferredHorizontalPosition(horizontalPosition: HorizontalPosition): Builder {
      this.horizontalPosition = horizontalPosition
      return this
    }

    fun preferredVerticalPosition(verticalPosition: VerticalPosition): Builder {
      this.verticalPosition = verticalPosition
      return this
    }

    fun show(items: List<ActionItem>): SignalContextMenu {
      return SignalContextMenu(
        anchor = anchor,
        container = container,
        items = items,
        baseOffsetX = offsetX,
        baseOffsetY = offsetY,
        horizontalPosition = horizontalPosition,
        verticalPosition = verticalPosition,
        onDismiss = onDismiss
      ).show()
    }
  }
}
