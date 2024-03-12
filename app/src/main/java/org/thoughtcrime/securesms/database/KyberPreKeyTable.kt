package org.thoughtcrime.securesms.database

import android.content.Context
import org.signal.core.util.delete
import org.signal.core.util.deleteAll
import org.signal.core.util.exists
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireNonNullBlob
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * A table for storing data related to [org.thoughtcrime.securesms.crypto.storage.SignalKyberPreKeyStore].
 */
class KyberPreKeyTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {
  companion object {
    private val TAG = Log.tag(KyberPreKeyTable::class.java)

    const val TABLE_NAME = "kyber_prekey"
    const val ID = "_id"
    const val ACCOUNT_ID = "account_id"
    const val KEY_ID = "key_id"
    const val TIMESTAMP = "timestamp"
    const val LAST_RESORT = "last_resort"
    const val SERIALIZED = "serialized"
    const val STALE_TIMESTAMP = "stale_timestamp"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $ACCOUNT_ID TEXT NOT NULL,
        $KEY_ID INTEGER NOT NULL, 
        $TIMESTAMP INTEGER NOT NULL,
        $LAST_RESORT INTEGER NOT NULL,
        $SERIALIZED BLOB NOT NULL,
        $STALE_TIMESTAMP INTEGER NOT NULL DEFAULT 0,
        UNIQUE($ACCOUNT_ID, $KEY_ID)
    )
    """

    private const val INDEX_ACCOUNT_KEY = "kyber_account_id_key_id"

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX IF NOT EXISTS $INDEX_ACCOUNT_KEY ON $TABLE_NAME ($ACCOUNT_ID, $KEY_ID, $LAST_RESORT, $SERIALIZED)"
    )

    const val PNI_ACCOUNT_ID = "PNI"
  }

  fun get(serviceId: ServiceId, keyId: Int): KyberPreKey? {
    return readableDatabase
      .select(LAST_RESORT, SERIALIZED)
      .from("$TABLE_NAME INDEXED BY $INDEX_ACCOUNT_KEY")
      .where("$ACCOUNT_ID = ? AND $KEY_ID = ?", serviceId.toAccountId(), keyId)
      .run()
      .readToSingleObject { cursor ->
        KyberPreKey(
          record = KyberPreKeyRecord(cursor.requireNonNullBlob(SERIALIZED)),
          lastResort = cursor.requireBoolean(LAST_RESORT)
        )
      }
  }

  fun getAll(serviceId: ServiceId): List<KyberPreKey> {
    return readableDatabase
      .select(LAST_RESORT, SERIALIZED)
      .from("$TABLE_NAME INDEXED BY $INDEX_ACCOUNT_KEY")
      .where("$ACCOUNT_ID = ?", serviceId.toAccountId())
      .run()
      .readToList { cursor ->
        KyberPreKey(
          record = KyberPreKeyRecord(cursor.requireNonNullBlob(SERIALIZED)),
          lastResort = cursor.requireBoolean(LAST_RESORT)
        )
      }
  }

  fun getAllLastResort(serviceId: ServiceId): List<KyberPreKey> {
    return readableDatabase
      .select(LAST_RESORT, SERIALIZED)
      .from("$TABLE_NAME INDEXED BY $INDEX_ACCOUNT_KEY")
      .where("$ACCOUNT_ID = ? AND $LAST_RESORT = ?", serviceId.toAccountId(), 1)
      .run()
      .readToList { cursor ->
        KyberPreKey(
          record = KyberPreKeyRecord(cursor.requireNonNullBlob(SERIALIZED)),
          lastResort = cursor.requireBoolean(LAST_RESORT)
        )
      }
  }

  fun contains(serviceId: ServiceId, keyId: Int): Boolean {
    return readableDatabase
      .exists("$TABLE_NAME INDEXED BY $INDEX_ACCOUNT_KEY")
      .where("$ACCOUNT_ID = ? AND $KEY_ID = ?", serviceId.toAccountId(), keyId)
      .run()
  }

  fun insert(serviceId: ServiceId, keyId: Int, record: KyberPreKeyRecord, lastResort: Boolean) {
    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        ACCOUNT_ID to serviceId.toAccountId(),
        KEY_ID to keyId,
        TIMESTAMP to record.timestamp,
        SERIALIZED to record.serialize(),
        LAST_RESORT to lastResort.toInt()
      )
      .run(SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun deleteIfNotLastResort(serviceId: ServiceId, keyId: Int) {
    writableDatabase
      .delete("$TABLE_NAME INDEXED BY $INDEX_ACCOUNT_KEY")
      .where("$ACCOUNT_ID = ? AND $KEY_ID = ? AND $LAST_RESORT = ?", serviceId.toAccountId(), keyId, 0)
      .run()
  }

  fun delete(serviceId: ServiceId, keyId: Int) {
    writableDatabase
      .delete("$TABLE_NAME INDEXED BY $INDEX_ACCOUNT_KEY")
      .where("$ACCOUNT_ID = ? AND $KEY_ID = ?", serviceId.toAccountId(), keyId)
      .run()
  }

  fun markAllStaleIfNecessary(serviceId: ServiceId, staleTime: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(STALE_TIMESTAMP to staleTime)
      .where("$ACCOUNT_ID = ? AND $STALE_TIMESTAMP = 0 AND $LAST_RESORT = 0", serviceId.toAccountId())
      .run()
  }

  /**
   * Deletes all keys that have been stale since before the specified threshold.
   * We will always keep at least [minCount] items, preferring more recent ones.
   */
  fun deleteAllStaleBefore(serviceId: ServiceId, threshold: Long, minCount: Int) {
    val count = writableDatabase
      .delete(TABLE_NAME)
      .where(
        """
          $ACCOUNT_ID = ? 
            AND $LAST_RESORT = 0
            AND $STALE_TIMESTAMP > 0 
            AND $STALE_TIMESTAMP < $threshold
            AND $ID NOT IN (
              SELECT $ID
              FROM $TABLE_NAME
              WHERE 
                $ACCOUNT_ID = ?
                AND $LAST_RESORT = 0
              ORDER BY 
                CASE $STALE_TIMESTAMP WHEN 0 THEN 1 ELSE 0 END DESC,
                $STALE_TIMESTAMP DESC,
                $ID DESC
              LIMIT $minCount
            )
        """,
        serviceId.toAccountId(),
        serviceId.toAccountId()
      )
      .run()

    Log.i(TAG, "Deleted $count stale one-time EC prekeys.")
  }

  fun debugDeleteAll() {
    writableDatabase.deleteAll(OneTimePreKeyTable.TABLE_NAME)
  }

  data class KyberPreKey(
    val record: KyberPreKeyRecord,
    val lastResort: Boolean
  )

  private fun ServiceId.toAccountId(): String {
    return when (this) {
      is ServiceId.ACI -> this.toString()
      is ServiceId.PNI -> PNI_ACCOUNT_ID
    }
  }
}
