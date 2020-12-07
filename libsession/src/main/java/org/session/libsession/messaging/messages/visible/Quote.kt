package org.session.libsession.messaging.messages.visible

import com.goterl.lazycode.lazysodium.BuildConfig
import org.session.libsession.database.MessageDataProvider
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos

class Quote() : VisibleMessageProto<SignalServiceProtos.DataMessage.Quote?>() {

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
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return (timestamp != null && publicKey != null)
    }

    override fun toProto(messageDataProvider: MessageDataProvider): SignalServiceProtos.DataMessage.Quote? {
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
        addAttachmentsIfNeeded(quoteProto, messageDataProvider)
        // Build
        try {
            return quoteProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct quote proto from: $this")
            return null
        }
    }

    private fun addAttachmentsIfNeeded(quoteProto: SignalServiceProtos.DataMessage.Quote.Builder, messageDataProvider: MessageDataProvider) {
        val attachmentID = attachmentID ?: return
        val attachmentProto = messageDataProvider.getAttachment(attachmentID)
        if (attachmentProto == null) {
            Log.w(TAG, "Ignoring invalid attachment for quoted message.")
            return
        }
        if (!attachmentProto.isUploaded) {
            if (BuildConfig.DEBUG) {
                //TODO equivalent to iOS's preconditionFailure
                Log.d(TAG,"Sending a message before all associated attachments have been uploaded.")
                return
            }
        }
        val quotedAttachmentProto = SignalServiceProtos.DataMessage.Quote.QuotedAttachment.newBuilder()
        quotedAttachmentProto.contentType = attachmentProto.contentType
        val fileName = attachmentProto.fileName
        fileName?.let { quotedAttachmentProto.fileName = fileName }
        quotedAttachmentProto.thumbnail = attachmentProto.toProto()
        try {
            quoteProto.addAttachments(quotedAttachmentProto.build())
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct quoted attachment proto from: $this")
        }
    }

    override fun toProto(): SignalServiceProtos.DataMessage.Quote? {
        TODO("Not implemented")
    }
}