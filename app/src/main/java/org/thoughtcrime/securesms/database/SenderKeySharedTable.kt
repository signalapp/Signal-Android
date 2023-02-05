package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.readToSet
import org.signal.core.util.requireInt
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.withinTransaction
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.DistributionId

/**
 * Keeps track of which recipients are aware of which distributionIds. For the storage of sender
 * keys themselves, see [SenderKeyTable].
 */
class SenderKeySharedTable internal constructor(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper) {
  companion object {
    private val TAG = Log.tag(SenderKeySharedTable::class.java)
    const val TABLE_NAME = "sender_key_shared"
    private const val ID = "_id"
    const val DISTRIBUTION_ID = "distribution_id"
    const val ADDRESS = "address"
    const val DEVICE = "device"
    const val TIMESTAMP = "timestamp"
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
        $DISTRIBUTION_ID TEXT NOT NULL, 
        $ADDRESS TEXT NOT NULL, 
        $DEVICE INTEGER NOT NULL, 
        $TIMESTAMP INTEGER DEFAULT 0, 
        UNIQUE($DISTRIBUTION_ID,$ADDRESS, $DEVICE) ON CONFLICT REPLACE
      )
    """
  }

/**
   * Mark that a distributionId has been shared with the provided recipients
   */
  fun markAsShared(distributionId: DistributionId, addresses: Collection<SignalProtocolAddress>) {
    writableDatabase.withinTransaction { db ->
      for (address in addresses) {
        val values = contentValuesOf(
          ADDRESS to address.name,
          DEVICE to address.deviceId,
          DISTRIBUTION_ID to distributionId.toString(),
          TIMESTAMP to System.currentTimeMillis(),
        )
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
      }
    }
  }

  /**
   * Get the set of recipientIds that know about the distributionId in question.
   */
  fun getSharedWith(distributionId: DistributionId): Set<SignalProtocolAddress> {
    return readableDatabase
      .select(ADDRESS, DEVICE)
      .from(TABLE_NAME)
      .where("$DISTRIBUTION_ID = ?", distributionId)
      .run()
      .readToSet { cursor ->
        SignalProtocolAddress(
          cursor.requireString(ADDRESS),
          cursor.requireInt(DEVICE)
        )
      }
  }

  /**
   * Clear the shared statuses for all provided addresses.
   */
  fun delete(distributionId: DistributionId, addresses: Collection<SignalProtocolAddress>) {
    writableDatabase.withinTransaction { db ->
      for (address in addresses) {
        db.delete(TABLE_NAME)
          .where("$DISTRIBUTION_ID = ? AND $ADDRESS = ? AND $DEVICE = ?", distributionId, address.name, address.deviceId)
          .run()
      }
    }
  }

  /**
   * Clear all shared statuses for a given distributionId.
   */
  fun deleteAllFor(distributionId: DistributionId) {
    writableDatabase
      .delete(TABLE_NAME)
      .where("$DISTRIBUTION_ID = ?", distributionId)
      .run()
  }

  /**
   * Clear the shared status for all distributionIds for a set of addresses.
   */
  fun deleteAllFor(addresses: Collection<SignalProtocolAddress>) {
    writableDatabase.withinTransaction { db ->
      for (address in addresses) {
        db.delete(TABLE_NAME)
          .where("$ADDRESS = ? AND $DEVICE = ?", address.name, address.deviceId)
          .run()
      }
    }
  }

  /**
   * Clear the shared status for all distributionIds for a given recipientId.
   */
  fun deleteAllFor(recipientId: RecipientId) {
    val recipient = Recipient.resolved(recipientId)
    if (recipient.hasServiceId()) {
      writableDatabase
        .delete(TABLE_NAME)
        .where("$ADDRESS = ?", recipient.requireServiceId().toString())
        .run()
    } else {
      Log.w(TAG, "Recipient doesn't have a UUID! $recipientId")
    }
  }

  /**
   * Clears all database content.
   */
  fun deleteAll() {
    writableDatabase
      .delete(TABLE_NAME)
      .run()
  }

  /**
   * Gets the shared state of all of our sender keys. Used for debugging.
   */
  fun getAllSharedWithCursor(): Cursor {
    return readableDatabase.query(TABLE_NAME, null, null, null, null, null, "$DISTRIBUTION_ID, $ADDRESS, $DEVICE")
  }
}
