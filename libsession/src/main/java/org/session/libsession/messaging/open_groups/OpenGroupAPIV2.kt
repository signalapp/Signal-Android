package org.session.libsession.messaging.open_groups

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.databind.type.TypeFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.sending_receiving.pollers.OpenGroupPollerV2
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.utilities.AESGCM
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.HTTP
import org.session.libsignal.utilities.HTTP.Verb.*
import org.session.libsignal.utilities.removing05PrefixIfNeeded
import org.session.libsignal.utilities.toHexString
import org.session.libsignal.utilities.Base64.*
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.whispersystems.curve25519.Curve25519
import java.util.*

object OpenGroupAPIV2 {
    private val moderators: HashMap<String, Set<String>> = hashMapOf() // Server URL to (channel ID to set of moderator IDs)
    private val curve = Curve25519.getInstance(Curve25519.BEST)
    val defaultRooms = MutableSharedFlow<List<DefaultGroup>>(replay = 1)
    private val hasPerformedInitialPoll = mutableMapOf<String, Boolean>()
    private var hasUpdatedLastOpenDate = false

    private val timeSinceLastOpen by lazy {
        val context = MessagingModuleConfiguration.shared.context
        val lastOpenDate = TextSecurePreferences.getLastOpenTimeDate(context)
        val now = System.currentTimeMillis()
        now - lastOpenDate
    }

    private const val defaultServerPublicKey = "a03c383cf63c3c4efe67acc52112a6dd734b3a946b9545f488aaa93da7991238"
    const val defaultServer = "http://116.203.70.33"

    sealed class Error(message: String) : Exception(message) {
        object Generic : Error("An error occurred.")
        object ParsingFailed : Error("Invalid response.")
        object DecryptionFailed : Error("Couldn't decrypt response.")
        object SigningFailed : Error("Couldn't sign message.")
        object InvalidURL : Error("Invalid URL.")
        object NoPublicKey : Error("Couldn't find server public key.")
    }

    data class DefaultGroup(val id: String, val name: String, val image: ByteArray?) {

        val joinURL: String get() = "$defaultServer/$id?public_key=$defaultServerPublicKey"
    }

