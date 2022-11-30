package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.DistributionId

/**
 * Sending to a distribution list is a bit trickier. When we send to multiple distribution lists with overlapping membership, we want to
 * show them as distinct items on the sending side, but as a single item on the receiving side. Basically, if Alice has two lists and Bob
 * is on both, Bob should always see a story for "Alice" and not know that Alice has him in multiple lists. And when Bob views the story,
 * Alice should update the UI to show a view in each list. To do this, we need to:
 * 1. Only send a single copy of each story to a given recipient, while
 * 2. Knowing which people would have gotten duplicate copies.
 */
class StorySendTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    const val TABLE_NAME = "story_sends"
    const val ID = "_id"
    const val MESSAGE_ID = "message_id"
    const val RECIPIENT_ID = "recipient_id"
    const val SENT_TIMESTAMP = "sent_timestamp"
    const val ALLOWS_REPLIES = "allows_replies"
    const val DISTRIBUTION_ID = "distribution_id"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $MESSAGE_ID INTEGER NOT NULL REFERENCES ${MmsTable.TABLE_NAME} (${MmsTable.ID}) ON DELETE CASCADE,
        $RECIPIENT_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $SENT_TIMESTAMP INTEGER NOT NULL,
        $ALLOWS_REPLIES INTEGER NOT NULL,
        $DISTRIBUTION_ID TEXT NOT NULL REFERENCES ${DistributionListTables.LIST_TABLE_NAME} (${DistributionListTables.DISTRIBUTION_ID}) ON DELETE CASCADE
      )
    """.trimIndent()

    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX story_sends_recipient_id_sent_timestamp_allows_replies_index ON $TABLE_NAME ($RECIPIENT_ID, $SENT_TIMESTAMP, $ALLOWS_REPLIES)",
      "CREATE INDEX story_sends_message_id_distribution_id_index ON $TABLE_NAME ($MESSAGE_ID, $DISTRIBUTION_ID)",
    )
  }

  fun insert(messageId: Long, recipientIds: Collection<RecipientId>, sentTimestamp: Long, allowsReplies: Boolean, distributionId: DistributionId) {
    val db = writableDatabase

    db.beginTransaction()
    try {
      val insertValues: List<ContentValues> = recipientIds.map { id ->
        contentValuesOf(
          MESSAGE_ID to messageId,
          RECIPIENT_ID to id.serialize(),
          SENT_TIMESTAMP to sentTimestamp,
          ALLOWS_REPLIES to allowsReplies.toInt(),
          DISTRIBUTION_ID to distributionId.toString()
        )
      }

      SqlUtil.buildBulkInsert(TABLE_NAME, arrayOf(MESSAGE_ID, RECIPIENT_ID, SENT_TIMESTAMP, ALLOWS_REPLIES, DISTRIBUTION_ID), insertValues)
        .forEach { query -> db.execSQL(query.where, query.whereArgs) }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun getRecipientsForDistributionId(messageId: Long, distributionId: DistributionId): Set<RecipientId> {
    return readableDatabase
      .select(RECIPIENT_ID)
      .from(TABLE_NAME)
      .where("$MESSAGE_ID = ? AND $DISTRIBUTION_ID = ?", messageId, distributionId.toString())
      .run()
      .readToList { cursor ->
        RecipientId.from(cursor.requireLong(RECIPIENT_ID))
      }
      .toSet()
  }

  fun getRecipientsToSendTo(messageId: Long, sentTimestamp: Long, allowsReplies: Boolean): List<RecipientId> {
    val recipientIds = mutableListOf<RecipientId>()

    val query = """
      SELECT DISTINCT $RECIPIENT_ID
      FROM $TABLE_NAME
      WHERE 
        $MESSAGE_ID = $messageId
        AND $RECIPIENT_ID NOT IN (
          SELECT $RECIPIENT_ID
          FROM $TABLE_NAME
          WHERE
            $SENT_TIMESTAMP = $sentTimestamp
            AND $MESSAGE_ID < $messageId
            AND $ALLOWS_REPLIES >= ${allowsReplies.toInt()}
        )
        AND $RECIPIENT_ID NOT IN (
          SELECT $RECIPIENT_ID
          FROM $TABLE_NAME
          WHERE
            $SENT_TIMESTAMP = $sentTimestamp
            AND $MESSAGE_ID > $messageId
            AND $ALLOWS_REPLIES > ${allowsReplies.toInt()}
        )
    """.trimIndent()

    readableDatabase.rawQuery(query, null).use { cursor ->
      while (cursor.moveToNext()) {
        recipientIds += RecipientId.from(cursor.requireLong(RECIPIENT_ID))
      }
    }

    return recipientIds
  }

  /**
   * The weirdness with remote deletes and stories is that just because you remote-delete a story to List A doesn’t mean you
   * send the delete to everyone on the list – some people have it through multiple lists.
   *
   * The general idea is to find all recipients for a story that still have a non-deleted copy of it.
   */
  fun getRemoteDeleteRecipients(messageId: Long, sentTimestamp: Long): List<RecipientId> {
    val recipientIds = mutableListOf<RecipientId>()

    val query = """
      SELECT $RECIPIENT_ID
      FROM $TABLE_NAME
      WHERE
        $MESSAGE_ID = $messageId
        AND $RECIPIENT_ID NOT IN (
          SELECT $RECIPIENT_ID
          FROM $TABLE_NAME
          WHERE $MESSAGE_ID != $messageId
          AND $SENT_TIMESTAMP = $sentTimestamp
          AND $MESSAGE_ID IN (
            SELECT ${MmsTable.ID}
            FROM ${MmsTable.TABLE_NAME}
            WHERE ${MmsTable.REMOTE_DELETED} = 0
          )
        )
    """.trimIndent()

    readableDatabase.rawQuery(query, null).use { cursor ->
      while (cursor.moveToNext()) {
        recipientIds += RecipientId.from(cursor.requireLong(RECIPIENT_ID))
      }
    }

    return recipientIds
  }

  fun canReply(recipientId: RecipientId, sentTimestamp: Long): Boolean {
    readableDatabase.query(
      TABLE_NAME,
      arrayOf("1"),
      "$RECIPIENT_ID = ? AND $SENT_TIMESTAMP = ? AND $ALLOWS_REPLIES = ?",
      SqlUtil.buildArgs(recipientId, sentTimestamp, 1),
      null,
      null,
      null
    ).use {
      return it.moveToFirst()
    }
  }

  fun getStoryMessagesFor(syncMessageId: MessageTable.SyncMessageId): Set<MessageId> {
    val messageIds = mutableSetOf<MessageId>()

    readableDatabase.query(
      TABLE_NAME,
      arrayOf(MESSAGE_ID),
      "$RECIPIENT_ID = ? AND $SENT_TIMESTAMP = ?",
      SqlUtil.buildArgs(syncMessageId.recipientId, syncMessageId.timetamp),
      null,
      null,
      null
    ).use { cursor ->
      while (cursor.moveToNext()) {
        messageIds += MessageId(cursor.requireLong(MESSAGE_ID), true)
      }
    }

    return messageIds
  }

  override fun remapRecipient(oldId: RecipientId, newId: RecipientId) {
    val query = "$RECIPIENT_ID = ?"
    val args = SqlUtil.buildArgs(oldId)
    val values = contentValuesOf(RECIPIENT_ID to newId.serialize())

    writableDatabase.update(TABLE_NAME, values, query, args)
  }

  /**
   * Gets the manifest for a given story, or null if the story should NOT be the one reporting the manifest.
   */
  fun getFullSentStorySyncManifest(messageId: Long, sentTimestamp: Long): SentStorySyncManifest? {
    val firstMessageId: Long = readableDatabase.select(MESSAGE_ID)
      .from(TABLE_NAME)
      .where(
        """
        $SENT_TIMESTAMP = ? AND
        (SELECT ${MmsTable.REMOTE_DELETED} FROM ${MmsTable.TABLE_NAME} WHERE ${MmsTable.ID} = $MESSAGE_ID) = 0
        """.trimIndent(),
        sentTimestamp
      )
      .orderBy(MESSAGE_ID)
      .limit(1)
      .run()
      .use {
        if (it.moveToFirst()) {
          CursorUtil.requireLong(it, MESSAGE_ID)
        } else {
          -1L
        }
      }

    if (firstMessageId == -1L || firstMessageId != messageId) {
      return null
    }

    return getLocalManifest(sentTimestamp)
  }

  /**
   * Applies the given manifest to the local database. This method will:
   *
   * 1. Generate the local manifest
   * 1. Gather the unique collective distribution id set from remote and local manifests
   * 1. Flatten both manifests into a set of Rows
   * 1. For each changed manifest row in remote, update the corresponding row in local
   * 1. For each new manifest row in remote, update the corresponding row in local
   * 1. For each unique message id in local not present in remote, we can assume that the message can be marked deleted.
   */
  fun applySentStoryManifest(remoteManifest: SentStorySyncManifest, sentTimestamp: Long) {
    if (remoteManifest.entries.isEmpty()) {
      return
    }

    writableDatabase.beginTransaction()
    try {
      val localManifest: SentStorySyncManifest = getLocalManifest(sentTimestamp)

      val query = """
        SELECT ${MmsTable.TABLE_NAME}.${MmsTable.ID} as $MESSAGE_ID, ${DistributionListTables.DISTRIBUTION_ID}
        FROM ${MmsTable.TABLE_NAME}
        INNER JOIN ${DistributionListTables.LIST_TABLE_NAME} ON ${DistributionListTables.RECIPIENT_ID} = ${MmsTable.RECIPIENT_ID}
        WHERE ${MmsTable.DATE_SENT} = $sentTimestamp AND ${DistributionListTables.DISTRIBUTION_ID} IS NOT NULL
      """.trimIndent()

      val distributionIdToMessageId = readableDatabase.query(query).use { cursor ->
        val results: MutableMap<DistributionId, Long> = mutableMapOf()

        while (cursor.moveToNext()) {
          val distributionId = DistributionId.from(CursorUtil.requireString(cursor, DistributionListTables.DISTRIBUTION_ID))
          val messageId = CursorUtil.requireLong(cursor, MESSAGE_ID)

          results[distributionId] = messageId
        }

        results
      }

      val localRows: Set<SentStorySyncManifest.Row> = localManifest.flattenToRows(distributionIdToMessageId)
      val remoteRows: Set<SentStorySyncManifest.Row> = remoteManifest.flattenToRows(distributionIdToMessageId)

      if (localRows == remoteRows) {
        return
      }

      val remoteOnly: List<SentStorySyncManifest.Row> = remoteRows.filterNot { localRows.contains(it) }
      val changedInRemoteManifest: List<SentStorySyncManifest.Row> = remoteOnly.filter { (recipientId, messageId) -> localRows.any { it.messageId == messageId && it.recipientId == recipientId } }
      val newInRemoteManifest: List<SentStorySyncManifest.Row> = remoteOnly.filterNot { (recipientId, messageId) -> localRows.any { it.messageId == messageId && it.recipientId == recipientId } }

      changedInRemoteManifest
        .forEach { (recipientId, messageId, allowsReplies, distributionId) ->
          writableDatabase.update(TABLE_NAME)
            .values(
              contentValuesOf(
                ALLOWS_REPLIES to allowsReplies,
                RECIPIENT_ID to recipientId.toLong(),
                SENT_TIMESTAMP to sentTimestamp,
                MESSAGE_ID to messageId,
                DISTRIBUTION_ID to distributionId.toString()
              )
            )
        }

      newInRemoteManifest
        .forEach { (recipientId, messageId, allowsReplies, distributionId) ->
          writableDatabase.insert(
            TABLE_NAME,
            null,
            contentValuesOf(
              ALLOWS_REPLIES to allowsReplies,
              RECIPIENT_ID to recipientId.toLong(),
              SENT_TIMESTAMP to sentTimestamp,
              MESSAGE_ID to messageId,
              DISTRIBUTION_ID to distributionId.toString()
            )
          )
        }

      val messagesWithoutAnyReceivers = localRows.map { it.messageId }.distinct() - remoteRows.map { it.messageId }.distinct()
      messagesWithoutAnyReceivers.forEach {
        SignalDatabase.mms.markAsRemoteDelete(it)
        SignalDatabase.mms.deleteRemotelyDeletedStory(it)
      }

      writableDatabase.setTransactionSuccessful()
    } finally {
      writableDatabase.endTransaction()
    }
  }

  fun getLocalManifest(sentTimestamp: Long): SentStorySyncManifest {
    val entries = readableDatabase.rawQuery(
      // language=sql
      """
        SELECT 
            $RECIPIENT_ID,
            $ALLOWS_REPLIES,
            $DISTRIBUTION_ID,
            ${MmsTable.REMOTE_DELETED}
        FROM $TABLE_NAME
        INNER JOIN ${MmsTable.TABLE_NAME} ON ${MmsTable.TABLE_NAME}.${MmsTable.ID} = $TABLE_NAME.$MESSAGE_ID
        WHERE $TABLE_NAME.$SENT_TIMESTAMP = ?
      """.trimIndent(),
      arrayOf(sentTimestamp)
    ).use { cursor ->
      val results: MutableMap<RecipientId, SentStorySyncManifest.Entry> = mutableMapOf()
      while (cursor.moveToNext()) {
        val isRemoteDeleted = CursorUtil.requireBoolean(cursor, MmsTable.REMOTE_DELETED)
        val recipientId = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID))
        val distributionId = DistributionId.from(CursorUtil.requireString(cursor, DISTRIBUTION_ID))
        val distributionIdList: List<DistributionId> = if (isRemoteDeleted) emptyList() else listOf(distributionId)
        val allowsReplies = CursorUtil.requireBoolean(cursor, ALLOWS_REPLIES)
        val entry = results[recipientId]?.let {
          it.copy(
            allowedToReply = it.allowedToReply or allowsReplies,
            distributionLists = it.distributionLists + distributionIdList
          )
        } ?: SentStorySyncManifest.Entry(recipientId, canReply(recipientId, sentTimestamp), distributionIdList)

        results[recipientId] = entry
      }

      results
    }

    return SentStorySyncManifest(entries.values.toList())
  }
}
