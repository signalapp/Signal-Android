/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.signal.core.util.EventTimer
import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.backup.v2.database.ChatItemImportInserter
import org.thoughtcrime.securesms.backup.v2.database.clearAllDataForBackupRestore
import org.thoughtcrime.securesms.backup.v2.processor.AccountDataProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatItemBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.RecipientBackupProcessor
import org.thoughtcrime.securesms.backup.v2.stream.BackupExportStream
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupExportStream
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupImportStream
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object BackupRepository {

  private val TAG = Log.tag(BackupRepository::class.java)

  fun export(): ByteArray {
    val eventTimer = EventTimer()

    val outputStream = ByteArrayOutputStream()
    val writer: BackupExportStream = PlainTextBackupExportStream(outputStream)

    // Note: Without a transaction, we may export inconsistent state. But because we have a transaction,
    // writes from other threads are blocked. This is something to think more about.
    SignalDatabase.rawDatabase.withinTransaction {
      AccountDataProcessor.export {
        writer.write(it)
        eventTimer.emit("account")
      }

      RecipientBackupProcessor.export {
        writer.write(it)
        eventTimer.emit("recipient")
      }

      ChatBackupProcessor.export { frame ->
        writer.write(frame)
        eventTimer.emit("thread")
      }

      ChatItemBackupProcessor.export { frame ->
        writer.write(frame)
        eventTimer.emit("message")
      }
    }

    Log.d(TAG, "export() ${eventTimer.stop().summary}")

    return outputStream.toByteArray()
  }

  fun import(data: ByteArray) {
    val eventTimer = EventTimer()

    val stream = ByteArrayInputStream(data)
    val frameReader = PlainTextBackupImportStream(stream)

    // Note: Without a transaction, bad imports could lead to lost data. But because we have a transaction,
    // writes from other threads are blocked. This is something to think more about.
    SignalDatabase.rawDatabase.withinTransaction {
      SignalStore.clearAllDataForBackupRestore()
      SignalDatabase.recipients.clearAllDataForBackupRestore()
      SignalDatabase.distributionLists.clearAllDataForBackupRestore()
      SignalDatabase.threads.clearAllDataForBackupRestore()
      SignalDatabase.messages.clearAllDataForBackupRestore()

      val backupState = BackupState()
      val chatItemInserter: ChatItemImportInserter = ChatItemBackupProcessor.beginImport(backupState)

      for (frame in frameReader) {
        when {
          frame.account != null -> {
            AccountDataProcessor.import(frame.account)
            eventTimer.emit("account")
          }

          frame.recipient != null -> {
            RecipientBackupProcessor.import(frame.recipient, backupState)
            eventTimer.emit("recipient")
          }

          frame.chat != null -> {
            ChatBackupProcessor.import(frame.chat, backupState)
            eventTimer.emit("chat")
          }

          frame.chatItem != null -> {
            chatItemInserter.insert(frame.chatItem)
            eventTimer.emit("chatItem")
            // TODO if there's stuff in the stream after chatItems, we need to flush the inserter before going to the next phase
          }

          else -> Log.w(TAG, "Unrecognized frame")
        }
      }

      if (chatItemInserter.flush()) {
        eventTimer.emit("chatItem")
      }

      backupState.chatIdToLocalThreadId.values.forEach {
        SignalDatabase.threads.update(it, unarchive = false, allowDeletion = false)
      }
    }

    Log.d(TAG, "import() ${eventTimer.stop().summary}")
  }
}

class BackupState {
  val backupToLocalRecipientId = HashMap<Long, RecipientId>()
  val chatIdToLocalThreadId = HashMap<Long, Long>()
  val chatIdToLocalRecipientId = HashMap<Long, RecipientId>()
  val chatIdToBackupRecipientId = HashMap<Long, Long>()
}
