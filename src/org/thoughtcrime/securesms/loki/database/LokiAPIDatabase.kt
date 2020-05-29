package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import android.util.Log
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.utilities.*
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.api.LokiAPITarget
import org.whispersystems.signalservice.loki.database.LokiAPIDatabaseProtocol
import org.whispersystems.signalservice.loki.protocol.multidevice.DeviceLink

class LokiAPIDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiAPIDatabaseProtocol {

    private val userPublicKey get() = TextSecurePreferences.getLocalNumber(context)

    companion object {
        // Snode pool cache
        private val snodePoolCache = "loki_snode_pool_cache"
        private val dummyKey = "dummy_key"
        private val snodePoolKey = "snode_pool_key"
        @JvmStatic val createSnodePoolCacheCommand = "CREATE TABLE $snodePoolCache ($dummyKey TEXT PRIMARY KEY, $snodePoolKey TEXT);"
        // Path cache
        private val pathCache = "loki_path_cache"
        private val indexPath = "index_path"
        private val snode = "snode"
        @JvmStatic val createPathCacheCommand = "CREATE TABLE $pathCache ($indexPath TEXT PRIMARY KEY, $snode TEXT);"
        // Swarm cache
        private val swarmCache = "loki_api_swarm_cache"
        private val hexEncodedPublicKey = "hex_encoded_public_key"
        private val swarm = "swarm"
        @JvmStatic val createSwarmCacheCommand = "CREATE TABLE $swarmCache ($hexEncodedPublicKey TEXT PRIMARY KEY, $swarm TEXT);"
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
        private val groupChatAuthTokenCache = "loki_api_group_chat_auth_token_database"
        private val server = "server"
        private val token = "token"
        @JvmStatic val createGroupChatAuthTokenTableCommand = "CREATE TABLE $groupChatAuthTokenCache ($server TEXT PRIMARY KEY, $token TEXT);"
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
        // Device link cache
        private val deviceLinkCache = "loki_pairing_authorisation_cache"
        private val masterHexEncodedPublicKey = "primary_device"
        private val slaveHexEncodedPublicKey = "secondary_device"
        private val requestSignature = "request_signature"
        private val authorizationSignature = "grant_signature"
        @JvmStatic val createDeviceLinkTableCommand = "CREATE TABLE $deviceLinkCache ($masterHexEncodedPublicKey TEXT, $slaveHexEncodedPublicKey TEXT, " +
            "$requestSignature TEXT NULLABLE DEFAULT NULL, $authorizationSignature TEXT NULLABLE DEFAULT NULL, PRIMARY KEY ($masterHexEncodedPublicKey, $slaveHexEncodedPublicKey));"
        // User count cache
        private val userCountCache = "loki_user_count_cache"
        private val publicChatID = "public_chat_id"
        private val userCount = "user_count"
        @JvmStatic val createUserCountTableCommand = "CREATE TABLE $userCountCache ($publicChatID STRING PRIMARY KEY, $userCount INTEGER DEFAULT 0);"
        // Session request timestamp cache
        private val sessionRequestTimestampCache = "session_request_timestamp_cache"
        private val publicKey = "public_key"
        private val timestamp = "timestamp"
        @JvmStatic val createSessionRequestTimestampTableCommand = "CREATE TABLE $sessionRequestTimestampCache ($publicKey STRING PRIMARY KEY, $timestamp INTEGER DEFAULT 0);"
    }

    override fun getSnodePool(): Set<LokiAPITarget> {
        val database = databaseHelper.readableDatabase
        return database.get(snodePoolCache, "${Companion.dummyKey} = ?", wrap("dummy_key")) { cursor ->
            val snodePoolAsString = cursor.getString(cursor.getColumnIndexOrThrow(snodePoolKey))
            snodePoolAsString.split(", ").mapNotNull { snodeAsString ->
                val components = snodeAsString.split("-")
                val address = components[0]
                val port = components.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                val ed25519Key = components.getOrNull(2) ?: return@mapNotNull null
                val x25519Key = components.getOrNull(3) ?: return@mapNotNull null
                LokiAPITarget(address, port, LokiAPITarget.KeySet(ed25519Key, x25519Key))
            }
        }?.toSet() ?: setOf()
    }

    override fun setSnodePool(newValue: Set<LokiAPITarget>) {
        val database = databaseHelper.writableDatabase
        val snodePoolAsString = newValue.joinToString(", ") { snode ->
            var string = "${snode.address}-${snode.port}"
            val keySet = snode.publicKeySet
            if (keySet != null) {
                string += "-${keySet.ed25519Key}-${keySet.x25519Key}"
            }
            string
        }
        val row = wrap(mapOf(Companion.dummyKey to "dummy_key", snodePoolKey to snodePoolAsString))
        database.insertOrUpdate(snodePoolCache, row, "${Companion.dummyKey} = ?", wrap("dummy_key"))
    }

