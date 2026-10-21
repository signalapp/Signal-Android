/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Reads and edits the profile behind [AddAllowedMembersViewModel].
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

  /**
   * Removes [recipientId] from the profile's members and returns them, resolved, so they can be named in the confirmation.
   */
  suspend fun removeMember(profileId: Long, recipientId: RecipientId): Recipient {
    NotificationProfilesRepository().removeMember(profileId, recipientId).await()

    return withContext(SignalDispatchers.Default) {
      Recipient.resolved(recipientId)
    }
  }

  suspend fun addMember(profileId: Long, recipientId: RecipientId) {
    NotificationProfilesRepository().addMember(profileId, recipientId).await()
  }

  suspend fun setAllowAllCalls(profileId: Long, enabled: Boolean) {
    updateProfile(profileId) { it.copy(allowAllCalls = enabled) }
  }

  suspend fun setAllowAllMentions(profileId: Long, enabled: Boolean) {
    updateProfile(profileId) { it.copy(allowAllMentions = enabled) }
  }

  private suspend fun updateProfile(profileId: Long, transform: (NotificationProfile) -> NotificationProfile) {
    val profile = withContext(SignalDispatchers.Default) { SignalDatabase.notificationProfiles.getProfile(profileId) } ?: return
    NotificationProfilesRepository().updateProfile(transform(profile)).await()
  }

  data class ProfileAndMembers(
    val profile: NotificationProfile,
    val members: List<Recipient>
  )
}
