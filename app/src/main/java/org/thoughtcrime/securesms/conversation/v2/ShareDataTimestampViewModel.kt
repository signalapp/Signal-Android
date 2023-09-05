/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import androidx.lifecycle.ViewModel

/**
 * Hold the last share timestamp in an activity scoped view model for sharing between
 * the activity and fragments.
 */
class ShareDataTimestampViewModel : ViewModel() {
  var timestamp: Long = -1L
}
