/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import org.signal.core.util.requireLong
import org.signal.core.util.select
import org.thoughtcrime.securesms.backup.v2.BackupState
import org.thoughtcrime.securesms.backup.v2.proto.AdHocCall
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.RecipientTable
import java.io.Closeable

fun CallTable.getAdhocCallsForBackup(): CallLogIterator {
  return CallLogIterator(
    readableDatabase
      .select()
      .from(CallTable.TABLE_NAME)
      .where("${CallTable.TYPE}=?", CallTable.Type.AD_HOC_CALL)
      .run()
  )
}

fun CallTable.restoreCallLogFromBackup(call: AdHocCall, backupState: BackupState) {
  val event = when (call.state) {
    AdHocCall.State.GENERIC -> CallTable.Event.GENERIC_GROUP_CALL
    AdHocCall.State.UNKNOWN_STATE -> CallTable.Event.GENERIC_GROUP_CALL
  }

  val values = contentValuesOf(
    CallTable.CALL_ID to call.callId,
    CallTable.PEER to backupState.backupToLocalRecipientId[call.recipientId]!!.serialize(),
    CallTable.TYPE to CallTable.Type.serialize(CallTable.Type.AD_HOC_CALL),
    CallTable.DIRECTION to CallTable.Direction.serialize(CallTable.Direction.OUTGOING),
    CallTable.EVENT to CallTable.Event.serialize(event),
    CallTable.TIMESTAMP to call.callTimestamp
  )

  writableDatabase.insert(CallTable.TABLE_NAME, SQLiteDatabase.CONFLICT_IGNORE, values)
}

/**
 * Provides a nice iterable interface over a [RecipientTable] cursor, converting rows to [BackupRecipient]s.
 * Important: Because this is backed by a cursor, you must close it. It's recommended to use `.use()` or try-with-resources.
 */
class CallLogIterator(private val cursor: Cursor) : Iterator<AdHocCall?>, Closeable {
  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): AdHocCall? {
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
