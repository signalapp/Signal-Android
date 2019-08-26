package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.api.LokiAPIDatabaseProtocol
import org.whispersystems.signalservice.loki.api.LokiAPITarget

class LokiAPIDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiAPIDatabaseProtocol {

    private val userPublicKey get() = TextSecurePreferences.getLocalNumber(context)

    companion object {
        // Swarm cache
        private val swarmCache = "loki_api_swarm_cache"
        private val hexEncodedPublicKey = "hex_encoded_public_key"
        private val swarm = "swarm"
        @JvmStatic val createSwarmCacheTableCommand = "CREATE TABLE $swarmCache ($hexEncodedPublicKey TEXT PRIMARY KEY, $swarm TEXT);"
        // Last message hash value cache
        private val lastMessageHashValueCache = "loki_api_last_message_hash_value_cache"
        private val target = "target"
        private val lastMessageHashValue = "last_message_hash_value"
        @JvmStatic val createLastMessageHashValueTableCommand = "CREATE TABLE $lastMessageHashValueCache ($target TEXT PRIMARY KEY, $lastMessageHashValue TEXT);"
        // Received message hash values cache
        private val receivedMessageHashValuesCache = "loki_api_received_message_hash_values_cache"
        private val userID = "user_id"
        private val receivedMessageHashValues = "received_message_hash_values"
        @JvmStatic val createReceivedMessageHashValuesTableCommand = "CREATE TABLE $receivedMessageHashValuesCache ($userID TEXT PRIMARY KEY, $receivedMessageHashValues TEXT);"
        // Group chat auth token cache
        private val groupChatAuthTokenTable = "loki_api_group_chat_auth_token_database"
        private val serverURL = "server_url"
        private val token = "token"
        @JvmStatic val createGroupChatAuthTokenTableCommand = "CREATE TABLE $groupChatAuthTokenTable ($serverURL TEXT PRIMARY KEY, $token TEXT);"
        // Last message server ID cache
        private val lastMessageServerIDCache = "loki_api_last_message_server_id_cache"
        private val lastMessageServerIDCacheGroupID = "group_id"
        private val lastMessageServerID = "last_message_server_id"
        @JvmStatic val createLastMessageServerIDTableCommand = "CREATE TABLE $lastMessageServerIDCache ($lastMessageServerIDCacheGroupID INTEGER PRIMARY KEY, $lastMessageServerID INTEGER);"
        // First message server ID cache
        private val firstMessageServerIDCache = "loki_api_first_message_server_id_cache"
        private val firstMessageServerIDCacheGroupID = "group_id"
        private val firstMessageServerID = "first_message_server_id"
        @JvmStatic val createFirstMessageServerIDTableCommand = "CREATE TABLE $firstMessageServerIDCache ($firstMessageServerIDCacheGroupID INTEGER PRIMARY KEY, $firstMessageServerID INTEGER);"
    }

    override fun getSwarmCache(hexEncodedPublicKey: String): Set<LokiAPITarget>? {
        val database = databaseHelper.readableDatabase
        return database.get(swarmCache, "${Companion.hexEncodedPublicKey} = ?", wrap(hexEncodedPublicKey)) { cursor ->
            val swarmAsString = cursor.getString(cursor.getColumnIndexOrThrow(swarm))
            swarmAsString.split(", ").map { targetAsString ->
                val components = targetAsString.split("?port=")
                LokiAPITarget(components[0], components[1].toInt())
            }
        }?.toSet()
    }

    override fun setSwarmCache(hexEncodedPublicKey: String, newValue: Set<LokiAPITarget>) {
        val database = databaseHelper.writableDatabase
        val swarmAsString = newValue.joinToString(", ") { target ->
            "${target.address}?port=${target.port}"
        }
        val row = wrap(mapOf( Companion.hexEncodedPublicKey to hexEncodedPublicKey, swarm to swarmAsString ))
        database.insertOrUpdate(swarmCache, row, "${Companion.hexEncodedPublicKey} = ?", wrap(hexEncodedPublicKey))
    }

