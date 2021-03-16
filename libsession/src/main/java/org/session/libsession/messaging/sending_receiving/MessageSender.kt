package org.session.libsession.messaging.sending_receiving

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageSendJob
import org.session.libsession.messaging.jobs.NotifyPNServerJob
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ClosedGroupControlMessage
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.messages.visible.*
import org.session.libsession.messaging.opengroups.OpenGroupAPI
import org.session.libsession.messaging.opengroups.OpenGroupMessage
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.snode.RawResponsePromise
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeConfiguration
import org.session.libsession.snode.SnodeMessage
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsignal.service.internal.push.PushTransportDetails
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.api.crypto.ProofOfWork
import org.session.libsignal.service.loki.utilities.hexEncodedPublicKey
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.logging.Log
import org.session.libsession.messaging.sending_receiving.attachments.Attachment as SignalAttachment
import org.session.libsession.messaging.sending_receiving.linkpreview.LinkPreview as SignalLinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel as SignalQuote


object MessageSender {

    // Error
    sealed class Error(val description: String) : Exception() {
        object InvalidMessage : Error("Invalid message.")
        object ProtoConversionFailed : Error("Couldn't convert message to proto.")
        object ProofOfWorkCalculationFailed : Error("Proof of work calculation failed.")
        object NoUserX25519KeyPair : Error("Couldn't find user X25519 key pair.")
        object NoUserED25519KeyPair : Error("Couldn't find user ED25519 key pair.")
        object SigningFailed : Error("Couldn't sign message.")
        object EncryptionFailed : Error("Couldn't encrypt message.")

        // Closed groups
        object NoThread : Error("Couldn't find a thread associated with the given group public key.")
        object NoKeyPair: Error("Couldn't find a private key associated with the given group public key.")
        object NoPrivateKey : Error("Couldn't find a private key associated with the given group public key.")
        object InvalidClosedGroupUpdate : Error("Invalid group update.")

        internal val isRetryable: Boolean = when (this) {
            is InvalidMessage -> false
            is ProtoConversionFailed -> false
            is ProofOfWorkCalculationFailed -> false
            is InvalidClosedGroupUpdate -> false
            else -> true
        }
    }

    // Convenience
    fun send(message: Message, destination: Destination): Promise<Unit, Exception> {
        if (destination is Destination.OpenGroup) {
            return sendToOpenGroupDestination(destination, message)
        }
        return sendToSnodeDestination(destination, message)
    }

