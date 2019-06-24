package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.whispersystems.libsignal.state.PreKeyRecord

class LokiPreKeyRecordDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

    companion object {
        private val tableName = "loki_pre_key_record_database"
        private val preKeyID = "pre_key_id"
        private val hexEncodedPublicKey = "public_key"
        @JvmStatic val createTableCommand = "CREATE TABLE $tableName ($preKeyID INTEGER PRIMARY KEY, $hexEncodedPublicKey TEXT);"
    }

    fun hasPreKey(hexEncodedPublicKey: String): Boolean {
        val database = databaseHelper.readableDatabase
        return database.get(tableName, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey )) { it.count > 0 } ?: false
    }

    fun getPreKey(hexEncodedPublicKey: String): PreKeyRecord? {
        val database = databaseHelper.readableDatabase
        return database.get(tableName, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey )) { cursor ->
            val preKeyID = cursor.getInt(preKeyID)
            PreKeyUtil.loadPreKey(context, preKeyID)
        }
    }

    fun getOrCreatePreKey(hexEncodedPublicKey: String): PreKeyRecord {
        return getPreKey(hexEncodedPublicKey) ?: generateAndStorePreKey(hexEncodedPublicKey)
    }

    private fun generateAndStorePreKey(hexEncodedPublicKey: String): PreKeyRecord {
        val preKeyRecords = PreKeyUtil.generatePreKeys(context, 1)
        PreKeyUtil.storePreKeyRecords(context, preKeyRecords)
        val record = preKeyRecords.first()
        val database = databaseHelper.writableDatabase
        val values = ContentValues(2)
        values.put(Companion.hexEncodedPublicKey, hexEncodedPublicKey)
        values.put(preKeyID, record.id)
        database.insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return record
    }
}