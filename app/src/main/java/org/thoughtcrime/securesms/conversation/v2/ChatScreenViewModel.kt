/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import androidx.lifecycle.ViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore

/** Remembers how tall the system keyboard is across runs. The scaffold measures it but does not persist it. */
class ChatScreenViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(ChatScreenViewModel::class.java)
  }

  /**
   * @param isLandscape Which stored height to read.
   * @param minimumPx Floor, used when nothing usable has been persisted.
   */
  fun getStoredKeyboardHeight(isLandscape: Boolean, minimumPx: Int): Int {
    val stored = if (isLandscape) SignalStore.misc.keyboardLandscapeHeight else SignalStore.misc.keyboardPortraitHeight
    if (stored <= minimumPx) {
      Log.w(TAG, "Saved keyboard height ($stored) is too low, using default size ($minimumPx)")
    }
    return maxOf(stored, minimumPx)
  }

  fun setKeyboardHeight(isLandscape: Boolean, heightPx: Int) {
    if (isLandscape) {
      SignalStore.misc.keyboardLandscapeHeight = heightPx
    } else {
      SignalStore.misc.keyboardPortraitHeight = heightPx
    }
  }
}
