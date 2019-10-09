package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.internal.util.JsonUtil
import org.whispersystems.signalservice.loki.api.LokiGroupChat
import org.whispersystems.signalservice.loki.messaging.LokiThreadDatabaseProtocol
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus
import org.whispersystems.signalservice.loki.messaging.LokiThreadSessionResetStatus

class LokiThreadDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiThreadDatabaseProtocol {
    var delegate: LokiThreadDatabaseDelegate? = null

    companion object {
        private val friendRequestTableName = "loki_thread_friend_request_database"
        private val sessionResetTableName = "loki_thread_session_reset_database"
        private val groupChatMappingTableName = "loki_group_chat_mapping_database"
        private val threadID = "thread_id"
        private val friendRequestStatus = "friend_request_status"
        private val sessionResetStatus = "session_reset_status"
        private val groupChatJSON = "group_chat_json"
        @JvmStatic val createFriendRequestTableCommand = "CREATE TABLE $friendRequestTableName ($threadID INTEGER PRIMARY KEY, $friendRequestStatus INTEGER DEFAULT 0);"
        @JvmStatic val createSessionResetTableCommand = "CREATE TABLE $sessionResetTableName ($threadID INTEGER PRIMARY KEY, $sessionResetStatus INTEGER DEFAULT 0);"
        @JvmStatic val createGroupChatMappingTableCommand = "CREATE TABLE $groupChatMappingTableName ($threadID INTEGER PRIMARY KEY, $groupChatJSON TEXT);"
    }

    override fun getThreadID(hexEncodedPublicKey: String): Long {
        val address = Address.fromSerialized(hexEncodedPublicKey)
        val recipient = Recipient.from(context, address, false)
        return DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
    }

    fun getThreadID(messageID: Long): Long {
        return DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageID)
    }

    fun getFriendRequestStatus(threadID: Long): LokiThreadFriendRequestStatus {
        val database = databaseHelper.readableDatabase
        val result = database.get(friendRequestTableName, "${Companion.threadID} = ?", arrayOf( threadID.toString() )) { cursor ->
            cursor.getInt(friendRequestStatus)
        }
        return if (result != null) {
            LokiThreadFriendRequestStatus.values().first { it.rawValue == result }
        } else {
            LokiThreadFriendRequestStatus.NONE
        }
    }

    override fun setFriendRequestStatus(threadID: Long, friendRequestStatus: LokiThreadFriendRequestStatus) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.threadID, threadID)
        contentValues.put(Companion.friendRequestStatus, friendRequestStatus.rawValue)
        database.insertOrUpdate(friendRequestTableName, contentValues, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
        notifyConversationListListeners()
        notifyConversationListeners(threadID)
        delegate?.handleThreadFriendRequestStatusChanged(threadID)
    }

    fun hasPendingFriendRequest(threadID: Long): Boolean {
        val friendRequestStatus = getFriendRequestStatus(threadID)
        return friendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_SENDING || friendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_SENT
            || friendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_RECEIVED
    }

    override fun getSessionResetStatus(threadID: Long): LokiThreadSessionResetStatus {
        val database = databaseHelper.readableDatabase
        val result = database.get(sessionResetTableName, "${Companion.threadID} = ?", arrayOf( threadID.toString() )) { cursor ->
            cursor.getInt(sessionResetStatus)
        }
        return if (result != null) {
            LokiThreadSessionResetStatus.values().first { it.rawValue == result }
        } else {
            LokiThreadSessionResetStatus.NONE
        }
    }

    override fun setSessionResetStatus(threadID: Long, sessionResetStatus: LokiThreadSessionResetStatus) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.threadID, threadID)
        contentValues.put(Companion.sessionResetStatus, sessionResetStatus.rawValue)
        database.insertOrUpdate(sessionResetTableName, contentValues, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
        notifyConversationListListeners()
        notifyConversationListeners(threadID)
    }

    override fun getGroupChat(threadID: Long): LokiGroupChat? {
        val database = databaseHelper.readableDatabase
        return database.get(groupChatMappingTableName, "${Companion.threadID} = ?", arrayOf( threadID.toString() )) { cursor ->
            val string = cursor.getString(groupChatJSON)
            LokiGroupChat.fromJSON(string)
        }
    }

    override fun setGroupChat(groupChat: LokiGroupChat, threadID: Long) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.threadID, threadID)
        contentValues.put(Companion.groupChatJSON, JsonUtil.toJson(groupChat.toJSON()))
        database.insertOrUpdate(groupChatMappingTableName, contentValues, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
    }

    override fun removeGroupChat(threadID: Long) {
        databaseHelper.writableDatabase.delete(groupChatMappingTableName, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
    }
}