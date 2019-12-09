package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.api.LokiAPIDatabaseProtocol
import org.whispersystems.signalservice.loki.api.LokiAPITarget
import org.whispersystems.signalservice.loki.api.PairingAuthorisation

// TODO: Clean this up a bit

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
        private val server = "server"
        private val token = "token"
        @JvmStatic val createGroupChatAuthTokenTableCommand = "CREATE TABLE $groupChatAuthTokenTable ($server TEXT PRIMARY KEY, $token TEXT);"
        // Last message server ID cache
        private val lastMessageServerIDCache = "loki_api_last_message_server_id_cache"
        private val lastMessageServerIDCacheIndex = "loki_api_last_message_server_id_cache_index"
        private val lastMessageServerID = "last_message_server_id"
        @JvmStatic val createLastMessageServerIDTableCommand = "CREATE TABLE $lastMessageServerIDCache ($lastMessageServerIDCacheIndex STRING PRIMARY KEY, $lastMessageServerID INTEGER DEFAULT 0);"
        // Last deletion server ID cache
        private val lastDeletionServerIDCache = "loki_api_last_deletion_server_id_cache"
        private val lastDeletionServerIDCacheIndex = "loki_api_last_deletion_server_id_cache_index"
        private val lastDeletionServerID = "last_deletion_server_id"
        @JvmStatic val createLastDeletionServerIDTableCommand = "CREATE TABLE $lastDeletionServerIDCache ($lastDeletionServerIDCacheIndex STRING PRIMARY KEY, $lastDeletionServerID INTEGER DEFAULT 0);"
        // Pairing authorisation cache
        private val pairingAuthorisationCache = "loki_pairing_authorisation_cache"
        private val primaryDevicePublicKey = "primary_device"
        private val secondaryDevicePublicKey = "secondary_device"
        private val requestSignature = "request_signature"
        private val grantSignature = "grant_signature"
        @JvmStatic val createPairingAuthorisationTableCommand = "CREATE TABLE $pairingAuthorisationCache ($primaryDevicePublicKey TEXT, $secondaryDevicePublicKey TEXT, " +
            "$requestSignature TEXT NULLABLE DEFAULT NULL, $grantSignature TEXT NULLABLE DEFAULT NULL, PRIMARY KEY ($primaryDevicePublicKey, $secondaryDevicePublicKey));"
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

    override fun getAuthToken(server: String): String? {
        val database = databaseHelper.readableDatabase
        return database.get(groupChatAuthTokenTable, "${Companion.server} = ?", wrap(server)) { cursor ->
            cursor.getString(cursor.getColumnIndexOrThrow(token))
        }
    }

    override fun setAuthToken(server: String, newValue: String?) {
        val database = databaseHelper.writableDatabase
        if (newValue != null) {
            val row = wrap(mapOf(Companion.server to server, token to newValue))
            database.insertOrUpdate(groupChatAuthTokenTable, row, "${Companion.server} = ?", wrap(server))
        } else {
            database.delete(groupChatAuthTokenTable, "${Companion.server} = ?", wrap(server))
        }
    }

    override fun getLastMessageServerID(group: Long, server: String): Long? {
        val database = databaseHelper.readableDatabase
        val index = "$server.$group"
        return database.get(lastMessageServerIDCache, "$lastMessageServerIDCacheIndex = ?", wrap(index)) { cursor ->
            cursor.getInt(lastMessageServerID)
        }?.toLong()
    }

    override fun setLastMessageServerID(group: Long, server: String, newValue: Long) {
        val database = databaseHelper.writableDatabase
        val index = "$server.$group"
        val row = wrap(mapOf( lastMessageServerIDCacheIndex to index, lastMessageServerID to newValue.toString() ))
        database.insertOrUpdate(lastMessageServerIDCache, row, "$lastMessageServerIDCacheIndex = ?", wrap(index))
    }

    fun removeLastMessageServerID(group: Long, server: String) {
        val database = databaseHelper.writableDatabase
        val index = "$server.$group"
        database.delete(lastMessageServerIDCache,"$lastMessageServerIDCacheIndex = ?", wrap(index))
    }

    override fun getLastDeletionServerID(group: Long, server: String): Long? {
        val database = databaseHelper.readableDatabase
        val index = "$server.$group"
        return database.get(lastDeletionServerIDCache, "$lastDeletionServerIDCacheIndex = ?", wrap(index)) { cursor ->
            cursor.getInt(lastDeletionServerID)
        }?.toLong()
    }

    override fun setLastDeletionServerID(group: Long, server: String, newValue: Long) {
        val database = databaseHelper.writableDatabase
        val index = "$server.$group"
        val row = wrap(mapOf( lastDeletionServerIDCacheIndex to index, lastDeletionServerID to newValue.toString() ))
        database.insertOrUpdate(lastDeletionServerIDCache, row, "$lastDeletionServerIDCacheIndex = ?", wrap(index))
    }

    fun removeLastDeletionServerID(group: Long, server: String) {
        val database = databaseHelper.writableDatabase
        val index = "$server.$group"
        database.delete(lastDeletionServerIDCache,"$lastDeletionServerIDCacheIndex = ?", wrap(index))
    }

    override fun getPairingAuthorisations(hexEncodedPublicKey: String): List<PairingAuthorisation> {
        val database = databaseHelper.readableDatabase
        return database.getAll(pairingAuthorisationCache, "$primaryDevicePublicKey = ? OR $secondaryDevicePublicKey = ?", arrayOf( hexEncodedPublicKey, hexEncodedPublicKey )) { cursor ->
            val primaryDevicePubKey = cursor.getString(primaryDevicePublicKey)
            val secondaryDevicePubKey = cursor.getString(secondaryDevicePublicKey)
            val requestSignature: ByteArray? = if (cursor.isNull(cursor.getColumnIndexOrThrow(requestSignature))) null else cursor.getBase64EncodedData(requestSignature)
            val grantSignature: ByteArray? = if (cursor.isNull(cursor.getColumnIndexOrThrow(grantSignature))) null else cursor.getBase64EncodedData(grantSignature)
            PairingAuthorisation(primaryDevicePubKey, secondaryDevicePubKey, requestSignature, grantSignature)
        }
    }

    override fun insertOrUpdatePairingAuthorisation(authorisation: PairingAuthorisation) {
        val database = databaseHelper.writableDatabase
        val values = ContentValues()
        values.put(primaryDevicePublicKey, authorisation.primaryDevicePublicKey)
        values.put(secondaryDevicePublicKey, authorisation.secondaryDevicePublicKey)
        if (authorisation.requestSignature != null) { values.put(requestSignature, Base64.encodeBytes(authorisation.requestSignature)) }
        if (authorisation.grantSignature != null) { values.put(grantSignature, Base64.encodeBytes(authorisation.grantSignature)) }
        database.insertOrUpdate(pairingAuthorisationCache, values, "$primaryDevicePublicKey = ? AND $secondaryDevicePublicKey = ?", arrayOf( authorisation.primaryDevicePublicKey, authorisation.secondaryDevicePublicKey ))
    }

    override fun removePairingAuthorisations(hexEncodedPublicKey: String) {
        val database = databaseHelper.writableDatabase
        database.delete(pairingAuthorisationCache, "$primaryDevicePublicKey = ? OR $secondaryDevicePublicKey = ?", arrayOf( hexEncodedPublicKey, hexEncodedPublicKey ))
    }

    fun removePairingAuthorisation(primaryDevicePublicKey: String, secondaryDevicePublicKey: String) {
        val database = databaseHelper.writableDatabase
        database.delete(pairingAuthorisationCache, "${Companion.primaryDevicePublicKey} = ? OR ${Companion.secondaryDevicePublicKey} = ?", arrayOf( primaryDevicePublicKey, secondaryDevicePublicKey ))
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