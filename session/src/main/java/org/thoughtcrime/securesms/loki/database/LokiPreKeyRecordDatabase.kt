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
        private val table = "loki_pre_key_record_database"
        private val publicKey = "public_key"
        private val preKeyID = "pre_key_id"
        @JvmStatic val createTableCommand = "CREATE TABLE $table ($publicKey TEXT PRIMARY KEY, $preKeyID INTEGER);"
    }

    fun hasPreKey(publicKey: String): Boolean {
        val database = databaseHelper.readableDatabase
        return database.get(table, "${Companion.publicKey} = ?", arrayOf( publicKey )) { it.count > 0 } ?: false
    }

    override fun getPreKeyRecord(publicKey: String): PreKeyRecord? {
        val database = databaseHelper.readableDatabase
        return database.get(table, "${Companion.publicKey} = ?", arrayOf( publicKey )) { cursor ->
            val preKeyID = cursor.getInt(preKeyID)
            PreKeyUtil.loadPreKey(context, preKeyID)
        }
    }

    fun getOrCreatePreKeyRecord(publicKey: String): PreKeyRecord {
        return getPreKeyRecord(publicKey) ?: generateAndStorePreKeyRecord(publicKey)
    }

    private fun generateAndStorePreKeyRecord(publicKey: String): PreKeyRecord {
        val records = PreKeyUtil.generatePreKeyRecords(context, 1)
        PreKeyUtil.storePreKeyRecords(context, records)
        val record = records.first()
        val database = databaseHelper.writableDatabase
        val values = ContentValues(2)
        values.put(Companion.publicKey, publicKey)
        values.put(preKeyID, record.id)
        database.insertOrUpdate(table, values, "${Companion.publicKey} = ?", arrayOf( publicKey ))
        return record
    }
}