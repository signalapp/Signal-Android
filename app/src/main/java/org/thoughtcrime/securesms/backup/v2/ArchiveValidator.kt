/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.signal.core.util.isNotNullOrBlank
import org.signal.libsignal.messagebackup.MessageBackup
import org.signal.libsignal.messagebackup.ValidationError
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.isStory
import org.thoughtcrime.securesms.util.isStoryReaction
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import java.io.File
import java.io.IOException
import org.signal.libsignal.messagebackup.BackupKey as LibSignalBackupKey
import org.signal.libsignal.messagebackup.MessageBackupKey as LibSignalMessageBackupKey

object ArchiveValidator {

  /**
   * Validates the provided [backupFile] that is encrypted with the provided [backupKey].
   */
  fun validate(backupFile: File, backupKey: MessageBackupKey): ValidationResult {
    return try {
      val backupId = backupKey.deriveBackupId(SignalStore.account.requireAci())
      val libSignalBackupKey = LibSignalBackupKey(backupKey.value)
      val backupKey = LibSignalMessageBackupKey(libSignalBackupKey, backupId.value)

      MessageBackup.validate(backupKey, MessageBackup.Purpose.REMOTE_BACKUP, { backupFile.inputStream() }, backupFile.length())

      ValidationResult.Success
    } catch (e: IOException) {
      ValidationResult.ReadError(e)
    } catch (e: ValidationError) {
      val sentTimestamp = "\\d{10,}+".toRegex().find(e.message ?: "")?.value?.toLongOrNull()
      ValidationResult.ValidationError(
        exception = e,
        messageDetails = sentTimestamp?.let { fetchMessageDetails(it) } ?: emptyList()
      )
    }
  }

  private fun fetchMessageDetails(sentTimestamp: Long): List<MessageDetails> {
    val messages = SignalDatabase.messages.getMessagesBySentTimestamp(sentTimestamp)
    return messages.map {
      MessageDetails(
        messageId = it.id,
        dateSent = it.dateSent,
        threadId = it.threadId,
        threadRecipientId = SignalDatabase.threads.getRecipientForThreadId(it.threadId)?.id?.toLong() ?: 0L,
        type = it.type,
        fromRecipientId = it.fromRecipient.id.toLong(),
        toRecipientId = it.toRecipient.id.toLong(),
        hasBody = it.body.isNotNullOrBlank(),
        hasExtras = it.messageExtras != null,
        outgoing = it.isOutgoing,
        viewOnce = it.isViewOnce,
        isStory = it.isStory(),
        isStoryReaction = it.isStoryReaction(),
        originalMessageId = it.originalMessageId?.id ?: 0,
        isLatestRevision = it.isLatestRevision
      )
    }
  }

  sealed interface ValidationResult {
    data object Success : ValidationResult
    data class ReadError(val exception: IOException) : ValidationResult
    data class ValidationError(
      val exception: org.signal.libsignal.messagebackup.ValidationError,
      val messageDetails: List<MessageDetails>
    ) : ValidationResult
  }

  data class MessageDetails(
    val messageId: Long,
    val dateSent: Long,
    val threadId: Long,
    val threadRecipientId: Long,
    val type: Long,
    val fromRecipientId: Long,
    val toRecipientId: Long,
    val hasBody: Boolean,
    val hasExtras: Boolean,
    val outgoing: Boolean,
    val viewOnce: Boolean,
    val isStory: Boolean,
    val isStoryReaction: Boolean,
    val originalMessageId: Long,
    val isLatestRevision: Boolean
  )
}
