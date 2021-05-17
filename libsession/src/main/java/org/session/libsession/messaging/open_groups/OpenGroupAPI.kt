package org.session.libsession.messaging.open_groups

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.then
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.file_server.FileServerAPI
import org.session.libsession.messaging.utilities.DotNetAPI
import org.session.libsession.utilities.DownloadUtilities
import org.session.libsignal.utilities.retryIfNeeded
import org.session.libsignal.utilities.*
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

object OpenGroupAPI: DotNetAPI() {

    private val moderators: HashMap<String, HashMap<Long, Set<String>>> = hashMapOf() // Server URL to (channel ID to set of moderator IDs)

    // region Settings
    private val fallbackBatchCount = 64
    private val maxRetryCount = 8
    // endregion

    // region Convenience
    private val channelInfoType = "net.patter-app.settings"
    private val attachmentType = "net.app.core.oembed"
    @JvmStatic
    val openGroupMessageType = "network.loki.messenger.publicChat"
    @JvmStatic
    val profilePictureType = "network.loki.messenger.avatar"

    fun getDefaultChats(): List<OpenGroup> {
        return listOf() // Don't auto-join any open groups right now
    }

    @JvmStatic
    fun isUserModerator(hexEncodedPublicKey: String, channel: Long, server: String): Boolean {
        if (moderators[server] != null && moderators[server]!![channel] != null) {
            return moderators[server]!![channel]!!.contains(hexEncodedPublicKey)
        }
        return false
    }
    // endregion

