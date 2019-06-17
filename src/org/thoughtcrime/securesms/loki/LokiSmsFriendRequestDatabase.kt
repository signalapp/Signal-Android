package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper

/**
 * A database for associating friend request data to Sms
 */
class LokiSmsFriendRequestDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

    companion object {
        private val tableName = "loki_sms_friend_request_database"
        private val smsId = "_id"
        private val isFriendRequest = "is_friend_request"

        @JvmStatic
        val createTableCommand = "CREATE TABLE $tableName ($smsId INTEGER PRIMARY KEY, $isFriendRequest INTEGER DEFAULT 0);"
    }

    fun getIsFriendRequest(messageId: Long): Boolean {
        val database = databaseHelper.readableDatabase
        return database.get(tableName, ID_WHERE, arrayOf(messageId.toString())) { cursor ->
            val rawIsFriendRequest =  cursor.getInt(isFriendRequest)
            rawIsFriendRequest == 1
        } ?: false
    }

    fun setIsFriendRequest(messageId: Long, isFriendRequest: Boolean) {
        val database = databaseHelper.writableDatabase

        val rawIsFriendRequest = if (isFriendRequest) 1 else 0

        val contentValues = ContentValues()
        contentValues.put(smsId, messageId)
        contentValues.put(Companion.isFriendRequest, rawIsFriendRequest)

        database.insertOrUpdate(tableName, contentValues, ID_WHERE, arrayOf(messageId.toString()))
    }
}