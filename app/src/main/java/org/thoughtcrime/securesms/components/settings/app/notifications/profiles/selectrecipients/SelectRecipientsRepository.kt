/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients

import kotlinx.coroutines.rx3.await
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository

/**
 * Saves the picks made in [SelectRecipientsViewModel].
 */
object SelectRecipientsRepository {

  /** Makes [recipients] exactly the people and groups profile [profileId] allows through. */
  suspend fun setAllowedMembers(profileId: Long, recipients: Set<RecipientId>) {
    NotificationProfilesRepository().updateAllowedMembers(profileId, recipients).await()
  }
}
