/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.signal.core.util.SqlUtil
import org.signal.core.util.forEach
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.select
import org.thoughtcrime.securesms.backup.v2.exporters.ChatArchiveExporter
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable

fun ThreadTable.getThreadsForBackup(db: SignalDatabase, includeImageWallpapers: Boolean): ChatArchiveExporter {
  //language=sql
  val query = """
      SELECT
        ${ThreadTable.TABLE_NAME}.${ThreadTable.ID}, 
        ${ThreadTable.RECIPIENT_ID}, 
        ${ThreadTable.PINNED_ORDER}, 
        ${ThreadTable.READ}, 
        ${ThreadTable.ARCHIVED},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MESSAGE_EXPIRATION_TIME},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MESSAGE_EXPIRATION_TIME_VERSION},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MUTE_UNTIL},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MENTION_SETTING}, 
        ${RecipientTable.TABLE_NAME}.${RecipientTable.CHAT_COLORS},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.CUSTOM_CHAT_COLORS_ID},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.WALLPAPER}
      FROM ${ThreadTable.TABLE_NAME}
        LEFT OUTER JOIN ${RecipientTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} = ${RecipientTable.TABLE_NAME}.${RecipientTable.ID}
      WHERE
        ${RecipientTable.TABLE_NAME}.${RecipientTable.TYPE} NOT IN (${RecipientTable.RecipientType.DISTRIBUTION_LIST.id}, ${RecipientTable.RecipientType.CALL_LINK.id})
    """
  val cursor = readableDatabase.query(query)

  return ChatArchiveExporter(cursor, db, includeImageWallpapers)
}

fun ThreadTable.getThreadGroupStatus(messageIds: Collection<Long>): Map<Long, Boolean> {
  if (messageIds.isEmpty()) {
    return emptyMap()
  }

  val out: MutableMap<Long, Boolean> = mutableMapOf()

  val query = SqlUtil.buildFastCollectionQuery("${MessageTable.TABLE_NAME}.${MessageTable.ID}", messageIds)
  readableDatabase
    .select(
      "${MessageTable.TABLE_NAME}.${MessageTable.ID}",
      "${RecipientTable.TABLE_NAME}.${RecipientTable.TYPE}"
    )
    .from(
      """
      ${MessageTable.TABLE_NAME}
        INNER JOIN ${ThreadTable.TABLE_NAME} ON ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID} = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID}
        INNER JOIN ${RecipientTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} = ${RecipientTable.TABLE_NAME}.${RecipientTable.ID}
      """
    )
    .where(query.where, query.whereArgs)
    .run()
    .forEach { cursor ->
      val messageId = cursor.requireLong(MessageTable.ID)
      val type = cursor.requireInt(RecipientTable.TYPE)
      out[messageId] = type != RecipientTable.RecipientType.INDIVIDUAL.id
    }

  return out
}
