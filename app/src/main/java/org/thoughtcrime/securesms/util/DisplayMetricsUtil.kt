package org.thoughtcrime.securesms.util

import android.util.DisplayMetrics
import android.view.View

object DisplayMetricsUtil {
  @JvmStatic
  fun forceAspectRatioToScreenByAdjustingHeight(displayMetrics: DisplayMetrics, view: View) {
    val screenHeight = displayMetrics.heightPixels
    val screenWidth = displayMetrics.widthPixels
    val params = view.layoutParams

    params.height = params.width * screenHeight / screenWidth
    view.layoutParams = params
  }
}
