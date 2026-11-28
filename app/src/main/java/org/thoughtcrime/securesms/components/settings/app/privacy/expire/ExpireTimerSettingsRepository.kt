/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.expire

import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.groups.GroupChangeException
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupErrors
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import java.io.IOException

/**
 * Reads and saves disappearing messages timers, for new chats and for individual and group chats.
 */
object ExpireTimerSettingsRepository {

  private val TAG = Log.tag(ExpireTimerSettingsRepository::class)

  /** The default timer, in seconds, for new chats the user starts. */
  fun getUniversalExpireTimer(): Int = SignalStore.settings.universalExpireTimer

  /** The timer, in seconds, for [recipientId]'s chat, emitting again whenever it changes. */
  fun observeExpiration(recipientId: RecipientId): Flow<Int> {
    return Recipient.observable(recipientId).asFlow()
      .map { it.expiresInSeconds }
      .distinctUntilChanged()
  }

  suspend fun setUniversalExpireTimer(seconds: Int) = withContext(Dispatchers.Default) {
    SignalStore.settings.universalExpireTimer = seconds
    SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  /** Sets [recipientId]'s timer, updating the group for a group chat, or sending an update message for a 1:1 chat. */
  suspend fun setExpiration(recipientId: RecipientId, seconds: Int): SetExpirationResult = withContext(Dispatchers.IO) {
    val recipient = Recipient.resolved(recipientId)
    if (recipient.groupId.isPresent && recipient.groupId.get().isPush) {
      try {
        GroupManager.updateGroupTimer(AppDependencies.application, recipient.groupId.get().requirePush(), seconds)
        SetExpirationResult.Success
      } catch (e: GroupChangeException) {
        Log.w(TAG, e)
        SetExpirationResult.Failure(GroupErrors.getUserDisplayMessage(GroupChangeFailureReason.fromException(e)))
      } catch (e: IOException) {
        Log.w(TAG, e)
        SetExpirationResult.Failure(GroupErrors.getUserDisplayMessage(GroupChangeFailureReason.fromException(e)))
      }
    } else {
      val expireTimerVersion = SignalDatabase.recipients.setExpireMessagesAndIncrementVersion(recipientId, seconds)
      val outgoingMessage = OutgoingMessage.expirationUpdateMessage(Recipient.resolved(recipientId), System.currentTimeMillis(), seconds * 1000L, expireTimerVersion)
      val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
      MessageSender.send(AppDependencies.application, outgoingMessage, threadId, MessageSender.SendType.SIGNAL, null, null)
      SetExpirationResult.Success
    }
  }

  sealed interface SetExpirationResult {
    data object Success : SetExpirationResult

    /** Saving failed, with [message] to show the user. */
    data class Failure(@param:StringRes val message: Int) : SetExpirationResult
  }
}
