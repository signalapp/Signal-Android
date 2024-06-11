/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.events

import java.util.concurrent.TimeUnit

data class GroupCallRaiseHandEvent(val sender: CallParticipant, val timestamp: Long) {

  fun getCollapseTimestamp(): Long {
    return timestamp + TimeUnit.SECONDS.toMillis(LIFESPAN_SECONDS)
  }

  companion object {
    const val LIFESPAN_SECONDS = 4L
  }
}
