package org.session.libsession.messaging.open_groups

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.databind.type.TypeFactory
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.GenericHash
import com.goterl.lazysodium.interfaces.Sign
import kotlinx.coroutines.flow.MutableSharedFlow
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.sending_receiving.pollers.OpenGroupPoller.Companion.maxInactivityPeriod
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.OnionResponse
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Base64.decode
import org.session.libsignal.utilities.Base64.encodeBytes
import org.session.libsignal.utilities.HTTP
import org.session.libsignal.utilities.HTTP.Verb.DELETE
import org.session.libsignal.utilities.HTTP.Verb.GET
import org.session.libsignal.utilities.HTTP.Verb.POST
import org.session.libsignal.utilities.HTTP.Verb.PUT
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.removingIdPrefixIfNeeded
import org.whispersystems.curve25519.Curve25519
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

object OpenGroupApi {
    private val curve = Curve25519.getInstance(Curve25519.BEST)
    val defaultRooms = MutableSharedFlow<List<DefaultGroup>>(replay = 1)
    private val hasPerformedInitialPoll = mutableMapOf<String, Boolean>()
    private var hasUpdatedLastOpenDate = false
    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid()) }
    private val timeSinceLastOpen by lazy {
        val context = MessagingModuleConfiguration.shared.context
        val lastOpenDate = TextSecurePreferences.getLastOpenTimeDate(context)
        val now = System.currentTimeMillis()
        now - lastOpenDate
    }

    const val defaultServerPublicKey = "a03c383cf63c3c4efe67acc52112a6dd734b3a946b9545f488aaa93da7991238"
    const val legacyServerIP = "116.203.70.33"
    const val legacyDefaultServer = "http://116.203.70.33" // TODO: migrate all references to use new value

    /** For migration purposes only, don't use this value in joining groups */
    const val httpDefaultServer = "http://open.getsession.org"

    const val defaultServer = "https://open.getsession.org"

    val pendingReactions = mutableListOf<PendingReaction>()

    sealed class Error(message: String) : Exception(message) {
        object Generic : Error("An error occurred.")
        object ParsingFailed : Error("Invalid response.")
        object DecryptionFailed : Error("Couldn't decrypt response.")
        object SigningFailed : Error("Couldn't sign message.")
        object InvalidURL : Error("Invalid URL.")
        object NoPublicKey : Error("Couldn't find server public key.")
        object NoEd25519KeyPair : Error("Couldn't find ed25519 key pair.")
    }

    data class DefaultGroup(val id: String, val name: String, val image: ByteArray?) {

        val joinURL: String get() = "$defaultServer/$id?public_key=$defaultServerPublicKey"
    }

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class RoomInfo(
        val token: String = "",
        val name: String = "",
        val description: String = "",
        val infoUpdates: Int = 0,
        val messageSequence: Long = 0,
        val created: Long = 0,
        val activeUsers: Int = 0,
        val activeUsersCutoff: Int = 0,
        val imageId: Long? = null,
        val pinnedMessages: List<PinnedMessage> = emptyList(),
        val admin: Boolean = false,
        val globalAdmin: Boolean = false,
        val admins: List<String> = emptyList(),
        val hiddenAdmins: List<String> = emptyList(),
        val moderator: Boolean = false,
        val globalModerator: Boolean = false,
        val moderators: List<String> = emptyList(),
        val hiddenModerators: List<String> = emptyList(),
        val read: Boolean = false,
        val defaultRead: Boolean = false,
        val defaultAccessible: Boolean = false,
        val write: Boolean = false,
        val defaultWrite: Boolean = false,
        val upload: Boolean = false,
        val defaultUpload: Boolean = false,
    )

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class PinnedMessage(
        val id: Long = 0,
        val pinnedAt: Long = 0,
        val pinnedBy: String = ""
    )

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class BatchRequestInfo<T>(
        val request: BatchRequest,
        val endpoint: Endpoint,
        val queryParameters: Map<String, String> = mapOf(),
        val responseType: TypeReference<T>
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class BatchRequest(
        val method: HTTP.Verb,
        val path: String,
        val headers: Map<String, String> = emptyMap(),
        val json: Map<String, Any>? = null,
        val b64: String? = null,
        val bytes: ByteArray? = null,
    )

    data class BatchResponse<T>(
        val endpoint: Endpoint,
        val code: Int,
        val headers: Map<String, String>,
        val body: T?
    )

    data class Capabilities(
        val capabilities: List<String> = emptyList(),
        val missing: List<String> = emptyList()
    )

    enum class Capability {
        BLIND, REACTIONS
    }

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class RoomPollInfo(
        val token: String = "",
        val activeUsers: Int = 0,
        val admin: Boolean = false,
        val globalAdmin: Boolean = false,
        val moderator: Boolean = false,
        val globalModerator: Boolean = false,
        val read: Boolean = false,
        val defaultRead: Boolean = false,
        val defaultAccessible: Boolean = false,
        val write: Boolean = false,
        val defaultWrite: Boolean = false,
        val upload: Boolean = false,
        val defaultUpload: Boolean = false,
        val details: RoomInfo? = null
    )

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class DirectMessage(
        val id: Long = 0,
        val sender: String = "",
        val recipient: String = "",
        val postedAt: Long = 0,
        val expiresAt: Long = 0,
        val message: String = "",
    )

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class Message(
        val id : Long = 0,
        val sessionId: String = "",
        val posted: Double = 0.0,
        val edited: Long = 0,
        val seqno: Long = 0,
        val deleted: Boolean = false,
        val whisper: Boolean = false,
        val whisperMods: String = "",
        val whisperTo: String = "",
        val data: String? = null,
        val signature: String? = null,
        val reactions: Map<String, Reaction>? = null,
    )

    data class Reaction(
        val count: Long = 0,
        val reactors: List<String> = emptyList(),
        val you: Boolean = false,
        val index: Long = 0
    )

    data class AddReactionResponse(
        val seqNo: Long,
        val added: Boolean
    )

    data class DeleteReactionResponse(
        val seqNo: Long,
        val removed: Boolean
    )

    data class DeleteAllReactionsResponse(
        val seqNo: Long,
        val removed: Boolean
    )

    data class PendingReaction(
        val server: String,
        val room: String,
        val messageId: Long,
        val emoji: String,
        val add: Boolean,
        var seqNo: Long? = null
    )

    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy::class)
    data class SendMessageRequest(
        val data: String? = null,
        val signature: String? = null,
        val whisperTo: List<String>? = null,
        val whisperMods: Boolean? = null,
        val files: List<String>? = null
    )

    data class MessageDeletion(
        @JsonProperty("id")
        val id: Long = 0,
        @JsonProperty("deleted_message_id")
        val deletedMessageServerID: Long = 0
    ) {

        companion object {
            val empty = MessageDeletion()
        }
    }

    data class Request(
        val verb: HTTP.Verb,
        val room: String?,
        val server: String,
        val endpoint: Endpoint,
        val queryParameters: Map<String, String> = mapOf(),
        val parameters: Any? = null,
        val headers: Map<String, String> = mapOf(),
        val isAuthRequired: Boolean = true,
        val body: ByteArray? = null,
        /**
         * Always `true` under normal circumstances. You might want to disable
         * this when running over Lokinet.
         */
        val useOnionRouting: Boolean = true
    )

    private fun createBody(body: ByteArray?, parameters: Any?): RequestBody? {
        if (body != null) return RequestBody.create(MediaType.get("application/octet-stream"), body)
        if (parameters == null) return null
        val parametersAsJSON = JsonUtil.toJson(parameters)
        return RequestBody.create(MediaType.get("application/json"), parametersAsJSON)
    }

    private fun getResponseBody(request: Request): Promise<ByteArray, Exception> {
        return send(request).map { response ->
            response.body ?: throw Error.ParsingFailed
        }
    }

    private fun getResponseBodyJson(request: Request): Promise<Map<*, *>, Exception> {
        return send(request).map {
            JsonUtil.fromJson(it.body, Map::class.java)
        }
    }

    private fun send(request: Request): Promise<OnionResponse, Exception> {
        HttpUrl.parse(request.server) ?: return Promise.ofFail(Error.InvalidURL)
        val urlBuilder = StringBuilder("${request.server}/${request.endpoint.value}")
        if (request.verb == GET && request.queryParameters.isNotEmpty()) {
            urlBuilder.append("?")
            for ((key, value) in request.queryParameters) {
                urlBuilder.append("$key=$value")
            }
        }
        fun execute(): Promise<OnionResponse, Exception> {
            val serverCapabilities = MessagingModuleConfiguration.shared.storage.getServerCapabilities(request.server)
            val publicKey =
                MessagingModuleConfiguration.shared.storage.getOpenGroupPublicKey(request.server)
                    ?: return Promise.ofFail(Error.NoPublicKey)
            val ed25519KeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair()
                ?: return Promise.ofFail(Error.NoEd25519KeyPair)
            val urlRequest = urlBuilder.toString()
            val headers = request.headers.toMutableMap()
            if (request.isAuthRequired) {
                val nonce = sodium.nonce(16)
                val timestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
                var pubKey = ""
                var signature = ByteArray(Sign.BYTES)
                var bodyHash = ByteArray(0)
                if (request.parameters != null) {
                    val parameterBytes = JsonUtil.toJson(request.parameters).toByteArray()
                    val parameterHash = ByteArray(GenericHash.BYTES_MAX)
                    if (sodium.cryptoGenericHash(
                            parameterHash,
                            parameterHash.size,
                            parameterBytes,
                            parameterBytes.size.toLong()
                        )
                    ) {
                        bodyHash = parameterHash
                    }
                } else if (request.body != null) {
                    val byteHash = ByteArray(GenericHash.BYTES_MAX)
                    if (sodium.cryptoGenericHash(
                            byteHash,
                            byteHash.size,
                            request.body,
                            request.body.size.toLong()
                        )
                    ) {
                        bodyHash = byteHash
                    }
                }
                val messageBytes = Hex.fromStringCondensed(publicKey)
                    .plus(nonce)
                    .plus("$timestamp".toByteArray(Charsets.US_ASCII))
                    .plus(request.verb.rawValue.toByteArray())
                    .plus("/${request.endpoint.value}".toByteArray())
                    .plus(bodyHash)
                if (serverCapabilities.contains(Capability.BLIND.name.lowercase())) {
                    SodiumUtilities.blindedKeyPair(publicKey, ed25519KeyPair)?.let { keyPair ->
                        pubKey = SessionId(
                            IdPrefix.BLINDED,
                            keyPair.publicKey.asBytes
                        ).hexString

                        signature = SodiumUtilities.sogsSignature(
                            messageBytes,
                            ed25519KeyPair.secretKey.asBytes,
                            keyPair.secretKey.asBytes,
                            keyPair.publicKey.asBytes
                        ) ?: return Promise.ofFail(Error.SigningFailed)
                    } ?: return Promise.ofFail(Error.SigningFailed)
                } else {
                    pubKey = SessionId(
                        IdPrefix.UN_BLINDED,
                        ed25519KeyPair.publicKey.asBytes
                    ).hexString
                    sodium.cryptoSignDetached(
                        signature,
                        messageBytes,
                        messageBytes.size.toLong(),
                        ed25519KeyPair.secretKey.asBytes
                    )
                }
                headers["X-SOGS-Nonce"] = encodeBytes(nonce)
                headers["X-SOGS-Timestamp"] = "$timestamp"
                headers["X-SOGS-Pubkey"] = pubKey
                headers["X-SOGS-Signature"] = encodeBytes(signature)
            }

            val requestBuilder = okhttp3.Request.Builder()
                .url(urlRequest)
                .headers(Headers.of(headers))
            when (request.verb) {
                GET -> requestBuilder.get()
                PUT -> requestBuilder.put(createBody(request.body, request.parameters)!!)
                POST -> requestBuilder.post(createBody(request.body, request.parameters)!!)
                DELETE -> requestBuilder.delete(createBody(request.body, request.parameters))
            }
            if (!request.room.isNullOrEmpty()) {
                requestBuilder.header("Room", request.room)
            }
            return if (request.useOnionRouting) {
                OnionRequestAPI.sendOnionRequest(requestBuilder.build(), request.server, publicKey).fail { e ->
                    Log.e("SOGS", "Failed onion request", e)
                }
            } else {
                Promise.ofFail(IllegalStateException("It's currently not allowed to send non onion routed requests."))
            }
        }
        return execute()
    }

    fun downloadOpenGroupProfilePicture(
        server: String,
        roomID: String,
        imageId: Long
    ): Promise<ByteArray, Exception> {
        val request = Request(
            verb = GET,
            room = roomID,
            server = server,
            endpoint = Endpoint.RoomFileIndividual(roomID, imageId.toString())
        )
        return getResponseBody(request)
    }

    // region Upload/Download
    fun upload(file: ByteArray, room: String, server: String): Promise<Long, Exception> {
        val request = Request(
            verb = POST,
            room = room,
            server = server,
            endpoint = Endpoint.RoomFile(room),
            body = file,
            headers = mapOf(
                "Content-Disposition" to "attachment",
                "Content-Type" to "application/octet-stream"
            )
        )
        return getResponseBodyJson(request).map { json ->
            (json["id"] as? Number)?.toLong() ?: throw Error.ParsingFailed
        }
    }

    fun download(fileId: String, room: String, server: String): Promise<ByteArray, Exception> {
        val request = Request(
            verb = GET,
            room = room,
            server = server,
            endpoint = Endpoint.RoomFileIndividual(room, fileId)
        )
        return getResponseBody(request)
    }
    // endregion

    // region Sending
    fun sendMessage(
        message: OpenGroupMessage,
        room: String,
        server: String,
        whisperTo: List<String>? = null,
        whisperMods: Boolean? = null,
        fileIds: List<String>? = null
    ): Promise<OpenGroupMessage, Exception> {
        val signedMessage = message.sign(room, server, fallbackSigningType = IdPrefix.STANDARD) ?: return Promise.ofFail(Error.SigningFailed)
        val parameters = signedMessage.toJSON().toMutableMap()

        // add file IDs if there are any (from attachments)
        if (!fileIds.isNullOrEmpty()) {
            parameters += "files" to fileIds
        }

        val request = Request(
            verb = POST,
            room = room,
            server = server,
            endpoint = Endpoint.RoomMessage(room),
            parameters = parameters
        )
        return getResponseBodyJson(request).map { json ->
            @Suppress("UNCHECKED_CAST") val rawMessage = json as? Map<String, Any>
                ?: throw Error.ParsingFailed
            val result = OpenGroupMessage.fromJSON(rawMessage) ?: throw Error.ParsingFailed
            val storage = MessagingModuleConfiguration.shared.storage
            storage.addReceivedMessageTimestamp(result.sentTimestamp)
            result
        }
    }
    // endregion

    // region Messages
    fun getMessages(room: String, server: String): Promise<List<OpenGroupMessage>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val queryParameters = mutableMapOf<String, String>()
        storage.getLastMessageServerID(room, server)?.let { lastId ->
            queryParameters += "from_server_id" to lastId.toString()
        }
        val request = Request(
            verb = GET,
            room = room,
            server = server,
            endpoint = Endpoint.RoomMessage(room),
            queryParameters = queryParameters
        )
        return getResponseBodyJson(request).map { json ->
            @Suppress("UNCHECKED_CAST") val rawMessages =
                json["messages"] as? List<Map<String, Any>>
                    ?: throw Error.ParsingFailed
            parseMessages(room, server, rawMessages)
        }
    }

    private fun parseMessages(
        room: String,
        server: String,
        rawMessages: List<Map<*, *>>
    ): List<OpenGroupMessage> {
        val messages = rawMessages.mapNotNull { json ->
            json as Map<String, Any>
            try {
                val message = OpenGroupMessage.fromJSON(json) ?: return@mapNotNull null
                if (message.serverID == null || message.sender.isNullOrEmpty()) return@mapNotNull null
                val sender = message.sender
                val data = decode(message.base64EncodedData)
                val signature = decode(message.base64EncodedSignature)
                val publicKey = Hex.fromStringCondensed(sender.removingIdPrefixIfNeeded())
                val isValid = curve.verifySignature(publicKey, data, signature)
                if (!isValid) {
                    Log.d("Loki", "Ignoring message with invalid signature.")
                    return@mapNotNull null
                }
                message
            } catch (e: Exception) {
                null
            }
        }
        return messages
    }

    fun getReactors(room: String, server: String, messageId: Long, emoji: String): Promise<Map<*, *>, Exception> {
        val request = Request(
            verb = GET,
            room = room,
            server = server,
            endpoint = Endpoint.Reactors(room, messageId, emoji)
        )
        return getResponseBody(request).map { response ->
            JsonUtil.fromJson(response, Map::class.java)
        }
    }

    fun addReaction(room: String, server: String, messageId: Long, emoji: String): Promise<AddReactionResponse, Exception> {
        val request = Request(
            verb = PUT,
            room = room,
            server = server,
            endpoint = Endpoint.Reaction(room, messageId, emoji),
            parameters = emptyMap<String, String>()
        )
        val pendingReaction = PendingReaction(server, room, messageId, emoji, true)
        return getResponseBody(request).map { response ->
            JsonUtil.fromJson(response, AddReactionResponse::class.java).also {
                val index = pendingReactions.indexOf(pendingReaction)
                pendingReactions[index].seqNo = it.seqNo
            }
        }
    }

    fun deleteReaction(room: String, server: String, messageId: Long, emoji: String): Promise<DeleteReactionResponse, Exception> {
        val request = Request(
            verb = DELETE,
            room = room,
            server = server,
            endpoint = Endpoint.Reaction(room, messageId, emoji)
        )
        val pendingReaction = PendingReaction(server, room, messageId, emoji, true)
        return getResponseBody(request).map { response ->
            JsonUtil.fromJson(response, DeleteReactionResponse::class.java).also {
                val index = pendingReactions.indexOf(pendingReaction)
                pendingReactions[index].seqNo = it.seqNo
            }
        }
    }

    fun deleteAllReactions(room: String, server: String, messageId: Long, emoji: String): Promise<DeleteAllReactionsResponse, Exception> {
        val request = Request(
            verb = DELETE,
            room = room,
            server = server,
            endpoint = Endpoint.ReactionDelete(room, messageId, emoji)
        )
        return getResponseBody(request).map { response ->
            JsonUtil.fromJson(response, DeleteAllReactionsResponse::class.java)
        }
    }
    // endregion

    // region Message Deletion
    @JvmStatic
    fun deleteMessage(serverID: Long, room: String, server: String): Promise<Unit, Exception> {
        val request =
            Request(verb = DELETE, room = room, server = server, endpoint = Endpoint.RoomMessageIndividual(room, serverID))
        return send(request).map {
            Log.d("Loki", "Message deletion successful.")
        }
    }

    fun getDeletedMessages(
        room: String,
        server: String
    ): Promise<List<MessageDeletion>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val queryParameters = mutableMapOf<String, String>()
        storage.getLastDeletionServerID(room, server)?.let { last ->
            queryParameters["from_server_id"] = last.toString()
        }
        val request = Request(
            verb = GET,
            room = room,
            server = server,
            endpoint = Endpoint.RoomDeleteMessages(room, storage.getUserPublicKey() ?: ""),
            queryParameters = queryParameters
        )
        return getResponseBody(request).map { response ->
            val json = JsonUtil.fromJson(response, Map::class.java)
            val type = TypeFactory.defaultInstance()
                .constructCollectionType(List::class.java, MessageDeletion::class.java)
            val idsAsString = JsonUtil.toJson(json["ids"])
            val serverIDs = JsonUtil.fromJson<List<MessageDeletion>>(idsAsString, type)
                ?: throw Error.ParsingFailed
            val lastMessageServerId = storage.getLastDeletionServerID(room, server) ?: 0
            val serverID = serverIDs.maxByOrNull { it.id } ?: MessageDeletion.empty
            if (serverID.id > lastMessageServerId) {
                storage.setLastDeletionServerID(room, server, serverID.id)
            }
            serverIDs
        }
    }
    // endregion

    // region Moderation
    @JvmStatic
    fun ban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val parameters =  mapOf("rooms" to listOf(room))
        val request = Request(
            verb = POST,
            room = room,
            server = server,
            endpoint = Endpoint.UserBan(publicKey),
            parameters = parameters
        )
        return send(request).map {
            Log.d("Loki", "Banned user: $publicKey from: $server.$room.")
        }
    }

    fun banAndDeleteAll(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val requests = mutableListOf<BatchRequestInfo<*>>(
            BatchRequestInfo(
                request = BatchRequest(
                    method = POST,
                    path = "/user/$publicKey/ban",
                    json = mapOf("rooms" to listOf(room))
                ),
                endpoint = Endpoint.UserBan(publicKey),
                responseType = object: TypeReference<Any>(){}
            ),
            BatchRequestInfo(
                request = BatchRequest(DELETE, "/room/$room/all/$publicKey"),
                endpoint = Endpoint.RoomDeleteMessages(room, publicKey),
                responseType = object: TypeReference<Any>(){}
            )
        )
        return sequentialBatch(server, requests).map {
            Log.d("Loki", "Banned user: $publicKey from: $server.$room.")
        }
    }

    fun unban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        val request =
            Request(verb = DELETE, room = room, server = server, endpoint = Endpoint.UserUnban(publicKey))
        return send(request).map {
            Log.d("Loki", "Unbanned user: $publicKey from: $server.$room")
        }
    }
    // endregion

    // region General
    @Suppress("UNCHECKED_CAST")
    fun poll(
        rooms: List<String>,
        server: String
    ): Promise<List<BatchResponse<*>>, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val context = MessagingModuleConfiguration.shared.context
        val timeSinceLastOpen = this.timeSinceLastOpen
        val shouldRetrieveRecentMessages = (hasPerformedInitialPoll[server] != true
                && timeSinceLastOpen > maxInactivityPeriod)
        hasPerformedInitialPoll[server] = true
        if (!hasUpdatedLastOpenDate) {
            hasUpdatedLastOpenDate = true
            TextSecurePreferences.setLastOpenDate(context)
        }
        val lastInboxMessageId = storage.getLastInboxMessageId(server)
        val lastOutboxMessageId = storage.getLastOutboxMessageId(server)
        val requests = mutableListOf<BatchRequestInfo<*>>(
            BatchRequestInfo(
                request = BatchRequest(
                    method = GET,
                    path = "/capabilities"
                ),
                endpoint = Endpoint.Capabilities,
                responseType = object : TypeReference<Capabilities>(){}
            )
        )
        rooms.forEach { room ->
            val infoUpdates = storage.getOpenGroup(room, server)?.infoUpdates ?: 0
            val lastMessageServerId = storage.getLastMessageServerID(room, server) ?: 0L
            requests.add(
                BatchRequestInfo(
                    request = BatchRequest(
                        method = GET,
                        path = "/room/$room/pollInfo/$infoUpdates"
                    ),
                    endpoint = Endpoint.RoomPollInfo(room, infoUpdates),
                    responseType = object : TypeReference<RoomPollInfo>(){}
                )
            )
            requests.add(
                if (shouldRetrieveRecentMessages || lastMessageServerId == 0L) {
                    BatchRequestInfo(
                        request = BatchRequest(
                            method = GET,
                            path = "/room/$room/messages/recent?t=r&reactors=5"
                        ),
                        endpoint = Endpoint.RoomMessagesRecent(room),
                        responseType = object : TypeReference<List<Message>>(){}
                    )
                } else {
                    BatchRequestInfo(
                        request = BatchRequest(
                            method = GET,
                            path = "/room/$room/messages/since/$lastMessageServerId?t=r&reactors=5"
                        ),
                        endpoint = Endpoint.RoomMessagesSince(room, lastMessageServerId),
                        responseType = object : TypeReference<List<Message>>(){}
                    )
                }
            )
        }
        val serverCapabilities = storage.getServerCapabilities(server)
        if (serverCapabilities.contains(Capability.BLIND.name.lowercase())) {
            requests.add(
                if (lastInboxMessageId == null) {
                    BatchRequestInfo(
                        request = BatchRequest(
                            method = GET,
                            path = "/inbox"
                        ),
                        endpoint = Endpoint.Inbox,
                        responseType = object : TypeReference<List<DirectMessage>>() {}
                    )
                } else {
                    BatchRequestInfo(
                        request = BatchRequest(
                            method = GET,
                            path = "/inbox/since/$lastInboxMessageId"
                        ),
                        endpoint = Endpoint.InboxSince(lastInboxMessageId),
                        responseType = object : TypeReference<List<DirectMessage>>() {}
                    )
                }
            )
            requests.add(
                if (lastOutboxMessageId == null) {
                    BatchRequestInfo(
                        request = BatchRequest(
                            method = GET,
                            path = "/outbox"
                        ),
                        endpoint = Endpoint.Outbox,
                        responseType = object : TypeReference<List<DirectMessage>>() {}
                    )
                } else {
                    BatchRequestInfo(
                        request = BatchRequest(
                            method = GET,
                            path = "/outbox/since/$lastOutboxMessageId"
                        ),
                        endpoint = Endpoint.OutboxSince(lastOutboxMessageId),
                        responseType = object : TypeReference<List<DirectMessage>>() {}
                    )
                }
            )
        }
        return parallelBatch(server, requests)
    }

    private fun parallelBatch(
        server: String,
        requests: MutableList<BatchRequestInfo<*>>
    ): Promise<List<BatchResponse<*>>, Exception> {
        val request = Request(
            verb = POST,
            room = null,
            server = server,
            endpoint = Endpoint.Batch,
            parameters = requests.map { it.request }
        )
        return getBatchResponseJson(request, requests)
    }

    private fun sequentialBatch(
        server: String,
        requests: MutableList<BatchRequestInfo<*>>,
        authRequired: Boolean = true
    ): Promise<List<BatchResponse<*>>, Exception> {
        val request = Request(
            verb = POST,
            room = null,
            server = server,
            endpoint = Endpoint.Sequence,
            parameters = requests.map { it.request },
            isAuthRequired = authRequired
        )
        return getBatchResponseJson(request, requests)
    }

    private fun getBatchResponseJson(
        request: Request,
        requests: MutableList<BatchRequestInfo<*>>
    ): Promise<List<BatchResponse<*>>, Exception> {
        return getResponseBody(request).map { batch ->
            val results = JsonUtil.fromJson(batch, List::class.java) ?: throw Error.ParsingFailed
            results.mapIndexed { idx, result ->
                val response = result as? Map<*, *> ?: throw Error.ParsingFailed
                val code = response["code"] as Int
                BatchResponse(
                    endpoint = requests[idx].endpoint,
                    code = code,
                    headers = response["headers"] as Map<String, String>,
                    body = if (code in 200..299) {
                        JsonUtil.toJson(response["body"]).takeIf { it != "[]" }?.let {
                            JsonUtil.fromJson(it, requests[idx].responseType)
                        }
                    } else null
                )
            }
        }
    }

    fun getDefaultServerCapabilities(): Promise<Capabilities, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        storage.setOpenGroupPublicKey(defaultServer, defaultServerPublicKey)
        return getCapabilities(defaultServer).map { capabilities ->
            storage.setServerCapabilities(defaultServer, capabilities.capabilities)
            capabilities
        }
    }

    fun getDefaultRoomsIfNeeded(): Promise<List<DefaultGroup>, Exception> {
        return getAllRooms().map { groups ->
            val earlyGroups = groups.map { group ->
                DefaultGroup(group.token, group.name, null)
            }
            // See if we have any cached rooms, and if they already have images don't overwrite them with early non-image results
            defaultRooms.replayCache.firstOrNull()?.let { replayed ->
                if (replayed.none { it.image?.isNotEmpty() == true }) {
                    defaultRooms.tryEmit(earlyGroups)
                }
            }
            val images = groups.associate { group ->
                group.token to group.imageId?.let { downloadOpenGroupProfilePicture(defaultServer, group.token, it) }
            }
            groups.map { group ->
                val image = try {
                    images[group.token]!!.get()
                } catch (e: Exception) {
                    // No image or image failed to download
                    null
                }
                DefaultGroup(group.token, group.name, image)
            }
        }.success { new ->
            defaultRooms.tryEmit(new)
        }
    }

    fun getRoomInfo(roomToken: String, server: String): Promise<RoomInfo, Exception> {
        val request = Request(
            verb = GET,
            room = null,
            server = server,
            endpoint = Endpoint.Room(roomToken)
        )
        return getResponseBody(request).map { response ->
            JsonUtil.fromJson(response, RoomInfo::class.java)
        }
    }

    private fun getAllRooms(): Promise<List<RoomInfo>, Exception> {
        val request = Request(
            verb = GET,
            room = null,
            server = defaultServer,
            endpoint = Endpoint.Rooms
        )
        return getResponseBody(request).map { response ->
            val rawRooms = JsonUtil.fromJson(response, List::class.java) ?: throw Error.ParsingFailed
            rawRooms.mapNotNull {
                JsonUtil.fromJson(JsonUtil.toJson(it), RoomInfo::class.java)
            }
        }
    }

    fun getMemberCount(room: String, server: String): Promise<Int, Exception> {
        return getRoomInfo(room, server).map { info ->
            val storage = MessagingModuleConfiguration.shared.storage
            storage.setUserCount(room, server, info.activeUsers)
            info.activeUsers
        }
    }

    fun getCapabilities(server: String): Promise<Capabilities, Exception> {
        val request = Request(verb = GET, room = null, server = server, endpoint = Endpoint.Capabilities, isAuthRequired = false)
        return getResponseBody(request).map { response ->
            JsonUtil.fromJson(response, Capabilities::class.java)
        }
    }

    fun getCapabilitiesAndRoomInfo(
        room: String,
        server: String,
        authRequired: Boolean = true
    ): Promise<Pair<Capabilities, RoomInfo>, Exception> {
        val requests = mutableListOf<BatchRequestInfo<*>>(
            BatchRequestInfo(
                request = BatchRequest(
                    method = GET,
                    path = "/capabilities"
                ),
                endpoint = Endpoint.Capabilities,
                responseType = object : TypeReference<Capabilities>(){}
            ),
            BatchRequestInfo(
                request = BatchRequest(
                    method = GET,
                    path = "/room/$room"
                ),
                endpoint = Endpoint.Room(room),
                responseType = object : TypeReference<RoomInfo>(){}
            )
        )
        return sequentialBatch(server, requests, authRequired).map {
            val capabilities = it.firstOrNull()?.body as? Capabilities ?: throw Error.ParsingFailed
            val roomInfo = it.lastOrNull()?.body as? RoomInfo ?: throw Error.ParsingFailed
            capabilities to roomInfo
        }
    }

    fun sendDirectMessage(message: String, blindedSessionId: String, server: String): Promise<DirectMessage, Exception> {
        val request = Request(
            verb = POST,
            room = null,
            server = server,
            endpoint = Endpoint.InboxFor(blindedSessionId),
            parameters = mapOf("message" to message)
        )
        return getResponseBody(request).map { response ->
            JsonUtil.fromJson(response, DirectMessage::class.java)
        }
    }

    // endregion
}