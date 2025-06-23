/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.megaphone

import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Schedule for showing an update pin megaphone after re-registering with AEP in a flow that doesn't provide the pin.
 *
 * That is at this point only manual AEP entry without remote backup restore.
 */
class UpdatePinAfterAepRegistrationSchedule : MegaphoneSchedule {

  override fun shouldDisplay(seenCount: Int, lastSeen: Long, firstVisible: Long, currentTime: Long): Boolean {
    return !SignalStore.svr.hasPin() &&
      !SignalStore.svr.hasOptedOut() &&
      SignalStore.registration.isRegistrationComplete &&
      (!SignalStore.backup.isMediaRestoreInProgress || hasBeenLongEnough())
  }

  /**
   * Should show if it's been more than 3 days regardless of restore progress.
   */
  private fun hasBeenLongEnough(): Boolean {
    val registeredAt = SignalStore.account.registeredAtTimestamp.milliseconds
    val now = System.currentTimeMillis().milliseconds

    return registeredAt.isNegative() || registeredAt > now || (registeredAt + 3.days) < now
  }
}
