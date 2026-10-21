/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Reads the profile behind [AddAllowedMembersViewModel].
 */
object AddAllowedMembersRepository {

  /**
   * Profile [profileId] together with its resolved members. Emits again whenever the profile changes.
   */
  fun observeProfile(profileId: Long): Flow<ProfileAndMembers> {
    return NotificationProfilesRepository()
      .getProfile(profileId)
      .asFlow()
      .map { profile -> ProfileAndMembers(profile, profile.allowedMembers.map { Recipient.resolved(it) }) }
      .flowOn(SignalDispatchers.Default)
  }

  data class ProfileAndMembers(
    val profile: NotificationProfile,
    val members: List<Recipient>
  )
}
