package org.thoughtcrime.securesms.components.webrtc

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.view.postDelayed
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.VibrateUtil
import java.util.concurrent.TimeUnit

/**
 * Popup shown when the device is connected to a WiFi and cellular network, and WiFi is unusable for
 * RingRTC.
 */
class WifiToCellularPopupWindow(private val parent: ViewGroup) : PopupWindow(
  LayoutInflater.from(parent.context).inflate(R.layout.wifi_to_cellular_popup, parent, false),
  WindowManager.LayoutParams.MATCH_PARENT,
  WindowManager.LayoutParams.WRAP_CONTENT
) {

  init {
    animationStyle = R.style.PopupAnimation
  }

  fun show() {
    if (parent.windowToken == null) {
      return
    }

    showAtLocation(parent, Gravity.TOP or Gravity.START, 0, 0)
    VibrateUtil.vibrate(parent.context, VIBRATE_DURATION_MS)
    contentView.postDelayed(DISPLAY_DURATION_MS) {
      dismiss()
    }
  }

  companion object {
    private val DISPLAY_DURATION_MS = TimeUnit.SECONDS.toMillis(4)
    private const val VIBRATE_DURATION_MS = 50
  }
}
