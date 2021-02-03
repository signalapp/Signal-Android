package org.session.libsession.messaging.fileserver

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import okhttp3.Request
import org.session.libsession.messaging.utilities.DotNetAPI
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.service.loki.api.onionrequests.OnionRequestAPI
import org.session.libsignal.service.loki.database.LokiAPIDatabaseProtocol
import org.session.libsignal.service.loki.protocol.shelved.multidevice.DeviceLink
import org.session.libsignal.service.loki.utilities.*
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class FileServerAPI(public val server: String, userPublicKey: String, userPrivateKey: ByteArray, private val database: LokiAPIDatabaseProtocol) : DotNetAPI() {

    companion object {
        // region Settings
        /**
         * Deprecated.
         */
        private val deviceLinkType = "network.loki.messenger.devicemapping"
        /**
         * Deprecated.
         */
        private val deviceLinkRequestCache = ConcurrentHashMap<String, Promise<Set<DeviceLink>, Exception>>()
        /**
         * Deprecated.
         */
        private val deviceLinkUpdateInterval = 60 * 1000
        private val lastDeviceLinkUpdate = ConcurrentHashMap<String, Long>()

        internal val fileServerPublicKey = "62509D59BDEEC404DD0D489C1E15BA8F94FD3D619B01C1BF48A9922BFCB7311C"
        internal val maxRetryCount = 4

        public val maxFileSize = 10_000_000 // 10 MB
        /**
         * The file server has a file size limit of `maxFileSize`, which the Service Nodes try to enforce as well. However, the limit applied by the Service Nodes
         * is on the **HTTP request** and not the actual file size. Because the file server expects the file data to be base 64 encoded, the size of the HTTP
         * request for a given file will be at least `ceil(n / 3) * 4` bytes, where n is the file size in bytes. This is the minimum size because there might also
         * be other parameters in the request. On average the multiplier appears to be about 1.5, so when checking whether the file will exceed the file size limit when
         * uploading a file we just divide the size of the file by this number. The alternative would be to actually check the size of the HTTP request but that's only
         * possible after proof of work has been calculated and the onion request encryption has happened, which takes several seconds.
         */
        public val fileSizeORMultiplier = 2 // TODO: It should be possible to set this to 1.5?
        val server = "https://file.getsession.org"
        public val fileStorageBucketURL = "https://file-static.lokinet.org"
        // endregion

        // region Initialization
        lateinit var shared: FileServerAPI

        /**
         * Must be called before `LokiAPI` is used.
         */
        fun configure(userPublicKey: String, userPrivateKey: ByteArray, database: LokiAPIDatabaseProtocol) {
            if (Companion::shared.isInitialized) { return }
            val server = "https://file.getsession.org"
            shared = FileServerAPI(server, userPublicKey, userPrivateKey, database)
        }
        // endregion
    }

    // region Device Link Update Result
    sealed class DeviceLinkUpdateResult {
        class Success(val publicKey: String, val deviceLinks: Set<DeviceLink>) : DeviceLinkUpdateResult()
        class Failure(val publicKey: String, val error: Exception) : DeviceLinkUpdateResult()
    }
    // endregion

    // region API
    public fun hasDeviceLinkCacheExpired(referenceTime: Long = System.currentTimeMillis(), publicKey: String): Boolean {
        return !lastDeviceLinkUpdate.containsKey(publicKey) || (referenceTime - lastDeviceLinkUpdate[publicKey]!! > deviceLinkUpdateInterval)
    }

    fun getDeviceLinks(publicKey: String, isForcedUpdate: Boolean = false): Promise<Set<DeviceLink>, Exception> {
        return Promise.of(setOf())
        /*
        if (deviceLinkRequestCache.containsKey(publicKey) && !isForcedUpdate) {
            val result = deviceLinkRequestCache[publicKey]
            if (result != null) { return result } // A request was already pending
        }
        val promise = getDeviceLinks(setOf(publicKey), isForcedUpdate)
        deviceLinkRequestCache[publicKey] = promise
        promise.always {
            deviceLinkRequestCache.remove(publicKey)
        }
        return promise
         */
    }

    fun getDeviceLinks(publicKeys: Set<String>, isForcedUpdate: Boolean = false): Promise<Set<DeviceLink>, Exception> {
        return Promise.of(setOf())
        /*
        val validPublicKeys = publicKeys.filter { PublicKeyValidation.isValid(it) }
        val now = System.currentTimeMillis()
        // IMPORTANT: Don't fetch device links for the current user (i.e. don't remove the it != userHexEncodedPublicKey) check below
        val updatees = validPublicKeys.filter { it != userPublicKey && (hasDeviceLinkCacheExpired(now, it) || isForcedUpdate) }.toSet()
        val cachedDeviceLinks = validPublicKeys.minus(updatees).flatMap { database.getDeviceLinks(it) }.toSet()
        if (updatees.isEmpty()) {
            return Promise.of(cachedDeviceLinks)
        } else {
            return getUserProfiles(updatees, server, true).map(SnodeAPI.sharedContext) { data ->
                data.map dataMap@ { node ->
                    val publicKey = node["username"] as String
                    val annotations = node["annotations"] as List<Map<*, *>>
                    val deviceLinksAnnotation = annotations.find {
                        annotation -> (annotation["type"] as String) == deviceLinkType
                    } ?: return@dataMap DeviceLinkUpdateResult.Success(publicKey, setOf())
                    val value = deviceLinksAnnotation["value"] as Map<*, *>
                    val deviceLinksAsJSON = value["authorisations"] as List<Map<*, *>>
                    val deviceLinks = deviceLinksAsJSON.mapNotNull { deviceLinkAsJSON ->
                        try {
                            val masterPublicKey = deviceLinkAsJSON["primaryDevicePubKey"] as String
                            val slavePublicKey = deviceLinkAsJSON["secondaryDevicePubKey"] as String
                            var requestSignature: ByteArray? = null
                            var authorizationSignature: ByteArray? = null
                            if (deviceLinkAsJSON["requestSignature"] != null) {
                                val base64EncodedSignature = deviceLinkAsJSON["requestSignature"] as String
                                requestSignature = Base64.decode(base64EncodedSignature)
                            }
                            if (deviceLinkAsJSON["grantSignature"] != null) {
                                val base64EncodedSignature = deviceLinkAsJSON["grantSignature"] as String
                                authorizationSignature = Base64.decode(base64EncodedSignature)
                            }
                            val deviceLink = DeviceLink(masterPublicKey, slavePublicKey, requestSignature, authorizationSignature)
                            val isValid = deviceLink.verify()
                            if (!isValid) {
                                Log.d("Loki", "Ignoring invalid device link: $deviceLinkAsJSON.")
                                return@mapNotNull null
                            }
                            deviceLink
                        } catch (e: Exception) {
                            Log.d("Loki", "Failed to parse device links for $publicKey from $deviceLinkAsJSON due to error: $e.")
                            null
                        }
                    }.toSet()
                    DeviceLinkUpdateResult.Success(publicKey, deviceLinks)
                }
            }.recover { e ->
                publicKeys.map { DeviceLinkUpdateResult.Failure(it, e) }
            }.success { updateResults ->
                for (updateResult in updateResults) {
                    if (updateResult is DeviceLinkUpdateResult.Success) {
                        database.clearDeviceLinks(updateResult.publicKey)
                        updateResult.deviceLinks.forEach { database.addDeviceLink(it) }
                    } else {
                        // Do nothing
                    }
                }
            }.map(SnodeAPI.sharedContext) { updateResults ->
                val deviceLinks = mutableListOf<DeviceLink>()
                for (updateResult in updateResults) {
                    when (updateResult) {
                        is DeviceLinkUpdateResult.Success -> {
                            lastDeviceLinkUpdate[updateResult.publicKey] = now
                            deviceLinks.addAll(updateResult.deviceLinks)
                        }
                        is DeviceLinkUpdateResult.Failure -> {
                            if (updateResult.error is SnodeAPI.Error.ParsingFailed) {
                                lastDeviceLinkUpdate[updateResult.publicKey] = now // Don't infinitely update in case of a parsing failure
                            }
                            deviceLinks.addAll(database.getDeviceLinks(updateResult.publicKey)) // Fall back on cached device links in case of a failure
                        }
                    }
                }
                // Updatees that didn't show up in the response provided by the file server are assumed to not have any device links
                val excludedUpdatees = updatees.filter { updatee ->
                    updateResults.find { updateResult ->
                        when (updateResult) {
                            is DeviceLinkUpdateResult.Success -> updateResult.publicKey == updatee
                            is DeviceLinkUpdateResult.Failure -> updateResult.publicKey == updatee
                        }
                    } == null
                }
                excludedUpdatees.forEach {
                    lastDeviceLinkUpdate[it] = now
                }
                deviceLinks.union(cachedDeviceLinks)
            }.recover {
                publicKeys.flatMap { database.getDeviceLinks(it) }.toSet()
            }
        }
         */
    }

    fun setDeviceLinks(deviceLinks: Set<DeviceLink>): Promise<Unit, Exception> {
        return Promise.of(Unit)
        /*
        val isMaster = deviceLinks.find { it.masterPublicKey == userPublicKey } != null
        val deviceLinksAsJSON = deviceLinks.map { it.toJSON() }
        val value = if (deviceLinks.isNotEmpty()) mapOf( "isPrimary" to isMaster, "authorisations" to deviceLinksAsJSON ) else null
        val annotation = mapOf( "type" to deviceLinkType, "value" to value )
        val parameters = mapOf( "annotations" to listOf( annotation ) )
        return retryIfNeeded(maxRetryCount) {
            execute(HTTPVerb.PATCH, server, "/users/me", parameters = parameters)
        }.map { Unit }
         */
    }

    fun addDeviceLink(deviceLink: DeviceLink): Promise<Unit, Exception> {
        return Promise.of(Unit)
        /*
        Log.d("Loki", "Updating device links.")
        return getDeviceLinks(userPublicKey, true).bind { deviceLinks ->
            val mutableDeviceLinks = deviceLinks.toMutableSet()
            mutableDeviceLinks.add(deviceLink)
            setDeviceLinks(mutableDeviceLinks)
        }.success {
            database.addDeviceLink(deviceLink)
        }.map { Unit }
         */
    }

    fun removeDeviceLink(deviceLink: DeviceLink): Promise<Unit, Exception> {
        return Promise.of(Unit)
        /*
        Log.d("Loki", "Updating device links.")
        return getDeviceLinks(userPublicKey, true).bind { deviceLinks ->
            val mutableDeviceLinks = deviceLinks.toMutableSet()
            mutableDeviceLinks.remove(deviceLink)
            setDeviceLinks(mutableDeviceLinks)
        }.success {
            database.removeDeviceLink(deviceLink)
        }.map { Unit }
         */
    }
    // endregion

    // region Open Group Server Public Key
    fun getPublicKeyForOpenGroupServer(openGroupServer: String): Promise<String, Exception> {
        val publicKey = database.getOpenGroupPublicKey(openGroupServer)
        if (publicKey != null && PublicKeyValidation.isValid(publicKey, 64, false)) {
            return Promise.of(publicKey)
        } else {
            val url = "$server/loki/v1/getOpenGroupKey/${URL(openGroupServer).host}"
            val request = Request.Builder().url(url)
            request.addHeader("Content-Type", "application/json")
            request.addHeader("Authorization", "Bearer loki") // Tokenless request; use a dummy token
            return OnionRequestAPI.sendOnionRequest(request.build(), server, fileServerPublicKey).map { json ->
                try {
                    val bodyAsString = json["data"] as String
                    val body = JsonUtil.fromJson(bodyAsString)
                    val base64EncodedPublicKey = body.get("data").asText()
                    val prefixedPublicKey = Base64.decode(base64EncodedPublicKey)
                    val hexEncodedPrefixedPublicKey = prefixedPublicKey.toHexString()
                    val result = hexEncodedPrefixedPublicKey.removing05PrefixIfNeeded()
                    database.setOpenGroupPublicKey(openGroupServer, result)
                    result
                } catch (exception: Exception) {
                    Log.d("Loki", "Couldn't parse open group public key from: $json.")
                    throw exception
                }
            }
        }
    }
}
