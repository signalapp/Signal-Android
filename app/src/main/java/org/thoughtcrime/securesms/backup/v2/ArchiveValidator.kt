/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.signal.libsignal.messagebackup.MessageBackup
import org.signal.libsignal.messagebackup.ValidationError
import org.thoughtcrime.securesms.keyvalue.SignalStore
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
      ValidationResult.ValidationError(e)
    }
  }

  sealed interface ValidationResult {
    data object Success : ValidationResult
    data class ReadError(val exception: IOException) : ValidationResult
    data class ValidationError(val exception: org.signal.libsignal.messagebackup.ValidationError) : ValidationResult
  }
}
