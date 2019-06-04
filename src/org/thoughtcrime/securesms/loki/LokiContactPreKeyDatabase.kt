package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.whispersystems.libsignal.state.PreKeyRecord

/**
 * A database for associating pre key records to contact public keys.
 */
class LokiContactPreKeyDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

    companion object {
        private val tableName = "loki_contact_pre_key_database"
        private val preKeyId = "pre_key_id"
        private val pubKey = "pub_key"

        @JvmStatic
        val createTableCommand = "CREATE TABLE $tableName ($preKeyId INTEGER PRIMARY KEY, $pubKey TEXT);"
    }

    fun hasPreKey(pubKey: String): Boolean {
        val database = databaseHelper.readableDatabase
        return database.get(tableName, "${Companion.pubKey} = ?", arrayOf(pubKey)) { cursor ->
            cursor.count > 0
        } ?: false
    }

    fun getPreKey(pubKey: String): PreKeyRecord? {
        val database = databaseHelper.readableDatabase
        return database.get(tableName, "${Companion.pubKey} = ?", arrayOf(pubKey)) { cursor ->
            val preKeyId = cursor.getInt(cursor.getColumnIndexOrThrow(preKeyId))
            PreKeyUtil.loadPreKey(context, preKeyId)
        }
    }

    fun getOrCreatePreKey(pubKey: String): PreKeyRecord {
        return getPreKey(pubKey) ?: generateAndStorePreKey(pubKey)
    }

    private fun generateAndStorePreKey(pubKey: String): PreKeyRecord {
        val records = PreKeyUtil.generatePreKeys(context, 1)
        PreKeyUtil.storePreKeyRecords(context, records)

        val record = records.first()
        val database = databaseHelper.writableDatabase

        val values = ContentValues()
        values.put(Companion.pubKey, pubKey)
        values.put(preKeyId, record.id)

        database.insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return record
    }
}