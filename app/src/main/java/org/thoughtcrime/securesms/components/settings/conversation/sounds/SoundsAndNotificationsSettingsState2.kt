/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.sounds

import org.thoughtcrime.securesms.database.RecipientTable.NotificationSetting
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

data class SoundsAndNotificationsSettingsState2(
  val recipientId: RecipientId = Recipient.UNKNOWN.id,
  val muteUntil: Long = 0L,
  val mentionSetting: NotificationSetting = NotificationSetting.ALWAYS_NOTIFY,
  val callNotificationSetting: NotificationSetting = NotificationSetting.ALWAYS_NOTIFY,
  val replyNotificationSetting: NotificationSetting = NotificationSetting.ALWAYS_NOTIFY,
  val hasCustomNotificationSettings: Boolean = false,
  val hasMentionsSupport: Boolean = false,
  val channelConsistencyCheckComplete: Boolean = false
) {
  val isMuted = muteUntil > 0
}
