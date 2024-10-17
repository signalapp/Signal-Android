package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.deleteAll
import org.signal.core.util.forEach
import org.signal.core.util.readToList
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.libsignal.protocol.util.Pair
import org.thoughtcrime.securesms.recipients.RecipientId

class GroupReceiptTable(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {
  companion object {
    const val TABLE_NAME = "group_receipts"
    private const val ID = "_id"
    const val MMS_ID = "mms_id"
    const val RECIPIENT_ID = "address"
    const val STATUS = "status"
    const val TIMESTAMP = "timestamp"
    const val UNIDENTIFIED = "unidentified"
    const val STATUS_UNKNOWN = -1
    const val STATUS_UNDELIVERED = 0
    const val STATUS_DELIVERED = 1
    const val STATUS_READ = 2
    const val STATUS_VIEWED = 3
    const val STATUS_SKIPPED = 4

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY, 
        $MMS_ID INTEGER, 
        $RECIPIENT_ID INTEGER, 
        $STATUS INTEGER, 
        $TIMESTAMP INTEGER, 
        $UNIDENTIFIED INTEGER DEFAULT 0
      )
    """

    @JvmField
    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX IF NOT EXISTS group_receipt_mms_id_index ON $TABLE_NAME ($MMS_ID);"
    )
  }

  fun insert(recipientIds: Collection<RecipientId>, mmsId: Long, status: Int, timestamp: Long) {
    val contentValues: List<ContentValues> = recipientIds.map { recipientId ->
      contentValuesOf(
        MMS_ID to mmsId,
        RECIPIENT_ID to recipientId.serialize(),
        STATUS to status,
        TIMESTAMP to timestamp
      )
    }

    val statements = SqlUtil.buildBulkInsert(TABLE_NAME, arrayOf(MMS_ID, RECIPIENT_ID, STATUS, TIMESTAMP), contentValues)
    writableDatabase.withinTransaction { db ->
      statements.forEach { db.execSQL(it.where, it.whereArgs) }
    }
  }

  fun update(recipientId: RecipientId, mmsId: Long, status: Int, timestamp: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        STATUS to status,
        TIMESTAMP to timestamp
      )
      .where("$MMS_ID = ? AND $RECIPIENT_ID = ? AND $STATUS < ?", mmsId.toString(), recipientId.serialize(), status.toString())
      .run()
  }

  fun setUnidentified(results: Collection<Pair<RecipientId, Boolean>>, mmsId: Long) {
    val mmsMatchPrefix = "$MMS_ID = $mmsId AND"
    val unidentifiedQueries = SqlUtil.buildCollectionQuery(
      column = RECIPIENT_ID,
      values = results.filter { it.second() }.map { it.first().serialize() },
      prefix = mmsMatchPrefix
    )
    val identifiedQueries = SqlUtil.buildCollectionQuery(
      column = RECIPIENT_ID,
      values = results.filterNot { it.second() }.map { it.first().serialize() },
      prefix = mmsMatchPrefix
    )
    writableDatabase.withinTransaction { db ->
      unidentifiedQueries.forEach {
        db.update(TABLE_NAME)
          .values(UNIDENTIFIED to 1)
          .where(it.where, it.whereArgs)
          .run()
      }

      identifiedQueries.forEach {
        db.update(TABLE_NAME)
          .values(UNIDENTIFIED to 0)
          .where(it.where, it.whereArgs)
          .run()
      }
    }
  }

  fun setSkipped(recipients: Collection<RecipientId>, mmsId: Long) {
    val mmsMatchPrefix = "$MMS_ID = $mmsId AND"
    val queries = SqlUtil.buildCollectionQuery(
      column = RECIPIENT_ID,
      values = recipients.map { it.serialize() },
      prefix = mmsMatchPrefix
    )
    writableDatabase.withinTransaction { db ->
      queries.forEach {
        db.update(TABLE_NAME)
          .values(STATUS to STATUS_SKIPPED)
          .where(it.where, it.whereArgs)
          .run()
      }
    }
  }

  fun getGroupReceiptInfo(mmsId: Long): List<GroupReceiptInfo> {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$MMS_ID = ?", mmsId)
      .run()
      .readToList { it.toGroupReceiptInfo() }
  }

  fun getGroupReceiptInfoForMessages(ids: Collection<Long>): Map<Long, List<GroupReceiptInfo>> {
    if (ids.isEmpty()) {
      return emptyMap()
    }

    val messageIdsToGroupReceipts: MutableMap<Long, MutableList<GroupReceiptInfo>> = mutableMapOf()

    val args: List<Array<String>> = ids.map { SqlUtil.buildArgs(it) }

    SqlUtil.buildCustomCollectionQuery("$MMS_ID = ?", args).forEach { query ->
      readableDatabase
        .select()
        .from(TABLE_NAME)
        .where(query.where, query.whereArgs)
        .run()
        .forEach { cursor ->
          val messageId = cursor.requireLong(MMS_ID)
          val receipts = messageIdsToGroupReceipts.getOrPut(messageId) { mutableListOf() }
          receipts += cursor.toGroupReceiptInfo()
        }
    }

    return messageIdsToGroupReceipts
  }

  fun deleteRowsForMessage(mmsId: Long) {
    writableDatabase
      .delete(TABLE_NAME)
      .where("$MMS_ID = ?", mmsId)
      .run()
  }

  fun deleteAbandonedRows() {
    writableDatabase
      .delete(TABLE_NAME)
      .where("$MMS_ID NOT IN (SELECT ${MessageTable.ID} FROM ${MessageTable.TABLE_NAME})")
      .run()
  }

  fun deleteAllRows() {
    writableDatabase.deleteAll(TABLE_NAME)
  }

  override fun remapRecipient(fromId: RecipientId, toId: RecipientId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(RECIPIENT_ID to toId.serialize())
      .where("$RECIPIENT_ID = ?", fromId)
      .run()
  }

  private fun Cursor.toGroupReceiptInfo(): GroupReceiptInfo {
    return GroupReceiptInfo(
      recipientId = RecipientId.from(this.requireLong(RECIPIENT_ID)),
      status = this.requireInt(STATUS),
      timestamp = this.requireLong(TIMESTAMP),
      isUnidentified = this.requireBoolean(UNIDENTIFIED)
    )
  }

  data class GroupReceiptInfo(
    val recipientId: RecipientId,
    val status: Int,
    val timestamp: Long,
    val isUnidentified: Boolean
  )
}
