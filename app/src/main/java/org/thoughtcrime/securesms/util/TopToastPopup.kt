package org.thoughtcrime.securesms.util

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import org.thoughtcrime.securesms.R
import java.util.concurrent.TimeUnit

/**
 * Show a "toast" like message with text and icon that animates in from the top and then animates out to the top.
 */
class TopToastPopup private constructor(parent: ViewGroup, iconResource: Int, descriptionText: String) : PopupWindow(
  LayoutInflater.from(parent.context).inflate(R.layout.top_toast_popup, parent, false),
  ViewGroup.LayoutParams.MATCH_PARENT,
  ViewUtil.dpToPx(86)
) {

  private val icon: ImageView = contentView.findViewById(R.id.top_toast_popup_icon)
  private val description: TextView = contentView.findViewById(R.id.top_toast_popup_description)

  init {
    elevation = ViewUtil.dpToPx(8).toFloat()
    animationStyle = R.style.PopupAnimation
    icon.setImageResource(iconResource)
    description.text = descriptionText
  }

  private fun show(parent: ViewGroup) {
    showAtLocation(parent, Gravity.TOP or Gravity.START, 0, 0)
    measureChild()
    update()
    contentView.postDelayed({ dismiss() }, DURATION)
  }

  private fun measureChild() {
    contentView.measure(
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
  }

  companion object {
    private val DURATION = TimeUnit.SECONDS.toMillis(2)

    @JvmStatic
    fun show(parent: ViewGroup, icon: Int, description: String): TopToastPopup {
      val topToast = TopToastPopup(parent, icon, description)
      topToast.show(parent)
      return topToast
    }
  }
}
