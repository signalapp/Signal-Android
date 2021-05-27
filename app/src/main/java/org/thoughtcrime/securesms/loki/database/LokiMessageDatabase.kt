package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase.CONFLICT_REPLACE
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.utilities.*
import org.session.libsignal.database.LokiMessageDatabaseProtocol
import org.session.libsignal.utilities.Log

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
        @JvmStatic
        val createMessageIDTableCommand = "CREATE TABLE $messageIDTable ($messageID INTEGER PRIMARY KEY, $serverID INTEGER DEFAULT 0, $friendRequestStatus INTEGER DEFAULT 0);"
        @JvmStatic
        val createMessageToThreadMappingTableCommand = "CREATE TABLE IF NOT EXISTS $messageThreadMappingTable ($messageID INTEGER PRIMARY KEY, $threadID INTEGER);"
        @JvmStatic
        val createErrorMessageTableCommand = "CREATE TABLE IF NOT EXISTS $errorMessageTable ($messageID INTEGER PRIMARY KEY, $errorMessage STRING);"
        @JvmStatic
        val updateMessageIDTableForType = "ALTER TABLE $messageIDTable ADD COLUMN $messageType INTEGER DEFAULT 0; ALTER TABLE $messageIDTable ADD CONSTRAINT PK_$messageIDTable PRIMARY KEY ($messageID, $serverID);"
        @JvmStatic
        val updateMessageMappingTable = "ALTER TABLE $messageThreadMappingTable ADD COLUMN $serverID INTEGER DEFAULT 0; ALTER TABLE $messageThreadMappingTable ADD CONSTRAINT PK_$messageThreadMappingTable PRIMARY KEY ($messageID, $serverID);"

        const val SMS_TYPE = 0
        const val MMS_TYPE = 1

    }

    fun getServerID(messageID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageIDTable, "${Companion.messageID} = ?", arrayOf(messageID.toString())) { cursor ->
            cursor.getInt(serverID)
        }?.toLong()
    }

    fun getServerID(messageID: Long, isSms: Boolean): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageIDTable, "${Companion.messageID} = ? AND $messageType = ?", arrayOf(messageID.toString(), if (isSms) SMS_TYPE.toString() else MMS_TYPE.toString())) { cursor ->
            cursor.getInt(serverID)
        }?.toLong()
    }

    fun getMessageID(serverID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(messageIDTable, "${Companion.serverID} = ?", arrayOf(serverID.toString())) { cursor ->
            cursor.getInt(messageID)
        }?.toLong()
    }

    fun deleteMessage(messageID: Long, isSms: Boolean) {
        val database = databaseHelper.writableDatabase

        val serverID = database.get(messageIDTable,
                "${Companion.messageID} = ? AND ${Companion.messageType} = ?",
                arrayOf(messageID.toString(), (if (isSms) SMS_TYPE else MMS_TYPE).toString())) { cursor ->
            cursor.getInt(Companion.serverID).toLong()
        } ?: return

        database.beginTransaction()

        database.delete(messageIDTable, "${Companion.messageID} = ? AND ${Companion.serverID} = ?", arrayOf(messageID.toString(), serverID.toString()))
        database.delete(messageThreadMappingTable, "${Companion.messageID} = ? AND ${Companion.serverID} = ?", arrayOf(messageID.toString(), serverID.toString()))

        database.setTransactionSuccessful()
        database.endTransaction()
    }

    fun getMessageID(serverID: Long, threadID: Long): Pair<Long, Boolean>? {
        val database = databaseHelper.readableDatabase
        val mappingResult = database.get(messageThreadMappingTable, "${Companion.serverID} = ? AND ${Companion.threadID} = ?",
                arrayOf(serverID.toString(), threadID.toString())) { cursor ->
            cursor.getInt(messageID) to cursor.getInt(Companion.serverID)
        } ?: return null

        val (mappedID, mappedServerID) = mappingResult

        return database.get(messageIDTable,
                "$messageID = ? AND ${Companion.serverID} = ?",
                arrayOf(mappedID.toString(), mappedServerID.toString())) { cursor ->
            cursor.getInt(Companion.messageID).toLong() to (cursor.getInt(messageType) == SMS_TYPE)
        }
    }

    override fun setServerID(messageID: Long, serverID: Long, isSms: Boolean) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(3)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.serverID, serverID)
        contentValues.put(messageType, if (isSms) SMS_TYPE else MMS_TYPE)
        database.insertWithOnConflict(messageIDTable, null, contentValues, CONFLICT_REPLACE)
    }

    fun getOriginalThreadID(messageID: Long): Long {
        val database = databaseHelper.readableDatabase
        return database.get(messageThreadMappingTable, "${Companion.messageID} = ?", arrayOf(messageID.toString())) { cursor ->
            cursor.getInt(threadID)
        }?.toLong() ?: -1L
    }

    fun setOriginalThreadID(messageID: Long, serverID: Long, threadID: Long) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(3)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.serverID, serverID)
        contentValues.put(Companion.threadID, threadID)
        database.insertWithOnConflict(messageThreadMappingTable, null, contentValues, CONFLICT_REPLACE)
    }

    fun getErrorMessage(messageID: Long): String? {
        val database = databaseHelper.readableDatabase
        return database.get(errorMessageTable, "${Companion.messageID} = ?", arrayOf(messageID.toString())) { cursor ->
            cursor.getString(errorMessage)
        }
    }

    fun setErrorMessage(messageID: Long, errorMessage: String) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(Companion.messageID, messageID)
        contentValues.put(Companion.errorMessage, errorMessage)
        database.insertOrUpdate(errorMessageTable, contentValues, "${Companion.messageID} = ?", arrayOf(messageID.toString()))
    }

    fun deleteThread(threadId: Long) {
        val database = databaseHelper.writableDatabase
        try {
            val messages = mutableSetOf<Pair<Long,Long>>()
            database.get(messageThreadMappingTable,  "${Companion.threadID} = ?", arrayOf(threadId.toString())) { cursor ->
                // for each add
                while (cursor.moveToNext()) {
                    messages.add(cursor.getLong(Companion.messageID) to cursor.getLong(Companion.serverID))
                }
            }
            var deletedCount = 0L
            database.beginTransaction()
            messages.forEach { (messageId, serverId) ->
                deletedCount += database.delete(messageIDTable, "${Companion.messageID} = ? AND ${Companion.serverID} = ?", arrayOf(messageId.toString(), serverId.toString()))
            }
            val mappingDeleted = database.delete(messageThreadMappingTable, "${Companion.threadID} = ?", arrayOf(threadId.toString()))
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }
}