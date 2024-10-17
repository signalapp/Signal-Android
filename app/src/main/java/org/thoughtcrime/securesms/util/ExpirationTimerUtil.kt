/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * This exists as a temporary shim to improve the callsites where we'll be setting the expiration timer.
 *
 * Until the versions that don't understand expiration timers expire, we'll have to check capabilities before incrementing the version.
 *
 * After those old clients expire, we can remove this shim entirely and call the RecipientTable methods directly.
 */
object ExpirationTimerUtil {

  @JvmStatic
  fun setExpirationTimer(recipientId: RecipientId, expirationTimeSeconds: Int): Int {
    val selfCapable = Recipient.self().versionedExpirationTimerCapability == Recipient.Capability.SUPPORTED
    val recipientCapable = Recipient.resolved(recipientId).let { it.versionedExpirationTimerCapability == Recipient.Capability.SUPPORTED || it.expireTimerVersion > 2 }

    return if (selfCapable && recipientCapable) {
      SignalDatabase.recipients.setExpireMessagesAndIncrementVersion(recipientId, expirationTimeSeconds)
    } else {
      SignalDatabase.recipients.setExpireMessagesWithoutIncrementingVersion(recipientId, expirationTimeSeconds)
      1
    }
  }
}
