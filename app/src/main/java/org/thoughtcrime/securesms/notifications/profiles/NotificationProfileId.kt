/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.notifications.profiles

import org.signal.core.util.DatabaseId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID

/**
 * Typed wrapper for notification profile uuid.
 */
data class NotificationProfileId(val uuid: UUID) : DatabaseId {
  companion object {
    fun from(id: String): NotificationProfileId {
      return NotificationProfileId(UuidUtil.parseOrThrow(id))
    }

    fun generate(): NotificationProfileId {
      return NotificationProfileId(UUID.randomUUID())
    }
  }

  override fun serialize(): String {
    return uuid.toString()
  }
}
