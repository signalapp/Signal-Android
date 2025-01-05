/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.signal.core.util.logging.Log
import org.signal.core.util.select
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.exporters.ChatItemArchiveExporter
import org.thoughtcrime.securesms.backup.v2.importer.ChatItemArchiveImporter
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.RecipientId

private val TAG = "MessageTableArchiveExtensions"

fun MessageTable.getMessagesForBackup(db: SignalDatabase, backupTime: Long, mediaBackupEnabled: Boolean, selfRecipientId: RecipientId, exportState: ExportState): ChatItemArchiveExporter {
  // We create a covering index for the query to drastically speed up perf here.
  // Remember that we're working on a temporary snapshot of the database, so we can create an index and not worry about cleaning it up.
  val startTime = System.currentTimeMillis()
  val dateReceivedIndex = "message_date_received"
  writableDatabase.execSQL(
    """CREATE INDEX $dateReceivedIndex ON ${MessageTable.TABLE_NAME} (
      ${MessageTable.DATE_RECEIVED} ASC,
      ${MessageTable.STORY_TYPE},
      ${MessageTable.ID},
      ${MessageTable.DATE_SENT},
      ${MessageTable.DATE_SERVER},
      ${MessageTable.TYPE},
      ${MessageTable.THREAD_ID},
      ${MessageTable.BODY},
      ${MessageTable.MESSAGE_RANGES},
      ${MessageTable.FROM_RECIPIENT_ID},
      ${MessageTable.TO_RECIPIENT_ID},
      ${MessageTable.EXPIRES_IN},
      ${MessageTable.EXPIRE_STARTED},
      ${MessageTable.REMOTE_DELETED},
      ${MessageTable.UNIDENTIFIED},
      ${MessageTable.LINK_PREVIEWS},
      ${MessageTable.SHARED_CONTACTS},
      ${MessageTable.QUOTE_ID},
      ${MessageTable.QUOTE_AUTHOR},
      ${MessageTable.QUOTE_BODY},
      ${MessageTable.QUOTE_MISSING},
      ${MessageTable.QUOTE_BODY_RANGES},
      ${MessageTable.QUOTE_TYPE},
      ${MessageTable.ORIGINAL_MESSAGE_ID},
      ${MessageTable.LATEST_REVISION_ID},
      ${MessageTable.HAS_DELIVERY_RECEIPT},
      ${MessageTable.HAS_READ_RECEIPT},
      ${MessageTable.VIEWED_COLUMN},
      ${MessageTable.RECEIPT_TIMESTAMP},
      ${MessageTable.READ},
      ${MessageTable.NETWORK_FAILURES},
      ${MessageTable.MISMATCHED_IDENTITIES},
      ${MessageTable.TYPE},
      ${MessageTable.MESSAGE_EXTRAS},
      ${MessageTable.VIEW_ONCE}
    )
    """.trimMargin()
  )
  Log.d(TAG, "Creating index took ${System.currentTimeMillis() - startTime} ms")

  // Unfortunately we have some bad legacy data where the from_recipient_id is a group.
  // This cleans it up. Reminder, this is only a snapshot of the data.
  db.rawWritableDatabase.execSQL(
    """
      UPDATE ${MessageTable.TABLE_NAME}
      SET ${MessageTable.FROM_RECIPIENT_ID} = ${selfRecipientId.toLong()}
      WHERE ${MessageTable.FROM_RECIPIENT_ID} IN (
        SELECT ${GroupTable.RECIPIENT_ID}
        FROM ${GroupTable.TABLE_NAME}
      )
    """
  )

  // If someone re-registers with a new phone number, previous outgoing messages will no longer be associated with self.
  // This cleans it up by changing the from to be the current self id for all outgoing messages.
  db.rawWritableDatabase.execSQL(
    """
      UPDATE ${MessageTable.TABLE_NAME}
      SET ${MessageTable.FROM_RECIPIENT_ID} = ${selfRecipientId.toLong()}
      WHERE (${MessageTable.TYPE} & ${MessageTypes.BASE_TYPE_MASK}) IN (${MessageTypes.OUTGOING_MESSAGE_TYPES.joinToString(",")}) 
    """
  )

  return ChatItemArchiveExporter(
    db = db,
    backupStartTime = backupTime,
    batchSize = 10_000,
    mediaArchiveEnabled = mediaBackupEnabled,
    selfRecipientId = selfRecipientId,
    exportState = exportState,
    cursorGenerator = { lastSeenReceivedTime, count ->
      readableDatabase
        .select(
          MessageTable.ID,
          MessageTable.DATE_SENT,
          MessageTable.DATE_RECEIVED,
          MessageTable.DATE_SERVER,
          MessageTable.TYPE,
          MessageTable.THREAD_ID,
          MessageTable.BODY,
          MessageTable.MESSAGE_RANGES,
          MessageTable.FROM_RECIPIENT_ID,
          MessageTable.TO_RECIPIENT_ID,
          MessageTable.EXPIRES_IN,
          MessageTable.EXPIRE_STARTED,
          MessageTable.REMOTE_DELETED,
          MessageTable.UNIDENTIFIED,
          MessageTable.LINK_PREVIEWS,
          MessageTable.SHARED_CONTACTS,
          MessageTable.QUOTE_ID,
          MessageTable.QUOTE_AUTHOR,
          MessageTable.QUOTE_BODY,
          MessageTable.QUOTE_MISSING,
          MessageTable.QUOTE_BODY_RANGES,
          MessageTable.QUOTE_TYPE,
          MessageTable.ORIGINAL_MESSAGE_ID,
          MessageTable.LATEST_REVISION_ID,
          MessageTable.HAS_DELIVERY_RECEIPT,
          MessageTable.HAS_READ_RECEIPT,
          MessageTable.VIEWED_COLUMN,
          MessageTable.RECEIPT_TIMESTAMP,
          MessageTable.READ,
          MessageTable.NETWORK_FAILURES,
          MessageTable.MISMATCHED_IDENTITIES,
          MessageTable.TYPE,
          MessageTable.MESSAGE_EXTRAS,
          MessageTable.VIEW_ONCE
        )
        .from("${MessageTable.TABLE_NAME} INDEXED BY $dateReceivedIndex")
        .where("${MessageTable.STORY_TYPE} = 0 AND ${MessageTable.DATE_RECEIVED} >= $lastSeenReceivedTime")
        .limit(count)
        .orderBy("${MessageTable.DATE_RECEIVED} ASC")
        .run()
    }
  )
}

fun MessageTable.createChatItemInserter(importState: ImportState): ChatItemArchiveImporter {
  return ChatItemArchiveImporter(writableDatabase, importState, 500)
}
