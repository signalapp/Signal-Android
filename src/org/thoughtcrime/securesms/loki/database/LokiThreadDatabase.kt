package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.utilities.*
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.loki.SessionResetStatus
import org.whispersystems.signalservice.internal.util.JsonUtil
import org.whispersystems.signalservice.loki.api.opengroups.PublicChat
import org.whispersystems.signalservice.loki.database.LokiThreadDatabaseProtocol
import org.whispersystems.signalservice.loki.protocol.todo.LokiThreadFriendRequestStatus
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation

class LokiThreadDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiThreadDatabaseProtocol {
    var delegate: LokiThreadDatabaseDelegate? = null

    companion object {
        private val friendRequestTable = "loki_thread_friend_request_database"
        private val sessionResetTable = "loki_thread_session_reset_database"
        val publicChatTable = "loki_public_chat_database"
        val threadID = "thread_id"
        private val friendRequestStatus = "friend_request_status"
        private val sessionResetStatus = "session_reset_status"
        val publicChat = "public_chat"
        @JvmStatic val createFriendRequestTableCommand = "CREATE TABLE $friendRequestTable ($threadID INTEGER PRIMARY KEY, $friendRequestStatus INTEGER DEFAULT 0);"
        @JvmStatic val createSessionResetTableCommand = "CREATE TABLE $sessionResetTable ($threadID INTEGER PRIMARY KEY, $sessionResetStatus INTEGER DEFAULT 0);"
        @JvmStatic val createPublicChatTableCommand = "CREATE TABLE $publicChatTable ($threadID INTEGER PRIMARY KEY, $publicChat TEXT);"
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
        if (threadID < 0) { return LokiThreadFriendRequestStatus.NONE }
        val recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadID)
        if (recipient != null && recipient.isGroupRecipient) { return LokiThreadFriendRequestStatus.FRIENDS; }
        val database = databaseHelper.readableDatabase
        val result = database.get(friendRequestTable, "${Companion.threadID} = ?", arrayOf( threadID.toString() )) { cursor ->
            cursor.getInt(friendRequestStatus)
        }
        return if (result != null) {
            LokiThreadFriendRequestStatus.values().first { it.rawValue == result }
        } else {
            LokiThreadFriendRequestStatus.NONE
        }
    }

    fun setFriendRequestStatus(threadID: Long, friendRequestStatus: LokiThreadFriendRequestStatus) {
        if (threadID < 0) { return }
        Log.d("Loki", "Setting FR status for thread with ID $threadID to $friendRequestStatus.")
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.threadID, threadID)
        contentValues.put(Companion.friendRequestStatus, friendRequestStatus.rawValue)
        database.insertOrUpdate(friendRequestTable, contentValues, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
        notifyConversationListListeners()
        notifyConversationListeners(threadID)
        delegate?.handleThreadFriendRequestStatusChanged(threadID)
    }

    fun getSessionResetStatus(hexEncodedPublicKey: String): SessionResetStatus {
        val threadID = getThreadID(hexEncodedPublicKey)
        val database = databaseHelper.readableDatabase
        val result = database.get(sessionResetTable, "${Companion.threadID} = ?", arrayOf( threadID.toString() )) { cursor ->
            cursor.getInt(sessionResetStatus)
        }
        return if (result != null) {
            SessionResetStatus.values().first { it.rawValue == result }
        } else {
            SessionResetStatus.NONE
        }
    }

    fun setSessionResetStatus(hexEncodedPublicKey: String, sessionResetStatus: SessionResetStatus) {
        val threadID = getThreadID(hexEncodedPublicKey)
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.threadID, threadID)
        contentValues.put(Companion.sessionResetStatus, sessionResetStatus.rawValue)
        database.insertOrUpdate(sessionResetTable, contentValues, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
        notifyConversationListListeners()
        notifyConversationListeners(threadID)
    }

    fun getAllPublicChats(): Map<Long, PublicChat> {
        val database = databaseHelper.readableDatabase
        var cursor: Cursor? = null
        val result = mutableMapOf<Long, PublicChat>()
        try {
            cursor = database.rawQuery("select * from $publicChatTable", null)
            while (cursor != null && cursor.moveToNext()) {
                val threadID = cursor.getLong(threadID)
                val string = cursor.getString(publicChat)
                val publicChat = PublicChat.fromJSON(string)
                if (publicChat != null) { result[threadID] = publicChat }
            }
        } catch (e: Exception) {
            // Do nothing
        }  finally {
            cursor?.close()
        }
        return result
    }

    fun getAllPublicChatServers(): Set<String> {
        return getAllPublicChats().values.fold(setOf()) { set, chat -> set.plus(chat.server) }
    }

    override fun getPublicChat(threadID: Long): PublicChat? {
        if (threadID < 0) { return null }
        val database = databaseHelper.readableDatabase
        return database.get(publicChatTable, "${Companion.threadID} = ?", arrayOf( threadID.toString() )) { cursor ->
            val publicChatAsJSON = cursor.getString(publicChat)
            PublicChat.fromJSON(publicChatAsJSON)
        }
    }

    override fun setPublicChat(publicChat: PublicChat, threadID: Long) {
        if (threadID < 0) { return }
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.threadID, threadID)
        contentValues.put(Companion.publicChat, JsonUtil.toJson(publicChat.toJSON()))
        database.insertOrUpdate(publicChatTable, contentValues, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
    }

    override fun removePublicChat(threadID: Long) {
        databaseHelper.writableDatabase.delete(publicChatTable, "${Companion.threadID} = ?", arrayOf( threadID.toString() ))
    }

    fun addSessionRestoreDevice(threadID: Long, publicKey: String) {
        val devices = getSessionRestoreDevices(threadID).toMutableSet()
        if (devices.add(publicKey)) {
            TextSecurePreferences.setStringPreference(context, "session_restore_devices_$threadID", devices.joinToString(","))
            delegate?.handleSessionRestoreDevicesChanged(threadID)
        }
    }

    fun getSessionRestoreDevices(threadID: Long): Set<String> {
        return TextSecurePreferences.getStringPreference(context, "session_restore_devices_$threadID", "")
            .split(",")
            .filter { PublicKeyValidation.isValid(it) }
            .toSet()
    }

    fun removeAllSessionRestoreDevices(threadID: Long) {
        TextSecurePreferences.setStringPreference(context, "session_restore_devices_$threadID", "")
        delegate?.handleSessionRestoreDevicesChanged(threadID)
    }
}