package org.session.libsession.snode

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import okhttp3.Request
import org.session.libsession.messaging.file_server.FileServerAPIV2
import org.session.libsession.utilities.AESGCM
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.*
import org.session.libsignal.utilities.Snode
import org.session.libsession.utilities.AESGCM.EncryptionResult
import org.session.libsession.utilities.getBodyForOnionRequest
import org.session.libsession.utilities.getHeadersForOnionRequest
import org.session.libsignal.crypto.getRandomElement
import org.session.libsignal.crypto.getRandomElementOrNull
import org.session.libsignal.utilities.Broadcaster
import org.session.libsignal.utilities.HTTP
import org.session.libsignal.database.LokiAPIDatabaseProtocol

private typealias Path = List<Snode>

/**
 * See the "Onion Requests" section of [The Session Whitepaper](https://arxiv.org/pdf/2002.04609.pdf) for more information.
 */
object OnionRequestAPI {
    private val database: LokiAPIDatabaseProtocol
        get() = SnodeModule.shared.storage
    private val broadcaster: Broadcaster
        get() = SnodeModule.shared.broadcaster
    private val pathFailureCount = mutableMapOf<Path, Int>()
    private val snodeFailureCount = mutableMapOf<Snode, Int>()

    var guardSnodes = setOf<Snode>()
    var paths: List<Path> // Not a set to ensure we consistently show the same path to the user
        get() = database.getOnionRequestPaths()
        set(newValue) {
            if (newValue.isEmpty()) {
                database.clearOnionRequestPaths()
            } else {
                database.setOnionRequestPaths(newValue)
            }
        }

    // region Settings
    /**
     * The number of snodes (including the guard snode) in a path.
     */
    private const val pathSize = 3
    /**
     * The number of times a path can fail before it's replaced.
     */
    private const val pathFailureThreshold = 3
    /**
     * The number of times a snode can fail before it's replaced.
     */
    private const val snodeFailureThreshold = 3
    /**
     * The number of guard snodes required to maintain `targetPathCount` paths.
     */
    private val targetGuardSnodeCount
        get() = targetPathCount // One per path
    /**
     * The number of paths to maintain.
     */
    const val targetPathCount = 2 // A main path and a backup path for the case where the target snode is in the main path
    // endregion

    class HTTPRequestFailedAtDestinationException(val statusCode: Int, val json: Map<*, *>)
        : Exception("HTTP request failed at destination with status code $statusCode.")
    class InsufficientSnodesException : Exception("Couldn't find enough snodes to build a path.")

    private data class OnionBuildingResult(
            val guardSnode: Snode,
            val finalEncryptionResult: EncryptionResult,
            val destinationSymmetricKey: ByteArray
    )

    internal sealed class Destination {
        class Snode(val snode: org.session.libsignal.utilities.Snode) : Destination()
        class Server(val host: String, val target: String, val x25519PublicKey: String, val scheme: String, val port: Int) : Destination()
    }

