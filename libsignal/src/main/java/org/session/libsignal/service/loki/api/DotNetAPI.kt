package org.session.libsignal.service.loki.api

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.then
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.utilities.DiffieHellman
import org.session.libsignal.service.api.crypto.ProfileCipherOutputStream
import org.session.libsignal.service.api.push.exceptions.NonSuccessfulResponseCodeException
import org.session.libsignal.service.api.push.exceptions.PushNetworkException
import org.session.libsignal.service.api.util.StreamDetails
import org.session.libsignal.service.internal.push.ProfileAvatarData
import org.session.libsignal.service.internal.push.PushAttachmentData
import org.session.libsignal.service.internal.push.http.DigestingRequestBody
import org.session.libsignal.service.internal.push.http.ProfileCipherOutputStreamFactory
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.service.loki.api.fileserver.FileServerAPI
import org.session.libsignal.service.loki.api.onionrequests.OnionRequestAPI
import org.session.libsignal.service.loki.api.utilities.HTTP
import org.session.libsignal.service.loki.database.LokiAPIDatabaseProtocol
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.service.loki.utilities.retryIfNeeded
import org.session.libsignal.utilities.recover
import java.util.*

/**
 * Base class that provides utilities for .NET based APIs.
 */
open class LokiDotNetAPI(internal val userPublicKey: String, private val userPrivateKey: ByteArray, private val apiDatabase: LokiAPIDatabaseProtocol) {

    internal enum class HTTPVerb { GET, PUT, POST, DELETE, PATCH }

    companion object {
        private val authTokenRequestCache = hashMapOf<String, Promise<String, Exception>>()
    }

    public data class UploadResult(val id: Long, val url: String, val digest: ByteArray?)

    public fun getAuthToken(server: String): Promise<String, Exception> {
        val token = apiDatabase.getAuthToken(server)
        if (token != null) { return Promise.of(token) }
        // Avoid multiple token requests to the server by caching
        var promise = authTokenRequestCache[server]
        if (promise == null) {
            promise = requestNewAuthToken(server).bind { submitAuthToken(it, server) }.then { newToken ->
                apiDatabase.setAuthToken(server, newToken)
                newToken
            }.always {
                authTokenRequestCache.remove(server)
            }
            authTokenRequestCache[server] = promise
        }
        return promise
    }

    private fun requestNewAuthToken(server: String): Promise<String, Exception> {
        Log.d("Loki", "Requesting auth token for server: $server.")
        val parameters: Map<String, Any> = mapOf( "pubKey" to userPublicKey )
        return execute(HTTPVerb.GET, server, "loki/v1/get_challenge", false, parameters).map(SnodeAPI.sharedContext) { json ->
            try {
                val base64EncodedChallenge = json["cipherText64"] as String
                val challenge = Base64.decode(base64EncodedChallenge)
                val base64EncodedServerPublicKey = json["serverPubKey64"] as String
                var serverPublicKey = Base64.decode(base64EncodedServerPublicKey)
                // Discard the "05" prefix if needed
                if (serverPublicKey.count() == 33) {
                    val hexEncodedServerPublicKey = Hex.toStringCondensed(serverPublicKey)
                    serverPublicKey = Hex.fromStringCondensed(hexEncodedServerPublicKey.removing05PrefixIfNeeded())
                }
                // The challenge is prefixed by the 16 bit IV
                val tokenAsData = DiffieHellman.decrypt(challenge, serverPublicKey, userPrivateKey)
                val token = tokenAsData.toString(Charsets.UTF_8)
                token
            } catch (exception: Exception) {
                Log.d("Loki", "Couldn't parse auth token for server: $server.")
                throw exception
            }
        }
    }

    private fun submitAuthToken(token: String, server: String): Promise<String, Exception> {
        Log.d("Loki", "Submitting auth token for server: $server.")
        val parameters = mapOf( "pubKey" to userPublicKey, "token" to token )
        return execute(HTTPVerb.POST, server, "loki/v1/submit_challenge", false, parameters, isJSONRequired = false).map { token }
    }

