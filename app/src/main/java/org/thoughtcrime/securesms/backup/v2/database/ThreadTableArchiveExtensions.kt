/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.exporters.ChatArchiveExporter
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable

fun ThreadTable.getThreadsForBackup(db: SignalDatabase, exportState: ExportState, includeImageWallpapers: Boolean): ChatArchiveExporter {
  val notReleaseNoteClause = exportState.releaseNoteRecipientId?.let {
    "AND ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} != $it"
  } ?: ""

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
        $notReleaseNoteClause
    """
  val cursor = readableDatabase.query(query)

  return ChatArchiveExporter(cursor, db, exportState, includeImageWallpapers)
}
