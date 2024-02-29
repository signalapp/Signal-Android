/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.backup.v2.proto.Chat
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.recipients.RecipientId
import java.io.Closeable

private val TAG = Log.tag(ThreadTable::class.java)

fun ThreadTable.getThreadsForBackup(): ChatIterator {
  //language=sql
  val query = """
      SELECT 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.ID}, 
        ${ThreadTable.RECIPIENT_ID}, 
        ${ThreadTable.PINNED}, 
        ${ThreadTable.READ}, 
        ${ThreadTable.ARCHIVED},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MESSAGE_EXPIRATION_TIME},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MUTE_UNTIL},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MENTION_SETTING} 
      FROM ${ThreadTable.TABLE_NAME} 
        LEFT OUTER JOIN ${RecipientTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} = ${RecipientTable.TABLE_NAME}.${RecipientTable.ID}
    """
  val cursor = readableDatabase.query(query)

  return ChatIterator(cursor)
}

fun ThreadTable.clearAllDataForBackupRestore() {
  writableDatabase.delete(ThreadTable.TABLE_NAME, null, null)
  SqlUtil.resetAutoIncrementValue(writableDatabase, ThreadTable.TABLE_NAME)
  clearCache()
}

fun ThreadTable.restoreFromBackup(chat: Chat, recipientId: RecipientId): Long? {
  val threadId = writableDatabase
    .insertInto(ThreadTable.TABLE_NAME)
    .values(
      ThreadTable.RECIPIENT_ID to recipientId.serialize(),
      ThreadTable.PINNED to chat.pinnedOrder,
      ThreadTable.ARCHIVED to chat.archived.toInt(),
      ThreadTable.READ to if (chat.markedUnread) ThreadTable.ReadStatus.FORCED_UNREAD.serialize() else ThreadTable.ReadStatus.READ.serialize(),
      ThreadTable.ACTIVE to 1
    )
    .run()
  writableDatabase
    .update(
      RecipientTable.TABLE_NAME,
      contentValuesOf(
        RecipientTable.MENTION_SETTING to (if (chat.dontNotifyForMentionsIfMuted) RecipientTable.MentionSetting.DO_NOT_NOTIFY.id else RecipientTable.MentionSetting.ALWAYS_NOTIFY.id),
        RecipientTable.MUTE_UNTIL to chat.muteUntilMs,
        RecipientTable.MESSAGE_EXPIRATION_TIME to chat.expirationTimerMs
      ),
      "${RecipientTable.ID} = ?",
      SqlUtil.buildArgs(recipientId.toLong())
    )

  return threadId
}

class ChatIterator(private val cursor: Cursor) : Iterator<Chat>, Closeable {
  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): Chat {
    if (!cursor.moveToNext()) {
      throw NoSuchElementException()
    }

    return Chat(
      id = cursor.requireLong(ThreadTable.ID),
      recipientId = cursor.requireLong(ThreadTable.RECIPIENT_ID),
      archived = cursor.requireBoolean(ThreadTable.ARCHIVED),
      pinnedOrder = cursor.requireInt(ThreadTable.PINNED),
      expirationTimerMs = cursor.requireLong(RecipientTable.MESSAGE_EXPIRATION_TIME),
      muteUntilMs = cursor.requireLong(RecipientTable.MUTE_UNTIL),
      markedUnread = ThreadTable.ReadStatus.deserialize(cursor.requireInt(ThreadTable.READ)) == ThreadTable.ReadStatus.FORCED_UNREAD,
      dontNotifyForMentionsIfMuted = RecipientTable.MentionSetting.DO_NOT_NOTIFY.id == cursor.requireInt(RecipientTable.MENTION_SETTING)
    )
  }

  override fun close() {
    cursor.close()
  }
}