    internal fun execute(verb: HTTPVerb, server: String, endpoint: String, isAuthRequired: Boolean = true, parameters: Map<String, Any> = mapOf(), isJSONRequired: Boolean = true): Promise<Map<*, *>, Exception> {
        fun execute(token: String?): Promise<Map<*, *>, Exception> {
            val sanitizedEndpoint = endpoint.removePrefix("/")
            var url = "$server/$sanitizedEndpoint"
            if (verb == HTTPVerb.GET || verb == HTTPVerb.DELETE) {
                val queryParameters = parameters.map { "${it.key}=${it.value}" }.joinToString("&")
                if (queryParameters.isNotEmpty()) { url += "?$queryParameters" }
            }
            var request = Request.Builder().url(url)
            if (isAuthRequired) {
                if (token == null) { throw IllegalStateException() }
                request = request.header("Authorization", "Bearer $token")
            }
            when (verb) {
                HTTPVerb.GET -> request = request.get()
                HTTPVerb.DELETE -> request = request.delete()
                else -> {
                    val parametersAsJSON = JsonUtil.toJson(parameters)
                    val body = RequestBody.create(MediaType.get("application/json"), parametersAsJSON)
                    when (verb) {
                        HTTPVerb.PUT -> request = request.put(body)
                        HTTPVerb.POST -> request = request.post(body)
                        HTTPVerb.PATCH -> request = request.patch(body)
                        else -> throw IllegalStateException()
                    }
                }
            }
            val serverPublicKeyPromise = if (server == FileServerAPI.shared.server) Promise.of(FileServerAPI.fileServerPublicKey)
                else FileServerAPI.shared.getPublicKeyForOpenGroupServer(server)
            return serverPublicKeyPromise.bind { serverPublicKey ->
                OnionRequestAPI.sendOnionRequest(request.build(), server, serverPublicKey, isJSONRequired = isJSONRequired).recover { exception ->
                    if (exception is HTTP.HTTPRequestFailedException) {
                        val statusCode = exception.statusCode
                        if (statusCode == 401 || statusCode == 403) {
                            apiDatabase.setAuthToken(server, null)
                            throw SnodeAPI.Error.TokenExpired
                        }
                    }
                    throw exception
                }
            }
        }
        return if (isAuthRequired) {
            getAuthToken(server).bind { execute(it) }
        } else {
            execute(null)
        }
    }

    internal fun getUserProfiles(publicKeys: Set<String>, server: String, includeAnnotations: Boolean): Promise<List<Map<*, *>>, Exception> {
        val parameters = mapOf( "include_user_annotations" to includeAnnotations.toInt(), "ids" to publicKeys.joinToString { "@$it" } )
        return execute(HTTPVerb.GET, server, "users", parameters = parameters).map { json ->
            val data = json["data"] as? List<Map<*, *>>
            if (data == null) {
                Log.d("Loki", "Couldn't parse user profiles for: $publicKeys from: $json.")
                throw SnodeAPI.Error.ParsingFailed
            }
            data!! // For some reason the compiler can't infer that this can't be null at this point
        }
    }

    internal fun setSelfAnnotation(server: String, type: String, newValue: Any?): Promise<Map<*, *>, Exception> {
        val annotation = mutableMapOf<String, Any>( "type" to type )
        if (newValue != null) { annotation["value"] = newValue }
        val parameters = mapOf( "annotations" to listOf( annotation ) )
        return execute(HTTPVerb.PATCH, server, "users/me", parameters = parameters)
    }