    data class Info(val id: String, val name: String, val imageID: String?)

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class CompactPollRequest(val roomID: String, val authToken: String, val fromDeletionServerID: Long?, val fromMessageServerID: Long?)
    data class CompactPollResult(val messages: List<OpenGroupMessageV2>, val deletions: List<Long>, val moderators: List<String>)

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class MessageDeletion
        @JvmOverloads constructor(val id: Long = 0, val deletedMessageId: Long = 0
    ) {

        companion object {
            val empty = MessageDeletion()
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
            /**
         * Always `true` under normal circumstances. You might want to disable
         * this when running over Lokinet.
         */
        val useOnionRouting: Boolean = true
    )

    private fun createBody(parameters: Any?): RequestBody? {
        if (parameters == null) return null
        val parametersAsJSON = JsonUtil.toJson(parameters)
        return RequestBody.create(MediaType.get("application/json"), parametersAsJSON)
    }

    private fun send(request: Request, isJsonRequired: Boolean = true): Promise<Map<*, *>, Exception> {
        val url = HttpUrl.parse(request.server) ?: return Promise.ofFail(Error.InvalidURL)
        val urlBuilder = HttpUrl.Builder()
            .scheme(url.scheme())
            .host(url.host())
            .port(url.port())
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
                if (token.isNullOrEmpty()) throw IllegalStateException("No auth token for request.")
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
                    ?: return Promise.ofFail(Error.NoPublicKey)
                return OnionRequestAPI.sendOnionRequest(requestBuilder.build(), request.server, publicKey, isJSONRequired = isJsonRequired).fail { e ->
                    // A 401 means that we didn't provide a (valid) auth token for a route that required one. We use this as an
                    // indication that the token we're using has expired. Note that a 403 has a different meaning; it means that
                    // we provided a valid token but it doesn't have a high enough permission level for the route in question.
                    if (e is OnionRequestAPI.HTTPRequestFailedAtDestinationException && e.statusCode == 401) {
                        val storage = MessagingModuleConfiguration.shared.storage
                        if (request.room != null) {
                            storage.removeAuthToken(request.room, request.server)
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
            val result = json["result"] as? String ?: throw Error.ParsingFailed
            decode(result)
        }
    }

    // region Authorization
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
            ?: return Promise.ofFail(Error.Generic)
        val queryParameters = mutableMapOf( "public_key" to publicKey )
        val request = Request(GET, room, server, "auth_token_challenge", queryParameters, isAuthRequired = false, parameters = null)
        return send(request).map { json ->
            val challenge = json["challenge"] as? Map<*, *> ?: throw Error.ParsingFailed
            val base64EncodedCiphertext = challenge["ciphertext"] as? String ?: throw Error.ParsingFailed
            val base64EncodedEphemeralPublicKey = challenge["ephemeral_public_key"] as? String ?: throw Error.ParsingFailed
            val ciphertext = decode(base64EncodedCiphertext)
            val ephemeralPublicKey = decode(base64EncodedEphemeralPublicKey)
            val symmetricKey = AESGCM.generateSymmetricKey(ephemeralPublicKey, privateKey)
            val tokenAsData = try {
                AESGCM.decrypt(ciphertext, symmetricKey)
            } catch (e: Exception) {
                throw Error.DecryptionFailed
            }
            tokenAsData.toHexString()
        }
    }

    fun claimAuthToken(authToken: String, room: String, server: String): Promise<String, Exception> {
        val parameters = mapOf( "public_key" to MessagingModuleConfiguration.shared.storage.getUserPublicKey()!! )
        val headers = mapOf( "Authorization" to authToken )
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
    // endregion

    // region Upload/Download
    fun upload(file: ByteArray, room: String, server: String): Promise<Long, Exception> {
        val base64EncodedFile = encodeBytes(file)
        val parameters = mapOf( "file" to base64EncodedFile )
        val request = Request(verb = POST, room = room, server = server, endpoint = "files", parameters = parameters)
        return send(request).map { json ->
            json["result"] as? Long ?: throw Error.ParsingFailed
        }
    }

    fun download(file: Long, room: String, server: String): Promise<ByteArray, Exception> {
        val request = Request(verb = GET, room = room, server = server, endpoint = "files/$file")
        return send(request).map { json ->
            val base64EncodedFile = json["result"] as? String ?: throw Error.ParsingFailed
            decode(base64EncodedFile) ?: throw Error.ParsingFailed
        }
    }
    // endregion

    // region Sending
    fun send(message: OpenGroupMessageV2, room: String, server: String): Promise<OpenGroupMessageV2, Exception> {
        val signedMessage = message.sign() ?: return Promise.ofFail(Error.SigningFailed)
        val jsonMessage = signedMessage.toJSON()
        val request = Request(verb = POST, room = room, server = server, endpoint = "messages", parameters = jsonMessage)
        return send(request).map { json ->
            @Suppress("UNCHECKED_CAST") val rawMessage = json["message"] as? Map<String, Any>
                    ?: throw Error.ParsingFailed
            OpenGroupMessageV2.fromJSON(rawMessage) ?: throw Error.ParsingFailed
        }
    }
    // endregion

    // region Messages
    fun getMessages(room: String, server: String): Promise<List<OpenGroupMessageV2>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val queryParameters = mutableMapOf<String, String>()
        storage.getLastMessageServerID(room, server)?.let { lastId ->
            queryParameters += "from_server_id" to lastId.toString()
        }
        val request = Request(verb = GET, room = room, server = server, endpoint = "messages", queryParameters = queryParameters)
        return send(request).map { json ->
            @Suppress("UNCHECKED_CAST") val rawMessages = json["messages"] as? List<Map<String, Any>>
                ?: throw Error.ParsingFailed
            parseMessages(room, server, rawMessages)
        }
    }

    private fun parseMessages(room: String, server: String, rawMessages: List<Map<*, *>>): List<OpenGroupMessageV2> {
        val storage = MessagingModuleConfiguration.shared.storage
        val lastMessageServerID = storage.getLastMessageServerID(room, server) ?: 0
        var currentLastMessageServerID = lastMessageServerID
        val messages = rawMessages.mapNotNull { json ->
            json as Map<String, Any>
            try {
                val message = OpenGroupMessageV2.fromJSON(json) ?: return@mapNotNull null
                if (message.serverID == null || message.sender.isNullOrEmpty()) return@mapNotNull null
                val sender = message.sender
                val data = decode(message.base64EncodedData)
                val signature = decode(message.base64EncodedSignature)
                val publicKey = Hex.fromStringCondensed(sender.removing05PrefixIfNeeded())
                val isValid = curve.verifySignature(publicKey, data, signature)
                if (!isValid) {
                    Log.d("Loki", "Ignoring message with invalid signature.")
                    return@mapNotNull null
                }
                if (message.serverID > lastMessageServerID) {
                    currentLastMessageServerID = message.serverID
                }
                message
            } catch (e: Exception) {
                null
            }
        }
        storage.setLastMessageServerID(room, server, currentLastMessageServerID)
        return messages
    }
    // endregion

    // region Message Deletion
    @JvmStatic
    fun deleteMessage(serverID: Long, room: String, server: String): Promise<Unit, Exception> {
        val request = Request(verb = DELETE, room = room, server = server, endpoint = "messages/$serverID")
        return send(request).map {
            Log.d("Loki", "Message deletion successful.")
        }
    }

    fun getDeletedMessages(room: String, server: String): Promise<List<MessageDeletion>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val queryParameters = mutableMapOf<String, String>()
        storage.getLastDeletionServerID(room, server)?.let { last ->
            queryParameters["from_server_id"] = last.toString()
        }
        val request = Request(verb = GET, room = room, server = server, endpoint = "deleted_messages", queryParameters = queryParameters)
        return send(request).map { json ->
            val type = TypeFactory.defaultInstance().constructCollectionType(List::class.java, MessageDeletion::class.java)
            val idsAsString = JsonUtil.toJson(json["ids"])
            val serverIDs = JsonUtil.fromJson<List<MessageDeletion>>(idsAsString, type) ?: throw Error.ParsingFailed
            val lastMessageServerId = storage.getLastDeletionServerID(room, server) ?: 0
            val serverID = serverIDs.maxByOrNull {it.id } ?: MessageDeletion.empty
            if (serverID.id > lastMessageServerId) {
                storage.setLastDeletionServerID(room, server, serverID.id)
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
                ?: throw Error.ParsingFailed
            val id = "$server.$room"
            handleModerators(id, moderatorsJson)
            moderatorsJson
        }
    }

    @JvmStatic
    fun ban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val parameters = mapOf( "public_key" to publicKey )
        val request = Request(verb = POST, room = room, server = server, endpoint = "block_list", parameters = parameters)
        return send(request).map {
            Log.d("Loki", "Banned user: $publicKey from: $server.$room.")
        }
    }

    fun unban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val request = Request(verb = DELETE, room = room, server = server, endpoint = "block_list/$publicKey")
        return send(request).map {
            Log.d("Loki", "Unbanned user: $publicKey from: $server.$room")
        }
    }