    // region Public API
    fun getMessages(channel: Long, server: String): Promise<List<OpenGroupMessage>, Exception> {
        Log.d("Loki", "Getting messages for open group with ID: $channel on server: $server.")
        val storage = MessagingModuleConfiguration.shared.storage
        val parameters = mutableMapOf<String, Any>( "include_annotations" to 1 )
        val lastMessageServerID = storage.getLastMessageServerID(channel, server)
        if (lastMessageServerID != null) {
            parameters["since_id"] = lastMessageServerID
        } else {
            parameters["count"] = fallbackBatchCount
            parameters["include_deleted"] = 0
        }
        return execute(HTTPVerb.GET, server, "channels/$channel/messages", parameters = parameters).then { json ->
            try {
                val data = json["data"] as List<Map<*, *>>
                val messages = data.mapNotNull { message ->
                    try {
                        val isDeleted = message["is_deleted"] as? Boolean ?: false
                        if (isDeleted) { return@mapNotNull null }
                        // Ignore messages without annotations
                        if (message["annotations"] == null) { return@mapNotNull null }
                        val annotation = (message["annotations"] as List<Map<*, *>>).find {
                            ((it["type"] as? String ?: "") == openGroupMessageType) && it["value"] != null
                        } ?: return@mapNotNull null
                        val value = annotation["value"] as Map<*, *>
                        val serverID = message["id"] as? Long ?: (message["id"] as? Int)?.toLong() ?: (message["id"] as String).toLong()
                        val user = message["user"] as Map<*, *>
                        val publicKey = user["username"] as String
                        val displayName = user["name"] as? String ?: "Anonymous"
                        var profilePicture: OpenGroupMessage.ProfilePicture? = null
                        if (user["annotations"] != null) {
                            val profilePictureAnnotation = (user["annotations"] as List<Map< *, *>>).find {
                                ((it["type"] as? String ?: "") == profilePictureType) && it["value"] != null
                            }
                            val profilePictureAnnotationValue = profilePictureAnnotation?.get("value") as? Map<*, *>
                            if (profilePictureAnnotationValue != null && profilePictureAnnotationValue["profileKey"] != null && profilePictureAnnotationValue["url"] != null) {
                                try {
                                    val profileKey = Base64.decode(profilePictureAnnotationValue["profileKey"] as String)
                                    val url = profilePictureAnnotationValue["url"] as String
                                    profilePicture = OpenGroupMessage.ProfilePicture(profileKey, url)
                                } catch (e: Exception) {}
                            }
                        }
                        @Suppress("NAME_SHADOWING") val body = message["text"] as String
                        val timestamp = value["timestamp"] as? Long ?: (value["timestamp"] as? Int)?.toLong() ?: (value["timestamp"] as String).toLong()
                        var quote: OpenGroupMessage.Quote? = null
                        if (value["quote"] != null) {
                            val replyTo = message["reply_to"] as? Long ?: (message["reply_to"] as? Int)?.toLong() ?: (message["reply_to"] as String).toLong()
                            val quoteAnnotation = value["quote"] as? Map<*, *>
                            val quoteTimestamp = quoteAnnotation?.get("id") as? Long ?: (quoteAnnotation?.get("id") as? Int)?.toLong() ?: (quoteAnnotation?.get("id") as? String)?.toLong() ?: 0L
                            val author = quoteAnnotation?.get("author") as? String
                            val text = quoteAnnotation?.get("text") as? String
                            quote = if (quoteTimestamp > 0L && author != null && text != null) OpenGroupMessage.Quote(quoteTimestamp, author, text, replyTo) else null
                        }
                        val attachmentsAsJSON = (message["annotations"] as List<Map<*, *>>).filter {
                            ((it["type"] as? String ?: "") == attachmentType) && it["value"] != null
                        }
                        val attachments = attachmentsAsJSON.mapNotNull { it["value"] as? Map<*, *> }.mapNotNull { attachmentAsJSON ->
                            try {
                                val kindAsString = attachmentAsJSON["lokiType"] as String
                                val kind = OpenGroupMessage.Attachment.Kind.values().first { it.rawValue == kindAsString }
                                val id = attachmentAsJSON["id"] as? Long ?: (attachmentAsJSON["id"] as? Int)?.toLong() ?: (attachmentAsJSON["id"] as String).toLong()
                                val contentType = attachmentAsJSON["contentType"] as String
                                val size = attachmentAsJSON["size"] as? Int ?: (attachmentAsJSON["size"] as? Long)?.toInt() ?: (attachmentAsJSON["size"] as String).toInt()
                                val fileName = attachmentAsJSON["fileName"] as? String
                                val flags = 0
                                val url = attachmentAsJSON["url"] as String
                                val caption = attachmentAsJSON["caption"] as? String
                                val linkPreviewURL = attachmentAsJSON["linkPreviewUrl"] as? String
                                val linkPreviewTitle = attachmentAsJSON["linkPreviewTitle"] as? String
                                if (kind == OpenGroupMessage.Attachment.Kind.LinkPreview && (linkPreviewURL == null || linkPreviewTitle == null)) {
                                    null
                                } else {
                                    OpenGroupMessage.Attachment(kind, server, id, contentType, size, fileName, flags, 0, 0, caption, url, linkPreviewURL, linkPreviewTitle)
                                }
                            } catch (e: Exception) {
                                Log.d("Loki","Couldn't parse attachment due to error: $e.")
                                null
                            }
                        }
                        // Set the last message server ID here to avoid the situation where a message doesn't have a valid signature and this function is called over and over
                        @Suppress("NAME_SHADOWING") val lastMessageServerID = storage.getLastMessageServerID(channel, server)
                        if (serverID > lastMessageServerID ?: 0) { storage.setLastMessageServerID(channel, server, serverID) }
                        val hexEncodedSignature = value["sig"] as String
                        val signatureVersion = value["sigver"] as? Long ?: (value["sigver"] as? Int)?.toLong() ?: (value["sigver"] as String).toLong()
                        val signature = OpenGroupMessage.Signature(Hex.fromStringCondensed(hexEncodedSignature), signatureVersion)
                        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                        format.timeZone = TimeZone.getTimeZone("GMT")
                        val dateAsString = message["created_at"] as String
                        val serverTimestamp = format.parse(dateAsString).time
                        // Verify the message
                        val groupMessage = OpenGroupMessage(serverID, publicKey, displayName, body, timestamp, openGroupMessageType, quote, attachments.toMutableList(), profilePicture, signature, serverTimestamp)
                        if (groupMessage.hasValidSignature()) groupMessage else null
                    } catch (exception: Exception) {
                        Log.d("Loki", "Couldn't parse message for open group with ID: $channel on server: $server from: ${JsonUtil.toJson(message)}. Exception: ${exception.message}")
                        return@mapNotNull null
                    }
                }.sortedBy { it.serverTimestamp }
                messages
            } catch (exception: Exception) {
                Log.d("Loki", "Couldn't parse messages for open group with ID: $channel on server: $server.")
                throw exception
            }
        }
    }

