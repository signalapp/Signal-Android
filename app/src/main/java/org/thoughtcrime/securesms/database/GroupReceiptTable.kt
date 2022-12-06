package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
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
    private const val STATUS = "status"
    private const val TIMESTAMP = "timestamp"
    private const val UNIDENTIFIED = "unidentified"
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
    for (statement in statements) {
      writableDatabase.execSQL(statement.where, statement.whereArgs)
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
    writableDatabase.withinTransaction { db ->
      for (result in results) {
        db.update(TABLE_NAME)
          .values(UNIDENTIFIED to if (result.second()) 1 else 0)
          .where("$MMS_ID = ? AND $RECIPIENT_ID = ?", mmsId.toString(), result.first().serialize())
          .run()
      }
    }
  }

  fun setSkipped(recipients: Collection<RecipientId>, mmsId: Long) {
    writableDatabase.withinTransaction { db ->
      for (recipient in recipients) {
        db.update(TABLE_NAME)
          .values(STATUS to STATUS_SKIPPED)
          .where("$MMS_ID = ? AND $RECIPIENT_ID = ?", mmsId.toString(), recipient.serialize())
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
      .readToList { cursor ->
        GroupReceiptInfo(
          recipientId = RecipientId.from(cursor.requireLong(RECIPIENT_ID)),
          status = cursor.requireInt(STATUS),
          timestamp = cursor.requireLong(TIMESTAMP),
          isUnidentified = cursor.requireBoolean(UNIDENTIFIED)
        )
      }
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
      .where("$MMS_ID NOT IN (SELECT ${MmsTable.ID} FROM ${MmsTable.TABLE_NAME})")
      .run()
  }

  fun deleteAllRows() {
    writableDatabase.delete(TABLE_NAME).run()
  }

  override fun remapRecipient(fromId: RecipientId, toId: RecipientId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(RECIPIENT_ID to toId.serialize())
      .where("$RECIPIENT_ID = ?", fromId)
      .run()
  }

  data class GroupReceiptInfo(
    val recipientId: RecipientId,
    val status: Int,
    val timestamp: Long,
    val isUnidentified: Boolean
  )
}
