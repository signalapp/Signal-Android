package org.session.libsession.messaging.file_server

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import okhttp3.Request
import org.session.libsession.messaging.utilities.DotNetAPI
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.utilities.*
import java.net.URL

class FileServerAPI(public val server: String, userPublicKey: String, userPrivateKey: ByteArray, private val database: LokiAPIDatabaseProtocol) : DotNetAPI() {

    companion object {
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
