/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.BackupValues.Companion.TAG
import org.thoughtcrime.securesms.keyvalue.protos.BackupDownloadNotifierState
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Manages setting and snoozing notifiers informing the user to download their backup
 * before it is deleted from the Signal service.
 *
 * This is only meant to be delegated to from [BackupValues]
 */
object BackupDownloadNotifierUtil {

  /**
   * Sets the notifier to trigger half way between now and the entitlement expiration time.
   *
   * @param state The current state, or null.
   * @param entitlementExpirationTime The time the user's backup entitlement expires
   * @param now The current time, for testing.
   *
   * @return the new state value.
   */
  fun setDownloadNotifierToTriggerAtHalfwayPoint(
    state: BackupDownloadNotifierState?,
    entitlementExpirationTime: Duration,
    now: Duration = System.currentTimeMillis().milliseconds
  ): BackupDownloadNotifierState? {
    if (state?.entitlementExpirationSeconds == entitlementExpirationTime.inWholeSeconds) {
      Log.d(TAG, "Entitlement expiration time already present.")
      return state
    }

    if (now >= entitlementExpirationTime) {
      Log.i(TAG, "Entitlement expiration time is in the past. Clearing state.")
      return null
    }

    val timeRemaining = entitlementExpirationTime - now
    val halfWayPoint = (entitlementExpirationTime - timeRemaining / 2)
    val lastDay = entitlementExpirationTime - 1.days

    val nextIntervalSeconds: Duration = when {
      timeRemaining <= 1.days -> 0.seconds
      timeRemaining <= 4.days -> lastDay - now
      else -> halfWayPoint - now
    }

    return BackupDownloadNotifierState(
      entitlementExpirationSeconds = entitlementExpirationTime.inWholeSeconds,
      lastSheetDisplaySeconds = now.inWholeSeconds,
      intervalSeconds = nextIntervalSeconds.inWholeSeconds,
      type = BackupDownloadNotifierState.Type.SHEET
    )
  }

  /**
   * Sets the notifier to trigger either one day before or four hours before expiration.
   *
   * @param state The current state, or null.
   * @param now The current time, for testing.
   *
   * @return The new state value.
   */
  fun snoozeDownloadNotifier(
    state: BackupDownloadNotifierState?,
    now: Duration = System.currentTimeMillis().milliseconds
  ): BackupDownloadNotifierState? {
    state ?: return null

    if (state.type == BackupDownloadNotifierState.Type.DIALOG) {
      Log.i(TAG, "Clearing state after dismissing download notifier dialog.")
      return null
    }

    val lastDay = state.entitlementExpirationSeconds.seconds - 1.days

    return if (now >= lastDay) {
      val fourHoursPriorToExpiration = state.entitlementExpirationSeconds.seconds - 4.hours

      state.newBuilder()
        .lastSheetDisplaySeconds(now.inWholeSeconds)
        .intervalSeconds(max(0L, (fourHoursPriorToExpiration - now).inWholeSeconds))
        .type(BackupDownloadNotifierState.Type.DIALOG)
        .build()
    } else {
      val timeUntilLastDay = lastDay - now

      state.newBuilder()
        .lastSheetDisplaySeconds(now.inWholeSeconds)
        .intervalSeconds(timeUntilLastDay.inWholeSeconds)
        .type(BackupDownloadNotifierState.Type.SHEET)
        .build()
    }
  }
}
