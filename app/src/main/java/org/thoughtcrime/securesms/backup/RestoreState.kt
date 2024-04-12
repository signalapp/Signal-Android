/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

import org.signal.core.util.LongSerializer

enum class RestoreState(val id: Int, val inProgress: Boolean) {
  FAILED(-1, false),
  NONE(0, false),
  PENDING(1, true),
  RESTORING_DB(2, true),
  RESTORING_MEDIA(3, true);

  companion object {
    val serializer: LongSerializer<RestoreState> = Serializer()
  }

  class Serializer : LongSerializer<RestoreState> {
    override fun serialize(data: RestoreState): Long {
      return data.id.toLong()
    }

    override fun deserialize(data: Long): RestoreState {
      return when (data.toInt()) {
        FAILED.id -> FAILED
        PENDING.id -> PENDING
        RESTORING_DB.id -> RESTORING_DB
        RESTORING_MEDIA.id -> RESTORING_MEDIA
        else -> NONE
      }
    }
  }
}
