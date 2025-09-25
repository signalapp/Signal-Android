/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

import org.signal.core.util.LongSerializer

enum class RestoreState(private val id: Int, val inProgress: Boolean) {
  NONE(0, false),
  PENDING(1, true),
  RESTORING_DB(2, true),
  CALCULATING_MEDIA(4, true),
  RESTORING_MEDIA(3, true),
  CANCELING_MEDIA(5, true);

  val isMediaRestoreOperation: Boolean
    get() = this == CALCULATING_MEDIA || this == RESTORING_MEDIA || this == CANCELING_MEDIA

  companion object {
    val serializer: LongSerializer<RestoreState> = Serializer()
  }

  class Serializer : LongSerializer<RestoreState> {
    override fun serialize(data: RestoreState): Long {
      return data.id.toLong()
    }

    override fun deserialize(input: Long): RestoreState {
      return entries.firstOrNull { it.id == input.toInt() } ?: throw IllegalStateException()
    }
  }
}
