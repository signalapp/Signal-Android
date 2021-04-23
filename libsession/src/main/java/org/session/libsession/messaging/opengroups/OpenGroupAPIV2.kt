package org.session.libsession.messaging.opengroups

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import nl.komponents.kovenant.Kovenant
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.fileserver.FileServerAPI
import org.session.libsession.messaging.opengroups.OpenGroupAPIV2.Error
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.utilities.AESGCM
import org.session.libsignal.service.loki.api.utilities.HTTP
import org.session.libsignal.service.loki.api.utilities.HTTP.Verb.*
import org.session.libsignal.service.loki.utilities.DownloadUtilities
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.service.loki.utilities.toHexString
import org.session.libsignal.utilities.Base64.*
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.createContext
import org.session.libsignal.utilities.logging.Log
import org.whispersystems.curve25519.Curve25519
import java.io.ByteArrayOutputStream
import java.util.*

object OpenGroupAPIV2 {

    private val moderators: HashMap<String, Set<String>> = hashMapOf() // Server URL to (channel ID to set of moderator IDs)
    const val DEFAULT_SERVER = "https://sog.ibolpap.finance"
    private const val DEFAULT_SERVER_PUBLIC_KEY = "b464aa186530c97d6bcf663a3a3b7465a5f782beaa67c83bee99468824b4aa10"

    val defaultRooms = MutableSharedFlow<List<DefaultGroup>>(replay = 1)

    private val sharedContext = Kovenant.createContext()
    private val curve = Curve25519.getInstance(Curve25519.BEST)

    sealed class Error : Exception() {
        object GENERIC : Error()
        object PARSING_FAILED : Error()
        object DECRYPTION_FAILED : Error()
        object SIGNING_FAILED : Error()
        object INVALID_URL : Error()
        object NO_PUBLIC_KEY : Error()
    }

    data class DefaultGroup(val id: String,
                            val name: String,
                            val image: ByteArray?)

    data class Info(
            val id: String,
            val name: String,
            val imageID: String?
    )

    data class CompactPollResult(val messages: List<OpenGroupMessageV2>,
                                 val deletions: List<Long>,
                                 val moderators: List<String>
    )

    data class Request(
            val verb: HTTP.Verb,
            val room: String?,
            val server: String,
            val endpoint: String,
            val queryParameters: Map<String, String> = mapOf(),
            val parameters: Any? = null,
            val headers: Map<String, String> = mapOf(),
            val isAuthRequired: Boolean = true,
            // Always `true` under normal circumstances. You might want to disable
            // this when running over Lokinet.
            val useOnionRouting: Boolean = true
    )

    private fun createBody(parameters: Any): RequestBody {
        val parametersAsJSON = JsonUtil.toJson(parameters)
        return RequestBody.create(MediaType.get("application/json"), parametersAsJSON)
    }

    private fun send(request: Request): Promise<Map<*, *>, Exception> {
        val parsed = HttpUrl.parse(request.server) ?: return Promise.ofFail(Error.INVALID_URL)
        val urlBuilder = HttpUrl.Builder()
                .scheme(parsed.scheme())
                .host(parsed.host())
                .addPathSegments(request.endpoint)

        if (request.verb == GET) {
            for ((key, value) in request.queryParameters) {
                urlBuilder.addQueryParameter(key, value)
            }
        }

        fun execute(token: String?): Promise<Map<*, *>, Exception> {
            val requestBuilder = okhttp3.Request.Builder()
                    .url(urlBuilder.build())
                    .headers(Headers.of(request.headers))
            if (request.isAuthRequired) {
                if (token.isNullOrEmpty()) throw IllegalStateException("No auth token for request")
                requestBuilder.header("Authorization", token)
            }
            when (request.verb) {
                GET -> requestBuilder.get()
                PUT -> requestBuilder.put(createBody(request.parameters!!))
                POST -> requestBuilder.post(createBody(request.parameters!!))
                DELETE -> requestBuilder.delete(createBody(request.parameters!!))
            }

            if (!request.room.isNullOrEmpty()) {
                requestBuilder.header("Room", request.room)
            }

            if (request.useOnionRouting) {
                val publicKey = MessagingConfiguration.shared.storage.getOpenGroupPublicKey(request.server)
                        ?: return Promise.ofFail(Error.NO_PUBLIC_KEY)
                return OnionRequestAPI.sendOnionRequest(requestBuilder.build(), request.server, publicKey)
                        .fail { e ->
                            if (e is OnionRequestAPI.HTTPRequestFailedAtDestinationException
                                    && e.statusCode == 401) {
                                MessagingConfiguration.shared.storage.removeAuthToken(request.server)
                            }
                        }
            } else {
                return Promise.ofFail(IllegalStateException("It's currently not allowed to send non onion routed requests."))
            }
        }
        return if (request.isAuthRequired) {
            getAuthToken(request.room!!, request.server).bind(sharedContext) { execute(it) }
        } else {
            execute(null)
        }
    }

