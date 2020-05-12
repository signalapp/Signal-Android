package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.utilities.get
import org.thoughtcrime.securesms.loki.utilities.getInt
import org.thoughtcrime.securesms.loki.utilities.insertOrUpdate
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.signalservice.loki.database.LokiPreKeyRecordDatabaseProtocol

class LokiPreKeyRecordDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiPreKeyRecordDatabaseProtocol {

    companion object {
        private val tableName = "loki_pre_key_record_database"
        private val hexEncodedPublicKey = "public_key"
        private val preKeyID = "pre_key_id"
        @JvmStatic val createTableCommand = "CREATE TABLE $tableName ($hexEncodedPublicKey TEXT PRIMARY KEY, $preKeyID INTEGER);"
    }

    fun hasPreKey(hexEncodedPublicKey: String): Boolean {
        val database = databaseHelper.readableDatabase
        return database.get(tableName, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey )) { it.count > 0 } ?: false
    }

    override fun getPreKeyRecord(hexEncodedPublicKey: String): PreKeyRecord? {
        val database = databaseHelper.readableDatabase
        return database.get(tableName, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey )) { cursor ->
            val preKeyID = cursor.getInt(preKeyID)
            PreKeyUtil.loadPreKey(context, preKeyID)
        }
    }

    fun getOrCreatePreKeyRecord(hexEncodedPublicKey: String): PreKeyRecord {
        return getPreKeyRecord(hexEncodedPublicKey) ?: generateAndStorePreKeyRecord(hexEncodedPublicKey)
    }

    private fun generateAndStorePreKeyRecord(hexEncodedPublicKey: String): PreKeyRecord {
        val records = PreKeyUtil.generatePreKeyRecords(context, 1)
        PreKeyUtil.storePreKeyRecords(context, records)
        val record = records.first()
        val database = databaseHelper.writableDatabase
        val values = ContentValues(2)
        values.put(Companion.hexEncodedPublicKey, hexEncodedPublicKey)
        values.put(preKeyID, record.id)
        database.insertOrUpdate(tableName, values, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey ))
        return record
    }
}