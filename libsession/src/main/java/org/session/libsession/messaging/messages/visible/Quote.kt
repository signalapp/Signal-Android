package org.session.libsession.messaging.messages.visible

import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos

class Quote() {

    var timestamp: Long? = 0
    var publicKey: String? = null
    var text: String? = null
    var attachmentID: String? = null

    companion object {
        const val TAG = "Quote"

        fun fromProto(proto: SignalServiceProtos.DataMessage.Quote): Quote? {
            val timestamp = proto.id
            val publicKey = proto.author
            val text = proto.text
            return Quote(timestamp, publicKey, text, null)
        }
    }

    //constructor
    internal constructor(timestamp: Long, publicKey: String, text: String?, attachmentID: String?) : this() {
        this.timestamp = timestamp
        this.publicKey = publicKey
        this.text = text
        this.attachmentID = attachmentID
    }


    // validation
    fun isValid(): Boolean {
        return (timestamp != null && publicKey != null)
    }

    fun toProto(): SignalServiceProtos.DataMessage.Quote? {
        val timestamp = timestamp
        val publicKey = publicKey
        if (timestamp == null || publicKey == null) {
            Log.w(TAG, "Couldn't construct quote proto from: $this")
            return null
        }
        val quoteProto = SignalServiceProtos.DataMessage.Quote.newBuilder()
        quoteProto.id = timestamp
        quoteProto.author = publicKey
        text?.let { quoteProto.text = text }
        addAttachmentsIfNeeded(quoteProto)
        // Build
        try {
            return quoteProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct quote proto from: $this")
            return null
        }
    }

    private fun addAttachmentsIfNeeded(quoteProto: SignalServiceProtos.DataMessage.Quote.Builder) {
        val attachmentID = attachmentID ?: return
        //TODO databas stuff
        val quotedAttachmentProto = SignalServiceProtos.DataMessage.Quote.QuotedAttachment.newBuilder()
        //TODO more database related stuff
        //quotedAttachmentProto.contentType =
        try {
            quoteProto.addAttachments(quotedAttachmentProto.build())
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct quoted attachment proto from: $this")
        }
    }
}