    fun downloadOpenGroupProfilePicture(imageUrl: String): ByteArray? {
        Log.d("Loki", "Downloading open group profile picture from \"$imageUrl\".")
        val outputStream = ByteArrayOutputStream()
        try {
            DownloadUtilities.downloadFile(outputStream, imageUrl, FileServerAPI.maxFileSize, null)
            Log.d("Loki", "Open group profile picture was successfully loaded from \"$imageUrl\"")
            return outputStream.toByteArray()
        } catch (e: Exception) {
            Log.d("Loki", "Failed to download open group profile picture from \"$imageUrl\" due to error: $e.")
            return null
        } finally {
            outputStream.close()
        }
    }

    fun downloadOpenGroupProfilePicture(roomID: String, server: String): Promise<ByteArray, Exception> {
        val request = Request(verb = GET, room = roomID, server = server, endpoint = "rooms/$roomID/image", isAuthRequired = false)
        return send(request).map(sharedContext) { json ->
            val result = json["result"] as? String ?: throw Error.PARSING_FAILED
            decode(result)
        }
    }

    fun getAuthToken(room: String, server: String): Promise<String, Exception> {
        val storage = MessagingConfiguration.shared.storage
        return storage.getAuthToken(room, server)?.let {
            Promise.of(it)
        } ?: run {
            requestNewAuthToken(room, server)
                    .bind(sharedContext) { claimAuthToken(it, room, server) }
                    .success { authToken ->
                        storage.setAuthToken(room, server, authToken)
                    }
        }
    }

    fun requestNewAuthToken(room: String, server: String): Promise<String, Exception> {
        val (publicKey, privateKey) = MessagingConfiguration.shared.storage.getUserKeyPair()
                ?: return Promise.ofFail(Error.GENERIC)
        val queryParameters = mutableMapOf("public_key" to publicKey)
        val request = Request(GET, room, server, "auth_token_challenge", queryParameters, isAuthRequired = false, parameters = null)
        return send(request).map(sharedContext) { json ->
            val challenge = json["challenge"] as? Map<*, *> ?: throw Error.PARSING_FAILED
            val base64EncodedCiphertext = challenge["ciphertext"] as? String
                    ?: throw Error.PARSING_FAILED
            val base64EncodedEphemeralPublicKey = challenge["ephemeral_public_key"] as? String
                    ?: throw Error.PARSING_FAILED
            val ciphertext = decode(base64EncodedCiphertext)
            val ephemeralPublicKey = decode(base64EncodedEphemeralPublicKey)
            val symmetricKey = AESGCM.generateSymmetricKey(ephemeralPublicKey, privateKey)
            val tokenAsData = try {
                AESGCM.decrypt(ciphertext, symmetricKey)
            } catch (e: Exception) {
                throw Error.DECRYPTION_FAILED
            }
            tokenAsData.toHexString()
        }
    }

    fun claimAuthToken(authToken: String, room: String, server: String): Promise<String, Exception> {
        val parameters = mapOf("public_key" to MessagingConfiguration.shared.storage.getUserPublicKey()!!)
        val headers = mapOf("Authorization" to authToken)
        val request = Request(verb = POST, room = room, server = server, endpoint = "claim_auth_token",
                parameters = parameters, headers = headers, isAuthRequired = false)
        return send(request).map(sharedContext) { authToken }
    }

    fun deleteAuthToken(room: String, server: String): Promise<Unit, Exception> {
        val request = Request(verb = DELETE, room = room, server = server, endpoint = "auth_token")
        return send(request).map(sharedContext) {
            MessagingConfiguration.shared.storage.removeAuthToken(room, server)
        }
    }

