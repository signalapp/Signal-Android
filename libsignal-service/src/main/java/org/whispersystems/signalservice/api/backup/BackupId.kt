/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.backup

/**
 * Safe typing around a backupId, which is a 16-byte array.
 */
@JvmInline
value class BackupId(val value: ByteArray) {

  init {
    require(value.size == 16) { "BackupId must be 16 bytes!" }
  }
}