    @JvmStatic
    fun getDeletedMessageServerIDs(channel: Long, server: String): Promise<List<Long>, Exception> {
        Log.d("Loki", "Getting deleted messages for open group with ID: $channel on server: $server.")
        val storage = MessagingModuleConfiguration.shared.storage
        val parameters = mutableMapOf<String, Any>()
        val lastDeletionServerID = storage.getLastDeletionServerID(channel, server)
        if (lastDeletionServerID != null) {
            parameters["since_id"] = lastDeletionServerID
        } else {
            parameters["count"] = fallbackBatchCount
        }
        return execute(HTTPVerb.GET, server, "loki/v1/channel/$channel/deletes", parameters = parameters).then { json ->
            try {
                val deletedMessageServerIDs = (json["data"] as List<Map<*, *>>).mapNotNull { deletion ->
                    try {
                        val serverID = deletion["id"] as? Long ?: (deletion["id"] as? Int)?.toLong() ?: (deletion["id"] as String).toLong()
                        val messageServerID = deletion["message_id"] as? Long ?: (deletion["message_id"] as? Int)?.toLong() ?: (deletion["message_id"] as String).toLong()
                        @Suppress("NAME_SHADOWING") val lastDeletionServerID = storage.getLastDeletionServerID(channel, server)
                        if (serverID > (lastDeletionServerID ?: 0)) { storage.setLastDeletionServerID(channel, server, serverID) }
                        messageServerID
                    } catch (exception: Exception) {
                        Log.d("Loki", "Couldn't parse deleted message for open group with ID: $channel on server: $server. Exception: ${exception.message}")
                        return@mapNotNull null
                    }
                }
                deletedMessageServerIDs
            } catch (exception: Exception) {
                Log.d("Loki", "Couldn't parse deleted messages for open group with ID: $channel on server: $server.")
                throw exception
            }
        }
    }

    @JvmStatic
    fun sendMessage(message: OpenGroupMessage, channel: Long, server: String): Promise<OpenGroupMessage, Exception> {
        val deferred = deferred<OpenGroupMessage, Exception>()
        val storage = MessagingModuleConfiguration.shared.storage
        val userKeyPair = storage.getUserKeyPair() ?: throw Error.Generic
        val userDisplayName = storage.getUserDisplayName() ?: throw Error.Generic
        ThreadUtils.queue {
            val signedMessage = message.sign(userKeyPair.second)
            if (signedMessage == null) {
                deferred.reject(Error.SigningFailed)
            } else {
                retryIfNeeded(maxRetryCount) {
                    Log.d("Loki", "Sending message to open group with ID: $channel on server: $server.")
                    val parameters = signedMessage.toJSON()
                    execute(HTTPVerb.POST, server, "channels/$channel/messages", parameters = parameters).then { json ->
                        try {
                            val data = json["data"] as Map<*, *>
                            val serverID = (data["id"] as? Long) ?: (data["id"] as? Int)?.toLong() ?: (data["id"] as String).toLong()
                            val text = data["text"] as String
                            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                            format.timeZone = TimeZone.getTimeZone("GMT")
                            val dateAsString = data["created_at"] as String
                            val timestamp = format.parse(dateAsString).time
                            OpenGroupMessage(serverID, userKeyPair.first, userDisplayName, text, timestamp, openGroupMessageType, message.quote, message.attachments, null, signedMessage.signature, timestamp)
                        } catch (exception: Exception) {
                            Log.d("Loki", "Couldn't parse message for open group with ID: $channel on server: $server.")
                            throw exception
                        }
                    }
                }.success {
                    deferred.resolve(it)
                }.fail {
                    deferred.reject(it)
                }
            }
        }
        return deferred.promise
    }

