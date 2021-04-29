package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import org.session.libsession.messaging.threads.Address
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.session.libsignal.service.loki.database.LokiMessageDatabaseProtocol
import org.thoughtcrime.securesms.loki.utilities.*

class LokiMessageDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiMessageDatabaseProtocol {

    companion object {
        private val messageIDTable = "loki_message_friend_request_database"
        private val messageThreadMappingTable = "loki_message_thread_mapping_database"
        private val errorMessageTable = "loki_error_message_database"
        private val messageID = "message_id"
        private val serverID = "server_id"
        private val friendRequestStatus = "friend_request_status"
        private val threadID = "thread_id"
        private val errorMessage = "error_message"
        private val messageType = "message_type"
        @JvmStatic val createMessageIDTableCommand = "CREATE TABLE $messageIDTable ($messageID INTEGER PRIMARY KEY, $serverID INTEGER DEFAULT 0, $friendRequestStatus INTEGER DEFAULT 0);"
        @JvmStatic val createMessageToThreadMappingTableCommand = "CREATE TABLE IF NOT EXISTS $messageThreadMappingTable ($messageID INTEGER PRIMARY KEY, $threadID INTEGER);"
        @JvmStatic val createErrorMessageTableCommand = "CREATE TABLE IF NOT EXISTS $errorMessageTable ($messageID INTEGER PRIMARY KEY, $errorMessage STRING);"
        @JvmStatic val updateMessageIDTableForType = "ALTER TABLE $messageIDTable ADD COLUMN $messageType INTEGER DEFAULT 0; ALTER TABLE $messageIDTable ADD CONSTRAINT PK_$messageIDTable PRIMARY KEY ($messageID, $serverID);"
        @JvmStatic val updateMessageMappingTable = "ALTER TABLE $messageThreadMappingTable ADD COLUMN $serverID INTEGER DEFAULT 0; ALTER TABLE $messageThreadMappingTable ADD CONSTRAINT PK_$messageThreadMappingTable PRIMARY KEY ($messageID, $serverID);"

        const val SMS_TYPE = 0
        const val MMS_TYPE = 1

    }

    override fun getQuoteServerID(quoteID: Long, quoteePublicKey: String): Long? {
        val message = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(quoteID, quoteePublicKey)
        return if (message != null) getServerID(message.getId(), !message.isMms) else null
    }

    fun getServerID(messageID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageIDTable, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
            cursor.getInt(serverID)
        }?.toLong()
    }

    fun getServerID(messageID: Long, isSms: Boolean): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageIDTable, "${Companion.messageID} = ? AND $messageType = ?", arrayOf( messageID.toString(), if (isSms) SMS_TYPE.toString() else MMS_TYPE.toString() )) { cursor ->
            cursor.getInt(serverID)
        }?.toLong()
    }

    fun getMessageID(serverID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageIDTable, "${Companion.serverID} = ?", arrayOf( serverID.toString() )) { cursor ->
            cursor.getInt(messageID)
        }?.toLong()
    }

    fun getMessageID(serverID: Long, threadID: Long): Pair<Long,Boolean>? {
        val database = databaseHelper.readableDatabase
        return database.get("$messageIDTable INNER JOIN $messageThreadMappingTable ON $messageIDTable.$messageID = $messageThreadMappingTable.$messageID",
                "${Companion.serverID} = ? AND ${Companion.threadID} = ?",
                arrayOf(serverID.toString(),threadID.toString())) { cursor ->
            cursor.getLong(messageID) to (cursor.getInt(messageType) == SMS_TYPE)
        }
    }

    override fun setServerID(messageID: Long, serverID: Long, isSms: Boolean) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.serverID, serverID)
        contentValues.put(messageType, if (isSms) SMS_TYPE else MMS_TYPE)
        database.insertOrUpdate(messageIDTable, contentValues, "${Companion.messageID} = ? AND ${Companion.serverID} = ?", arrayOf( messageID.toString(), serverID.toString() ))
    }

    fun getOriginalThreadID(messageID: Long): Long {
        val database = databaseHelper.readableDatabase
        return database.get(messageThreadMappingTable, "${Companion.messageID} = ?", arrayOf( messageID.toString() )) { cursor ->
            cursor.getInt(threadID)
        }?.toLong() ?: -1L
    }

    fun setOriginalThreadID(messageID: Long, serverID: Long, threadID: Long) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.serverID, serverID)
        contentValues.put(Companion.threadID, threadID)
        database.insertOrUpdate(messageThreadMappingTable, contentValues, "${Companion.messageID} = ? AND ${Companion.serverID} = ?", arrayOf( messageID.toString(), serverID.toString() ))
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