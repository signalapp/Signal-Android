/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.blocked

import android.content.Context
import androidx.core.util.Consumer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.w
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.groups.GroupChangeBusyException
import org.thoughtcrime.securesms.groups.GroupChangeFailedException
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.Companion.external
import org.thoughtcrime.securesms.recipients.Recipient.Companion.resolved
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import java.io.IOException

class BlockedUsersRepository(private val context: Context, private val dispatcher:CoroutineDispatcher = Dispatchers.IO) {
  companion object {
    private val TAG = Log.tag(BlockedUsersRepository::class.java)
  }
  

  suspend fun getBlocked(blockedUsers: Consumer<List<Recipient>>) {
    withContext(dispatcher) {
      val records: List<RecipientRecord> = recipients.getBlocked()
      val recipients: List<Recipient> = records.map { resolved(it.id) }
      blockedUsers.accept(recipients)
    }
  }

  suspend fun block(recipientId: RecipientId, onSuccess: ()-> Unit, onFailure: ()-> Unit) {
    withContext(dispatcher) {
      runCatching {
        RecipientUtil.block(context, resolved(recipientId))
      }.onSuccess {
        onSuccess()
      }.onFailure { throwable ->
        when (throwable) {
          is IOException, is GroupChangeFailedException, is GroupChangeBusyException -> {
            onFailure().also { w(TAG, "block: failed to block recipient: ", throwable) }
          }else -> throw throwable
        }
      }
    }
  }

  suspend fun createAndBlock(number: String, onSuccess: ()-> Unit) {
    withContext(dispatcher) {
      RecipientUtil.blockNonGroup(context, external(context, number))
      onSuccess()
    }
  }

  suspend fun unblock(recipientId: RecipientId, onSuccess: ()->Unit) {
    withContext(dispatcher) {
      RecipientUtil.unblock(resolved(recipientId))
      onSuccess()
    }
  }
}