package org.session.libsession.messaging.sending_receiving

import android.util.Size
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.NotifyPNServerJob
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ClosedGroupUpdate
import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.messages.visible.Profile
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.opengroups.OpenGroupAPI
import org.session.libsession.messaging.opengroups.OpenGroupMessage
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.snode.RawResponsePromise
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeMessage
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.api.messages.SignalServiceAttachment
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.internal.util.Base64
import org.session.libsignal.service.loki.api.crypto.ProofOfWork
import org.session.libsignal.service.loki.utilities.hexEncodedPublicKey


object MessageSender {

    // Error
    internal sealed class Error(val description: String) : Exception() {
        object InvalidMessage : Error("Invalid message.")
        object ProtoConversionFailed : Error("Couldn't convert message to proto.")
        object ProofOfWorkCalculationFailed : Error("Proof of work calculation failed.")
        object NoUserX25519KeyPair : Error("Couldn't find user X25519 key pair.")
        object NoUserED25519KeyPair : Error("Couldn't find user ED25519 key pair.")
        object SigningFailed : Error("Couldn't sign message.")
        object EncryptionFailed : Error("Couldn't encrypt message.")

        // Closed groups
        object NoThread : Error("Couldn't find a thread associated with the given group public key.")
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

    // Preparation
    fun prep(signalAttachments: List<SignalServiceAttachment>, message: VisibleMessage) {
        // TODO: Deal with SignalServiceAttachmentStream
        val attachments = mutableListOf<Attachment>()
        for (signalAttachment in signalAttachments) {
            val attachment = Attachment()
            if (signalAttachment.isPointer) {
                val signalAttachmentPointer = signalAttachment.asPointer()
                attachment.fileName = signalAttachmentPointer.fileName.orNull()
                attachment.caption = signalAttachmentPointer.caption.orNull()
                attachment.contentType = signalAttachmentPointer.contentType
                attachment.digest = signalAttachmentPointer.digest.orNull()
                attachment.key = signalAttachmentPointer.key
                attachment.sizeInBytes = signalAttachmentPointer.size.orNull()
                attachment.url = signalAttachmentPointer.url
                attachment.size = Size(signalAttachmentPointer.width, signalAttachmentPointer.height)
                attachments.add(attachment)
            }
        }
        val attachmentIDs = MessagingConfiguration.shared.storage.persist(attachments)
        message.attachmentIDs.addAll(attachmentIDs)
    }

    // Convenience
    fun send(message: Message, destination: Destination): Promise<Unit, Exception> {
        if (destination is Destination.OpenGroup) {
            return sendToOpenGroupDestination(destination, message)
        }
        return sendToSnodeDestination(destination, message)
    }

    // One-on-One Chats & Closed Groups
    fun sendToSnodeDestination(destination: Destination, message: Message): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        val promise = deferred.promise
        val storage = MessagingConfiguration.shared.storage
        val userPublicKey = storage.getUserPublicKey()
        val preconditionFailure = Exception("Destination should not be open groups!")
        var snodeMessage: SnodeMessage? = null
        // Set the timestamp, sender and recipient
        message.sentTimestamp ?: run { message.sentTimestamp = System.currentTimeMillis() } /* Visible messages will already have their sent timestamp set */
        message.sender = userPublicKey
        try {
            when (destination) {
                is Destination.Contact -> message.recipient = destination.publicKey
                is Destination.ClosedGroup -> message.recipient = destination.groupPublicKey
                is Destination.OpenGroup -> throw preconditionFailure
            }
            val isSelfSend = (message.recipient == userPublicKey)
            // Set the failure handler (need it here already for precondition failure handling)
            fun handleFailure(error: Exception) {
                handleFailedMessageSend(message, error)
                if (destination is Destination.Contact && message is VisibleMessage && !isSelfSend) {
                    //TODO Notify user for send failure
                }
                deferred.reject(error)
            }
            // Validate the message
            if (!message.isValid()) { throw Error.InvalidMessage }
            // Stop here if this is a self-send
            if (isSelfSend) {
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
            val plaintext = proto.toByteArray()
            // Encrypt the serialized protobuf
            if (destination is Destination.Contact && message is VisibleMessage && !isSelfSend) {
                //TODO Notify user for encrypting message
            }
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
                //TODO Notify user for proof of work calculating
            }
            val recipient = message.recipient!!
            val base64EncodedData = Base64.encodeBytes(wrappedMessage)
            val timestamp = System.currentTimeMillis()
            val nonce = ProofOfWork.calculate(base64EncodedData, recipient, timestamp, message.ttl.toInt()) ?: throw Error.ProofOfWorkCalculationFailed
            // Send the result
            snodeMessage = SnodeMessage(recipient, base64EncodedData, message.ttl, timestamp, nonce)
            SnodeAPI.sendMessage(snodeMessage).success { promises: Set<RawResponsePromise> ->
                var isSuccess = false
                val promiseCount = promises.size
                var errorCount = 0
                promises.forEach { promise: RawResponsePromise ->
                    promise.success {
                        if (isSuccess) { return@success } // Succeed as soon as the first promise succeeds
                        isSuccess = true
                        if (destination is Destination.Contact && message is VisibleMessage && !isSelfSend) {
                            //TODO Notify user for message sent
                        }
                        handleSuccessfulMessageSend(message, destination)
                        var shouldNotify = (message is VisibleMessage)
                        if (message is ClosedGroupUpdate && message.kind is ClosedGroupUpdate.Kind.New) {
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
                        deferred.reject(it)
                    }
                }
            }.fail {
                Log.d("Loki", "Couldn't send message due to error: $it.")
                deferred.reject(it)
            }
        } catch (exception: Exception) {
            deferred.reject(exception)
        }
        return promise
    }

    // Open Groups
    fun sendToOpenGroupDestination(destination: Destination, message: Message): Promise<Unit, Exception> {
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
    fun handleSuccessfulMessageSend(message: Message, destination: Destination) {
        val storage = MessagingConfiguration.shared.storage
        val messageId = storage.getMessageIdInDatabase(message.sentTimestamp!!, message.sender!!) ?: return
        if (message.openGroupServerMessageID != null) {
            storage.setOpenGroupServerMessageID(messageId, message.openGroupServerMessageID!!)
        }
        storage.markAsSent(messageId)
        storage.markUnidentified(messageId)
        SSKEnvironment.shared.messageExpirationManager.startAnyExpiration(messageId)
    }

    fun handleFailedMessageSend(message: Message, error: Exception) {
        val storage = MessagingConfiguration.shared.storage
        val messageId = storage.getMessageIdInDatabase(message.sentTimestamp!!, message.sender!!) ?: return
        storage.setErrorMessage(messageId, error)
    }
}