    @JvmStatic
    fun isUserModerator(publicKey: String, room: String, server: String): Boolean =
        moderators["$server.$room"]?.contains(publicKey) ?: false
    // endregion

    // region General
    @Suppress("UNCHECKED_CAST")
    fun compactPoll(rooms: List<String>, server: String): Promise<Map<String, CompactPollResult>, Exception> {
        val authTokenRequests = rooms.associateWith { room -> getAuthToken(room, server) }
        val storage = MessagingModuleConfiguration.shared.storage
        val context = MessagingModuleConfiguration.shared.context
        val timeSinceLastOpen = this.timeSinceLastOpen
        val useMessageLimit = (hasPerformedInitialPoll[server] != true
            && timeSinceLastOpen > OpenGroupPollerV2.maxInactivityPeriod)
        hasPerformedInitialPoll[server] = true
        if (!hasUpdatedLastOpenDate) {
            hasUpdatedLastOpenDate = true
            TextSecurePreferences.setLastOpenDate(context)
        }
        val requests = rooms.mapNotNull { room ->
            val authToken = try {
                authTokenRequests[room]?.get()
            } catch (e: Exception) {
                Log.e("Loki", "Failed to get auth token for $room.", e)
                null
            } ?: return@mapNotNull null
            CompactPollRequest(
                roomID = room,
                authToken = authToken,
                fromDeletionServerID = if (useMessageLimit) null else storage.getLastDeletionServerID(room, server),
                fromMessageServerID = if (useMessageLimit) null else storage.getLastMessageServerID(room, server)
            )
        }
        val request = Request(verb = POST, room = null, server = server, endpoint = "compact_poll", isAuthRequired = false, parameters = mapOf( "requests" to requests ))
        return send(request = request).map { json ->
            val results = json["results"] as? List<*> ?: throw Error.ParsingFailed
            results.mapNotNull { json ->
                if (json !is Map<*,*>) return@mapNotNull null
                val roomID = json["room_id"] as? String ?: return@mapNotNull null
                // A 401 means that we didn't provide a (valid) auth token for a route that required one. We use this as an
                // indication that the token we're using has expired. Note that a 403 has a different meaning; it means that
                // we provided a valid token but it doesn't have a high enough permission level for the route in question.
                val statusCode = json["status_code"] as? Int ?: return@mapNotNull null
                if (statusCode == 401) {
                    // delete auth token and return null
                    storage.removeAuthToken(roomID, server)
                }
                // Moderators
                val moderators = json["moderators"] as? List<String> ?: return@mapNotNull null
                handleModerators("$server.$roomID", moderators)
                // Deletions
                val type = TypeFactory.defaultInstance().constructCollectionType(List::class.java, MessageDeletion::class.java)
                val idsAsString = JsonUtil.toJson(json["deletions"])
                val deletedServerIDs = JsonUtil.fromJson<List<MessageDeletion>>(idsAsString, type) ?: throw Error.ParsingFailed
                val lastDeletionServerID = storage.getLastDeletionServerID(roomID, server) ?: 0
                val serverID = deletedServerIDs.maxByOrNull { it.id } ?: MessageDeletion.empty
                if (serverID.id > lastDeletionServerID) {
                    storage.setLastDeletionServerID(roomID, server, serverID.id)
                }
                // Messages
                val rawMessages = json["messages"] as? List<Map<String, Any>> ?: return@mapNotNull null
                val messages = parseMessages(roomID, server, rawMessages)
                roomID to CompactPollResult(
                    messages = messages,
                    deletions = deletedServerIDs.map { it.deletedMessageId },
                    moderators = moderators
                )
            }.toMap()
        }
    }

