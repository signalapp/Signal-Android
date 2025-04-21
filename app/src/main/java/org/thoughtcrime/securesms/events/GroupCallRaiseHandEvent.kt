/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.events

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class GroupCallRaiseHandEvent(val sender: CallParticipant, private val timestampMillis: Long) {

  val timestamp = timestampMillis.milliseconds

  fun getCollapseTimestamp(): Duration {
    return timestamp + LIFESPAN
  }

  companion object {
    private val LIFESPAN = 4L.seconds
  }
}
