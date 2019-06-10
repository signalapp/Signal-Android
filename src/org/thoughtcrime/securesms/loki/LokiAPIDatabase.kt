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
    }

    override fun getSwarmCache(hexEncodedPublicKey: String): List<LokiAPITarget>? {
        val database = databaseHelper.readableDatabase
        return database.get(swarmCache, "${Companion.hexEncodedPublicKey} = ?", wrap(hexEncodedPublicKey)) { cursor ->
            val swarmAsString = cursor.getString(cursor.getColumnIndexOrThrow(swarm))
            swarmAsString.split(",").map { targetAsString ->
                val components = targetAsString.split("?port=")
                LokiAPITarget(components[0], components[1].toInt())
            }
        }
    }

    override fun setSwarmCache(hexEncodedPublicKey: String, newValue: List<LokiAPITarget>) {
        val database = databaseHelper.writableDatabase
        val swarmAsString = newValue.joinToString(",") { target ->
            "${target.address}?port=${target.port}"
        }
        database.update(swarmCache, wrap(mapOf( swarm to swarmAsString )), "${Companion.hexEncodedPublicKey} = ?", wrap(hexEncodedPublicKey))
    }

    override fun getLastMessageHashValue(target: LokiAPITarget): String? {
        val database = databaseHelper.readableDatabase
        return database.get(lastMessageHashValueCache, "${Companion.target} = ?", wrap(target.address)) { cursor ->
            cursor.getString(cursor.getColumnIndexOrThrow(lastMessageHashValue))
        }
    }

    override fun setLastMessageHashValue(target: LokiAPITarget, newValue: String) {
        val database = databaseHelper.writableDatabase
        database.update(lastMessageHashValueCache, wrap(mapOf( lastMessageHashValue to newValue )), "${Companion.target} = ?", wrap(target.address))
    }

    override fun getReceivedMessageHashValues(): Set<String>? {
        val database = databaseHelper.readableDatabase
        return database.get(receivedMessageHashValuesCache, "$userID = ?", wrap(userPublicKey)) { cursor ->
            val receivedMessageHashValuesAsString = cursor.getString(cursor.getColumnIndexOrThrow(receivedMessageHashValues))
            receivedMessageHashValuesAsString.split(",").toSet()
        }
    }

    override fun setReceivedMessageHashValues(newValue: Set<String>) {
        val database = databaseHelper.writableDatabase
        val receivedMessageHashValuesAsString = newValue.joinToString(",")
        database.update(receivedMessageHashValuesCache, wrap(mapOf( receivedMessageHashValues to receivedMessageHashValuesAsString )), "$userID = ?", wrap(userPublicKey))
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