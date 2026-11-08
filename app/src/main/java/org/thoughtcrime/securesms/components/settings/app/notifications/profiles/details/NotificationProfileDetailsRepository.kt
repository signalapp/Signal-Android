/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx3.await
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Reads and edits the profile behind [NotificationProfileDetailsViewModel].
 */
object NotificationProfileDetailsRepository {

  /**
   * Profile [profileId] with its resolved members and whether it is on, or null once it no longer exists. Emits again
   * whenever any profile changes, since another profile turning on turns this one off.
   */
  fun observeProfile(profileId: Long): Flow<ProfileDetails?> {
    return NotificationProfilesRepository()
      .getProfiles()
      .asFlow()
      .map { profiles ->
        val profile = profiles.firstOrNull { it.id == profileId } ?: return@map null
        val isActive = NotificationProfiles.getActiveProfile(profiles)?.id == profileId

        ProfileDetails(
          profile = profile,
          members = profile.allowedMembers.map { Recipient.resolved(it) },
          activeStatus = if (isActive) NotificationProfiles.getActiveStatus(profile) else null
        )
      }
      .flowOn(SignalDispatchers.Default)
  }

  /**
   * Turns [profile] off if it is on, otherwise turns it on until the end of its schedule or indefinitely.
   */
  suspend fun toggleProfile(profile: NotificationProfile) {
    NotificationProfilesRepository().manuallyToggleProfile(profile).await()
  }

  suspend fun deleteProfile(profileId: Long) {
    NotificationProfilesRepository().deleteProfile(profileId).await()
  }

  data class ProfileDetails(
    val profile: NotificationProfile,
    val members: List<Recipient>,
    val activeStatus: NotificationProfiles.ActiveStatus?
  )
}
