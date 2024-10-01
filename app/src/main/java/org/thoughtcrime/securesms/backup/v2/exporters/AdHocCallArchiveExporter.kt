/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.database.Cursor
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.backup.v2.proto.AdHocCall
import org.thoughtcrime.securesms.database.CallTable
import java.io.Closeable

/**
 * Provides a nice iterable interface over a [RecipientTable] cursor, converting rows to [BackupRecipient]s.
 * Important: Because this is backed by a cursor, you must close it. It's recommended to use `.use()` or try-with-resources.
 */
class AdHocCallArchiveExporter(private val cursor: Cursor) : Iterator<AdHocCall>, Closeable {
  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): AdHocCall {
    if (!cursor.moveToNext()) {
      throw NoSuchElementException()
    }

    val callId = cursor.requireLong(CallTable.CALL_ID)

    return AdHocCall(
      callId = callId,
      recipientId = cursor.requireLong(CallTable.PEER),
      state = AdHocCall.State.GENERIC,
      callTimestamp = cursor.requireLong(CallTable.TIMESTAMP)
    )
  }

  override fun close() {
    cursor.close()
  }
}
