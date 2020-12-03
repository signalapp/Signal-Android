package org.session.libsession.messaging.sending_receiving

object MessageReceiver {
    internal sealed class Error(val description: String) : Exception() {
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
}