    // region Private API
    /**
     * Tests the given snode. The returned promise errors out if the snode is faulty; the promise is fulfilled otherwise.
     */
    private fun testSnode(snode: Snode): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        ThreadUtils.queue { // No need to block the shared context for this
            val url = "${snode.address}:${snode.port}/get_stats/v1"
            try {
                val json = HTTP.execute(HTTP.Verb.GET, url, 3)
                val version = json["version"] as? String
                if (version == null) { deferred.reject(Exception("Missing snode version.")); return@queue }
                if (version >= "2.0.7") {
                    deferred.resolve(Unit)
                } else {
                    val message = "Unsupported snode version: $version."
                    Log.d("Loki", message)
                    deferred.reject(Exception(message))
                }
            } catch (exception: Exception) {
                deferred.reject(exception)
            }
        }
        return deferred.promise
    }

    /**
     * Finds `targetGuardSnodeCount` guard snodes to use for path building. The returned promise errors out if not
     * enough (reliable) snodes are available.
     */
    private fun getGuardSnodes(reusableGuardSnodes: List<Snode>): Promise<Set<Snode>, Exception> {
        if (guardSnodes.count() >= targetGuardSnodeCount) {
            return Promise.of(guardSnodes)
        } else {
            Log.d("Loki", "Populating guard snode cache.")
            return SnodeAPI.getRandomSnode().bind { // Just used to populate the snode pool
                var unusedSnodes = SnodeAPI.snodePool.minus(reusableGuardSnodes)
                val reusableGuardSnodeCount = reusableGuardSnodes.count()
                if (unusedSnodes.count() < (targetGuardSnodeCount - reusableGuardSnodeCount)) { throw InsufficientSnodesException() }
                fun getGuardSnode(): Promise<Snode, Exception> {
                    val candidate = unusedSnodes.getRandomElementOrNull()
                        ?: return Promise.ofFail(InsufficientSnodesException())
                    unusedSnodes = unusedSnodes.minus(candidate)
                    Log.d("Loki", "Testing guard snode: $candidate.")
                    // Loop until a reliable guard snode is found
                    val deferred = deferred<Snode, Exception>()
                    testSnode(candidate).success {
                        deferred.resolve(candidate)
                    }.fail {
                        getGuardSnode().success {
                            deferred.resolve(candidate)
                        }.fail { exception ->
                            if (exception is InsufficientSnodesException) {
                                deferred.reject(exception)
                            }
                        }
                    }
                    return deferred.promise
                }
                val promises = (0 until (targetGuardSnodeCount - reusableGuardSnodeCount)).map { getGuardSnode() }
                all(promises).map { guardSnodes ->
                    val guardSnodesAsSet = (guardSnodes + reusableGuardSnodes).toSet()
                    OnionRequestAPI.guardSnodes = guardSnodesAsSet
                    guardSnodesAsSet
                }
            }
        }
    }

    /**
     * Builds and returns `targetPathCount` paths. The returned promise errors out if not
     * enough (reliable) snodes are available.
     */
    private fun buildPaths(reusablePaths: List<Path>): Promise<List<Path>, Exception> {
        Log.d("Loki", "Building onion request paths.")
        broadcaster.broadcast("buildingPaths")
        return SnodeAPI.getRandomSnode().bind { // Just used to populate the snode pool
            val reusableGuardSnodes = reusablePaths.map { it[0] }
            getGuardSnodes(reusableGuardSnodes).map { guardSnodes ->
                var unusedSnodes = SnodeAPI.snodePool.minus(guardSnodes).minus(reusablePaths.flatten())
                val reusableGuardSnodeCount = reusableGuardSnodes.count()
                val pathSnodeCount = (targetGuardSnodeCount - reusableGuardSnodeCount) * pathSize - (targetGuardSnodeCount - reusableGuardSnodeCount)
                if (unusedSnodes.count() < pathSnodeCount) { throw InsufficientSnodesException() }
                // Don't test path snodes as this would reveal the user's IP to them
                guardSnodes.minus(reusableGuardSnodes).map { guardSnode ->
                    val result = listOf( guardSnode ) + (0 until (pathSize - 1)).map {
                        val pathSnode = unusedSnodes.getRandomElement()
                        unusedSnodes = unusedSnodes.minus(pathSnode)
                        pathSnode
                    }
                    Log.d("Loki", "Built new onion request path: $result.")
                    result
                }
            }.map { paths ->
                OnionRequestAPI.paths = paths + reusablePaths
                broadcaster.broadcast("pathsBuilt")
                paths
            }
        }
    }

    /**
     * Returns a `Path` to be used for building an onion request. Builds new paths as needed.
     */
    private fun getPath(snodeToExclude: Snode?): Promise<Path, Exception> {
        if (pathSize < 1) { throw Exception("Can't build path of size zero.") }
        val paths = this.paths
        val guardSnodes = mutableSetOf<Snode>()
        if (paths.isNotEmpty()) {
            guardSnodes.add(paths[0][0])
            if (paths.count() >= 2) {
                guardSnodes.add(paths[1][0])
            }
        }
        OnionRequestAPI.guardSnodes = guardSnodes
        fun getPath(paths: List<Path>): Path {
            if (snodeToExclude != null) {
                return paths.filter { !it.contains(snodeToExclude) }.getRandomElement()
            } else {
                return paths.getRandomElement()
            }
        }
        if (paths.count() >= targetPathCount) {
            return Promise.of(getPath(paths))
        } else if (paths.isNotEmpty()) {
            if (paths.any { !it.contains(snodeToExclude) }) {
                buildPaths(paths) // Re-build paths in the background
                return Promise.of(getPath(paths))
            } else {
                return buildPaths(paths).map { newPaths ->
                    getPath(newPaths)
                }
            }
        } else {
            return buildPaths(listOf()).map { newPaths ->
                getPath(newPaths)
            }
        }
    }

    private fun dropGuardSnode(snode: Snode) {
        guardSnodes = guardSnodes.filter { it != snode }.toSet()
    }

    private fun dropSnode(snode: Snode) {
        // We repair the path here because we can do it sync. In the case where we drop a whole
        // path we leave the re-building up to getPath() because re-building the path in that case
        // is async.
        snodeFailureCount[snode] = 0
        val oldPaths = paths.toMutableList()
        val pathIndex = oldPaths.indexOfFirst { it.contains(snode) }
        if (pathIndex == -1) { return }
        val path = oldPaths[pathIndex].toMutableList()
        val snodeIndex = path.indexOf(snode)
        if (snodeIndex == -1) { return }
        path.removeAt(snodeIndex)
        val unusedSnodes = SnodeAPI.snodePool.minus(oldPaths.flatten())
        if (unusedSnodes.isEmpty()) { throw InsufficientSnodesException() }
        path.add(unusedSnodes.getRandomElement())
        // Don't test the new snode as this would reveal the user's IP
        oldPaths.removeAt(pathIndex)
        val newPaths = oldPaths + listOf( path )
        paths = newPaths
    }

    private fun dropPath(path: Path) {
        pathFailureCount[path] = 0
        val paths = OnionRequestAPI.paths.toMutableList()
        val pathIndex = paths.indexOf(path)
        if (pathIndex == -1) { return }
        paths.removeAt(pathIndex)
        OnionRequestAPI.paths = paths
    }

    /**
     * Builds an onion around `payload` and returns the result.
     */
    private fun buildOnionForDestination(payload: Map<*, *>, destination: Destination): Promise<OnionBuildingResult, Exception> {
        lateinit var guardSnode: Snode
        lateinit var destinationSymmetricKey: ByteArray // Needed by LokiAPI to decrypt the response sent back by the destination
        lateinit var encryptionResult: EncryptionResult
        val snodeToExclude = when (destination) {
            is Destination.Snode -> destination.snode
            is Destination.Server -> null
        }
        return getPath(snodeToExclude).bind { path ->
            guardSnode = path.first()
            // Encrypt in reverse order, i.e. the destination first
            OnionRequestEncryption.encryptPayloadForDestination(payload, destination).bind { r ->
                destinationSymmetricKey = r.symmetricKey
                // Recursively encrypt the layers of the onion (again in reverse order)
                encryptionResult = r
                @Suppress("NAME_SHADOWING") var path = path
                var rhs = destination
                fun addLayer(): Promise<EncryptionResult, Exception> {
                    if (path.isEmpty()) {
                        return Promise.of(encryptionResult)
                    } else {
                        val lhs = Destination.Snode(path.last())
                        path = path.dropLast(1)
                        return OnionRequestEncryption.encryptHop(lhs, rhs, encryptionResult).bind { r ->
                            encryptionResult = r
                            rhs = lhs
                            addLayer()
                        }
                    }
                }
                addLayer()
            }
        }.map { OnionBuildingResult(guardSnode, encryptionResult, destinationSymmetricKey) }
    }

    /**
     * Sends an onion request to `destination`. Builds new paths as needed.
     */
    private fun sendOnionRequest(destination: Destination, payload: Map<*, *>, isJSONRequired: Boolean = true): Promise<Map<*, *>, Exception> {
        val deferred = deferred<Map<*, *>, Exception>()
        lateinit var guardSnode: Snode
        buildOnionForDestination(payload, destination).success { result ->
            guardSnode = result.guardSnode
            val url = "${guardSnode.address}:${guardSnode.port}/onion_req/v2"
            val finalEncryptionResult = result.finalEncryptionResult
            val onion = finalEncryptionResult.ciphertext
            if (destination is Destination.Server && onion.count().toDouble() > 0.75 * FileServerAPIV2.maxFileSize.toDouble()) {
                Log.d("Loki", "Approaching request size limit: ~${onion.count()} bytes.")
            }
            @Suppress("NAME_SHADOWING") val parameters = mapOf(
                "ephemeral_key" to finalEncryptionResult.ephemeralPublicKey.toHexString()
            )
            val body: ByteArray
            try {
                body = OnionRequestEncryption.encode(onion, parameters)
            } catch (exception: Exception) {
                return@success deferred.reject(exception)
            }
            val destinationSymmetricKey = result.destinationSymmetricKey
            ThreadUtils.queue {
                try {
                    val json = HTTP.execute(HTTP.Verb.POST, url, body)
                    val base64EncodedIVAndCiphertext = json["result"] as? String ?: return@queue deferred.reject(Exception("Invalid JSON"))
                    val ivAndCiphertext = Base64.decode(base64EncodedIVAndCiphertext)
                    try {
                        val plaintext = AESGCM.decrypt(ivAndCiphertext, destinationSymmetricKey)
                        try {
                            @Suppress("NAME_SHADOWING") val json = JsonUtil.fromJson(plaintext.toString(Charsets.UTF_8), Map::class.java)
                            val statusCode = json["status_code"] as? Int ?: json["status"] as Int
                            if (statusCode == 406) {
                                @Suppress("NAME_SHADOWING") val body = mapOf( "result" to "Your clock is out of sync with the service node network." )
                                val exception = HTTPRequestFailedAtDestinationException(statusCode, body)
                                return@queue deferred.reject(exception)
                            } else if (json["body"] != null) {
                                @Suppress("NAME_SHADOWING") val body: Map<*, *>
                                if (json["body"] is Map<*, *>) {
                                    body = json["body"] as Map<*, *>
                                } else {
                                    val bodyAsString = json["body"] as String
                                    if (!isJSONRequired) {
                                        body = mapOf( "result" to bodyAsString )
                                    } else {
                                        body = JsonUtil.fromJson(bodyAsString, Map::class.java)
                                    }
                                }
                                if (statusCode != 200) {
                                    val exception = HTTPRequestFailedAtDestinationException(statusCode, body)
                                    return@queue deferred.reject(exception)
                                }
                                deferred.resolve(body)
                            } else {
                                if (statusCode != 200) {
                                    val exception = HTTPRequestFailedAtDestinationException(statusCode, json)
                                    return@queue deferred.reject(exception)
                                }
                                deferred.resolve(json)
                            }
                        } catch (exception: Exception) {
                            deferred.reject(Exception("Invalid JSON: ${plaintext.toString(Charsets.UTF_8)}."))
                        }
                    } catch (exception: Exception) {
                        deferred.reject(exception)
                    }
                } catch (exception: Exception) {
                    deferred.reject(exception)
                }
            }
        }.fail { exception ->
            deferred.reject(exception)
        }
        val promise = deferred.promise
        promise.fail { exception ->
            if (exception is HTTP.HTTPRequestFailedException && SnodeModule.isInitialized) {
                val path = paths.firstOrNull { it.contains(guardSnode) }
                fun handleUnspecificError() {
                    if (path == null) { return }
                    var pathFailureCount = OnionRequestAPI.pathFailureCount[path] ?: 0
                    pathFailureCount += 1
                    if (pathFailureCount >= pathFailureThreshold) {
                        dropGuardSnode(guardSnode)
                        path.forEach { snode ->
                            @Suppress("ThrowableNotThrown")
                            SnodeAPI.handleSnodeError(exception.statusCode, exception.json, snode, null) // Intentionally don't throw
                        }
                        dropPath(path)
                    } else {
                        OnionRequestAPI.pathFailureCount[path] = pathFailureCount
                    }
                }
                val json = exception.json
                val message = json?.get("result") as? String
                val prefix = "Next node not found: "
                if (message != null && message.startsWith(prefix)) {
                    val ed25519PublicKey = message.substringAfter(prefix)
                    val snode = path?.firstOrNull { it.publicKeySet!!.ed25519Key == ed25519PublicKey }
                    if (snode != null) {
                        var snodeFailureCount = OnionRequestAPI.snodeFailureCount[snode] ?: 0
                        snodeFailureCount += 1
                        if (snodeFailureCount >= snodeFailureThreshold) {
                            @Suppress("ThrowableNotThrown")
                            SnodeAPI.handleSnodeError(exception.statusCode, json, snode, null) // Intentionally don't throw
                            try {
                                dropSnode(snode)
                            } catch (exception: Exception) {
                                handleUnspecificError()
                            }
                        } else {
                            OnionRequestAPI.snodeFailureCount[snode] = snodeFailureCount
                        }
                    } else {
                        handleUnspecificError()
                    }
                } else if (destination is Destination.Server && exception.statusCode == 400) {
                    Log.d("Loki","Destination server returned ${exception.statusCode}")
                } else if (message == "Loki Server error") {
                    Log.d("Loki", "message was $message")
                } else { // Only drop snode/path if not receiving above two exception cases
                    handleUnspecificError()
                }
            }
        }
        return promise
    }
    // endregion

    // region Internal API
    /**
     * Sends an onion request to `snode`. Builds new paths as needed.
     */
    internal fun sendOnionRequest(method: Snode.Method, parameters: Map<*, *>, snode: Snode, publicKey: String? = null): Promise<Map<*, *>, Exception> {
        val payload = mapOf( "method" to method.rawValue, "params" to parameters )
        return sendOnionRequest(Destination.Snode(snode), payload).recover { exception ->
            val error = when (exception) {
                is HTTP.HTTPRequestFailedException -> SnodeAPI.handleSnodeError(exception.statusCode, exception.json, snode, publicKey)
                is HTTPRequestFailedAtDestinationException -> SnodeAPI.handleSnodeError(exception.statusCode, exception.json, snode, publicKey)
                else -> null
            }
            if (error != null) { throw error }
            throw exception
        }
    }

    /**
     * Sends an onion request to `server`. Builds new paths as needed.
     *
     * `publicKey` is the hex encoded public key of the user the call is associated with. This is needed for swarm cache maintenance.
     */
    fun sendOnionRequest(request: Request, server: String, x25519PublicKey: String, target: String = "/loki/v3/lsrpc", isJSONRequired: Boolean = true): Promise<Map<*, *>, Exception> {
        val headers = request.getHeadersForOnionRequest()
        val url = request.url()
        val urlAsString = url.toString()
        val host = url.host()
        val endpoint = when {
            server.count() < urlAsString.count() -> urlAsString.substringAfter(server).removePrefix("/")
            else -> ""
        }
        val body = request.getBodyForOnionRequest() ?: "null"
        val payload = mapOf(
            "body" to body,
            "endpoint" to endpoint,
            "method" to request.method(),
            "headers" to headers
        )
        val destination = Destination.Server(host, target, x25519PublicKey, url.scheme(), url.port())
        return sendOnionRequest(destination, payload, isJSONRequired).recover { exception ->
            Log.d("Loki", "Couldn't reach server: $urlAsString due to error: $exception.")
            throw exception
        }
    }
    // endregion
}
