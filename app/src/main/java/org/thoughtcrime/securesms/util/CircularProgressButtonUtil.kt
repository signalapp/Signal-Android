package org.thoughtcrime.securesms.util

import com.dd.CircularProgressButton

object CircularProgressButtonUtil {

  @JvmStatic
  fun setSpinning(button: CircularProgressButton?) {
    button?.apply {
      isClickable = false
      isIndeterminateProgressMode = true
      progress = 50
    }
  }

  @JvmStatic
  fun cancelSpinning(button: CircularProgressButton?) {
    button?.apply {
      progress = 0
      isIndeterminateProgressMode = false
      isClickable = true
    }
  }
}
