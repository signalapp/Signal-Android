package org.session.libsession.messaging.messages.visible

import com.goterl.lazysodium.BuildConfig
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel as SignalQuote

class Quote() {
    var timestamp: Long? = 0
    var publicKey: String? = null
    var text: String? = null
    var attachmentID: Long? = null

    fun isValid(): Boolean {
        return (timestamp != null && publicKey != null)
    }

    companion object {
        const val TAG = "Quote"

        fun fromProto(proto: SignalServiceProtos.DataMessage.Quote): Quote? {
            val timestamp = proto.id
            val publicKey = proto.author
            val text = proto.text
            return Quote(timestamp, publicKey, text, null)
        }

        fun from(signalQuote: SignalQuote?): Quote? {
            if (signalQuote == null) { return null }
            val attachmentID = (signalQuote.attachments?.firstOrNull() as? DatabaseAttachment)?.attachmentId?.rowId
            return Quote(signalQuote.id, signalQuote.author.serialize(), signalQuote.text, attachmentID)
        }
    }

    internal constructor(timestamp: Long, publicKey: String, text: String?, attachmentID: Long?) : this() {
        this.timestamp = timestamp
        this.publicKey = publicKey
        this.text = text
        this.attachmentID = attachmentID
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
        text?.let { quoteProto.text = it }
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
        val database = MessagingModuleConfiguration.shared.messageDataProvider
        val pointer = database.getSignalAttachmentPointer(attachmentID)
        if (pointer == null) {
            Log.w(TAG, "Ignoring invalid attachment for quoted message.")
            return
        }
        if (pointer.url.isNullOrEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG,"Sending a message before all associated attachments have been uploaded.")
                return
            }
        }
        val quotedAttachmentProto = SignalServiceProtos.DataMessage.Quote.QuotedAttachment.newBuilder()
        quotedAttachmentProto.contentType = pointer.contentType
        if (pointer.fileName.isPresent) { quotedAttachmentProto.fileName = pointer.fileName.get() }
        quotedAttachmentProto.thumbnail = Attachment.createAttachmentPointer(pointer)
        try {
            quoteProto.addAttachments(quotedAttachmentProto.build())
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct quoted attachment proto from: $this")
        }
    }
}