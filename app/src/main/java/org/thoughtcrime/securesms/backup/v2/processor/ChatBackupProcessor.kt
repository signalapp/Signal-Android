/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupState
import org.thoughtcrime.securesms.backup.v2.database.getThreadsForBackup
import org.thoughtcrime.securesms.backup.v2.proto.Chat
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Collections

object ChatBackupProcessor {
  val TAG = Log.tag(ChatBackupProcessor::class.java)

  fun export(emitter: BackupFrameEmitter) {
    SignalDatabase.threads.getThreadsForBackup().use { reader ->
      for (chat in reader) {
        emitter.emit(Frame(chat = chat))
      }
    }
  }

  fun import(chat: Chat, backupState: BackupState) {
    // TODO Perf can be improved here by doing a single insert instead of insert + multiple updates

    val recipientId: RecipientId? = backupState.backupToLocalRecipientId[chat.recipientId]

    if (recipientId != null) {
      val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(org.thoughtcrime.securesms.recipients.Recipient.resolved(recipientId))

      if (chat.archived) {
        SignalDatabase.threads.archiveConversation(threadId)
      }

      if (chat.pinned) {
        SignalDatabase.threads.pinConversations(Collections.singleton(threadId))
      }

      backupState.chatIdToLocalRecipientId[chat.id] = recipientId
      backupState.chatIdToLocalThreadId[chat.id] = threadId
      backupState.chatIdToBackupRecipientId[chat.id] = chat.recipientId
    } else {
      Log.w(TAG, "Recipient doesnt exist with id $recipientId")
    }
  }
}
