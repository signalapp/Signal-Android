package org.session.libsession.messaging.messages.visible

import com.google.protobuf.ByteString
import com.goterl.lazycode.lazysodium.BuildConfig
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.internal.push.SignalServiceProtos.AttachmentPointer
import org.session.libsignal.utilities.logging.Log
import org.session.libsession.messaging.sending_receiving.attachments.Attachment as SignalAttachment

class VisibleMessage : Message()  {

    var syncTarget: String? = null
    var text: String? = null
    var attachmentIDs = ArrayList<Long>()
    var quote: Quote? = null
    var linkPreview: LinkPreview? = null
    var contact: Contact? = null
    var profile: Profile? = null

    override val isSelfSendValid: Boolean = true

    companion object {
        const val TAG = "VisibleMessage"

        fun fromProto(proto: SignalServiceProtos.Content): VisibleMessage? {
            val dataMessage = proto.dataMessage ?: return null
            val result = VisibleMessage()
            result.syncTarget = dataMessage.syncTarget
            result.text = dataMessage.body
            // Attachments are handled in MessageReceiver
            val quoteProto = dataMessage.quote
            quoteProto?.let {
                val quote = Quote.fromProto(quoteProto)
                quote?.let { result.quote = quote }
            }
            val linkPreviewProto = dataMessage.previewList.first()
            linkPreviewProto?.let {
                val linkPreview = LinkPreview.fromProto(linkPreviewProto)
                linkPreview?.let { result.linkPreview = linkPreview }
            }
            // TODO Contact
            val profile = Profile.fromProto(dataMessage)
            profile?.let { result.profile = profile }
            return  result
        }
    }

    fun addSignalAttachments(signalAttachments: List<SignalAttachment>) {
        val attachmentIDs = signalAttachments.map {
            val databaseAttachment = it as DatabaseAttachment
            databaseAttachment.attachmentId.rowId
        }
        this.attachmentIDs = attachmentIDs as ArrayList<Long>
    }

    fun isMediaMessage(): Boolean {
        return attachmentIDs.isNotEmpty() || quote != null || linkPreview != null || contact != null
    }

    // validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        if (attachmentIDs.isNotEmpty()) return true
        val text = text?.trim() ?: return false
        if (text.isNotEmpty()) return true
        return false
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val proto = SignalServiceProtos.Content.newBuilder()
        var attachmentIDs = this.attachmentIDs
        val dataMessage: SignalServiceProtos.DataMessage.Builder
        // Profile
        val profile = profile
        val profileProto = profile?.toProto()
        if (profileProto != null) {
            dataMessage = profileProto.toBuilder()
        } else {
            dataMessage = SignalServiceProtos.DataMessage.newBuilder()
        }
        // Text
        text?.let { dataMessage.body = text }
        // Quote
        val quotedAttachmentID = quote?.attachmentID
        quotedAttachmentID?.let {
            val index = attachmentIDs.indexOf(quotedAttachmentID)
            if (index >= 0) { attachmentIDs.removeAt(index) }
        }
        val quote = quote
        quote?.let {
            val quoteProto = quote.toProto()
            if (quoteProto != null) dataMessage.quote = quoteProto
        }
        //Link preview
        val linkPreviewAttachmentID = linkPreview?.attachmentID
        linkPreviewAttachmentID?.let {
            val index = attachmentIDs.indexOf(quotedAttachmentID)
            if (index >= 0) { attachmentIDs.removeAt(index) }
        }
        val linkPreview = linkPreview
        linkPreview?.let {
            val linkPreviewProto = linkPreview.toProto()
            linkPreviewProto?.let {
                dataMessage.addAllPreview(listOf(linkPreviewProto))
            }
        }
        //Attachments
        val attachments = attachmentIDs.mapNotNull { MessagingConfiguration.shared.messageDataProvider.getSignalAttachmentPointer(it) }
        if (!attachments.all { !it.url.isNullOrEmpty() }) {
            if (BuildConfig.DEBUG) {
                //TODO equivalent to iOS's preconditionFailure
                Log.d(TAG, "Sending a message before all associated attachments have been uploaded.")
            }
        }
        val attachmentPointers = attachments.mapNotNull { createAttachmentPointer(it) }
        dataMessage.addAllAttachments(attachmentPointers)
        // Sync target
        if (syncTarget != null) {
            dataMessage.syncTarget = syncTarget
        }
        // TODO Contact
        // Build
        try {
            proto.dataMessage = dataMessage.build()
            return proto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct visible message proto from: $this")
            return null
        }
    }

    private fun createAttachmentPointer(attachment: SignalServiceAttachmentPointer): AttachmentPointer? {
        val builder = AttachmentPointer.newBuilder()
                .setContentType(attachment.contentType)
                .setId(attachment.id)
                .setKey(ByteString.copyFrom(attachment.key))
                .setDigest(ByteString.copyFrom(attachment.digest.get()))
                .setSize(attachment.size.get())
                .setUrl(attachment.url)
        if (attachment.fileName.isPresent) {
            builder.fileName = attachment.fileName.get()
        }
        if (attachment.preview.isPresent) {
            builder.thumbnail = ByteString.copyFrom(attachment.preview.get())
        }
        if (attachment.width > 0) {
            builder.width = attachment.width
        }
        if (attachment.height > 0) {
            builder.height = attachment.height
        }
        if (attachment.voiceNote) {
            builder.flags = AttachmentPointer.Flags.VOICE_MESSAGE_VALUE
        }
        if (attachment.caption.isPresent) {
            builder.caption = attachment.caption.get()
        }
        return builder.build()
    }
}