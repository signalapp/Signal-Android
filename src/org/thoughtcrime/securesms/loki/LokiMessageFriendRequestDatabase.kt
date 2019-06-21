package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper

class LokiMessageFriendRequestDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

    companion object {
        private val tableName = "loki_sms_friend_request_database"
        private val messageID = "message_id"
        private val isFriendRequest = "is_friend_request"
        @JvmStatic val createTableCommand = "CREATE TABLE $tableName ($messageID INTEGER PRIMARY KEY, $isFriendRequest INTEGER DEFAULT 0);"
    }

    fun getIsFriendRequest(messageID: Long): Boolean {
        val database = databaseHelper.readableDatabase
        return database.get(tableName, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
            val rawIsFriendRequest = cursor.getInt(isFriendRequest)
            rawIsFriendRequest == 1
        } ?: false
    }

    fun setIsFriendRequest(messageID: Long, isFriendRequest: Boolean) {
        val database = databaseHelper.writableDatabase
        val rawIsFriendRequest = if (isFriendRequest) 1 else 0
        val contentValues = ContentValues()
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.isFriendRequest, rawIsFriendRequest)
        database.insertOrUpdate(tableName, contentValues, "${Companion.messageID} = ?", arrayOf( messageID.toString() ))
    }
}