/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.database.createChatItemInserter
import org.thoughtcrime.securesms.backup.v2.database.getMessagesForBackup
import org.thoughtcrime.securesms.backup.v2.importer.ChatItemArchiveImporter
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.database.SignalDatabase

/**
 * Handles importing/exporting [ChatItem] frames for an archive.
 */
object ChatItemArchiveProcessor {
  val TAG = Log.tag(ChatItemArchiveProcessor::class.java)

  fun export(db: SignalDatabase, exportState: ExportState, cancellationSignal: () -> Boolean, emitter: BackupFrameEmitter) {
    db.messageTable.getMessagesForBackup(db, exportState.backupTime, exportState.mediaBackupEnabled).use { chatItems ->
      var count = 0
      while (chatItems.hasNext()) {
        if (count % 1000 == 0 && cancellationSignal()) {
          return@use
        }

        val chatItem: ChatItem? = chatItems.next()
        if (chatItem != null) {
          if (exportState.threadIds.contains(chatItem.chatId)) {
            emitter.emit(Frame(chatItem = chatItem))
          }
        }
        count++
      }
    }
  }

  fun beginImport(importState: ImportState): ChatItemArchiveImporter {
    return SignalDatabase.messages.createChatItemInserter(importState)
  }
}
