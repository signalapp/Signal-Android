package org.session.libsession.messaging.sending_receiving

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ClosedGroupUpdate
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.messages.control.ReadReceipt
import org.session.libsession.messaging.messages.control.TypingIndicator
import org.session.libsession.messaging.messages.visible.VisibleMessage

import org.session.libsignal.service.internal.push.SignalServiceProtos

object MessageReceiver {
    internal sealed class Error(val description: String) : Exception() {
        object DuplicateMessage: Error("Duplicate message.")
        object InvalidMessage: Error("Invalid message.")
        object UnknownMessage: Error("Unknown message type.")
        object UnknownEnvelopeType: Error("Unknown envelope type.")
        object NoUserPublicKey: Error("Couldn't find user key pair.")
        object NoData: Error("Received an empty envelope.")
        object SenderBlocked: Error("Received a message from a blocked user.")
        object NoThread: Error("Couldn't find thread for message.")
        object SelfSend: Error("Message addressed at self.")
        object ParsingFailed : Error("Couldn't parse ciphertext message.")
        // Shared sender keys
        object InvalidGroupPublicKey: Error("Invalid group public key.")
        object NoGroupPrivateKey: Error("Missing group private key.")
        object SharedSecretGenerationFailed: Error("Couldn't generate a shared secret.")

        internal val isRetryable: Boolean = when (this) {
            is InvalidMessage -> false
            is UnknownMessage -> false
            is UnknownEnvelopeType -> false
            is NoData -> false
            is SenderBlocked -> false
            is SelfSend -> false
            else -> true
        }
    }

    internal fun parse(data: ByteArray, openGroupServerID: Long?): Pair<Message, SignalServiceProtos.Content> {
        val storage = MessagingConfiguration.shared.storage
        val userPublicKey = storage.getUserPublicKey()
        val isOpenGroupMessage = openGroupServerID != null
        // Parse the envelope
        val envelope = SignalServiceProtos.Envelope.parseFrom(data)
        if (storage.getReceivedMessageTimestamps().contains(envelope.timestamp)) throw Error.DuplicateMessage
        storage.addReceivedMessageTimestamp(envelope.timestamp)
        // Decrypt the contents
        val plaintext: ByteArray
        val sender: String
        var groupPublicKey: String? = null
        if (isOpenGroupMessage) {
            plaintext = envelope.content.toByteArray()
            sender = envelope.source
        } else {
            when (envelope.type) {
                SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER -> {
                    val decryptionResult = MessageReceiverDecryption.decryptWithSessionProtocol(envelope)
                    plaintext = decryptionResult.first
                    sender = decryptionResult.second
                }
                SignalServiceProtos.Envelope.Type.CLOSED_GROUP_CIPHERTEXT -> {
                    val decryptionResult = MessageReceiverDecryption.decryptWithSharedSenderKeys(envelope)
                    plaintext = decryptionResult.first
                    sender = decryptionResult.second
                }
                else -> throw Error.UnknownEnvelopeType
            }
        }
        // Don't process the envelope any further if the sender is blocked
        if (isBlock(sender)) throw Error.SenderBlocked
        // Ignore self sends
        if (sender == userPublicKey) throw Error.SelfSend
        // Parse the proto
        val proto = SignalServiceProtos.Content.parseFrom(plaintext)
        // Parse the message
        val message: Message = ReadReceipt.fromProto(proto) ?:
                               TypingIndicator.fromProto(proto) ?:
                               ClosedGroupUpdate.fromProto(proto) ?:
                               ExpirationTimerUpdate.fromProto(proto) ?:
                               VisibleMessage.fromProto(proto) ?: throw Error.UnknownMessage
        if (isOpenGroupMessage && message !is VisibleMessage) throw Error.InvalidMessage
        message.sender = sender
        message.recipient = userPublicKey
        message.sentTimestamp = envelope.timestamp
        message.receivedTimestamp = System.currentTimeMillis()
        message.groupPublicKey = groupPublicKey
        message.openGroupServerMessageID = openGroupServerID
        var isValid = message.isValid()
        if (message is VisibleMessage && !isValid && proto.dataMessage.attachmentsCount == 0) { isValid = true }
        if (!isValid) { throw Error.InvalidMessage }
        return Pair(message, proto)
    }
}