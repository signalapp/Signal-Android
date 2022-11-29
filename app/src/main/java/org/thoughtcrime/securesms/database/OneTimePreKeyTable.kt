package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.requireNonNullString
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.thoughtcrime.securesms.util.Base64
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
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $ACCOUNT_ID TEXT NOT NULL,
        $KEY_ID INTEGER UNIQUE, 
        $PUBLIC_KEY TEXT NOT NULL, 
        $PRIVATE_KEY TEXT NOT NULL,
        UNIQUE($ACCOUNT_ID, $KEY_ID)
      )
    """
  }

  fun get(serviceId: ServiceId, keyId: Int): PreKeyRecord? {
    readableDatabase.query(TABLE_NAME, null, "$ACCOUNT_ID = ? AND $KEY_ID = ?", SqlUtil.buildArgs(serviceId, keyId), null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        try {
          val publicKey = Curve.decodePoint(Base64.decode(cursor.requireNonNullString(PUBLIC_KEY)), 0)
          val privateKey = Curve.decodePrivatePoint(Base64.decode(cursor.requireNonNullString(PRIVATE_KEY)))
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
      ACCOUNT_ID to serviceId.toString(),
      KEY_ID to keyId,
      PUBLIC_KEY to Base64.encodeBytes(record.keyPair.publicKey.serialize()),
      PRIVATE_KEY to Base64.encodeBytes(record.keyPair.privateKey.serialize())
    )

    writableDatabase.replace(TABLE_NAME, null, contentValues)
  }

  fun delete(serviceId: ServiceId, keyId: Int) {
    val database = databaseHelper.signalWritableDatabase
    database.delete(TABLE_NAME, "$ACCOUNT_ID = ? AND $KEY_ID = ?", SqlUtil.buildArgs(serviceId, keyId))
  }
}
