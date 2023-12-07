/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.events

import org.thoughtcrime.securesms.recipients.Recipient
import java.util.concurrent.TimeUnit

/**
 * This is a data class to represent a reaction coming in over the wire in the format we need (mapped to a [Recipient]) in a way that can be easily
 * compared across Rx streams.
 */
data class GroupCallReactionEvent(val sender: Recipient, val reaction: String, val timestamp: Long) {
  fun getExpirationTimestamp(): Long {
    return timestamp + TimeUnit.SECONDS.toMillis(LIFESPAN_SECONDS)
  }

  companion object {
    const val LIFESPAN_SECONDS = 4L
  }
}
