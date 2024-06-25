/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.signal.core.util.LongSerializer

/**
 * Serializable enum value for what we think a user's current backup tier is.
 *
 * We should not trust the stored value on its own, we should also verify it
 * against what the server knows, but it is a useful flag that helps avoid a
 * network call in some cases.
 */
enum class MessageBackupTier(val value: Int) {
  FREE(0),
  PAID(1);

  companion object Serializer : LongSerializer<MessageBackupTier?> {
    override fun serialize(data: MessageBackupTier?): Long {
      return data?.value?.toLong() ?: -1
    }

    override fun deserialize(data: Long): MessageBackupTier? {
      return entries.firstOrNull { it.value == data.toInt() }
    }
  }
}
