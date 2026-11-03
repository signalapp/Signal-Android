/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.muted

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.appsettings.mutednotifications.MutedNotificationsState
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.RecipientTable.NotificationSetting
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * All of the storage access behind [MutedNotificationsViewModel]. A null recipient means the defaults for every muted
 * chat, which are kept in [SignalStore] and synced; a recipient means that one chat's own settings.
 */
object MutedNotificationsRepository {

  /** Emits the settings whenever they may have changed, which for the defaults is whenever the local user's recipient does. */
  fun observeSettings(recipientId: RecipientId?): Flow<MutedNotificationsState> {
    return Recipient.observable(recipientId ?: Recipient.self().id).asFlow().map { recipient ->
      if (recipientId == null) {
        getDefaultSettings()
      } else {
        MutedNotificationsState(
          isGlobal = false,
          allowCalls = recipient.callNotificationSetting == NotificationSetting.ALWAYS_NOTIFY,
          showMentions = recipient.isPushV2Group,
          allowMentions = recipient.mentionSetting == NotificationSetting.ALWAYS_NOTIFY,
          showReplies = recipient.isPushV2Group,
          allowReplies = recipient.replyNotificationSetting == NotificationSetting.ALWAYS_NOTIFY
        )
      }
    }
  }

  /** The defaults for every muted chat, read straight out of [SignalStore] so that they can be shown immediately. */
  fun getDefaultSettings(): MutedNotificationsState {
    return MutedNotificationsState(
      isGlobal = true,
      allowCalls = SignalStore.settings.allowCallsWhileMuted,
      showMentions = true,
      allowMentions = SignalStore.settings.allowMentionsWhileMuted,
      showReplies = true,
      allowReplies = SignalStore.settings.allowRepliesWhileMuted
    )
  }

  suspend fun setAllowCalls(recipientId: RecipientId?, allow: Boolean) {
    if (recipientId == null) {
      SignalStore.settings.allowCallsWhileMuted = allow
      markSelfNeedsSync()
    } else {
      withContext(SignalDispatchers.Default) {
        SignalDatabase.recipients.setCallNotificationSetting(recipientId, allow.toNotificationSetting())
      }
    }
  }

  suspend fun setAllowMentions(recipientId: RecipientId?, allow: Boolean) {
    if (recipientId == null) {
      SignalStore.settings.allowMentionsWhileMuted = allow
      markSelfNeedsSync()
    } else {
      withContext(SignalDispatchers.Default) {
        SignalDatabase.recipients.setMentionSetting(recipientId, allow.toNotificationSetting())
      }
    }
  }

  suspend fun setAllowReplies(recipientId: RecipientId?, allow: Boolean) {
    if (recipientId == null) {
      SignalStore.settings.allowRepliesWhileMuted = allow
      markSelfNeedsSync()
    } else {
      withContext(SignalDispatchers.Default) {
        SignalDatabase.recipients.setReplyNotificationSetting(recipientId, allow.toNotificationSetting())
      }
    }
  }

  private suspend fun markSelfNeedsSync() {
    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  private fun Boolean.toNotificationSetting(): NotificationSetting {
    return if (this) NotificationSetting.ALWAYS_NOTIFY else NotificationSetting.DO_NOT_NOTIFY
  }
}
