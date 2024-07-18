package org.thoughtcrime.securesms.components.webrtc

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
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
  private var pendingUpdate: CallStateUpdate? = null
  private var lastUpdate: CallStateUpdate? = null
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

  fun onCallStateUpdate(callStateUpdate: CallStateUpdate) {
    if (isShowing && lastUpdate == callStateUpdate) {
      dismissDebouncer.publish { dismiss() }
    } else if (isShowing) {
      dismissDebouncer.clear()
      pendingUpdate = callStateUpdate
      dismiss()
    } else {
      pendingUpdate = null
      lastUpdate = callStateUpdate
      presentCallState(callStateUpdate)
      show()
    }
  }

  private fun presentCallState(callStateUpdate: CallStateUpdate) {
    if (callStateUpdate.iconRes == null) {
      iconView.setImageDrawable(null)
    } else {
      iconView.setImageResource(callStateUpdate.iconRes)
    }

    iconView.visible = callStateUpdate.iconRes != null
    descriptionView.setText(callStateUpdate.stringRes)
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

  enum class CallStateUpdate(
    @DrawableRes val iconRes: Int?,
    @StringRes val stringRes: Int
  ) {
    RINGING_ON(R.drawable.symbol_bell_ring_compact_16, R.string.CallStateUpdatePopupWindow__ringing_on),
    RINGING_OFF(R.drawable.symbol_bell_slash_compact_16, R.string.CallStateUpdatePopupWindow__ringing_off),
    RINGING_DISABLED(null, R.string.CallStateUpdatePopupWindow__group_is_too_large),
    MIC_ON(R.drawable.symbol_mic_compact_16, R.string.CallStateUpdatePopupWindow__mic_on),
    MIC_OFF(R.drawable.symbol_mic_slash_compact_16, R.string.CallStateUpdatePopupWindow__mic_off),
    SPEAKER_ON(R.drawable.symbol_speaker_24, R.string.CallStateUpdatePopupWindow__speaker_on),
    SPEAKER_OFF(R.drawable.symbol_speaker_slash_24, R.string.CallStateUpdatePopupWindow__speaker_off)
  }
}
