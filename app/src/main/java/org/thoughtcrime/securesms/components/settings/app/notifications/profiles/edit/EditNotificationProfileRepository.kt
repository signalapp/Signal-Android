/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository
import org.thoughtcrime.securesms.database.NotificationProfileTables.NotificationProfileChangeResult
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile

/**
 * Reads and saves the profile behind [EditNotificationProfileViewModel].
 */
object EditNotificationProfileRepository {

  suspend fun getProfile(profileId: Long): NotificationProfile? {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.notificationProfiles.getProfile(profileId)
    }
  }

  suspend fun createProfile(name: String, emoji: String): SaveResult {
    return NotificationProfilesRepository().createProfile(name, emoji).await().toSaveResult()
  }

  suspend fun updateProfile(profileId: Long, name: String, emoji: String): SaveResult {
    return NotificationProfilesRepository().updateProfile(profileId, name, emoji).await().toSaveResult()
  }

  private fun NotificationProfileChangeResult.toSaveResult(): SaveResult {
    return when (this) {
      is NotificationProfileChangeResult.Success -> SaveResult.Success(notificationProfile.id)
      NotificationProfileChangeResult.DuplicateName -> SaveResult.DuplicateName
    }
  }

  sealed interface SaveResult {
    data class Success(val profileId: Long) : SaveResult

    /** Another profile already has this name. */
    data object DuplicateName : SaveResult
  }
}
