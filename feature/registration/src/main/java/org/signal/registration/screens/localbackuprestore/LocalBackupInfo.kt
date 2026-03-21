/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import android.net.Uri
import java.time.LocalDateTime

/**
 * Describes a local backup found on the device.
 */
data class LocalBackupInfo(
  val type: BackupType,
  val date: LocalDateTime,
  val name: String,
  val uri: Uri,
  val sizeBytes: Long? = null
) {
  enum class BackupType {
    /** V1 .backup file format (signal-yyyy-MM-dd-HH-mm-ss.backup) */
    V1,

    /** V2 folder-based format (signal-backup-yyyy-MM-dd-HH-mm-ss) */
    V2
  }
}
