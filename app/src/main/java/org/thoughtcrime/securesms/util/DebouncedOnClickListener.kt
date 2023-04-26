package org.thoughtcrime.securesms.util

import android.view.View

/**
 * A View.OnClickListener that ignores clicks for a specified internal. This is useful for fixing
 * double press events that might be more difficult/cumbersome to fix by managing explicit state.
 */
class DebouncedOnClickListener(
  private val interval: Long = 500,
  private val onClickListener: View.OnClickListener
) : View.OnClickListener {
  private var lastClickTime = 0L

  override fun onClick(v: View) {
    val time = System.currentTimeMillis()
    if (time - lastClickTime >= interval) {
      lastClickTime = time
      onClickListener.onClick(v)
    }
  }
}
