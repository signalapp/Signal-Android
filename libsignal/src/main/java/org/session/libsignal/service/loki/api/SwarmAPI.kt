package org.session.libsignal.service.loki.api

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.loki.api.utilities.HTTP
import org.session.libsignal.service.loki.database.LokiAPIDatabaseProtocol
import org.session.libsignal.service.loki.utilities.getRandomElement
import org.session.libsignal.service.loki.utilities.prettifiedDescription
import java.security.SecureRandom

class SwarmAPI private constructor(private val database: LokiAPIDatabaseProtocol) {
    internal var snodeFailureCount: MutableMap<Snode, Int> = mutableMapOf()

    internal var snodePool: Set<Snode>
        get() = database.getSnodePool()
        set(newValue) { database.setSnodePool(newValue) }

    companion object {
        private val seedNodePool: Set<String> = setOf( "https://storage.seed1.loki.network", "https://storage.seed3.loki.network", "https://public.loki.foundation" )

        // region Settings
        private val minimumSnodePoolCount = 64
        private val minimumSwarmSnodeCount = 2
        private val targetSwarmSnodeCount = 2

        /**
         * A snode is kicked out of a swarm and/or the snode pool if it fails this many times.
         */
        internal val snodeFailureThreshold = 4
        // endregion

        // region Initialization
        lateinit var shared: SwarmAPI

        fun configureIfNeeded(database: LokiAPIDatabaseProtocol) {
            if (::shared.isInitialized) { return; }
            shared = SwarmAPI(database)
        }
        // endregion
    }

    // region Swarm API
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
                    "fields" to mapOf( "public_ip" to true, "storage_port" to true, "pubkey_x25519" to true, "pubkey_ed25519" to true )
                )
            )
            val deferred = deferred<Snode, Exception>()
            deferred<Snode, Exception>(SnodeAPI.sharedContext)
            Thread {
                try {
                    val json = HTTP.execute(HTTP.Verb.POST, url, parameters, useSeedNodeConnection = true)
                    val intermediate = json["result"] as? Map<*, *>
                    val rawSnodes = intermediate?.get("service_node_states") as? List<*>
                    if (rawSnodes != null) {
                        @Suppress("NAME_SHADOWING") val snodePool = rawSnodes.mapNotNull { rawSnode ->
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
            }.start()
            return deferred.promise
        } else {
            return Promise.of(snodePool.getRandomElement())
        }
    }

    public fun getSwarm(publicKey: String): Promise<Set<Snode>, Exception> {
        val cachedSwarm = database.getSwarm(publicKey)
        if (cachedSwarm != null && cachedSwarm.size >= minimumSwarmSnodeCount) {
            val cachedSwarmCopy = mutableSetOf<Snode>() // Workaround for a Kotlin compiler issue
            cachedSwarmCopy.addAll(cachedSwarm)
            return task { cachedSwarmCopy }
        } else {
            val parameters = mapOf( "pubKey" to publicKey )
            return getRandomSnode().bind {
                SnodeAPI.shared.invoke(Snode.Method.GetSwarm, it, publicKey, parameters)
            }.map(SnodeAPI.sharedContext) {
                parseSnodes(it).toSet()
            }.success {
                database.setSwarm(publicKey, it)
            }
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

    internal fun getTargetSnodes(publicKey: String): Promise<List<Snode>, Exception> {
        // SecureRandom() should be cryptographically secure
        return getSwarm(publicKey).map { it.shuffled(SecureRandom()).take(targetSwarmSnodeCount) }
    }
    // endregion

    // region Parsing
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
    // endregion
}