    fun deleteMessage(messageServerID: Long, channel: Long, server: String, isSentByUser: Boolean): Promise<Long, Exception> {
        return retryIfNeeded(maxRetryCount) {
            val isModerationRequest = !isSentByUser
            Log.d("Loki", "Deleting message with ID: $messageServerID from open group with ID: $channel on server: $server (isModerationRequest = $isModerationRequest).")
            val endpoint = if (isSentByUser) "channels/$channel/messages/$messageServerID" else "loki/v1/moderation/message/$messageServerID"
            execute(HTTPVerb.DELETE, server, endpoint, isJSONRequired = false).then {
                Log.d("Loki", "Deleted message with ID: $messageServerID from open group with ID: $channel on server: $server.")
                messageServerID
            }
        }
    }

    @JvmStatic
    fun deleteMessages(messageServerIDs: List<Long>, channel: Long, server: String, isSentByUser: Boolean): Promise<List<Long>, Exception> {
        return retryIfNeeded(maxRetryCount) {
            val isModerationRequest = !isSentByUser
            val parameters = mapOf( "ids" to messageServerIDs.joinToString(",") )
            Log.d("Loki", "Deleting messages with IDs: ${messageServerIDs.joinToString()} from open group with ID: $channel on server: $server (isModerationRequest = $isModerationRequest).")
            val endpoint = if (isSentByUser) "loki/v1/messages" else "loki/v1/moderation/messages"
            execute(HTTPVerb.DELETE, server, endpoint, parameters = parameters, isJSONRequired = false).then { json ->
                Log.d("Loki", "Deleted messages with IDs: $messageServerIDs from open group with ID: $channel on server: $server.")
                messageServerIDs
            }
        }
    }

    @JvmStatic
    fun getModerators(channel: Long, server: String): Promise<Set<String>, Exception> {
        return execute(HTTPVerb.GET, server, "loki/v1/channel/$channel/get_moderators").then { json ->
            try {
                @Suppress("UNCHECKED_CAST") val moderators = json["moderators"] as? List<String>
                val moderatorsAsSet = moderators.orEmpty().toSet()
                if (this.moderators[server] != null) {
                    this.moderators[server]!![channel] = moderatorsAsSet
                } else {
                    this.moderators[server] = hashMapOf( channel to moderatorsAsSet )
                }
                moderatorsAsSet
            } catch (exception: Exception) {
                Log.d("Loki", "Couldn't parse moderators for open group with ID: $channel on server: $server.")
                throw exception
            }
        }
    }

    @JvmStatic
    fun getChannelInfo(channel: Long, server: String): Promise<OpenGroupInfo, Exception> {
        return retryIfNeeded(maxRetryCount) {
            val parameters = mapOf( "include_annotations" to 1 )
            execute(HTTPVerb.GET, server, "/channels/$channel", parameters = parameters).then { json ->
                try {
                    val data = json["data"] as Map<*, *>
                    val annotations = data["annotations"] as List<Map<*, *>>
                    val annotation = annotations.find { (it["type"] as? String ?: "") == channelInfoType } ?: throw Error.ParsingFailed
                    val info = annotation["value"] as Map<*, *>
                    val displayName = info["name"] as String
                    val countInfo = data["counts"] as Map<*, *>
                    val memberCount = countInfo["subscribers"] as? Int ?: (countInfo["subscribers"] as? Long)?.toInt() ?: (countInfo["subscribers"] as String).toInt()
                    val profilePictureURL = info["avatar"] as String
                    val publicChatInfo = OpenGroupInfo(displayName, profilePictureURL, memberCount)
                    MessagingModuleConfiguration.shared.storage.setUserCount(channel, server, memberCount)
                    publicChatInfo
                } catch (exception: Exception) {
                    Log.d("Loki", "Couldn't parse info for open group with ID: $channel on server: $server.")
                    throw exception
                }
            }
        }
    }

