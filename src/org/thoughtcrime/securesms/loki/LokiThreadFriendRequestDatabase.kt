package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper

class LokiThreadFriendRequestDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

    companion object {
        private val tableName = "loki_thread_friend_request_database"
        private val threadID = "thread_id"
        private val friendRequestStatus = "friend_request_status"
        @JvmStatic val createTableCommand = "CREATE TABLE $tableName ($threadID INTEGER PRIMARY KEY, $friendRequestStatus INTEGER DEFAULT 0);"
    }

    fun friendRequestStatus(threadID: Long): LokiThreadFriendRequestStatus {
        val database = databaseHelper.readableDatabase
        val result = database.get(tableName, "${Companion.threadID} = ?", arrayOf( threadID.toString() )) { cursor ->
            cursor.getInt(friendRequestStatus)
        }
        return if (result != null) {
            LokiThreadFriendRequestStatus.values().first { it.rawValue == result }
        } else {
            LokiThreadFriendRequestStatus.NONE
        }
    }

    fun setFriendRequestStatus(threadID: Long, friendRequestStatus: LokiThreadFriendRequestStatus) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.threadID, threadID)
        contentValues.put(Companion.friendRequestStatus, friendRequestStatus.rawValue)
        database.insertOrUpdate(tableName, contentValues, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
        notifyConversationListListeners()
    }
}