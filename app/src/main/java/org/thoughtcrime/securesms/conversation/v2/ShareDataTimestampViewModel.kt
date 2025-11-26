/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.conversation.ConversationArgs
import org.thoughtcrime.securesms.util.delegate

/**
 * Hold the last share timestamp in an activity scoped view model for sharing between
 * the activity and fragments.
 */
class ShareDataTimestampViewModel(
  savedStateHandle: SavedStateHandle
) : ViewModel() {

  companion object {
    private const val TIMESTAMP = "timestamp"
  }

  var timestamp: Long by savedStateHandle.delegate(TIMESTAMP, -1L)
    private set

  fun setTimestampFromActivityCreation(savedInstanceState: Bundle?, intent: Intent) {
    if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
      timestamp = System.currentTimeMillis()
    }
  }

  fun setTimestampFromConversationArgs(args: ConversationArgs) {
    timestamp = args.shareDataTimestamp
  }
}