    override fun getPaths(): List<List<LokiAPITarget>> {
        val database = databaseHelper.readableDatabase
        fun get(indexPath: String): LokiAPITarget? {
            return database.get(pathCache, "${Companion.indexPath} = ?", wrap(indexPath)) { cursor ->
                val snodeAsString = cursor.getString(cursor.getColumnIndexOrThrow(snode))
                val components = snodeAsString.split("-")
                val address = components[0]
                val port = components.getOrNull(1)?.toIntOrNull()
                val ed25519Key = components.getOrNull(2)
                val x25519Key = components.getOrNull(3)
                if (port != null && ed25519Key != null && x25519Key != null) {
                    LokiAPITarget(address, port, LokiAPITarget.KeySet(ed25519Key, x25519Key))
                } else {
                    null
                }
            }
        }
        val path0Snode0 = get("0-0") ?: return listOf(); val path0Snode1 = get("0-1") ?: return listOf()
        val path0Snode2 = get("0-2") ?: return listOf(); val path1Snode0 = get("1-0") ?: return listOf()
        val path1Snode1 = get("1-1") ?: return listOf(); val path1Snode2 = get("1-2") ?: return listOf()
        return listOf( listOf( path0Snode0, path0Snode1, path0Snode2 ), listOf( path1Snode0, path1Snode1, path1Snode2 ) )
    }

    fun clearPaths() {
        val database = databaseHelper.writableDatabase
        fun delete(indexPath: String) {
            database.delete(pathCache, "${Companion.indexPath} = ?", wrap(indexPath))
        }
        delete("0-0"); delete("0-1")
        delete("0-2"); delete("1-0")
        delete("1-1"); delete("1-2")
    }

    override fun setPaths(newValue: List<List<LokiAPITarget>>) {
        // FIXME: This is a bit of a dirty approach that assumes 2 paths of length 3 each. We should do better than this.
        if (newValue.count() != 2) { return }
        val path0 = newValue[0]
        val path1 = newValue[1]
        if (path0.count() != 3 || path1.count() != 3) { return }
        Log.d("Loki", "Persisting onion request paths to database.")
        val database = databaseHelper.writableDatabase
        fun set(indexPath: String ,snode: LokiAPITarget) {
            var snodeAsString = "${snode.address}-${snode.port}"
            val keySet = snode.publicKeySet
            if (keySet != null) {
                snodeAsString += "-${keySet.ed25519Key}-${keySet.x25519Key}"
            }
            val row = wrap(mapOf(Companion.indexPath to indexPath, Companion.snode to snodeAsString))
            database.insertOrUpdate(pathCache, row, "${Companion.indexPath} = ?", wrap(indexPath))
        }
        set("0-0", path0[0]); set("0-1", path0[1])
        set("0-2", path0[2]); set("1-0", path1[0])
        set("1-1", path1[1]); set("1-2", path1[2])
    }

    override fun getSwarmCache(hexEncodedPublicKey: String): Set<LokiAPITarget>? {
        val database = databaseHelper.readableDatabase
        return database.get(swarmCache, "${Companion.hexEncodedPublicKey} = ?", wrap(hexEncodedPublicKey)) { cursor ->
            val swarmAsString = cursor.getString(cursor.getColumnIndexOrThrow(swarm))
            swarmAsString.split(", ").mapNotNull { targetAsString ->
                val components = targetAsString.split("-")
                val address = components[0]
                val port = components.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                val ed25519Key = components.getOrNull(2) ?: return@mapNotNull null
                val x25519Key = components.getOrNull(3) ?: return@mapNotNull null
                LokiAPITarget(address, port, LokiAPITarget.KeySet(ed25519Key, x25519Key))
            }
        }?.toSet()
    }

    override fun setSwarmCache(hexEncodedPublicKey: String, newValue: Set<LokiAPITarget>) {
        val database = databaseHelper.writableDatabase
        val swarmAsString = newValue.joinToString(", ") { target ->
            var string = "${target.address}-${target.port}"
            val keySet = target.publicKeySet
            if (keySet != null) {
                string += "-${keySet.ed25519Key}-${keySet.x25519Key}"
            }
            string
        }
        val row = wrap(mapOf(Companion.hexEncodedPublicKey to hexEncodedPublicKey, swarm to swarmAsString))
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
        val row = wrap(mapOf(Companion.target to target.address, lastMessageHashValue to newValue))
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
        val row = wrap(mapOf(userID to userPublicKey, receivedMessageHashValues to receivedMessageHashValuesAsString))
        database.insertOrUpdate(receivedMessageHashValuesCache, row, "$userID = ?", wrap(userPublicKey))
    }

    override fun getAuthToken(server: String): String? {
        val database = databaseHelper.readableDatabase
        return database.get(groupChatAuthTokenCache, "${Companion.server} = ?", wrap(server)) { cursor ->
            cursor.getString(cursor.getColumnIndexOrThrow(token))
        }
    }

