/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.created

import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.SignalDatabase

/**
 * Reads the newly created profile behind [NotificationProfileCreatedViewModel].
 */
object NotificationProfileCreatedRepository {

  /**
   * Whether profile [profileId] has its schedule turned on, or null if the profile doesn't exist.
   */
  suspend fun hasSchedule(profileId: Long): Boolean? {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.notificationProfiles.getProfile(profileId)?.schedule?.enabled
    }
  }
}
