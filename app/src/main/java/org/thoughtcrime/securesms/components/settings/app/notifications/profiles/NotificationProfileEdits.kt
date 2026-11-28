/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Edits to a profile's members and exceptions shared by the screens that offer them.
 */
object NotificationProfileEdits {

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
}
