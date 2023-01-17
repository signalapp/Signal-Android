package org.thoughtcrime.securesms.conversation.colors.ui

import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil

class ChatSelectionContextMenu(val context: Context) : PopupWindow(context) {

  var callback: Callback? = null

  init {
    contentView = LayoutInflater.from(context).inflate(R.layout.chat_colors_fragment_context_menu, null, false)

    elevation = ViewUtil.dpToPx(8).toFloat()

    isOutsideTouchable = false
    isFocusable = true

    width = ViewUtil.dpToPx(280)

    setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.round_background))

    val edit: View = contentView.findViewById(R.id.context_menu_edit)
    val duplicate: View = contentView.findViewById(R.id.context_menu_duplicate)
    val delete: View = contentView.findViewById(R.id.context_menu_delete)

    edit.setOnClickListener {
      dismiss()
      callback?.onEditPressed()
    }

    duplicate.setOnClickListener {
      dismiss()
      callback?.onDuplicatePressed()
    }

    delete.setOnClickListener {
      dismiss()
      callback?.onDeletePressed()
    }
  }

  fun show(anchor: View) {
    val rect = Rect()
    val root: ViewGroup = anchor.rootView as ViewGroup

    anchor.getDrawingRect(rect)
    root.offsetDescendantRectToMyCoords(anchor, rect)

    contentView.measure(0, 0)

    if (rect.bottom + contentView.measuredHeight > root.bottom) {
      showAsDropDown(anchor, 0, -(contentView.measuredHeight + anchor.height))
    } else {
      showAsDropDown(anchor, 0, 0)
    }
  }

  interface Callback {
    fun onEditPressed()
    fun onDuplicatePressed()
    fun onDeletePressed()
  }
}
