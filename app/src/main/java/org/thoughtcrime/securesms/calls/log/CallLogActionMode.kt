package org.thoughtcrime.securesms.calls.log

import android.content.res.Resources
import org.thoughtcrime.securesms.main.MainToolbarViewModel

class CallLogActionMode(
  private val callback: Callback,
  private val mainToolbarViewModel: MainToolbarViewModel
) {

  private var count: Int = 0

  fun getCount(): Int {
    return if (mainToolbarViewModel.isInActionMode()) count else 0
  }

  fun setCount(count: Int) {
    this.count = count
    mainToolbarViewModel.setActionModeCount(count)
  }

  fun start() {
    callback.startActionMode()
  }

  fun end() {
    if (mainToolbarViewModel.isInActionMode()) {
      callback.onActionModeWillEnd()
      count = 0
    }
  }

  interface Callback {
    fun startActionMode()
    fun onActionModeWillEnd()
    fun getResources(): Resources
    fun onResetSelectionState()
  }
}