    override fun getLastMessageHashValue(target: LokiAPITarget): String? {
        val database = databaseHelper.readableDatabase
        return database.get(lastMessageHashValueCache, "${Companion.target} = ?", wrap(target.address)) { cursor ->
            cursor.getString(cursor.getColumnIndexOrThrow(lastMessageHashValue))
        }
    }

    override fun setLastMessageHashValue(target: LokiAPITarget, newValue: String) {
        val database = databaseHelper.writableDatabase
        val row = wrap(mapOf( Companion.target to target.address, lastMessageHashValue to newValue ))
        database.insertOrUpdate(lastMessageHashValueCache, row, "${Companion.target} = ?", wrap(target.address))
    }

    override fun getReceivedMessageHashValues(): Set<String>? {
        val database = databaseHelper.readableDatabase
        return database.get(receivedMessageHashValuesCache, "$userID = ?", wrap(userPublicKey)) { cursor ->
            val receivedMessageHashValuesAsString = cursor.getString(cursor.getColumnIndexOrThrow(receivedMessageHashValues))
            receivedMessageHashValuesAsString.split(", ").toSet()
        }
    }

    override fun setReceivedMessageHashValues(newValue: Set<String>) {
        val database = databaseHelper.writableDatabase
        val receivedMessageHashValuesAsString = newValue.joinToString(", ")
        val row = wrap(mapOf( userID to userPublicKey, receivedMessageHashValues to receivedMessageHashValuesAsString ))
        database.insertOrUpdate(receivedMessageHashValuesCache, row, "$userID = ?", wrap(userPublicKey))
    }

    override fun getGroupChatAuthToken(serverURL: String): String? {
        val database = databaseHelper.readableDatabase
        return database.get(groupChatAuthTokenTable, "${Companion.serverURL} = ?", wrap(serverURL)) { cursor ->
            cursor.getString(cursor.getColumnIndexOrThrow(token))
        }
    }

    override fun setGroupChatAuthToken(serverURL: String, newValue: String?) {
        val database = databaseHelper.writableDatabase
        if (newValue != null) {
            val row = wrap(mapOf(Companion.serverURL to serverURL, token to newValue!!))
            database.insertOrUpdate(groupChatAuthTokenTable, row, "${Companion.serverURL} = ?", wrap(serverURL))
        } else {
            database.delete(groupChatAuthTokenTable, "${Companion.serverURL} = ?", wrap(serverURL))
        }
    }

    override fun getLastMessageServerID(groupID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(lastMessageServerIDCache, "$lastMessageServerIDCacheGroupID = ?", wrap(groupID.toString())) { cursor ->
            cursor.getInt(lastMessageServerID)
        }?.toLong()
    }

    override fun setLastMessageServerID(groupID: Long, newValue: Long) {
        val database = databaseHelper.writableDatabase
        val row = wrap(mapOf( lastMessageServerIDCacheGroupID to groupID.toString(), lastMessageServerID to newValue.toString() ))
        database.insertOrUpdate(lastMessageServerIDCache, row, "$lastMessageServerIDCacheGroupID = ?", wrap(groupID.toString()))
    }

    override fun getFirstMessageServerID(groupID: Long): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(firstMessageServerIDCache, "$firstMessageServerIDCacheGroupID = ?", wrap(groupID.toString())) { cursor ->
            cursor.getInt(firstMessageServerID)
        }?.toLong()
    }

    override fun setFirstMessageServerID(groupID: Long, newValue: Long) {
        val database = databaseHelper.writableDatabase
        val row = wrap(mapOf( firstMessageServerIDCacheGroupID to groupID.toString(), firstMessageServerID to newValue.toString() ))
        database.insertOrUpdate(firstMessageServerIDCache, row, "$firstMessageServerIDCacheGroupID = ?", wrap(groupID.toString()))
    }
}

// region Convenience
private inline fun <reified T> wrap(x: T): Array<T> {
    return Array(1) { x }
}

private fun wrap(x: Map<String, String>): ContentValues {
    val result = ContentValues(x.size)
    x.forEach { result.put(it.key, it.value) }
    return result
}
// endregion