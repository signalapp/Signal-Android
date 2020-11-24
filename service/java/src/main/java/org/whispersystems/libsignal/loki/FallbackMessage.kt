package org.whispersystems.libsignal.loki

import org.whispersystems.libsignal.protocol.CiphertextMessage

class FallbackMessage(private val paddedMessageBody: ByteArray) : CiphertextMessage {

    override fun serialize(): ByteArray {
        return paddedMessageBody
    }

    override fun getType(): Int {
        return CiphertextMessage.FALLBACK_MESSAGE_TYPE
    }
}
