package org.session.libsession.messaging.sending_receiving

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageSendJob
import org.session.libsession.messaging.jobs.NotifyPNServerJob
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ClosedGroupControlMessage
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.messages.visible.*
import org.session.libsession.messaging.open_groups.*
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.snode.RawResponsePromise
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeModule
import org.session.libsession.snode.SnodeMessage
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsignal.crypto.PushTransportDetails
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import java.lang.IllegalStateException
import org.session.libsession.messaging.sending_receiving.attachments.Attachment as SignalAttachment
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview as SignalLinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel as SignalQuote

object MessageSender {

    // Error
    sealed class Error(val description: String) : Exception(description) {
        object InvalidMessage : Error("Invalid message.")
        object ProtoConversionFailed : Error("Couldn't convert message to proto.")
        object NoUserED25519KeyPair : Error("Couldn't find user ED25519 key pair.")
        object SigningFailed : Error("Couldn't sign message.")
        object EncryptionFailed : Error("Couldn't encrypt message.")

        // Closed groups
        object NoThread : Error("Couldn't find a thread associated with the given group public key.")
        object NoKeyPair: Error("Couldn't find a private key associated with the given group public key.")
        object InvalidClosedGroupUpdate : Error("Invalid group update.")

        internal val isRetryable: Boolean = when (this) {
            is InvalidMessage, ProtoConversionFailed, InvalidClosedGroupUpdate -> false
            else -> true
        }
    }

    // Convenience
    fun send(message: Message, destination: Destination): Promise<Unit, Exception> {
        if (destination is Destination.OpenGroup || destination is Destination.OpenGroupV2) {
            return sendToOpenGroupDestination(destination, message)
        }
        return sendToSnodeDestination(destination, message)
    }

