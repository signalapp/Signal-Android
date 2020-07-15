package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.utilities.get
import org.thoughtcrime.securesms.loki.utilities.getInt
import org.thoughtcrime.securesms.loki.utilities.getString
import org.thoughtcrime.securesms.loki.utilities.insertOrUpdate
import org.whispersystems.signalservice.loki.database.LokiMessageDatabaseProtocol
import org.whispersystems.signalservice.loki.protocol.todo.LokiMessageFriendRequestStatus

class LokiMessageDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiMessageDatabaseProtocol {

    companion object {
        private val messageFriendRequestTable = "loki_message_friend_request_database"
        private val messageThreadMappingTable = "loki_message_thread_mapping_database"
        private val errorMessageTable = "loki_error_message_database"
        private val messageID = "message_id"
        private val serverID = "server_id"
        private val friendRequestStatus = "friend_request_status"
        private val threadID = "thread_id"
        private val errorMessage = "error_message"
        @JvmStatic val createMessageFriendRequestTableCommand = "CREATE TABLE $messageFriendRequestTable ($messageID INTEGER PRIMARY KEY, $serverID INTEGER DEFAULT 0, $friendRequestStatus INTEGER DEFAULT 0);"
        @JvmStatic val createMessageToThreadMappingTableCommand = "CREATE TABLE IF NOT EXISTS $messageThreadMappingTable ($messageID INTEGER PRIMARY KEY, $threadID INTEGER);"
        @JvmStatic val createErrorMessageTableCommand = "CREATE TABLE IF NOT EXISTS $errorMessageTable ($messageID INTEGER PRIMARY KEY, $errorMessage STRING);"
    }

    override fun getQuoteServerID(quoteID: Long, quoteePublicKey: String): Long? {
        val message = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(quoteID, Address.fromSerialized(quoteePublicKey))
        return if (message != null) getServerID(message.getId()) else null
    }

    fun getServerID(messageID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageFriendRequestTable, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
            cursor.getInt(serverID)
        }?.toLong()
    }

    fun getMessageID(serverID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageFriendRequestTable, "${Companion.serverID} = ?", arrayOf( serverID.toString() )) { cursor ->
            cursor.getInt(messageID)
        }?.toLong()
    }

    override fun setServerID(messageID: Long, serverID: Long) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.serverID, serverID)
        database.insertOrUpdate(messageFriendRequestTable, contentValues, "${Companion.messageID} = ?", arrayOf( messageID.toString() ))
    }

    fun getOriginalThreadID(messageID: Long): Long {
        val database = databaseHelper.readableDatabase
        return database.get(messageThreadMappingTable, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
            cursor.getInt(threadID)
        }?.toLong() ?: -1L
    }

    fun setOriginalThreadID(messageID: Long, threadID: Long) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.threadID, threadID)
        database.insertOrUpdate(messageThreadMappingTable, contentValues, "${Companion.messageID} = ?", arrayOf( messageID.toString() ))
    }

    fun getFriendRequestStatus(messageID: Long): LokiMessageFriendRequestStatus {
        val database = databaseHelper.readableDatabase
        val result = database.get(messageFriendRequestTable, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
            cursor.getInt(friendRequestStatus)
        }
        return if (result != null) {
            LokiMessageFriendRequestStatus.values().first { it.rawValue == result }
        } else {
            LokiMessageFriendRequestStatus.NONE
        }
    }

    fun setFriendRequestStatus(messageID: Long, friendRequestStatus: LokiMessageFriendRequestStatus) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.friendRequestStatus, friendRequestStatus.rawValue)
        database.insertOrUpdate(messageFriendRequestTable, contentValues, "${Companion.messageID} = ?", arrayOf( messageID.toString() ))
        val threadID = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageID)
        notifyConversationListeners(threadID)
    }

    fun isFriendRequest(messageID: Long): Boolean {
        return getFriendRequestStatus(messageID) != LokiMessageFriendRequestStatus.NONE
    }

    fun getErrorMessage(messageID: Long): String? {
        val database = databaseHelper.readableDatabase
        return database.get(errorMessageTable, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
            cursor.getString(errorMessage)
        }
    }

    fun setErrorMessage(messageID: Long, errorMessage: String) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.errorMessage, errorMessage)
        database.insertOrUpdate(errorMessageTable, contentValues, "${Companion.messageID} = ?", arrayOf( messageID.toString() ))
    }
}