    @Throws(PushNetworkException::class, NonSuccessfulResponseCodeException::class)
    fun uploadAttachment(server: String, attachment: PushAttachmentData): UploadResult {
        // This function mimics what Signal does in PushServiceSocket
        val contentType = "application/octet-stream"
        val file = DigestingRequestBody(attachment.data, attachment.outputStreamFactory, contentType, attachment.dataSize, attachment.listener)
        Log.d("Loki", "File size: ${attachment.dataSize} bytes.")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("type", "network.loki")
            .addFormDataPart("Content-Type", contentType)
            .addFormDataPart("content", UUID.randomUUID().toString(), file)
            .build()
        val request = Request.Builder().url("$server/files").post(body)
        return upload(server, request) { json -> // Retrying is handled by AttachmentUploadJob
            val data = json["data"] as? Map<*, *>
            if (data == null) {
                Log.d("Loki", "Couldn't parse attachment from: $json.")
                throw SnodeAPI.Error.ParsingFailed
            }
            val id = data["id"] as? Long ?: (data["id"] as? Int)?.toLong() ?: (data["id"] as? String)?.toLong()
            val url = data["url"] as? String
            if (id == null || url == null || url.isEmpty()) {
                Log.d("Loki", "Couldn't parse upload from: $json.")
                throw SnodeAPI.Error.ParsingFailed
            }
            UploadResult(id, url, file.transmittedDigest)
        }.get()
    }

    @Throws(PushNetworkException::class, NonSuccessfulResponseCodeException::class)
    fun uploadProfilePicture(server: String, key: ByteArray, profilePicture: StreamDetails, setLastProfilePictureUpload: () -> Unit): UploadResult {
        val profilePictureUploadData = ProfileAvatarData(profilePicture.stream, ProfileCipherOutputStream.getCiphertextLength(profilePicture.length), profilePicture.contentType, ProfileCipherOutputStreamFactory(key))
        val file = DigestingRequestBody(profilePictureUploadData.data, profilePictureUploadData.outputStreamFactory,
            profilePictureUploadData.contentType, profilePictureUploadData.dataLength, null)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("type", "network.loki")
            .addFormDataPart("Content-Type", "application/octet-stream")
            .addFormDataPart("content", UUID.randomUUID().toString(), file)
            .build()
        val request = Request.Builder().url("$server/files").post(body)
        return retryIfNeeded(4) {
            upload(server, request) { json ->
                val data = json["data"] as? Map<*, *>
                if (data == null) {
                    Log.d("Loki", "Couldn't parse profile picture from: $json.")
                    throw SnodeAPI.Error.ParsingFailed
                }
                val id = data["id"] as? Long ?: (data["id"] as? Int)?.toLong() ?: (data["id"] as? String)?.toLong()
                val url = data["url"] as? String
                if (id == null || url == null || url.isEmpty()) {
                    Log.d("Loki", "Couldn't parse profile picture from: $json.")
                    throw SnodeAPI.Error.ParsingFailed
                }
                setLastProfilePictureUpload()
                UploadResult(id, url, file.transmittedDigest)
            }
        }.get()
    }

    @Throws(PushNetworkException::class, NonSuccessfulResponseCodeException::class)
    private fun upload(server: String, request: Request.Builder, parse: (Map<*, *>) -> UploadResult): Promise<UploadResult, Exception> {
        val promise: Promise<Map<*, *>, Exception>
        if (server == FileServerAPI.shared.server) {
            request.addHeader("Authorization", "Bearer loki")
            // Uploads to the Loki File Server shouldn't include any personally identifiable information, so use a dummy auth token
            promise = OnionRequestAPI.sendOnionRequest(request.build(), FileServerAPI.shared.server, FileServerAPI.fileServerPublicKey)
        } else {
            promise = FileServerAPI.shared.getPublicKeyForOpenGroupServer(server).bind { openGroupServerPublicKey ->
                getAuthToken(server).bind { token ->
                    request.addHeader("Authorization", "Bearer $token")
                    OnionRequestAPI.sendOnionRequest(request.build(), server, openGroupServerPublicKey)
                }
            }
        }
        return promise.map { json ->
            parse(json)
        }.recover { exception ->
            if (exception is HTTP.HTTPRequestFailedException) {
                val statusCode = exception.statusCode
                if (statusCode == 401 || statusCode == 403) {
                    apiDatabase.setAuthToken(server, null)
                }
                throw NonSuccessfulResponseCodeException("Request returned with status code ${exception.statusCode}.")
            }
            throw PushNetworkException(exception)
        }
    }
}

private fun Boolean.toInt(): Int { return if (this) 1 else 0 }