    // One-on-One Chats & Closed Groups
    private fun sendToSnodeDestination(destination: Destination, message: Message, isSyncMessage: Boolean = false): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        val promise = deferred.promise
        val storage = MessagingConfiguration.shared.storage
        val userPublicKey = storage.getUserPublicKey()
        val preconditionFailure = Exception("Destination should not be open groups!")
        // Set the timestamp, sender and recipient
        message.sentTimestamp ?: run { message.sentTimestamp = System.currentTimeMillis() } /* Visible messages will already have their sent timestamp set */
        message.sender = userPublicKey
        val isSelfSend = (message.recipient == userPublicKey)
        // Set the failure handler (need it here already for precondition failure handling)
        fun handleFailure(error: Exception) {
            handleFailedMessageSend(message, error)
            if (destination is Destination.Contact && message is VisibleMessage && !isSelfSend) {
                SnodeConfiguration.shared.broadcaster.broadcast("messageFailed", message.sentTimestamp!!)
            }
            deferred.reject(error)
        }
        try {
            when (destination) {
                is Destination.Contact -> message.recipient = destination.publicKey
                is Destination.ClosedGroup -> message.recipient = destination.groupPublicKey
                is Destination.OpenGroup -> throw preconditionFailure
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
                val profilePrictureUrl = storage.getUserProfilePictureURL()
                if (profileKey != null && profilePrictureUrl != null) {
                    message.profile = Profile(displayName, profileKey, profilePrictureUrl)
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
                is Destination.Contact -> ciphertext = MessageSenderEncryption.encryptWithSessionProtocol(plaintext, destination.publicKey)
                is Destination.ClosedGroup -> {
                    val encryptionKeyPair = MessagingConfiguration.shared.storage.getLatestClosedGroupEncryptionKeyPair(destination.groupPublicKey)!!
                    ciphertext = MessageSenderEncryption.encryptWithSessionProtocol(plaintext, encryptionKeyPair.hexEncodedPublicKey)
                }
                is Destination.OpenGroup -> throw preconditionFailure
            }
            // Wrap the result
            val kind: SignalServiceProtos.Envelope.Type
            val senderPublicKey: String
            when (destination) {
                is Destination.Contact -> {
                    kind = SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER
                    senderPublicKey = ""
                }
                is Destination.ClosedGroup -> {
                    kind = SignalServiceProtos.Envelope.Type.CLOSED_GROUP_CIPHERTEXT
                    senderPublicKey = destination.groupPublicKey
                }
                is Destination.OpenGroup -> throw preconditionFailure
            }
            val wrappedMessage = MessageWrapper.wrap(kind, message.sentTimestamp!!, senderPublicKey, ciphertext)
            // Calculate proof of work
            if (destination is Destination.Contact && message is VisibleMessage && !isSelfSend) {
                SnodeConfiguration.shared.broadcaster.broadcast("calculatingPoW", message.sentTimestamp!!)
            }
            val recipient = message.recipient!!
            val base64EncodedData = Base64.encodeBytes(wrappedMessage)
            val timestamp = System.currentTimeMillis()
            val nonce = ProofOfWork.calculate(base64EncodedData, recipient, timestamp, message.ttl.toInt()) ?: throw Error.ProofOfWorkCalculationFailed
            // Send the result
            val snodeMessage = SnodeMessage(recipient, base64EncodedData, message.ttl, timestamp, nonce)
            if (destination is Destination.Contact && message is VisibleMessage && !isSelfSend) {
                SnodeConfiguration.shared.broadcaster.broadcast("sendingMessage", message.sentTimestamp!!)
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
                            SnodeConfiguration.shared.broadcaster.broadcast("messageSent", message.sentTimestamp!!)
                        }
                        handleSuccessfulMessageSend(message, destination, isSyncMessage)
                        var shouldNotify = (message is VisibleMessage && !isSyncMessage)
                        if (message is ClosedGroupControlMessage && message.kind is ClosedGroupControlMessage.Kind.New) {
                            shouldNotify = true
                        }
                        if (shouldNotify) {
                            val notifyPNServerJob = NotifyPNServerJob(snodeMessage)
                            JobQueue.shared.add(notifyPNServerJob)
                            deferred.resolve(Unit)
                        }
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
        val storage = MessagingConfiguration.shared.storage
        val preconditionFailure = Exception("Destination should not be contacts or closed groups!")
        message.sentTimestamp ?: run { message.sentTimestamp = System.currentTimeMillis() }
        message.sender = storage.getUserPublicKey()
        try {
            val server: String
            val channel: Long
            when (destination) {
                is Destination.Contact -> throw preconditionFailure
                is Destination.ClosedGroup -> throw preconditionFailure
                is Destination.OpenGroup -> {
                    message.recipient = "${destination.server}.${destination.channel}"
                    server = destination.server
                    channel = destination.channel
                }
            }
            // Set the failure handler (need it here already for precondition failure handling)
            fun handleFailure(error: Exception) {
                handleFailedMessageSend(message, error)
                deferred.reject(error)
            }
            // Validate the message
            if (message !is VisibleMessage || !message.isValid()) {
                handleFailure(Error.InvalidMessage)
                throw Error.InvalidMessage
            }
            // Convert the message to an open group message
            val openGroupMessage = OpenGroupMessage.from(message, server) ?: kotlin.run {
                handleFailure(Error.InvalidMessage)
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
        } catch (exception: Exception) {
            deferred.reject(exception)
        }
        return deferred.promise
    }

    // Result Handling
    fun handleSuccessfulMessageSend(message: Message, destination: Destination, isSyncMessage: Boolean = false) {
        val storage = MessagingConfiguration.shared.storage
        val messageId = storage.getMessageIdInDatabase(message.sentTimestamp!!, message.sender!!) ?: return
        // Ignore future self-sends
        storage.addReceivedMessageTimestamp(message.sentTimestamp!!)
        // Track the open group server message ID
        if (message.openGroupServerMessageID != null) {
            storage.setOpenGroupServerMessageID(messageId, message.openGroupServerMessageID!!)
        }
        // Mark the message as sent
        storage.markAsSent(message.sentTimestamp!!, message.sender!!)
        storage.markUnidentified(message.sentTimestamp!!, message.sender!!)
        // Start the disappearing messages timer if needed
        if (message is VisibleMessage && !isSyncMessage) {
            SSKEnvironment.shared.messageExpirationManager.startAnyExpiration(message.sentTimestamp!!, message.sender!!)
        }
        // Sync the message if:
        // • it's a visible message
        // • the destination was a contact
        // • we didn't sync it already
        val userPublicKey = storage.getUserPublicKey()!!
        if (destination is Destination.Contact && !isSyncMessage) {
            if (message is VisibleMessage) { message.syncTarget = destination.publicKey }
            if (message is ExpirationTimerUpdate) { message.syncTarget = destination.publicKey }
            sendToSnodeDestination(Destination.Contact(userPublicKey), message, true)
        }
    }

    fun handleFailedMessageSend(message: Message, error: Exception) {
        val storage = MessagingConfiguration.shared.storage
        storage.setErrorMessage(message.sentTimestamp!!, message.sender!!, error)
    }

    // Convenience
    @JvmStatic
    fun send(message: VisibleMessage, address: Address, attachments: List<SignalAttachment>, quote: SignalQuote?, linkPreview: SignalLinkPreview?) {
        val dataProvider = MessagingConfiguration.shared.messageDataProvider
        val attachmentIDs = dataProvider.getAttachmentIDsFor(message.id!!)
        message.attachmentIDs.addAll(attachmentIDs)
        message.quote = Quote.from(quote)
        message.linkPreview = LinkPreview.from(linkPreview)
        message.linkPreview?.let {
            if (it.attachmentID == null) {
                dataProvider.getLinkPreviewAttachmentIDFor(message.id!!)?.let {
                    message.linkPreview!!.attachmentID = it
                    message.attachmentIDs.remove(it)
                }
            }
        }
        send(message, address)
    }

    @JvmStatic
    fun send(message: Message, address: Address) {
        val threadID = MessagingConfiguration.shared.storage.getOrCreateThreadIdFor(address)
        message.threadID = threadID
        val destination = Destination.from(address)
        val job = MessageSendJob(message, destination)
        JobQueue.shared.add(job)
    }

    fun sendNonDurably(message: VisibleMessage, attachments: List<SignalAttachment>, address: Address): Promise<Unit, Exception> {
        val attachmentIDs = MessagingConfiguration.shared.messageDataProvider.getAttachmentIDsFor(message.id!!)
        message.attachmentIDs.addAll(attachmentIDs)
        return sendNonDurably(message, address)
    }

    fun sendNonDurably(message: Message, address: Address): Promise<Unit, Exception> {
        val threadID = MessagingConfiguration.shared.storage.getOrCreateThreadIdFor(address)
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
    fun explicitLeave(groupPublicKey: String): Promise<Unit, Exception> {
        return leave(groupPublicKey)
    }
}