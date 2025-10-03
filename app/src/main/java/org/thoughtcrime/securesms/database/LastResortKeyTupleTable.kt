/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.ecc.ECPublicKey

/**
 * Stores a Tuple of (kyberPreKeyId, signedPreKeyId, baseKey) for each
 * last-resort key when [KyberPreKeyStore#markKyberPreKeyUsed] is called.
 *
 * Entries in this table are unique and an error will be thrown on trying to insert a duplicate.
 */
class LastResortKeyTupleTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(LastResortKeyTupleTable::class)

    private const val TABLE_NAME = "last_resort_key_tuple"

    private const val ID = "_id"
    private const val KYBER_PREKEY = "kyber_prekey_id"
    private const val SIGNED_KEY_ID = "signed_key_id"
    private const val PUBLIC_KEY = "public_key"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $KYBER_PREKEY INTEGER NOT NULL REFERENCES ${KyberPreKeyTable.TABLE_NAME} (${KyberPreKeyTable.ID}) ON DELETE CASCADE,
        $SIGNED_KEY_ID INTEGER NOT NULL,
        $PUBLIC_KEY BLOB NOT NULL,
        UNIQUE($KYBER_PREKEY, $SIGNED_KEY_ID, $PUBLIC_KEY)
      )
    """
  }

  /**
   * Inserts the Last-resort tuple. If it already exists, we will throw a [ReusedBaseKeyException]
   */
  @Throws(ReusedBaseKeyException::class)
  fun insert(kyberPreKeyRowId: Int, signedKeyId: Int, publicKey: ECPublicKey) {
    try {
      writableDatabase.insertInto(TABLE_NAME)
        .values(
          KYBER_PREKEY to kyberPreKeyRowId,
          SIGNED_KEY_ID to signedKeyId,
          PUBLIC_KEY to publicKey.serialize()
        )
        .run(conflictStrategy = SQLiteDatabase.CONFLICT_ABORT)
    } catch (e: SQLiteConstraintException) {
      Log.w(TAG, "Found duplicate use of last-resort kyber key set.", e)
      throw ReusedBaseKeyException(e)
    }
  }
}