    @JvmStatic
    fun updateProfileIfNeeded(channel: Long, server: String, groupID: String, info: OpenGroupInfo, isForcedUpdate: Boolean) {
        val storage = MessagingModuleConfiguration.shared.storage
        storage.setUserCount(channel, server, info.memberCount)
        storage.updateTitle(groupID, info.displayName)
        // Download and update profile picture if needed
        val oldProfilePictureURL = storage.getOpenGroupProfilePictureURL(channel, server)
        if (isForcedUpdate || oldProfilePictureURL != info.profilePictureURL) {
            val profilePictureAsByteArray = downloadOpenGroupProfilePicture(server, info.profilePictureURL) ?: return
            storage.updateProfilePicture(groupID, profilePictureAsByteArray)
            storage.setOpenGroupProfilePictureURL(channel, server, info.profilePictureURL)
        }
    }

    @JvmStatic
    fun downloadOpenGroupProfilePicture(server: String, endpoint: String): ByteArray? {
        val url = "${server.removeSuffix("/")}/${endpoint.removePrefix("/")}"
        Log.d("Loki", "Downloading open group profile picture from \"$url\".")
        val outputStream = ByteArrayOutputStream()
        try {
            DownloadUtilities.downloadFile(outputStream, url, FileServerAPI.maxFileSize, null)
            Log.d("Loki", "Open group profile picture was successfully loaded from \"$url\"")
            return outputStream.toByteArray()
        } catch (e: Exception) {
            Log.d("Loki", "Failed to download open group profile picture from \"$url\" due to error: $e.")
            return null
        } finally {
            outputStream.close()
        }
    }

    @JvmStatic
    fun join(channel: Long, server: String): Promise<Unit, Exception> {
        return retryIfNeeded(maxRetryCount) {
            execute(HTTPVerb.POST, server, "/channels/$channel/subscribe").then {
                Log.d("Loki", "Joined channel with ID: $channel on server: $server.")
            }
        }
    }

    @JvmStatic
    fun leave(channel: Long, server: String): Promise<Unit, Exception> {
        return retryIfNeeded(maxRetryCount) {
            execute(HTTPVerb.DELETE, server, "/channels/$channel/subscribe").then {
                Log.d("Loki", "Left channel with ID: $channel on server: $server.")
            }
        }
    }

    @JvmStatic
    fun ban(publicKey: String, server: String): Promise<Unit,Exception> {
        return retryIfNeeded(maxRetryCount) {
            execute(HTTPVerb.POST, server, "/loki/v1/moderation/blacklist/@$publicKey").then {
                Log.d("Loki", "Banned user with ID: $publicKey from $server")
            }
        }
    }

    @JvmStatic
    fun getDisplayNames(publicKeys: Set<String>, server: String): Promise<Map<String, String>, Exception> {
        return getUserProfiles(publicKeys, server, false).map { json ->
            val mapping = mutableMapOf<String, String>()
            for (user in json) {
                if (user["username"] != null) {
                    val publicKey = user["username"] as String
                    val displayName = user["name"] as? String ?: "Anonymous"
                    mapping[publicKey] = displayName
                }
            }
            mapping
        }
    }

    @JvmStatic
    fun setDisplayName(newDisplayName: String?, server: String): Promise<Unit, Exception> {
        Log.d("Loki", "Updating display name on server: $server.")
        val parameters = mapOf( "name" to (newDisplayName ?: "") )
        return execute(HTTPVerb.PATCH, server, "users/me", parameters = parameters).map { Unit }
    }

    @JvmStatic
    fun setProfilePicture(server: String, profileKey: ByteArray, url: String?): Promise<Unit, Exception> {
        return setProfilePicture(server, Base64.encodeBytes(profileKey), url)
    }

    fun setProfilePicture(server: String, profileKey: String, url: String?): Promise<Unit, Exception> {
        Log.d("Loki", "Updating profile picture on server: $server.")
        val value = when (url) {
            null -> null
            else -> mapOf( "profileKey" to profileKey, "url" to url )
        }
        // TODO: This may actually completely replace the annotations, have to double check it
        return setSelfAnnotation(server, profilePictureType, value).map { Unit }.fail {
            Log.d("Loki", "Failed to update profile picture due to error: $it.")
        }
    }
    // endregion
}
