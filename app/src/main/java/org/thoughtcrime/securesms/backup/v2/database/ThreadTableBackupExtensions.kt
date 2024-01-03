/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.database.Cursor
import org.signal.core.util.SqlUtil
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.backup.v2.proto.Chat
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.recipients.RecipientId
import java.io.Closeable

private val TAG = Log.tag(ThreadTable::class.java)

fun ThreadTable.getThreadsForBackup(): ChatIterator {
  val cursor = readableDatabase
    .select(
      ThreadTable.ID,
      ThreadTable.RECIPIENT_ID,
      ThreadTable.ARCHIVED,
      ThreadTable.PINNED,
      ThreadTable.EXPIRES_IN
    )
    .from(ThreadTable.TABLE_NAME)
    .run()

  return ChatIterator(cursor)
}

fun ThreadTable.clearAllDataForBackupRestore() {
  writableDatabase.delete(ThreadTable.TABLE_NAME, null, null)
  SqlUtil.resetAutoIncrementValue(writableDatabase, ThreadTable.TABLE_NAME)
  clearCache()
}

fun ThreadTable.restoreFromBackup(chat: Chat, recipientId: RecipientId): Long? {
  return writableDatabase
    .insertInto(ThreadTable.TABLE_NAME)
    .values(
      ThreadTable.RECIPIENT_ID to recipientId.serialize(),
      ThreadTable.PINNED to chat.pinnedOrder,
      ThreadTable.ARCHIVED to chat.archived.toInt()
    )
    .run()
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
      expirationTimerMs = cursor.requireLong(ThreadTable.EXPIRES_IN)
    )
  }

  override fun close() {
    cursor.close()
  }
}
