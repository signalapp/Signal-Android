package org.thoughtcrime.securesms.components.webrtc

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.v2.CallControlsChange
import org.thoughtcrime.securesms.util.Debouncer
import org.thoughtcrime.securesms.util.visible
import java.util.concurrent.TimeUnit

/**
 * Popup window which is displayed whenever the call state changes from user input.
 */
class CallStateUpdatePopupWindow(private val parent: ViewGroup) : PopupWindow(
  LayoutInflater.from(parent.context).inflate(R.layout.call_state_update, parent, false),
  ViewGroup.LayoutParams.MATCH_PARENT,
  ViewGroup.LayoutParams.WRAP_CONTENT
) {

  private var enabled: Boolean = true
  private var pendingUpdate: CallControlsChange? = null
  private var lastUpdate: CallControlsChange? = null
  private val dismissDebouncer = Debouncer(2, TimeUnit.SECONDS)
  private val iconView = contentView.findViewById<ImageView>(R.id.icon)
  private val descriptionView = contentView.findViewById<TextView>(R.id.description)

  init {
    setOnDismissListener {
      val pending = pendingUpdate
      if (pending != null) {
        onCallStateUpdate(pending)
      }
    }

    animationStyle = R.style.CallStateToastAnimation
  }

  fun setEnabled(enabled: Boolean) {
    this.enabled = enabled
    if (!enabled) {
      dismissDebouncer.clear()
      dismiss()
    }
  }

  fun onCallStateUpdate(callControlsChange: CallControlsChange) {
    if (isShowing && lastUpdate == callControlsChange) {
      dismissDebouncer.publish { dismiss() }
    } else if (isShowing) {
      dismissDebouncer.clear()
      pendingUpdate = callControlsChange
      dismiss()
    } else {
      pendingUpdate = null
      lastUpdate = callControlsChange
      presentCallState(callControlsChange)
      show()
    }
  }

  private fun presentCallState(callControlsChange: CallControlsChange) {
    if (callControlsChange.iconRes == null) {
      iconView.setImageDrawable(null)
    } else {
      iconView.setImageResource(callControlsChange.iconRes)
    }

    iconView.visible = callControlsChange.iconRes != null
    descriptionView.setText(callControlsChange.stringRes)
  }

  private fun show() {
    if (!enabled || parent.windowToken == null) {
      return
    }

    measureChild()

    val anchor: View = ViewCompat.requireViewById(parent, R.id.call_screen_above_controls_guideline)
    val pill: View = ViewCompat.requireViewById(contentView, R.id.call_state_pill)

    // 54 is the top margin of the contentView (30) plus the desired padding (24)
    showAtLocation(
      parent,
      Gravity.TOP or Gravity.START,
      0,
      anchor.top - 54.dp - pill.measuredHeight
    )

    update()
    dismissDebouncer.publish { dismiss() }
  }

  private fun measureChild() {
    contentView.measure(
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
  }
}