    fun getDefaultRoomsIfNeeded(): Promise<List<DefaultGroup>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        storage.setOpenGroupPublicKey(defaultServer, defaultServerPublicKey)
        return getAllRooms(defaultServer).map { groups ->
            val earlyGroups = groups.map { group ->
                DefaultGroup(group.id, group.name, null)
            }
            // See if we have any cached rooms, and if they already have images don't overwrite them with early non-image results
            defaultRooms.replayCache.firstOrNull()?.let { replayed ->
                if (replayed.none { it.image?.isNotEmpty() == true}) {
                    defaultRooms.tryEmit(earlyGroups)
                }
            }
            val images = groups.map { group ->
                group.id to downloadOpenGroupProfilePicture(group.id, defaultServer)
            }.toMap()
            groups.map { group ->
                val image = try {
                    images[group.id]!!.get()
                } catch (e: Exception) {
                    // No image or image failed to download
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
            val rawRoom = json["room"] as? Map<*, *> ?: throw Error.ParsingFailed
            val id = rawRoom["id"] as? String ?: throw Error.ParsingFailed
            val name = rawRoom["name"] as? String ?: throw Error.ParsingFailed
            val imageID = rawRoom["image_id"] as? String
            Info(id = id, name = name, imageID = imageID)
        }
    }

    fun getAllRooms(server: String): Promise<List<Info>, Exception> {
        val request = Request(verb = GET, room = null, server = server, endpoint = "rooms", isAuthRequired = false)
        return send(request).map { json ->
            val rawRooms = json["rooms"] as? List<Map<*, *>> ?: throw Error.ParsingFailed
            rawRooms.mapNotNull {
                val roomJson = it as? Map<*, *> ?: return@mapNotNull null
                val id = roomJson["id"] as? String ?: return@mapNotNull null
                val name = roomJson["name"] as? String ?: return@mapNotNull null
                val imageID = roomJson["image_id"] as? String
                Info(id, name, imageID)
            }
        }
    }

    fun getMemberCount(room: String, server: String): Promise<Int, Exception> {
        val request = Request(verb = GET, room = room, server = server, endpoint = "member_count")
        return send(request).map { json ->
            val memberCount = json["member_count"] as? Int ?: throw Error.ParsingFailed
            val storage = MessagingModuleConfiguration.shared.storage
            storage.setUserCount(room, server, memberCount)
            memberCount
        }
    }
    // endregion
}