package org.session.libsession.messaging.sending_receiving

import com.google.protobuf.MessageOrBuilder
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred

import org.session.libsession.messaging.Configuration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.jobs.NotifyPNServerJob
import org.session.libsession.messaging.opengroups.OpenGroupAPI
import org.session.libsession.messaging.opengroups.OpenGroupMessage
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.snode.RawResponsePromise
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeMessage

import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.api.messages.SignalServiceAttachment
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.internal.util.Base64
import org.session.libsignal.service.loki.api.crypto.ProofOfWork


object MessageSender {

    // Error
    internal sealed class Error(val description: String) : Exception() {
        object InvalidMessage : Error("Invalid message.")
        object ProtoConversionFailed : Error("Couldn't convert message to proto.")
        object ProofOfWorkCalculationFailed : Error("Proof of work calculation failed.")
        object NoUserPublicKey : Error("Couldn't find user key pair.")

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
        // TODO: Deal with attachments
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
        val storage = Configuration.shared.storage
        val preconditionFailure = Exception("Destination should not be open groups!")
        var snodeMessage: SnodeMessage? = null
        message.sentTimestamp ?: run { message.sentTimestamp = System.currentTimeMillis() } /* Visible messages will already have their sent timestamp set */
        message.sender = storage.getUserPublicKey()
        try {
            when (destination) {
                is Destination.Contact -> message.recipient = destination.publicKey
                is Destination.ClosedGroup -> message.recipient = destination.groupPublicKey
                is Destination.OpenGroup -> throw preconditionFailure
            }
            // Validate the message
            if (!message.isValid()) { throw Error.InvalidMessage }
            // Convert it to protobuf
            val proto = message.toProto() ?: throw Error.ProtoConversionFailed
            // Serialize the protobuf
            val plaintext = proto.toByteArray()
            // Encrypt the serialized protobuf
            val ciphertext: ByteArray
            when (destination) {
                is Destination.Contact -> ciphertext = MessageSenderEncryption.encryptWithSignalProtocol(plaintext, message, destination.publicKey)
                is Destination.ClosedGroup -> ciphertext = MessageSenderEncryption.encryptWithSharedSenderKeys(plaintext, destination.groupPublicKey)
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
                        deferred.resolve(Unit)
                    }
                    promise.fail {
                        errorCount += 1
                        if (errorCount != promiseCount) { return@fail } // Only error out if all promises failed
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
        // Handle completion
        promise.success {
            handleSuccessfulMessageSend(message)
            if (message is VisibleMessage && snodeMessage != null) {
                val notifyPNServerJob = NotifyPNServerJob(snodeMessage)
                JobQueue.shared.add(notifyPNServerJob)
            }
        }
        promise.fail {
            handleFailedMessageSend(message, it)
        }

        return promise
    }

    // Open Groups
    fun sendToOpenGroupDestination(destination: Destination, message: Message): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        val promise = deferred.promise
        val storage = Configuration.shared.storage
        val preconditionFailure = Exception("Destination should not be contacts or closed groups!")
        message.sentTimestamp = System.currentTimeMillis()
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
            // Validate the message
            if (message !is VisibleMessage || !message.isValid()) {
                throw Error.InvalidMessage
            }
            // Convert the message to an open group message
            val openGroupMessage = OpenGroupMessage.from(message, server) ?: throw Error.InvalidMessage
            // Send the result
            OpenGroupAPI.sendMessage(openGroupMessage, channel, server).success {
                message.openGroupServerMessageID = it.serverID
                deferred.resolve(Unit)
            }.fail {
                deferred.reject(it)
            }
        } catch (exception: Exception) {
            deferred.reject(exception)
        }
        // Handle completion
        promise.success {
            handleSuccessfulMessageSend(message)
        }
        promise.fail {
            handleFailedMessageSend(message, it)
        }
        return deferred.promise
    }

    // Result Handling
    fun handleSuccessfulMessageSend(message: Message) {

    }

    fun handleFailedMessageSend(message: Message, error: Exception) {

    }
}