    // region Sending
    fun upload(file: ByteArray, room: String, server: String): Promise<Long, Exception> {
        val base64EncodedFile = encodeBytes(file)
        val parameters = mapOf("file" to base64EncodedFile)
        val request = Request(verb = POST, room = room, server = server, endpoint = "files", parameters = parameters)
        return send(request).map(sharedContext) { json ->
            json["result"] as? Long ?: throw Error.PARSING_FAILED
        }
    }

    fun download(file: Long, room: String, server: String): Promise<ByteArray, Exception> {
        val request = Request(verb = GET, room = room, server = server, endpoint = "files/$file")
        return send(request).map(sharedContext) { json ->
            val base64EncodedFile = json["result"] as? String ?: throw Error.PARSING_FAILED
            decode(base64EncodedFile) ?: throw Error.PARSING_FAILED
        }
    }

    fun send(message: OpenGroupMessageV2, room: String, server: String): Promise<OpenGroupMessageV2, Exception> {
        val signedMessage = message.sign() ?: return Promise.ofFail(Error.SIGNING_FAILED)
        val jsonMessage = signedMessage.toJSON()
        val request = Request(verb = POST, room = room, server = server, endpoint = "messages", parameters = jsonMessage)
        return send(request).map(sharedContext) { json ->
            @Suppress("UNCHECKED_CAST") val rawMessage = json["message"] as? Map<String, Any>
                    ?: throw Error.PARSING_FAILED
            OpenGroupMessageV2.fromJSON(rawMessage) ?: throw Error.PARSING_FAILED
        }
    }
    // endregion

    // region Messages
    fun getMessages(room: String, server: String): Promise<List<OpenGroupMessageV2>, Exception> {
        val storage = MessagingConfiguration.shared.storage
        val queryParameters = mutableMapOf<String, String>()
        storage.getLastMessageServerId(room, server)?.let { lastId ->
            queryParameters += "from_server_id" to lastId.toString()
        }
        val request = Request(verb = GET, room = room, server = server, endpoint = "messages", queryParameters = queryParameters)
        return send(request).map(sharedContext) { jsonList ->
            @Suppress("UNCHECKED_CAST") val rawMessages = jsonList["messages"] as? List<Map<String, Any>>
                    ?: throw Error.PARSING_FAILED
            val lastMessageServerId = storage.getLastMessageServerId(room, server) ?: 0

            var currentMax = lastMessageServerId
            val messages = rawMessages.mapNotNull { json ->
                try {
                    val message = OpenGroupMessageV2.fromJSON(json) ?: return@mapNotNull null
                    if (message.serverID == null || message.sender.isNullOrEmpty()) return@mapNotNull null
                    val sender = message.sender
                    val data = decode(message.base64EncodedData)
                    val signature = decode(message.base64EncodedSignature)
                    val publicKey = Hex.fromStringCondensed(sender.removing05PrefixIfNeeded())
                    val isValid = curve.verifySignature(publicKey, data, signature)
                    if (!isValid) {
                        Log.d("Loki", "Ignoring message with invalid signature")
                        return@mapNotNull null
                    }
                    if (message.serverID > lastMessageServerId) {
                        currentMax = message.serverID
                    }
                    message
                } catch (e: Exception) {
                    null
                }
            }
            storage.setLastMessageServerId(room, server, currentMax)
            messages
        }
    }
    // endregion

    // region Message Deletion
    fun deleteMessage(serverID: Long, room: String, server: String): Promise<Unit, Exception> {
        val request = Request(verb = DELETE, room = room, server = server, endpoint = "message/$serverID")
        return send(request).map(sharedContext) {
            Log.d("Loki", "Deleted server message")
        }
    }

    fun getDeletedMessages(room: String, server: String): Promise<List<Long>, Exception> {
        val storage = MessagingConfiguration.shared.storage
        val queryParameters = mutableMapOf<String, String>()
        storage.getLastDeletionServerId(room, server)?.let { last ->
            queryParameters["from_server_id"] = last.toString()
        }
        val request = Request(verb = GET, room = room, server = server, endpoint = "deleted_messages", queryParameters = queryParameters)
        return send(request).map(sharedContext) { json ->
            @Suppress("UNCHECKED_CAST") val serverIDs = json["ids"] as? List<Long>
                    ?: throw Error.PARSING_FAILED
            val lastMessageServerId = storage.getLastMessageServerId(room, server) ?: 0
            val serverID = serverIDs.maxOrNull() ?: 0
            if (serverID > lastMessageServerId) {
                storage.setLastDeletionServerId(room, server, serverID)
            }
            serverIDs
        }
    }
    // endregion

