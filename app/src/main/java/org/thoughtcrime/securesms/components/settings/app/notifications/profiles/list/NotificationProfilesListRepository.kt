/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles

/**
 * The profile list behind [NotificationProfilesViewModel].
 */
object NotificationProfilesListRepository {

  /**
   * The user's profiles, newest first, together with the one that is currently on and how long it stays on. Emits again
   * whenever a profile changes.
   */
  fun observeProfiles(): Flow<ProfileList> {
    return NotificationProfilesRepository()
      .getProfiles()
      .asFlow()
      .map { profiles ->
        val activeProfile = NotificationProfiles.getActiveProfile(profiles)

        ProfileList(
          profiles = profiles.sortedDescending(),
          activeProfileId = activeProfile?.id,
          activeStatus = activeProfile?.let { NotificationProfiles.getActiveStatus(it) }
        )
      }
      .flowOn(SignalDispatchers.Default)
  }

  data class ProfileList(
    val profiles: List<NotificationProfile>,
    val activeProfileId: Long?,
    val activeStatus: NotificationProfiles.ActiveStatus?
  )
}
