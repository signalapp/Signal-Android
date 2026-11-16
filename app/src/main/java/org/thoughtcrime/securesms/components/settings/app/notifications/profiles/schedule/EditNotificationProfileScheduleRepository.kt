/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule

/**
 * Reads and saves the schedule behind [EditNotificationProfileScheduleViewModel].
 */
object EditNotificationProfileScheduleRepository {

  /**
   * Profile [profileId]'s schedule, or null if the profile doesn't exist.
   */
  suspend fun getSchedule(profileId: Long): NotificationProfileSchedule? {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.notificationProfiles.getProfile(profileId)?.schedule
    }
  }

  /**
   * Saves [schedule], syncs it, and turns the profile on now if the schedule already covers the current time.
   */
  suspend fun saveSchedule(profileId: Long, schedule: NotificationProfileSchedule) {
    val repository = NotificationProfilesRepository()
    repository.updateSchedule(schedule).await()

    withContext(SignalDispatchers.Default) {
      repository.scheduleNotificationProfileSync(profileId)
    }

    if (schedule.enabled && schedule.coversTime(System.currentTimeMillis())) {
      repository.manuallyEnableProfileForSchedule(profileId, schedule).await()
    }
  }
}
