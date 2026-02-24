/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.attachments

import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Combined key used to encrypt/decrypt attachments for local backups.
 */
@JvmInline
value class LocalBackupKey(val key: ByteArray) {
  fun toByteString(): ByteString {
    return key.toByteString()
  }
}
