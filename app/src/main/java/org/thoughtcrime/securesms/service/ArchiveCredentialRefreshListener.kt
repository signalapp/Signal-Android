/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service

import android.content.Context
import org.thoughtcrime.securesms.jobs.ArchiveCredentialRefreshJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Checks roughly once a day, at a randomized time, whether archive credentials need to be fetched. See [ArchiveCredentialRefreshJob].
 */
class ArchiveCredentialRefreshListener : PersistentAlarmManagerListener() {

  companion object {
    private val MIN_INTERVAL = 12.hours
    private val MAX_INTERVAL = 36.hours

    @JvmStatic
    fun schedule(context: Context) {
      ArchiveCredentialRefreshListener().onReceive(context, getScheduleIntent())
    }
  }

  override fun getNextScheduledExecutionTime(context: Context): Long {
    val now = System.currentTimeMillis()
    var nextTime = SignalStore.backup.nextCredentialRefreshTime

    if (nextTime == 0L || nextTime > now + MAX_INTERVAL.inWholeMilliseconds) {
      nextTime = getNextTime(now)
      SignalStore.backup.nextCredentialRefreshTime = nextTime
    }

    return nextTime
  }

  override fun onAlarm(context: Context, scheduledTime: Long): Long {
    ArchiveCredentialRefreshJob.enqueueIfNecessary()

    val nextTime = getNextTime(System.currentTimeMillis())
    SignalStore.backup.nextCredentialRefreshTime = nextTime

    return nextTime
  }

  private fun getNextTime(now: Long): Long {
    val jitter = Random.nextLong((MAX_INTERVAL - MIN_INTERVAL).inWholeMilliseconds).milliseconds
    return now + (MIN_INTERVAL + jitter).inWholeMilliseconds
  }
}
