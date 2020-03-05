package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.redesign.utilities.get
import org.thoughtcrime.securesms.loki.redesign.utilities.getInt
import org.thoughtcrime.securesms.loki.redesign.utilities.getString
import org.thoughtcrime.securesms.loki.redesign.utilities.insertOrUpdate
import org.whispersystems.signalservice.loki.messaging.LokiMessageDatabaseProtocol
import org.whispersystems.signalservice.loki.messaging.LokiMessageFriendRequestStatus

class LokiMessageDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiMessageDatabaseProtocol {

    companion object {
        private val messageFriendRequestTableName = "loki_message_friend_request_database"
        private val messageThreadMappingTableName = "loki_message_thread_mapping_database"
        private val errorMessageTableName = "loki_error_message_database"
        private val messageID = "message_id"
        private val serverID = "server_id"
        private val friendRequestStatus = "friend_request_status"
        private val threadID = "thread_id"
        private val errorMessage = "error_message"
        @JvmStatic val createMessageFriendRequestTableCommand = "CREATE TABLE $messageFriendRequestTableName ($messageID INTEGER PRIMARY KEY, $serverID INTEGER DEFAULT 0, $friendRequestStatus INTEGER DEFAULT 0);"
        @JvmStatic val createMessageToThreadMappingTableCommand = "CREATE TABLE IF NOT EXISTS $messageThreadMappingTableName ($messageID INTEGER PRIMARY KEY, $threadID INTEGER);"
        @JvmStatic val createErrorMessageTableCommand = "CREATE TABLE IF NOT EXISTS $errorMessageTableName ($messageID INTEGER PRIMARY KEY, $errorMessage STRING);"
    }

    override fun getQuoteServerID(quoteID: Long, quoteeHexEncodedPublicKey: String): Long? {
        val message = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(quoteID, Address.fromSerialized(quoteeHexEncodedPublicKey))
        return if (message != null) getServerID(message.getId()) else null
    }

    fun getServerID(messageID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageFriendRequestTableName, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
            cursor.getInt(Companion.serverID)
        }?.toLong()
    }

    fun getMessageID(serverID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageFriendRequestTableName, "${Companion.serverID} = ?", arrayOf( serverID.toString() )) { cursor ->
            cursor.getInt(messageID)
        }?.toLong()
    }

    override fun setServerID(messageID: Long, serverID: Long) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.serverID, serverID)
        database.insertOrUpdate(messageFriendRequestTableName, contentValues, "${Companion.messageID} = ?", arrayOf( messageID.toString() ))
    }

    fun getOriginalThreadID(messageID: Long): Long {
        val database = databaseHelper.readableDatabase
        return database.get(messageThreadMappingTableName, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
            cursor.getInt(Companion.threadID)
        }?.toLong() ?: -1L
    }

    fun setOriginalThreadID(messageID: Long, threadID: Long) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.threadID, threadID)
        database.insertOrUpdate(messageThreadMappingTableName, contentValues, "${Companion.messageID} = ?", arrayOf( messageID.toString() ))
    }

    fun getFriendRequestStatus(messageID: Long): LokiMessageFriendRequestStatus {
        val database = databaseHelper.readableDatabase
        val result = database.get(messageFriendRequestTableName, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
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
        database.insertOrUpdate(messageFriendRequestTableName, contentValues, "${Companion.messageID} = ?", arrayOf( messageID.toString() ))
        val threadID = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageID)
        notifyConversationListeners(threadID)
    }

    fun isFriendRequest(messageID: Long): Boolean {
        return getFriendRequestStatus(messageID) != LokiMessageFriendRequestStatus.NONE
    }

    fun getErrorMessage(messageID: Long): String? {
        val database = databaseHelper.readableDatabase
        return database.get(errorMessageTableName, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
            cursor.getString(errorMessage)
        }
    }

    fun setErrorMessage(messageID: Long, errorMessage: String) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.errorMessage, errorMessage)
        database.insertOrUpdate(errorMessageTableName, contentValues, "${Companion.messageID} = ?", arrayOf( messageID.toString() ))
    }
}