    // region Moderation
    fun getModerators(room: String, server: String): Promise<List<String>, Exception> {
        val request = Request(verb = GET, room = room, server = server, endpoint = "moderators")
        return send(request).map(sharedContext) { json ->
            @Suppress("UNCHECKED_CAST") val moderatorsJson = json["moderators"] as? List<String>
                    ?: throw Error.PARSING_FAILED
            val id = "$server.$room"
            moderators[id] = moderatorsJson.toMutableSet()
            moderatorsJson
        }
    }

    fun ban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val parameters = mapOf("public_key" to publicKey)
        val request = Request(verb = POST, room = room, server = server, endpoint = "block_list", parameters = parameters)
        return send(request).map(sharedContext) {
            Log.d("Loki", "Banned user $publicKey from $server.$room")
        }
    }

    fun unban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val request = Request(verb = DELETE, room = room, server = server, endpoint = "block_list/$publicKey")
        return send(request).map(sharedContext) {
            Log.d("Loki", "Unbanned user $publicKey from $server.$room")
        }
    }

    @JvmStatic
    fun isUserModerator(publicKey: String, room: String, server: String): Boolean =
            moderators["$server.$room"]?.contains(publicKey) ?: false
    // endregion

    // region General
//    fun getCompactPoll(): Promise<CompactPollResult, Exception> {
//        val request = Request()
//    }

    fun getDefaultRoomsIfNeeded(): Promise<List<DefaultGroup>, Exception> {
        val storage = MessagingConfiguration.shared.storage
        storage.setOpenGroupPublicKey(DEFAULT_SERVER, DEFAULT_SERVER_PUBLIC_KEY)
        return getAllRooms(DEFAULT_SERVER).map { groups ->
            val images = groups.map { group ->
                group.id to downloadOpenGroupProfilePicture(group.id, DEFAULT_SERVER)
            }.toMap()

            groups.map { group ->
                val image = try {
                    images[group.id]!!.get()
                } catch (e: Exception) {
                    // no image or image failed to download
                    null
                }
                DefaultGroup(group.id, group.name, image)
            }
        }.success { new ->
            defaultRooms.tryEmit(new)
        }
    }

    fun getInfo(room: String, server: String): Promise<Info, Exception> {
        val request = Request(verb = GET, room = null, server = server, endpoint = "rooms/$room", isAuthRequired = false)
        return send(request).map(sharedContext) { json ->
            val rawRoom = json["room"] as? Map<*, *> ?: throw Error.PARSING_FAILED
            val id = rawRoom["id"] as? String ?: throw Error.PARSING_FAILED
            val name = rawRoom["name"] as? String ?: throw Error.PARSING_FAILED
            val imageID = rawRoom["image_id"] as? String
            Info(id = id, name = name, imageID = imageID)
        }
    }

    fun getAllRooms(server: String): Promise<List<Info>, Exception> {
        val request = Request(verb = GET, room = null, server = server, endpoint = "rooms", isAuthRequired = false)
        return send(request).map(sharedContext) { json ->
            val rawRooms = json["rooms"] as? List<Map<*, *>> ?: throw Error.PARSING_FAILED
            rawRooms.mapNotNull {
                val roomJson = it as? Map<*, *> ?: return@mapNotNull null
                val id = roomJson["id"] as? String ?: return@mapNotNull null
                val name = roomJson["name"] as? String ?: return@mapNotNull null
                val imageId = roomJson["image_id"] as? String
                Info(id, name, imageId)
            }
        }
    }

    fun getMemberCount(room: String, server: String): Promise<Long, Exception> {
        val request = Request(verb = GET, room = room, server = server, endpoint = "member_count")
        return send(request).map(sharedContext) { json ->
            val memberCount = json["member_count"] as? Long ?: throw Error.PARSING_FAILED
            val storage = MessagingConfiguration.shared.storage
            storage.setUserCount(room, server, memberCount)
            memberCount
        }
    }
    // endregion

}

fun Error.errorDescription() = when (this) {
    Error.GENERIC -> "An error occurred."
    Error.PARSING_FAILED -> "Invalid response."
    Error.DECRYPTION_FAILED -> "Couldn't decrypt response."
    Error.SIGNING_FAILED -> "Couldn't sign message."
    Error.INVALID_URL -> "Invalid URL."
    Error.NO_PUBLIC_KEY -> "Couldn't find server public key."
}