/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import org.signal.core.util.isNull
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.select
import org.thoughtcrime.securesms.backup.v2.BackupState
import org.thoughtcrime.securesms.backup.v2.proto.Call
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.RecipientTable
import java.io.Closeable

typealias BackupCall = org.thoughtcrime.securesms.backup.v2.proto.Call

fun CallTable.getCallsForBackup(): CallLogIterator {
  return CallLogIterator(
    readableDatabase
      .select()
      .from(CallTable.TABLE_NAME)
      .where("${CallTable.EVENT} != ${CallTable.Event.serialize(CallTable.Event.DELETE)}")
      .run()
  )
}

fun CallTable.restoreCallLogFromBackup(call: BackupCall, backupState: BackupState) {
  val type = when (call.type) {
    Call.Type.VIDEO_CALL -> CallTable.Type.VIDEO_CALL
    Call.Type.AUDIO_CALL -> CallTable.Type.AUDIO_CALL
    Call.Type.AD_HOC_CALL -> CallTable.Type.AD_HOC_CALL
    Call.Type.GROUP_CALL -> CallTable.Type.GROUP_CALL
    Call.Type.UNKNOWN_TYPE -> return
  }

  val event = when (call.event) {
    Call.Event.DELETE -> CallTable.Event.DELETE
    Call.Event.JOINED -> CallTable.Event.JOINED
    Call.Event.GENERIC_GROUP_CALL -> CallTable.Event.GENERIC_GROUP_CALL
    Call.Event.DECLINED -> CallTable.Event.DECLINED
    Call.Event.ACCEPTED -> CallTable.Event.ACCEPTED
    Call.Event.MISSED -> CallTable.Event.MISSED
    Call.Event.OUTGOING_RING -> CallTable.Event.OUTGOING_RING
    Call.Event.OUTGOING -> CallTable.Event.ONGOING
    Call.Event.NOT_ACCEPTED -> CallTable.Event.NOT_ACCEPTED
    Call.Event.UNKNOWN_EVENT -> return
  }

  val direction = if (call.outgoing) CallTable.Direction.OUTGOING else CallTable.Direction.INCOMING

  backupState.callIdToType[call.callId] = CallTable.Call.getMessageType(type, direction, event)

  val values = contentValuesOf(
    CallTable.CALL_ID to call.callId,
    CallTable.PEER to backupState.backupToLocalRecipientId[call.conversationRecipientId]!!.serialize(),
    CallTable.TYPE to CallTable.Type.serialize(type),
    CallTable.DIRECTION to CallTable.Direction.serialize(direction),
    CallTable.EVENT to CallTable.Event.serialize(event),
    CallTable.TIMESTAMP to call.timestamp
  )

  writableDatabase.insert(CallTable.TABLE_NAME, SQLiteDatabase.CONFLICT_IGNORE, values)
}

/**
 * Provides a nice iterable interface over a [RecipientTable] cursor, converting rows to [BackupRecipient]s.
 * Important: Because this is backed by a cursor, you must close it. It's recommended to use `.use()` or try-with-resources.
 */
class CallLogIterator(private val cursor: Cursor) : Iterator<BackupCall?>, Closeable {
  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): BackupCall? {
    if (!cursor.moveToNext()) {
      throw NoSuchElementException()
    }

    val callId = cursor.requireLong(CallTable.CALL_ID)
    val type = CallTable.Type.deserialize(cursor.requireInt(CallTable.TYPE))
    val direction = CallTable.Direction.deserialize(cursor.requireInt(CallTable.DIRECTION))
    val event = CallTable.Event.deserialize(cursor.requireInt(CallTable.EVENT))

    return BackupCall(
      callId = callId,
      conversationRecipientId = cursor.requireLong(CallTable.PEER),
      type = when (type) {
        CallTable.Type.AUDIO_CALL -> Call.Type.AUDIO_CALL
        CallTable.Type.VIDEO_CALL -> Call.Type.VIDEO_CALL
        CallTable.Type.AD_HOC_CALL -> Call.Type.AD_HOC_CALL
        CallTable.Type.GROUP_CALL -> Call.Type.GROUP_CALL
      },
      outgoing = when (direction) {
        CallTable.Direction.OUTGOING -> true
        else -> false
      },
      timestamp = cursor.requireLong(CallTable.TIMESTAMP),
      ringerRecipientId = if (cursor.isNull(CallTable.RINGER)) null else cursor.requireLong(CallTable.RINGER),
      event = when (event) {
        CallTable.Event.ONGOING -> Call.Event.OUTGOING
        CallTable.Event.OUTGOING_RING -> Call.Event.OUTGOING_RING
        CallTable.Event.ACCEPTED -> Call.Event.ACCEPTED
        CallTable.Event.DECLINED -> Call.Event.DECLINED
        CallTable.Event.GENERIC_GROUP_CALL -> Call.Event.GENERIC_GROUP_CALL
        CallTable.Event.JOINED -> Call.Event.JOINED
        CallTable.Event.MISSED,
        CallTable.Event.MISSED_NOTIFICATION_PROFILE -> Call.Event.MISSED
        CallTable.Event.DELETE -> Call.Event.DELETE
        CallTable.Event.RINGING -> Call.Event.UNKNOWN_EVENT
        CallTable.Event.NOT_ACCEPTED -> Call.Event.NOT_ACCEPTED
      }
    )
  }

  override fun close() {
    cursor.close()
  }
}
