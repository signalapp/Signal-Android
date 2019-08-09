package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.whispersystems.signalservice.loki.messaging.LokiMessageDatabaseProtocol
import org.whispersystems.signalservice.loki.messaging.LokiMessageFriendRequestStatus

class LokiMessageDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiMessageDatabaseProtocol {

    companion object {
        private val tableName = "loki_message_friend_request_database"
        private val messageID = "message_id"
        private val serverID = "server_id"
        private val friendRequestStatus = "friend_request_status"
        @JvmStatic val createTableCommand = "CREATE TABLE $tableName ($messageID INTEGER PRIMARY KEY, $serverID INTEGER DEFAULT 0, $friendRequestStatus INTEGER DEFAULT 0);"
    }

    override fun setServerID(messageID: Long, serverID: Long) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.serverID, serverID)
        database.insertOrUpdate(tableName, contentValues, "${Companion.messageID} = ?", arrayOf( messageID.toString() ))
    }

    override fun getFriendRequestStatus(messageID: Long): LokiMessageFriendRequestStatus {
        val database = databaseHelper.readableDatabase
        val result = database.get(tableName, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
            cursor.getInt(friendRequestStatus)
        }
        return if (result != null) {
            LokiMessageFriendRequestStatus.values().first { it.rawValue == result }
        } else {
            LokiMessageFriendRequestStatus.NONE
        }
    }

    override fun setFriendRequestStatus(messageID: Long, friendRequestStatus: LokiMessageFriendRequestStatus) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.friendRequestStatus, friendRequestStatus.rawValue)
        database.insertOrUpdate(tableName, contentValues, "${Companion.messageID} = ?", arrayOf( messageID.toString() ))
        val threadID = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageID)
        notifyConversationListeners(threadID)
    }

    fun isFriendRequest(messageID: Long): Boolean {
        return getFriendRequestStatus(messageID) != LokiMessageFriendRequestStatus.NONE
    }
}