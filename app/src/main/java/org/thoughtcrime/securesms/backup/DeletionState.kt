/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

import org.signal.core.util.LongSerializer

/**
 * Denotes the deletion state for backups.
 */
enum class DeletionState(val id: Int) {
  FAILED(-1),
  NONE(0),
  RUNNING(1);

  companion object {
    val serializer: LongSerializer<DeletionState> = Serializer()
  }

  class Serializer : LongSerializer<DeletionState> {
    override fun serialize(data: DeletionState): Long {
      return data.id.toLong()
    }

    override fun deserialize(data: Long): DeletionState {
      return when (data.toInt()) {
        FAILED.id -> FAILED
        RUNNING.id -> RUNNING
        else -> NONE
      }
    }
  }
}
