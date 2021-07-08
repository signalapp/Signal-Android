@file:Suppress("NAME_SHADOWING")

package org.session.libsession.snode

import android.os.Build
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.exceptions.SodiumException
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.GenericHash
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.utils.Key
import nl.komponents.kovenant.*
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsignal.crypto.getRandomElement
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.*
import org.session.libsignal.utilities.Base64
import java.security.SecureRandom
import java.util.*

object SnodeAPI {
    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid()) }

    private val database: LokiAPIDatabaseProtocol
        get() = SnodeModule.shared.storage
    private val broadcaster: Broadcaster
        get() = SnodeModule.shared.broadcaster

    internal var snodeFailureCount: MutableMap<Snode, Int> = mutableMapOf()
    internal var snodePool: Set<Snode>
        get() = database.getSnodePool()
        set(newValue) { database.setSnodePool(newValue) }

    // Settings
    private val maxRetryCount = 6
    private val minimumSnodePoolCount = 12
    private val minimumSwarmSnodeCount = 3
    // Use port 4433 if the API level can handle the network security configuration and enforce pinned certificates
    private val seedNodePort = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) 443 else 4433
    private val seedNodePool by lazy {
        if (useTestnet) {
            setOf( "http://public.loki.foundation:38157" )
        } else {
            setOf( "https://storage.seed1.loki.network:$seedNodePort ", "https://storage.seed3.loki.network:$seedNodePort ", "https://public.loki.foundation:$seedNodePort" )
        }
    }
    private val snodeFailureThreshold = 3
    private val targetSwarmSnodeCount = 2
    private val useOnionRequests = true

    internal val useTestnet = false

    // Error
    internal sealed class Error(val description: String) : Exception(description) {
        object Generic : Error("An error occurred.")
        object ClockOutOfSync : Error("Your clock is out of sync with the Service Node network.")
        // ONS
        object DecryptionFailed : Error("Couldn't decrypt ONS name.")
        object HashingFailed : Error("Couldn't compute ONS name hash.")
        object ValidationFailed : Error("ONS name validation failed.")
    }

    // Internal API
    internal fun invoke(method: Snode.Method, snode: Snode, publicKey: String? = null, parameters: Map<String, Any>): RawResponsePromise {
        val url = "${snode.address}:${snode.port}/storage_rpc/v1"
        if (useOnionRequests) {
            return OnionRequestAPI.sendOnionRequest(method, parameters, snode, publicKey)
        } else {
            val deferred = deferred<Map<*, *>, Exception>()
            ThreadUtils.queue {
                val payload = mapOf( "method" to method.rawValue, "params" to parameters )
                try {
                    val json = HTTP.execute(HTTP.Verb.POST, url, payload)
                    deferred.resolve(json)
                } catch (exception: Exception) {
                    val httpRequestFailedException = exception as? HTTP.HTTPRequestFailedException
                    if (httpRequestFailedException != null) {
                        val error = handleSnodeError(httpRequestFailedException.statusCode, httpRequestFailedException.json, snode, publicKey)
                        if (error != null) { return@queue deferred.reject(exception) }
                    }
                    Log.d("Loki", "Unhandled exception: $exception.")
                    deferred.reject(exception)
                }
            }
            return deferred.promise
        }
    }

    internal fun getRandomSnode(): Promise<Snode, Exception> {
        val snodePool = this.snodePool
        if (snodePool.count() < minimumSnodePoolCount) {
            val target = seedNodePool.random()
            val url = "$target/json_rpc"
            Log.d("Loki", "Populating snode pool using: $target.")
            val parameters = mapOf(
                    "method" to "get_n_service_nodes",
                    "params" to mapOf(
                        "active_only" to true,
                        "limit" to 256,
                        "fields" to mapOf( "public_ip" to true, "storage_port" to true, "pubkey_x25519" to true, "pubkey_ed25519" to true )
                    )
            )
            val deferred = deferred<Snode, Exception>()
            deferred<Snode, Exception>()
            ThreadUtils.queue {
                try {
                    val json = HTTP.execute(HTTP.Verb.POST, url, parameters, useSeedNodeConnection = true)
                    val intermediate = json["result"] as? Map<*, *>
                    val rawSnodes = intermediate?.get("service_node_states") as? List<*>
                    if (rawSnodes != null) {
                        val snodePool = rawSnodes.mapNotNull { rawSnode ->
                            val rawSnodeAsJSON = rawSnode as? Map<*, *>
                            val address = rawSnodeAsJSON?.get("public_ip") as? String
                            val port = rawSnodeAsJSON?.get("storage_port") as? Int
                            val ed25519Key = rawSnodeAsJSON?.get("pubkey_ed25519") as? String
                            val x25519Key = rawSnodeAsJSON?.get("pubkey_x25519") as? String
                            if (address != null && port != null && ed25519Key != null && x25519Key != null && address != "0.0.0.0") {
                                Snode("https://$address", port, Snode.KeySet(ed25519Key, x25519Key))
                            } else {
                                Log.d("Loki", "Failed to parse: ${rawSnode?.prettifiedDescription()}.")
                                null
                            }
                        }.toMutableSet()
                        Log.d("Loki", "Persisting snode pool to database.")
                        this.snodePool = snodePool
                        try {
                            deferred.resolve(snodePool.getRandomElement())
                        } catch (exception: Exception) {
                            Log.d("Loki", "Got an empty snode pool from: $target.")
                            deferred.reject(SnodeAPI.Error.Generic)
                        }
                    } else {
                        Log.d("Loki", "Failed to update snode pool from: ${(rawSnodes as List<*>?)?.prettifiedDescription()}.")
                        deferred.reject(SnodeAPI.Error.Generic)
                    }
                } catch (exception: Exception) {
                    deferred.reject(exception)
                }
            }
            return deferred.promise
        } else {
            return Promise.of(snodePool.getRandomElement())
        }
    }

    internal fun dropSnodeFromSwarmIfNeeded(snode: Snode, publicKey: String) {
        val swarm = database.getSwarm(publicKey)?.toMutableSet()
        if (swarm != null && swarm.contains(snode)) {
            swarm.remove(snode)
            database.setSwarm(publicKey, swarm)
        }
    }

    internal fun getSingleTargetSnode(publicKey: String): Promise<Snode, Exception> {
        // SecureRandom() should be cryptographically secure
        return getSwarm(publicKey).map { it.shuffled(SecureRandom()).random() }
    }

    // Public API
    fun getSessionIDFor(onsName: String): Promise<String, Exception> {
        val deferred = deferred<String, Exception>()
        val promise = deferred.promise
        val validationCount = 3
        val sessionIDByteCount = 33
        // Hash the ONS name using BLAKE2b
        val onsName = onsName.toLowerCase(Locale.US)
        val nameAsData = onsName.toByteArray()
        val nameHash = ByteArray(GenericHash.BYTES)
        if (!sodium.cryptoGenericHash(nameHash, nameHash.size, nameAsData, nameAsData.size.toLong())) {
            deferred.reject(Error.HashingFailed)
            return promise
        }
        val base64EncodedNameHash = Base64.encodeBytes(nameHash)
        // Ask 3 different snodes for the Session ID associated with the given name hash
        val parameters = mapOf(
            "endpoint" to "ons_resolve",
            "params" to mapOf( "type" to 0, "name_hash" to base64EncodedNameHash )
        )
        val promises = (1..validationCount).map {
            getRandomSnode().bind { snode ->
                retryIfNeeded(maxRetryCount) {
                    invoke(Snode.Method.OxenDaemonRPCCall, snode, null, parameters)
                }

            }
        }
        all(promises).success { results ->
            val sessionIDs = mutableListOf<String>()
            for (json in results) {
                val intermediate = json["result"] as? Map<*, *>
                val hexEncodedCiphertext = intermediate?.get("encrypted_value") as? String
                if (hexEncodedCiphertext != null) {
                    val ciphertext = Hex.fromStringCondensed(hexEncodedCiphertext)
                    val isArgon2Based = (intermediate["nonce"] == null)
                    if (isArgon2Based) {
                        // Handle old Argon2-based encryption used before HF16
                        val salt = ByteArray(PwHash.SALTBYTES)
                        val key: ByteArray
                        val nonce = ByteArray(SecretBox.NONCEBYTES)
                        val sessionIDAsData = ByteArray(sessionIDByteCount)
                        try {
                            key = Key.fromHexString(sodium.cryptoPwHash(onsName, SecretBox.KEYBYTES, salt, PwHash.OPSLIMIT_MODERATE, PwHash.MEMLIMIT_MODERATE, PwHash.Alg.PWHASH_ALG_ARGON2ID13)).asBytes
                        } catch (e: SodiumException) {
                            deferred.reject(Error.HashingFailed)
                            return@success
                        }
                        if (!sodium.cryptoSecretBoxOpenEasy(sessionIDAsData, ciphertext, ciphertext.size.toLong(), nonce, key)) {
                            deferred.reject(Error.DecryptionFailed)
                            return@success
                        }
                        sessionIDs.add(Hex.toStringCondensed(sessionIDAsData))
                    } else {
                        val hexEncodedNonce = intermediate["nonce"] as? String
                        if (hexEncodedNonce == null) {
                            deferred.reject(Error.Generic)
                            return@success
                        }
                        val nonce = Hex.fromStringCondensed(hexEncodedNonce)
                        val key = ByteArray(GenericHash.BYTES)
                        if (!sodium.cryptoGenericHash(key, key.size, nameAsData, nameAsData.size.toLong(), nameHash, nameHash.size)) {
                            deferred.reject(Error.HashingFailed)
                            return@success
                        }
                        val sessionIDAsData = ByteArray(sessionIDByteCount)
                        if (!sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(sessionIDAsData, null, null, ciphertext, ciphertext.size.toLong(), null, 0, nonce, key)) {
                            deferred.reject(Error.DecryptionFailed)
                            return@success
                        }
                        sessionIDs.add(Hex.toStringCondensed(sessionIDAsData))
                    }
                } else {
                    deferred.reject(Error.Generic)
                    return@success
                }
            }
            if (sessionIDs.size == validationCount && sessionIDs.toSet().size == 1) {
                deferred.resolve(sessionIDs.first())
            } else {
                deferred.reject(Error.ValidationFailed)
            }
        }
        return promise
    }

    fun getTargetSnodes(publicKey: String): Promise<List<Snode>, Exception> {
        // SecureRandom() should be cryptographically secure
        return getSwarm(publicKey).map { it.shuffled(SecureRandom()).take(targetSwarmSnodeCount) }
    }

    fun getSwarm(publicKey: String): Promise<Set<Snode>, Exception> {
        val cachedSwarm = database.getSwarm(publicKey)
        if (cachedSwarm != null && cachedSwarm.size >= minimumSwarmSnodeCount) {
            val cachedSwarmCopy = mutableSetOf<Snode>() // Workaround for a Kotlin compiler issue
            cachedSwarmCopy.addAll(cachedSwarm)
            return task { cachedSwarmCopy }
        } else {
            val parameters = mapOf( "pubKey" to if (useTestnet) publicKey.removing05PrefixIfNeeded() else publicKey )
            return getRandomSnode().bind {
                invoke(Snode.Method.GetSwarm, it, publicKey, parameters)
            }.map {
                parseSnodes(it).toSet()
            }.success {
                database.setSwarm(publicKey, it)
            }
        }
    }

    fun getRawMessages(snode: Snode, publicKey: String): RawResponsePromise {
        val lastHashValue = database.getLastMessageHashValue(snode, publicKey) ?: ""
        val parameters = mapOf( "pubKey" to if (useTestnet) publicKey.removing05PrefixIfNeeded() else publicKey, "lastHash" to lastHashValue )
        return invoke(Snode.Method.GetMessages, snode, publicKey, parameters)
    }

    fun getMessages(publicKey: String): MessageListPromise {
        return retryIfNeeded(maxRetryCount) {
           getSingleTargetSnode(publicKey).bind { snode ->
                getRawMessages(snode, publicKey).map { parseRawMessagesResponse(it, snode, publicKey) }
            }
        }
    }

    fun sendMessage(message: SnodeMessage): Promise<Set<RawResponsePromise>, Exception> {
        val destination = if (useTestnet) message.recipient.removing05PrefixIfNeeded() else message.recipient
        return retryIfNeeded(maxRetryCount) {
            getTargetSnodes(destination).map { swarm ->
                swarm.map { snode ->
                    val parameters = message.toJSON()
                    invoke(Snode.Method.SendMessage, snode, destination, parameters)
                }.toSet()
            }
        }
    }

    // Parsing
    private fun parseSnodes(rawResponse: Any): List<Snode> {
        val json = rawResponse as? Map<*, *>
        val rawSnodes = json?.get("snodes") as? List<*>
        if (rawSnodes != null) {
            return rawSnodes.mapNotNull { rawSnode ->
                val rawSnodeAsJSON = rawSnode as? Map<*, *>
                val address = rawSnodeAsJSON?.get("ip") as? String
                val portAsString = rawSnodeAsJSON?.get("port") as? String
                val port = portAsString?.toInt()
                val ed25519Key = rawSnodeAsJSON?.get("pubkey_ed25519") as? String
                val x25519Key = rawSnodeAsJSON?.get("pubkey_x25519") as? String
                if (address != null && port != null && ed25519Key != null && x25519Key != null && address != "0.0.0.0") {
                    Snode("https://$address", port, Snode.KeySet(ed25519Key, x25519Key))
                } else {
                    Log.d("Loki", "Failed to parse snode from: ${rawSnode?.prettifiedDescription()}.")
                    null
                }
            }
        } else {
            Log.d("Loki", "Failed to parse snodes from: ${rawResponse.prettifiedDescription()}.")
            return listOf()
        }
    }

    fun parseRawMessagesResponse(rawResponse: RawResponse, snode: Snode, publicKey: String): List<SignalServiceProtos.Envelope> {
        val messages = rawResponse["messages"] as? List<*>
        return if (messages != null) {
            updateLastMessageHashValueIfPossible(snode, publicKey, messages)
            val newRawMessages = removeDuplicates(publicKey, messages)
            return parseEnvelopes(newRawMessages);
        } else {
            listOf()
        }
    }

    private fun updateLastMessageHashValueIfPossible(snode: Snode, publicKey: String, rawMessages: List<*>) {
        val lastMessageAsJSON = rawMessages.lastOrNull() as? Map<*, *>
        val hashValue = lastMessageAsJSON?.get("hash") as? String
        if (hashValue != null) {
            database.setLastMessageHashValue(snode, publicKey, hashValue)
        } else if (rawMessages.isNotEmpty()) {
            Log.d("Loki", "Failed to update last message hash value from: ${rawMessages.prettifiedDescription()}.")
        }
    }

    private fun removeDuplicates(publicKey: String, rawMessages: List<*>): List<*> {
        val receivedMessageHashValues = database.getReceivedMessageHashValues(publicKey)?.toMutableSet() ?: mutableSetOf()
        val result = rawMessages.filter { rawMessage ->
            val rawMessageAsJSON = rawMessage as? Map<*, *>
            val hashValue = rawMessageAsJSON?.get("hash") as? String
            if (hashValue != null) {
                val isDuplicate = receivedMessageHashValues.contains(hashValue)
                receivedMessageHashValues.add(hashValue)
                !isDuplicate
            } else {
                Log.d("Loki", "Missing hash value for message: ${rawMessage?.prettifiedDescription()}.")
                false
            }
        }
        database.setReceivedMessageHashValues(publicKey, receivedMessageHashValues)
        return result
    }

    private fun parseEnvelopes(rawMessages: List<*>): List<SignalServiceProtos.Envelope> {
        return rawMessages.mapNotNull { rawMessage ->
            val rawMessageAsJSON = rawMessage as? Map<*, *>
            val base64EncodedData = rawMessageAsJSON?.get("data") as? String
            val data = base64EncodedData?.let { Base64.decode(it) }
            if (data != null) {
                try {
                    MessageWrapper.unwrap(data)
                } catch (e: Exception) {
                    Log.d("Loki", "Failed to unwrap data for message: ${rawMessage.prettifiedDescription()}.")
                    null
                }
            } else {
                Log.d("Loki", "Failed to decode data for message: ${rawMessage?.prettifiedDescription()}.")
                null
            }
        }
    }
    // endregion

    // Error Handling
    internal fun handleSnodeError(statusCode: Int, json: Map<*, *>?, snode: Snode, publicKey: String? = null): Exception? {
        fun handleBadSnode() {
            val oldFailureCount = snodeFailureCount[snode] ?: 0
            val newFailureCount = oldFailureCount + 1
            snodeFailureCount[snode] = newFailureCount
            Log.d("Loki", "Couldn't reach snode at $snode; setting failure count to $newFailureCount.")
            if (newFailureCount >= snodeFailureThreshold) {
                Log.d("Loki", "Failure threshold reached for: $snode; dropping it.")
                if (publicKey != null) {
                    dropSnodeFromSwarmIfNeeded(snode, publicKey)
                }
                snodePool = snodePool.toMutableSet().minus(snode).toSet()
                Log.d("Loki", "Snode pool count: ${snodePool.count()}.")
                snodeFailureCount[snode] = 0
            }
        }
        when (statusCode) {
            400, 500, 502, 503 -> { // Usually indicates that the snode isn't up to date
                handleBadSnode()
            }
            406 -> {
                Log.d("Loki", "The user's clock is out of sync with the service node network.")
                broadcaster.broadcast("clockOutOfSync")
                return Error.ClockOutOfSync
            }
            421 -> {
                // The snode isn't associated with the given public key anymore
                if (publicKey != null) {
                    fun invalidateSwarm() {
                        Log.d("Loki", "Invalidating swarm for: $publicKey.")
                        dropSnodeFromSwarmIfNeeded(snode, publicKey)
                    }
                    if (json != null) {
                        val snodes = parseSnodes(json)
                        if (snodes.isNotEmpty()) {
                            database.setSwarm(publicKey, snodes.toSet())
                        } else {
                            invalidateSwarm()
                        }
                    } else {
                        invalidateSwarm()
                    }
                } else {
                    Log.d("Loki", "Got a 421 without an associated public key.")
                }
            }
            else -> {
                handleBadSnode()
                Log.d("Loki", "Unhandled response code: ${statusCode}.")
                return Error.Generic
            }
        }
        return null
    }
}

// Type Aliases
typealias RawResponse = Map<*, *>
typealias MessageListPromise = Promise<List<SignalServiceProtos.Envelope>, Exception>
typealias RawResponsePromise = Promise<RawResponse, Exception>
