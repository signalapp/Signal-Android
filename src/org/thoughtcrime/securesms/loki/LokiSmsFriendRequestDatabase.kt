package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.whispersystems.libsignal.state.PreKeyRecord

/**
 * A database for associating friend request data to Sms objects
 */
class LokiSmsFriendRequestDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

    companion object {
        private val tableName = "loki_sms_friend_request_database"
        private val smsId = "_id"
        private val isFriendRequest = "is_friend_request"

        @JvmStatic
        val createTableCommand = "CREATE TABLE $tableName ($smsId INTEGER PRIMARY KEY, $isFriendRequest INTEGER DEFAULT 0);"
    }

    fun setIsFriendRequest(messageId: Long, isFriendRequest: Boolean) {
        val database = databaseHelper.writableDatabase

        val rawIsFriendRequest = if (isFriendRequest) 1 else 0

        val values = ContentValues()
        values.put(smsId, messageId)
        values.put(Companion.isFriendRequest, rawIsFriendRequest)

        // Note: If we add any other fields, then `SQLiteDatabase.CONFLICT_REPLACE` will most likely overwrite them
        // we probably want to switch to `database.update` later, for now since we only have 1 field, it is fine
        database.insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getIsFriendRequest(messageId: Long): Boolean {
        val database = databaseHelper.readableDatabase
        return database.get(tableName, ID_WHERE, arrayOf(messageId.toString())) { cursor ->
            val rawIsFriendRequest =  cursor.getInt(isFriendRequest)
            rawIsFriendRequest == 1
        } ?: false
    }
}