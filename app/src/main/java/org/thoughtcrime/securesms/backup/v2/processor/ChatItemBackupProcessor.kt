/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupState
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.database.ChatItemImportInserter
import org.thoughtcrime.securesms.backup.v2.database.createChatItemInserter
import org.thoughtcrime.securesms.backup.v2.database.getMessagesForBackup
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.database.SignalDatabase

object ChatItemBackupProcessor {
  val TAG = Log.tag(ChatItemBackupProcessor::class.java)

  fun export(db: SignalDatabase, exportState: ExportState, emitter: BackupFrameEmitter) {
    db.messageTable.getMessagesForBackup(exportState.backupTime, exportState.allowMediaBackup).use { chatItems ->
      while (chatItems.hasNext()) {
        val chatItem = chatItems.next()
        if (chatItem != null) {
          if (exportState.threadIds.contains(chatItem.chatId)) {
            emitter.emit(Frame(chatItem = chatItem))
          }
        }
      }
    }
  }

  fun beginImport(backupState: BackupState): ChatItemImportInserter {
    return SignalDatabase.messages.createChatItemInserter(backupState)
  }
}