    // One-on-One Chats & Closed Groups
    private fun sendToSnodeDestination(destination: Destination, message: Message, isSyncMessage: Boolean = false): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        val promise = deferred.promise
        val storage = MessagingModuleConfiguration.shared.storage
        val userPublicKey = storage.getUserPublicKey()
        // Set the timestamp, sender and recipient
        if (message.sentTimestamp == null) {
            message.sentTimestamp = System.currentTimeMillis() // Visible messages will already have their sent timestamp set
        }
        message.sender = userPublicKey
        val isSelfSend = (message.recipient == userPublicKey)
        // Set the failure handler (need it here already for precondition failure handling)
        fun handleFailure(error: Exception) {
            handleFailedMessageSend(message, error)
            if (destination is Destination.Contact && message is VisibleMessage && !isSelfSend) {
                SnodeModule.shared.broadcaster.broadcast("messageFailed", message.sentTimestamp!!)
            }
            deferred.reject(error)
        }
        try {
            when (destination) {
                is Destination.Contact -> message.recipient = destination.publicKey
                is Destination.ClosedGroup -> message.recipient = destination.groupPublicKey
                is Destination.OpenGroup, is Destination.OpenGroupV2 -> throw IllegalStateException("Destination should not be an open group.")
            }
            // Validate the message
            if (!message.isValid()) { throw Error.InvalidMessage }
            // Stop here if this is a self-send, unless it's:
            // • a configuration message
            // • a sync message
            // • a closed group control message of type `new`
            var isNewClosedGroupControlMessage = false
            if (message is ClosedGroupControlMessage && message.kind is ClosedGroupControlMessage.Kind.New) isNewClosedGroupControlMessage = true
            if (isSelfSend && message !is ConfigurationMessage && !isSyncMessage && !isNewClosedGroupControlMessage) {
                handleSuccessfulMessageSend(message, destination)
                deferred.resolve(Unit)
                return promise
            }
            // Attach the user's profile if needed
            if (message is VisibleMessage) {
                val displayName = storage.getUserDisplayName()!!
                val profileKey = storage.getUserProfileKey()
                val profilePictureUrl = storage.getUserProfilePictureURL()
                if (profileKey != null && profilePictureUrl != null) {
                    message.profile = Profile(displayName, profileKey, profilePictureUrl)
                } else {
                    message.profile = Profile(displayName)
                }
            }
            // Convert it to protobuf
            val proto = message.toProto() ?: throw Error.ProtoConversionFailed
            // Serialize the protobuf
            val plaintext = PushTransportDetails.getPaddedMessageBody(proto.toByteArray())
            // Encrypt the serialized protobuf
            val ciphertext: ByteArray
            when (destination) {
                is Destination.Contact -> ciphertext = MessageEncrypter.encrypt(plaintext, destination.publicKey)
                is Destination.ClosedGroup -> {
                    val encryptionKeyPair = MessagingModuleConfiguration.shared.storage.getLatestClosedGroupEncryptionKeyPair(destination.groupPublicKey)!!
                    ciphertext = MessageEncrypter.encrypt(plaintext, encryptionKeyPair.hexEncodedPublicKey)
                }
                is Destination.OpenGroup, is Destination.OpenGroupV2 -> throw IllegalStateException("Destination should not be open group.")
            }
            // Wrap the result
            val kind: SignalServiceProtos.Envelope.Type
            val senderPublicKey: String
            when (destination) {
                is Destination.Contact -> {
                    kind = SignalServiceProtos.Envelope.Type.SESSION_MESSAGE
                    senderPublicKey = ""
                }
                is Destination.ClosedGroup -> {
                    kind = SignalServiceProtos.Envelope.Type.CLOSED_GROUP_MESSAGE
                    senderPublicKey = destination.groupPublicKey
                }
                is Destination.OpenGroup, is Destination.OpenGroupV2 -> throw IllegalStateException("Destination should not be open group.")
            }
            val wrappedMessage = MessageWrapper.wrap(kind, message.sentTimestamp!!, senderPublicKey, ciphertext)
            // Send the result
            if (destination is Destination.Contact && message is VisibleMessage && !isSelfSend) {
                SnodeModule.shared.broadcaster.broadcast("calculatingPoW", message.sentTimestamp!!)
            }
            val base64EncodedData = Base64.encodeBytes(wrappedMessage)
            // Send the result
            val snodeMessage = SnodeMessage(message.recipient!!, base64EncodedData, message.ttl, message.sentTimestamp!!)
            if (destination is Destination.Contact && message is VisibleMessage && !isSelfSend) {
                SnodeModule.shared.broadcaster.broadcast("sendingMessage", message.sentTimestamp!!)
            }
            SnodeAPI.sendMessage(snodeMessage).success { promises: Set<RawResponsePromise> ->
                var isSuccess = false
                val promiseCount = promises.size
                var errorCount = 0
                promises.forEach { promise: RawResponsePromise ->
                    promise.success {
                        if (isSuccess) { return@success } // Succeed as soon as the first promise succeeds
                        isSuccess = true
                        if (destination is Destination.Contact && message is VisibleMessage && !isSelfSend) {
                            SnodeModule.shared.broadcaster.broadcast("messageSent", message.sentTimestamp!!)
                        }
                        handleSuccessfulMessageSend(message, destination, isSyncMessage)
                        var shouldNotify = (message is VisibleMessage && !isSyncMessage)
                        if (message is ClosedGroupControlMessage && message.kind is ClosedGroupControlMessage.Kind.New) {
                            shouldNotify = true
                        }
                        if (shouldNotify) {
                            val notifyPNServerJob = NotifyPNServerJob(snodeMessage)
                            JobQueue.shared.add(notifyPNServerJob)
                        }
                        deferred.resolve(Unit)
                    }
                    promise.fail {
                        errorCount += 1
                        if (errorCount != promiseCount) { return@fail } // Only error out if all promises failed
                        handleFailure(it)
                    }
                }
            }.fail {
                Log.d("Loki", "Couldn't send message due to error: $it.")
                handleFailure(it)
            }
        } catch (exception: Exception) {
            handleFailure(exception)
        }
        return promise
    }

    // Open Groups
    private fun sendToOpenGroupDestination(destination: Destination, message: Message): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        val storage = MessagingModuleConfiguration.shared.storage
        if (message.sentTimestamp == null) {
            message.sentTimestamp = System.currentTimeMillis()
        }
        message.sender = storage.getUserPublicKey()
        // Set the failure handler (need it here already for precondition failure handling)
        fun handleFailure(error: Exception) {
            handleFailedMessageSend(message, error)
            deferred.reject(error)
        }
        try {
            when (destination) {
                is Destination.Contact, is Destination.ClosedGroup -> throw IllegalStateException("Invalid destination.")
                is Destination.OpenGroup -> {
                    message.recipient = "${destination.server}.${destination.channel}"
                    val server = destination.server
                    val channel = destination.channel
                    // Validate the message
                    if (message !is VisibleMessage || !message.isValid()) {
                        throw Error.InvalidMessage
                    }
                    // Convert the message to an open group message
                    val openGroupMessage = OpenGroupMessage.from(message, server) ?: run {
                        throw Error.InvalidMessage
                    }
                    // Send the result
                    OpenGroupAPI.sendMessage(openGroupMessage, channel, server).success {
                        message.openGroupServerMessageID = it.serverID
                        handleSuccessfulMessageSend(message, destination)
                        deferred.resolve(Unit)
                    }.fail {
                        handleFailure(it)
                    }
                }
                is Destination.OpenGroupV2 -> {
                    message.recipient = "${destination.server}.${destination.room}"
                    val server = destination.server
                    val room = destination.room
                    // Attach the user's profile if needed
                    if (message is VisibleMessage) {
                        val displayName = storage.getUserDisplayName()!!
                        val profileKey = storage.getUserProfileKey()
                        val profilePictureUrl = storage.getUserProfilePictureURL()
                        if (profileKey != null && profilePictureUrl != null) {
                            message.profile = Profile(displayName, profileKey, profilePictureUrl)
                        } else {
                            message.profile = Profile(displayName)
                        }
                    }
                    // Validate the message
                    if (message !is VisibleMessage || !message.isValid()) {
                        throw Error.InvalidMessage
                    }
                    val proto = message.toProto()!!
                    val plaintext = PushTransportDetails.getPaddedMessageBody(proto.toByteArray())
                    val openGroupMessage = OpenGroupMessageV2(
                        sender = message.sender,
                        sentTimestamp = message.sentTimestamp!!,
                        base64EncodedData = Base64.encodeBytes(plaintext),
                    )
                    OpenGroupAPIV2.send(openGroupMessage,room,server).success {
                        message.openGroupServerMessageID = it.serverID
                        handleSuccessfulMessageSend(message, destination)
                        deferred.resolve(Unit)
                    }.fail {
                        handleFailure(it)
                    }
                }
            }
        } catch (exception: Exception) {
            handleFailure(exception)
        }
        return deferred.promise
    }

    // Result Handling
    fun handleSuccessfulMessageSend(message: Message, destination: Destination, isSyncMessage: Boolean = false) {
        val storage = MessagingModuleConfiguration.shared.storage
        val userPublicKey = storage.getUserPublicKey()!!
        val messageID = storage.getMessageIdInDatabase(message.sentTimestamp!!, message.sender?:userPublicKey) ?: return
        // Ignore future self-sends
        storage.addReceivedMessageTimestamp(message.sentTimestamp!!)
        // Track the open group server message ID
        if (message.openGroupServerMessageID != null && destination is Destination.OpenGroupV2) {
            val encoded = GroupUtil.getEncodedOpenGroupID("${destination.server}.${destination.room}".toByteArray())
            val threadID = storage.getThreadIdFor(Address.fromSerialized(encoded))
            if (threadID != null && threadID >= 0) {
                storage.setOpenGroupServerMessageID(messageID, message.openGroupServerMessageID!!, threadID, !(message as VisibleMessage).isMediaMessage())
            }
        }
        // Mark the message as sent
        storage.markAsSent(message.sentTimestamp!!, message.sender?:userPublicKey)
        storage.markUnidentified(message.sentTimestamp!!, message.sender?:userPublicKey)
        // Start the disappearing messages timer if needed
        if (message is VisibleMessage && !isSyncMessage) {
            SSKEnvironment.shared.messageExpirationManager.startAnyExpiration(message.sentTimestamp!!, message.sender?:userPublicKey)
        }
        // Sync the message if:
        // • it's a visible message
        // • the destination was a contact
        // • we didn't sync it already
        if (destination is Destination.Contact && !isSyncMessage) {
            if (message is VisibleMessage) { message.syncTarget = destination.publicKey }
            if (message is ExpirationTimerUpdate) { message.syncTarget = destination.publicKey }
            sendToSnodeDestination(Destination.Contact(userPublicKey), message, true)
        }
    }

    fun handleFailedMessageSend(message: Message, error: Exception) {
        val storage = MessagingModuleConfiguration.shared.storage
        val userPublicKey = storage.getUserPublicKey()!!
        storage.setErrorMessage(message.sentTimestamp!!, message.sender?:userPublicKey, error)
    }

    // Convenience
    @JvmStatic
    fun send(message: VisibleMessage, address: Address, attachments: List<SignalAttachment>, quote: SignalQuote?, linkPreview: SignalLinkPreview?) {
        val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val attachmentIDs = messageDataProvider.getAttachmentIDsFor(message.id!!)
        message.attachmentIDs.addAll(attachmentIDs)
        message.quote = Quote.from(quote)
        message.linkPreview = LinkPreview.from(linkPreview)
        message.linkPreview?.let { linkPreview ->
            if (linkPreview.attachmentID == null) {
                messageDataProvider.getLinkPreviewAttachmentIDFor(message.id!!)?.let { attachmentID ->
                    message.linkPreview!!.attachmentID = attachmentID
                    message.attachmentIDs.remove(attachmentID)
                }
            }
        }
        send(message, address)
    }

    @JvmStatic
    fun send(message: Message, address: Address) {
        val threadID = MessagingModuleConfiguration.shared.storage.getOrCreateThreadIdFor(address)
        message.threadID = threadID
        val destination = Destination.from(address)
        val job = MessageSendJob(message, destination)
        JobQueue.shared.add(job)
    }

    fun sendNonDurably(message: VisibleMessage, attachments: List<SignalAttachment>, address: Address): Promise<Unit, Exception> {
        val attachmentIDs = MessagingModuleConfiguration.shared.messageDataProvider.getAttachmentIDsFor(message.id!!)
        message.attachmentIDs.addAll(attachmentIDs)
        return sendNonDurably(message, address)
    }

    fun sendNonDurably(message: Message, address: Address): Promise<Unit, Exception> {
        val threadID = MessagingModuleConfiguration.shared.storage.getOrCreateThreadIdFor(address)
        message.threadID = threadID
        val destination = Destination.from(address)
        return send(message, destination)
    }

    // Closed groups
    fun createClosedGroup(name: String, members: Collection<String>): Promise<String, Exception> {
        return create(name, members)
    }

    fun explicitNameChange(groupPublicKey: String, newName: String) {
        return setName(groupPublicKey, newName)
    }

    fun explicitAddMembers(groupPublicKey: String, membersToAdd: List<String>) {
        return addMembers(groupPublicKey, membersToAdd)
    }

    fun explicitRemoveMembers(groupPublicKey: String, membersToRemove: List<String>) {
        return removeMembers(groupPublicKey, membersToRemove)
    }

    @JvmStatic
    fun explicitLeave(groupPublicKey: String, notifyUser: Boolean): Promise<Unit, Exception> {
        return leave(groupPublicKey, notifyUser)
    }
}