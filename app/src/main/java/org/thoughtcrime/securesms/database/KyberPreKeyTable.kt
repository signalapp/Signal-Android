package org.thoughtcrime.securesms.database

import android.content.Context
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.insertInto
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireNonNullBlob
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * A table for storing data related to [org.thoughtcrime.securesms.crypto.storage.SignalKyberPreKeyStore].
 */
class KyberPreKeyTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {
  companion object {
    const val TABLE_NAME = "kyber_prekey"
    const val ID = "_id"
    const val ACCOUNT_ID = "account_id"
    const val KEY_ID = "key_id"
    const val TIMESTAMP = "timestamp"
    const val LAST_RESORT = "last_resort"
    const val SERIALIZED = "serialized"
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $ACCOUNT_ID TEXT NOT NULL,
        $KEY_ID INTEGER UNIQUE NOT NULL, 
        $TIMESTAMP INTEGER NOT NULL,
        $LAST_RESORT INTEGER NOT NULL,
        $SERIALIZED BLOB NOT NULL,
        UNIQUE($ACCOUNT_ID, $KEY_ID)
    )
    """

    private const val INDEX_ACCOUNT_KEY = "kyber_account_id_key_id"

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX IF NOT EXISTS $INDEX_ACCOUNT_KEY ON $TABLE_NAME ($ACCOUNT_ID, $KEY_ID, $LAST_RESORT, $SERIALIZED)"
    )
  }

  fun get(serviceId: ServiceId, keyId: Int): KyberPreKey? {
    return readableDatabase
      .select(LAST_RESORT, SERIALIZED)
      .from("$TABLE_NAME INDEXED BY $INDEX_ACCOUNT_KEY")
      .where("$ACCOUNT_ID = ? AND $KEY_ID = ?", serviceId, keyId)
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
      .where("$ACCOUNT_ID = ?", serviceId)
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
      .where("$ACCOUNT_ID = ? AND $LAST_RESORT = ?", serviceId, 1)
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
      .where("$ACCOUNT_ID = ? AND $KEY_ID = ?", serviceId, keyId)
      .run()
  }

  fun insert(serviceId: ServiceId, keyId: Int, record: KyberPreKeyRecord, lastResort: Boolean) {
    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        ACCOUNT_ID to serviceId.toString(),
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
      .where("$ACCOUNT_ID = ? AND $KEY_ID = ? AND $LAST_RESORT = ?", serviceId, keyId, 0)
      .run()
  }

  fun delete(serviceId: ServiceId, keyId: Int) {
    writableDatabase
      .delete("$TABLE_NAME INDEXED BY $INDEX_ACCOUNT_KEY")
      .where("$ACCOUNT_ID = ? AND $KEY_ID = ?", serviceId, keyId)
      .run()
  }

  data class KyberPreKey(
    val record: KyberPreKeyRecord,
    val lastResort: Boolean
  )
}
