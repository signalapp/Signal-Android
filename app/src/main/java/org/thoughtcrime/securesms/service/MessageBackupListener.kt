/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service

import android.content.Context
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.toMillis
import java.time.LocalDateTime
import java.util.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class MessageBackupListener : PersistentAlarmManagerListener() {
  override fun shouldScheduleExact(): Boolean {
    return true
  }

  override fun getNextScheduledExecutionTime(context: Context): Long {
    return SignalStore.backup.nextBackupTime
  }

  override fun onAlarm(context: Context, scheduledTime: Long): Long {
    if (SignalStore.backup.areBackupsEnabled) {
      val timeSinceLastSync = System.currentTimeMillis() - SignalStore.backup.lastMediaSyncTime
      BackupMessagesJob.enqueue(pruneAbandonedRemoteMedia = timeSinceLastSync >= BACKUP_MEDIA_SYNC_INTERVAL || timeSinceLastSync < 0)
    }
    return setNextBackupTimeToIntervalFromNow()
  }

  companion object {
    private val BACKUP_JITTER_WINDOW_SECONDS = 10.minutes.inWholeSeconds.toInt()
    private val BACKUP_MEDIA_SYNC_INTERVAL = 7.days.inWholeMilliseconds

    @JvmStatic
    fun schedule(context: Context?) {
      if (RemoteConfig.messageBackups && SignalStore.backup.areBackupsEnabled) {
        MessageBackupListener().onReceive(context, getScheduleIntent())
      }
    }

    @JvmStatic
    fun getNextDailyBackupTimeFromNowWithJitter(now: LocalDateTime, hour: Int, minute: Int, maxJitterSeconds: Int, randomSource: Random = Random()): LocalDateTime {
      var next = now.withHour(hour).withMinute(minute).withSecond(0)

      val endOfJitterWindowForNow = now.plusSeconds(maxJitterSeconds.toLong() / 2)
      while (!endOfJitterWindowForNow.isBefore(next)) {
        next = next.plusDays(1)
      }

      val jitter = randomSource.nextInt(maxJitterSeconds) - maxJitterSeconds / 2
      return next.plusSeconds(jitter.toLong())
    }

    fun setNextBackupTimeToIntervalFromNow(maxJitterSeconds: Int = BACKUP_JITTER_WINDOW_SECONDS): Long {
      val now = LocalDateTime.now()
      val hour = SignalStore.settings.backupHour
      val minute = SignalStore.settings.backupMinute
      var next = getNextDailyBackupTimeFromNowWithJitter(now, hour, minute, maxJitterSeconds)
      next = when (SignalStore.backup.backupFrequency) {
        BackupFrequency.MANUAL -> next.plusDays(364)
        BackupFrequency.MONTHLY -> next.plusDays(29)
        BackupFrequency.WEEKLY -> next.plusDays(6)
        else -> next
      }
      val nextTime = next.toMillis()
      SignalStore.backup.nextBackupTime = nextTime
      return nextTime
    }
  }
}
