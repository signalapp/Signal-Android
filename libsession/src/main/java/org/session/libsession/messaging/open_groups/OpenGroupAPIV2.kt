package org.session.libsession.messaging.open_groups

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.databind.type.TypeFactory
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
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2.Error
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.utilities.AESGCM
import org.session.libsignal.service.loki.HTTP
import org.session.libsignal.service.loki.HTTP.Verb.*
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.service.loki.utilities.toHexString
import org.session.libsignal.utilities.Base64.*
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.logging.Log
import org.whispersystems.curve25519.Curve25519
import java.util.*

object OpenGroupAPIV2 {

    private val moderators: HashMap<String, Set<String>> = hashMapOf() // Server URL to (channel ID to set of moderator IDs)
    const val DEFAULT_SERVER = "http://116.203.70.33"
    private const val DEFAULT_SERVER_PUBLIC_KEY = "a03c383cf63c3c4efe67acc52112a6dd734b3a946b9545f488aaa93da7991238"

    val defaultRooms = MutableSharedFlow<List<DefaultGroup>>(replay = 1)

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
                            val image: ByteArray?) {
        fun toJoinUrl(): String = "$DEFAULT_SERVER/$id?public_key=$DEFAULT_SERVER_PUBLIC_KEY"
    }

    data class Info(
            val id: String,
            val name: String,
            val imageID: String?
    )

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class CompactPollRequest(val roomId: String,
                                  val authToken: String,
                                  val fromDeletionServerId: Long?,
                                  val fromMessageServerId: Long?
    )

    data class CompactPollResult(val messages: List<OpenGroupMessageV2>,
                                 val deletions: List<Long>,
                                 val moderators: List<String>
    )

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class MessageDeletion @JvmOverloads constructor(val id: Long = 0,
                                                         val deletedMessageId: Long = 0
    ) {
        companion object {
            val EMPTY = MessageDeletion()
        }
    }

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

    private fun createBody(parameters: Any?): RequestBody? {
        if (parameters == null) return null

        val parametersAsJSON = JsonUtil.toJson(parameters)
        return RequestBody.create(MediaType.get("application/json"), parametersAsJSON)
    }

    private fun send(request: Request, isJsonRequired: Boolean = true): Promise<Map<*, *>, Exception> {
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
                PUT -> requestBuilder.put(createBody(request.parameters)!!)
                POST -> requestBuilder.post(createBody(request.parameters)!!)
                DELETE -> requestBuilder.delete(createBody(request.parameters))
            }

            if (!request.room.isNullOrEmpty()) {
                requestBuilder.header("Room", request.room)
            }

            if (request.useOnionRouting) {
                val publicKey = MessagingModuleConfiguration.shared.storage.getOpenGroupPublicKey(request.server)
                        ?: return Promise.ofFail(Error.NO_PUBLIC_KEY)
                return OnionRequestAPI.sendOnionRequest(requestBuilder.build(), request.server, publicKey, isJSONRequired = isJsonRequired)
                        .fail { e ->
                            if (e is OnionRequestAPI.HTTPRequestFailedAtDestinationException && e.statusCode == 401) {
                                val storage = MessagingModuleConfiguration.shared.storage
                                if (request.room != null) {
                                    storage.removeAuthToken("${request.server}.${request.room}")
                                } else {
                                    storage.removeAuthToken(request.server)
                                }
                            }
                        }
            } else {
                return Promise.ofFail(IllegalStateException("It's currently not allowed to send non onion routed requests."))
            }
        }
        return if (request.isAuthRequired) {
            getAuthToken(request.room!!, request.server).bind { execute(it) }
        } else {
            execute(null)
        }
    }

    fun downloadOpenGroupProfilePicture(roomID: String, server: String): Promise<ByteArray, Exception> {
        val request = Request(verb = GET, room = roomID, server = server, endpoint = "rooms/$roomID/image", isAuthRequired = false)
        return send(request).map { json ->
            val result = json["result"] as? String ?: throw Error.PARSING_FAILED
            decode(result)
        }
    }

    fun getAuthToken(room: String, server: String): Promise<String, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        return storage.getAuthToken(room, server)?.let {
            Promise.of(it)
        } ?: run {
            requestNewAuthToken(room, server)
                    .bind { claimAuthToken(it, room, server) }
                    .success { authToken ->
                        storage.setAuthToken(room, server, authToken)
                    }
        }
    }

    fun requestNewAuthToken(room: String, server: String): Promise<String, Exception> {
        val (publicKey, privateKey) = MessagingModuleConfiguration.shared.storage.getUserKeyPair()
                ?: return Promise.ofFail(Error.GENERIC)
        val queryParameters = mutableMapOf("public_key" to publicKey)
        val request = Request(GET, room, server, "auth_token_challenge", queryParameters, isAuthRequired = false, parameters = null)
        return send(request).map { json ->
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
        val parameters = mapOf("public_key" to MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!)
        val headers = mapOf("Authorization" to authToken)
        val request = Request(verb = POST, room = room, server = server, endpoint = "claim_auth_token",
                parameters = parameters, headers = headers, isAuthRequired = false)
        return send(request).map { authToken }
    }

    fun deleteAuthToken(room: String, server: String): Promise<Unit, Exception> {
        val request = Request(verb = DELETE, room = room, server = server, endpoint = "auth_token")
        return send(request).map {
            MessagingModuleConfiguration.shared.storage.removeAuthToken(room, server)
        }
    }

    // region Sending
    fun upload(file: ByteArray, room: String, server: String): Promise<Long, Exception> {
        val base64EncodedFile = encodeBytes(file)
        val parameters = mapOf("file" to base64EncodedFile)
        val request = Request(verb = POST, room = room, server = server, endpoint = "files", parameters = parameters)
        return send(request).map { json ->
            json["result"] as? Long ?: throw Error.PARSING_FAILED
        }
    }

    fun download(file: Long, room: String, server: String): Promise<ByteArray, Exception> {
        val request = Request(verb = GET, room = room, server = server, endpoint = "files/$file")
        return send(request).map { json ->
            val base64EncodedFile = json["result"] as? String ?: throw Error.PARSING_FAILED
            decode(base64EncodedFile) ?: throw Error.PARSING_FAILED
        }
    }

    fun send(message: OpenGroupMessageV2, room: String, server: String): Promise<OpenGroupMessageV2, Exception> {
        val signedMessage = message.sign() ?: return Promise.ofFail(Error.SIGNING_FAILED)
        val jsonMessage = signedMessage.toJSON()
        val request = Request(verb = POST, room = room, server = server, endpoint = "messages", parameters = jsonMessage)
        return send(request).map { json ->
            @Suppress("UNCHECKED_CAST") val rawMessage = json["message"] as? Map<String, Any>
                    ?: throw Error.PARSING_FAILED
            OpenGroupMessageV2.fromJSON(rawMessage) ?: throw Error.PARSING_FAILED
        }
    }
    // endregion

    // region Messages
    fun getMessages(room: String, server: String): Promise<List<OpenGroupMessageV2>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val queryParameters = mutableMapOf<String, String>()
        storage.getLastMessageServerId(room, server)?.let { lastId ->
            queryParameters += "from_server_id" to lastId.toString()
        }
        val request = Request(verb = GET, room = room, server = server, endpoint = "messages", queryParameters = queryParameters)
        return send(request).map { jsonList ->
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
    @JvmStatic
    fun deleteMessage(serverID: Long, room: String, server: String): Promise<Unit, Exception> {
        val request = Request(verb = DELETE, room = room, server = server, endpoint = "messages/$serverID")
        return send(request).map {
            Log.d("Loki", "Deleted server message")
        }
    }

    fun getDeletedMessages(room: String, server: String): Promise<List<MessageDeletion>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val queryParameters = mutableMapOf<String, String>()
        storage.getLastDeletionServerId(room, server)?.let { last ->
            queryParameters["from_server_id"] = last.toString()
        }
        val request = Request(verb = GET, room = room, server = server, endpoint = "deleted_messages", queryParameters = queryParameters)
        return send(request).map { json ->
            val type = TypeFactory.defaultInstance().constructCollectionType(List::class.java, MessageDeletion::class.java)
            val idsAsString = JsonUtil.toJson(json["ids"])
            val serverIDs = JsonUtil.fromJson<List<MessageDeletion>>(idsAsString, type) ?: throw Error.PARSING_FAILED
            val lastMessageServerId = storage.getLastDeletionServerId(room, server) ?: 0
            val serverID = serverIDs.maxByOrNull {it.id } ?: MessageDeletion.EMPTY
            if (serverID.id > lastMessageServerId) {
                storage.setLastDeletionServerId(room, server, serverID.id)
            }
            serverIDs
        }
    }
    // endregion

    // region Moderation
    private fun handleModerators(serverRoomId: String, moderatorList: List<String>) {
        moderators[serverRoomId] = moderatorList.toMutableSet()
    }

    fun getModerators(room: String, server: String): Promise<List<String>, Exception> {
        val request = Request(verb = GET, room = room, server = server, endpoint = "moderators")
        return send(request).map { json ->
            @Suppress("UNCHECKED_CAST") val moderatorsJson = json["moderators"] as? List<String>
                    ?: throw Error.PARSING_FAILED
            val id = "$server.$room"
            handleModerators(id, moderatorsJson)
            moderatorsJson
        }
    }

    @JvmStatic
    fun ban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val parameters = mapOf("public_key" to publicKey)
        val request = Request(verb = POST, room = room, server = server, endpoint = "block_list", parameters = parameters)
        return send(request).map {
            Log.d("Loki", "Banned user $publicKey from $server.$room")
        }
    }

    fun unban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val request = Request(verb = DELETE, room = room, server = server, endpoint = "block_list/$publicKey")
        return send(request).map {
            Log.d("Loki", "Unbanned user $publicKey from $server.$room")
        }
    }

    @JvmStatic
    fun isUserModerator(publicKey: String, room: String, server: String): Boolean =
            moderators["$server.$room"]?.contains(publicKey) ?: false
    // endregion

    // region General
    @Suppress("UNCHECKED_CAST")
    fun getCompactPoll(rooms: List<String>, server: String): Promise<Map<String, CompactPollResult>, Exception> {
        val requestAuths = rooms.associateWith { room -> getAuthToken(room, server) }
        val storage = MessagingModuleConfiguration.shared.storage
        val requests = rooms.mapNotNull { room ->
            val authToken = try {
                requestAuths[room]?.get()
            } catch (e: Exception) {
                Log.e("Loki", "Failed to get auth token for $room", e)
                null
            } ?: return@mapNotNull null

            CompactPollRequest(roomId = room,
                    authToken = authToken,
                    fromDeletionServerId = storage.getLastDeletionServerId(room, server),
                    fromMessageServerId = storage.getLastMessageServerId(room, server)
            )
        }
        val request = Request(verb = POST, room = null, server = server, endpoint = "compact_poll", isAuthRequired = false, parameters = mapOf("requests" to requests))
        // build a request for all rooms
        return send(request = request).map { json ->
            val results = json["results"] as? List<*> ?: throw Error.PARSING_FAILED

            results.mapNotNull { roomJson ->
                if (roomJson !is Map<*,*>) return@mapNotNull null
                val roomId = roomJson["room_id"] as? String ?: return@mapNotNull null

                // check the status was fine
                val statusCode = roomJson["status_code"] as? Int ?: return@mapNotNull null
                if (statusCode == 401) {
                    // delete auth token and return null
                    storage.removeAuthToken(roomId, server)
                }

                // check and store mods
                val moderators = roomJson["moderators"] as? List<String> ?: return@mapNotNull null
                handleModerators("$server.$roomId", moderators)

                // get deletions
                val type = TypeFactory.defaultInstance().constructCollectionType(List::class.java, MessageDeletion::class.java)
                val idsAsString = JsonUtil.toJson(roomJson["deletions"])
                val deletedServerIDs = JsonUtil.fromJson<List<MessageDeletion>>(idsAsString, type) ?: throw Error.PARSING_FAILED
                val lastDeletionServerId = storage.getLastDeletionServerId(roomId, server) ?: 0
                val serverID = deletedServerIDs.maxByOrNull {it.id } ?: MessageDeletion.EMPTY
                if (serverID.id > lastDeletionServerId) {
                    storage.setLastDeletionServerId(roomId, server, serverID.id)
                }

                // get messages
                val rawMessages = roomJson["messages"] as? List<Map<String, Any>> ?: return@mapNotNull null // parsing failed

                val lastMessageServerId = storage.getLastMessageServerId(roomId, server) ?: 0
                var currentMax = lastMessageServerId
                val messages = rawMessages.mapNotNull { rawMessage ->
                    val message = OpenGroupMessageV2.fromJSON(rawMessage)?.apply {
                        currentMax = maxOf(currentMax,this.serverID ?: 0)
                    }
                    message
                }
                storage.setLastMessageServerId(roomId, server, currentMax)
                roomId to CompactPollResult(
                        messages = messages,
                        deletions = deletedServerIDs.map { it.deletedMessageId },
                        moderators = moderators
                )
            }.toMap()
        }
    }

    fun getDefaultRoomsIfNeeded(): Promise<List<DefaultGroup>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        storage.setOpenGroupPublicKey(DEFAULT_SERVER, DEFAULT_SERVER_PUBLIC_KEY)
        return getAllRooms(DEFAULT_SERVER).map { groups ->
            val earlyGroups = groups.map { group ->
                DefaultGroup(group.id, group.name, null)
            }
            // see if we have any cached rooms, and if they already have images, don't overwrite with early non-image results
            defaultRooms.replayCache.firstOrNull()?.let { replayed ->
                if (replayed.none { it.image?.isNotEmpty() == true}) {
                    defaultRooms.tryEmit(earlyGroups)
                }
            }
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
        return send(request).map { json ->
            val rawRoom = json["room"] as? Map<*, *> ?: throw Error.PARSING_FAILED
            val id = rawRoom["id"] as? String ?: throw Error.PARSING_FAILED
            val name = rawRoom["name"] as? String ?: throw Error.PARSING_FAILED
            val imageID = rawRoom["image_id"] as? String
            Info(id = id, name = name, imageID = imageID)
        }
    }

    fun getAllRooms(server: String): Promise<List<Info>, Exception> {
        val request = Request(verb = GET, room = null, server = server, endpoint = "rooms", isAuthRequired = false)
        return send(request).map { json ->
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
        return send(request).map { json ->
            val memberCount = json["member_count"] as? Long ?: throw Error.PARSING_FAILED
            val storage = MessagingModuleConfiguration.shared.storage
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