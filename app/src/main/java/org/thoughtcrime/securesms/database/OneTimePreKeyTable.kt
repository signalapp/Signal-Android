package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.Base64
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.deleteAll
import org.signal.core.util.logging.Log
import org.signal.core.util.requireNonNullString
import org.signal.core.util.update
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.whispersystems.signalservice.api.push.ServiceId
import java.io.IOException

class OneTimePreKeyTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {
  companion object {
    private val TAG = Log.tag(OneTimePreKeyTable::class.java)

    const val TABLE_NAME = "one_time_prekeys"
    const val ID = "_id"
    const val ACCOUNT_ID = "account_id"
    const val KEY_ID = "key_id"
    const val PUBLIC_KEY = "public_key"
    const val PRIVATE_KEY = "private_key"
    const val STALE_TIMESTAMP = "stale_timestamp"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $ACCOUNT_ID TEXT NOT NULL,
        $KEY_ID INTEGER NOT NULL, 
        $PUBLIC_KEY TEXT NOT NULL, 
        $PRIVATE_KEY TEXT NOT NULL,
        $STALE_TIMESTAMP INTEGER NOT NULL DEFAULT 0,
        UNIQUE($ACCOUNT_ID, $KEY_ID)
      )
    """

    const val PNI_ACCOUNT_ID = "PNI"
  }

  fun get(serviceId: ServiceId, keyId: Int): PreKeyRecord? {
    readableDatabase.query(TABLE_NAME, null, "$ACCOUNT_ID = ? AND $KEY_ID = ?", SqlUtil.buildArgs(serviceId.toAccountId(), keyId), null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        try {
          val publicKey = ECPublicKey(Base64.decode(cursor.requireNonNullString(PUBLIC_KEY)))
          val privateKey = ECPrivateKey(Base64.decode(cursor.requireNonNullString(PRIVATE_KEY)))
          return PreKeyRecord(keyId, ECKeyPair(publicKey, privateKey))
        } catch (e: InvalidKeyException) {
          Log.w(TAG, e)
        } catch (e: IOException) {
          Log.w(TAG, e)
        }
      }
    }

    return null
  }

  fun insert(serviceId: ServiceId, keyId: Int, record: PreKeyRecord) {
    val contentValues = contentValuesOf(
      ACCOUNT_ID to serviceId.toAccountId(),
      KEY_ID to keyId,
      PUBLIC_KEY to Base64.encodeWithPadding(record.keyPair.publicKey.serialize()),
      PRIVATE_KEY to Base64.encodeWithPadding(record.keyPair.privateKey.serialize())
    )

    writableDatabase.replace(TABLE_NAME, null, contentValues)
  }

  fun delete(serviceId: ServiceId, keyId: Int) {
    val database = databaseHelper.signalWritableDatabase
    database.delete(TABLE_NAME, "$ACCOUNT_ID = ? AND $KEY_ID = ?", SqlUtil.buildArgs(serviceId.toAccountId(), keyId))
  }

  fun markAllStaleIfNecessary(serviceId: ServiceId, staleTime: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(STALE_TIMESTAMP to staleTime)
      .where("$ACCOUNT_ID = ? AND $STALE_TIMESTAMP = 0", serviceId.toAccountId())
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
            AND $STALE_TIMESTAMP > 0 
            AND $STALE_TIMESTAMP < $threshold
            AND $ID NOT IN (
              SELECT $ID
              FROM $TABLE_NAME
              WHERE $ACCOUNT_ID = ?
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
    writableDatabase.deleteAll(TABLE_NAME)
  }

  private fun ServiceId.toAccountId(): String {
    return when (this) {
      is ServiceId.ACI -> this.toString()
      is ServiceId.PNI -> PNI_ACCOUNT_ID
    }
  }
}