    override fun setAuthToken(server: String, newValue: String?) {
        val database = databaseHelper.writableDatabase
        if (newValue != null) {
            val row = wrap(mapOf(Companion.server to server, token to newValue))
            database.insertOrUpdate(groupChatAuthTokenCache, row, "${Companion.server} = ?", wrap(server))
        } else {
            database.delete(groupChatAuthTokenCache, "${Companion.server} = ?", wrap(server))
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
        val row = wrap(mapOf(lastMessageServerIDCacheIndex to index, lastMessageServerID to newValue.toString()))
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
        val row = wrap(mapOf(lastDeletionServerIDCacheIndex to index, lastDeletionServerID to newValue.toString()))
        database.insertOrUpdate(lastDeletionServerIDCache, row, "$lastDeletionServerIDCacheIndex = ?", wrap(index))
    }

    fun removeLastDeletionServerID(group: Long, server: String) {
        val database = databaseHelper.writableDatabase
        val index = "$server.$group"
        database.delete(lastDeletionServerIDCache,"$lastDeletionServerIDCacheIndex = ?", wrap(index))
    }

    override fun getDeviceLinks(hexEncodedPublicKey: String): Set<DeviceLink> {
        val database = databaseHelper.readableDatabase
        return database.getAll(deviceLinkCache, "$masterHexEncodedPublicKey = ? OR $slaveHexEncodedPublicKey = ?", arrayOf( hexEncodedPublicKey, hexEncodedPublicKey )) { cursor ->
            val masterHexEncodedPublicKey = cursor.getString(masterHexEncodedPublicKey)
            val slaveHexEncodedPublicKey = cursor.getString(slaveHexEncodedPublicKey)
            val requestSignature: ByteArray? = if (cursor.isNull(cursor.getColumnIndexOrThrow(requestSignature))) null else cursor.getBase64EncodedData(requestSignature)
            val authorizationSignature: ByteArray? = if (cursor.isNull(cursor.getColumnIndexOrThrow(authorizationSignature))) null else cursor.getBase64EncodedData(authorizationSignature)
            DeviceLink(masterHexEncodedPublicKey, slaveHexEncodedPublicKey, requestSignature, authorizationSignature)
        }.toSet()
    }

    override fun clearDeviceLinks(hexEncodedPublicKey: String) {
        val database = databaseHelper.writableDatabase
        database.delete(deviceLinkCache, "$masterHexEncodedPublicKey = ? OR $slaveHexEncodedPublicKey = ?", arrayOf( hexEncodedPublicKey, hexEncodedPublicKey ))
    }

    override fun addDeviceLink(deviceLink: DeviceLink) {
        val database = databaseHelper.writableDatabase
        val values = ContentValues()
        values.put(masterHexEncodedPublicKey, deviceLink.masterHexEncodedPublicKey)
        values.put(slaveHexEncodedPublicKey, deviceLink.slaveHexEncodedPublicKey)
        if (deviceLink.requestSignature != null) { values.put(requestSignature, Base64.encodeBytes(deviceLink.requestSignature)) }
        if (deviceLink.authorizationSignature != null) { values.put(authorizationSignature, Base64.encodeBytes(deviceLink.authorizationSignature)) }
        database.insertOrUpdate(deviceLinkCache, values, "$masterHexEncodedPublicKey = ? AND $slaveHexEncodedPublicKey = ?", arrayOf( deviceLink.masterHexEncodedPublicKey, deviceLink.slaveHexEncodedPublicKey ))
    }

    override fun removeDeviceLink(deviceLink: DeviceLink) {
        val database = databaseHelper.writableDatabase
        database.delete(deviceLinkCache, "$masterHexEncodedPublicKey = ? OR $slaveHexEncodedPublicKey = ?", arrayOf( deviceLink.masterHexEncodedPublicKey, deviceLink.slaveHexEncodedPublicKey ))
    }

    fun getUserCount(group: Long, server: String): Int? {
        val database = databaseHelper.readableDatabase
        val index = "$server.$group"
        return database.get(userCountCache, "$publicChatID = ?", wrap(index)) { cursor ->
            cursor.getInt(userCount)
        }?.toInt()
    }

    override fun setUserCount(userCount: Int, group: Long, server: String) {
        val database = databaseHelper.writableDatabase
        val index = "$server.$group"
        val row = wrap(mapOf(publicChatID to index, Companion.userCount to userCount.toString()))
        database.insertOrUpdate(userCountCache, row, "$publicChatID = ?", wrap(index))
    }

    fun getSessionRequestTimestamp(publicKey: String): Long? {
        val database = databaseHelper.readableDatabase
        return database.get(sessionRequestTimestampCache, "$LokiAPIDatabase.publicKey = ?", wrap(publicKey)) { cursor ->
            cursor.getInt(LokiAPIDatabase.timestamp)
        }?.toLong()
    }

    fun setSessionRequestTimestamp(publicKey: String, timestamp: Long) {
        val database = databaseHelper.writableDatabase
        val row = wrap(mapOf(LokiAPIDatabase.publicKey to publicKey, LokiAPIDatabase.timestamp to timestamp.toString()))
        database.insertOrUpdate(sessionRequestTimestampCache, row, "${LokiAPIDatabase.publicKey} = ?", wrap(publicKey))
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