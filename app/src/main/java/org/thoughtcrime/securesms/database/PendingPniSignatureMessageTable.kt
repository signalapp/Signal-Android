package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.logging.Log
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.FeatureFlags
import org.whispersystems.signalservice.api.messages.SendMessageResult

/**
 * Contains records of messages that have been sent with PniSignatures on them.
 * When we receive delivery receipts for these messages, we remove entries from the table and can clear
 * the `needsPniSignature` flag on the recipient when all are delivered.
 */
class PendingPniSignatureMessageTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(PendingPniSignatureMessageTable::class.java)

    const val TABLE_NAME = "pending_pni_signature_message"

    private const val ID = "_id"
    private const val RECIPIENT_ID = "recipient_id"
    private const val SENT_TIMESTAMP = "sent_timestamp"
    private const val DEVICE_ID = "device_id"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $RECIPIENT_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $SENT_TIMESTAMP INTEGER NOT NULL,
        $DEVICE_ID INTEGER NOT NULL
      )
     """

    val CREATE_INDEXES = arrayOf(
      "CREATE UNIQUE INDEX pending_pni_recipient_sent_device_index ON $TABLE_NAME ($RECIPIENT_ID, $SENT_TIMESTAMP, $DEVICE_ID)"
    )
  }

  fun insertIfNecessary(recipientId: RecipientId, sentTimestamp: Long, result: SendMessageResult) {
    if (!FeatureFlags.phoneNumberPrivacy()) return

    if (!result.isSuccess) {
      return
    }

    writableDatabase.withinTransaction { db ->
      for (deviceId in result.success.devices) {
        val values = contentValuesOf(
          RECIPIENT_ID to recipientId.serialize(),
          SENT_TIMESTAMP to sentTimestamp,
          DEVICE_ID to deviceId
        )

        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE)
      }
    }
  }

  fun acknowledgeReceipts(recipientId: RecipientId, sentTimestamps: Collection<Long>, deviceId: Int) {
    if (!FeatureFlags.phoneNumberPrivacy()) return

    writableDatabase.withinTransaction { db ->
      val count = db
        .delete(TABLE_NAME)
        .where("$RECIPIENT_ID = ? AND $SENT_TIMESTAMP IN (?) AND $DEVICE_ID = ?", recipientId, sentTimestamps.joinToString(separator = ","), deviceId)
        .run()

      if (count <= 0) {
        return@withinTransaction
      }

      val stillPending: Boolean = db.exists(TABLE_NAME, "$RECIPIENT_ID = ? AND $SENT_TIMESTAMP = ?", recipientId, sentTimestamps)

      if (!stillPending) {
        Log.i(TAG, "All devices for ($recipientId, $sentTimestamps) have acked the PNI signature message. Clearing flag and removing any other pending receipts.")
        SignalDatabase.recipients.clearNeedsPniSignature(recipientId)

        db
          .delete(TABLE_NAME)
          .where("$RECIPIENT_ID = ?", recipientId)
          .run()
      }
    }
  }

  /**
   * Deletes all record of pending PNI verification messages. Should only be called after the user changes their number.
   */
  fun deleteAll() {
    if (!FeatureFlags.phoneNumberPrivacy()) return
    writableDatabase.delete(TABLE_NAME).run()
  }

  override fun remapRecipient(oldId: RecipientId, newId: RecipientId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(RECIPIENT_ID to newId.serialize())
      .where("$RECIPIENT_ID = ?", oldId)
      .run()
  }
}
