package org.session.libsession.messaging.messages.visible

import com.goterl.lazycode.lazysodium.BuildConfig
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.utilities.logging.Log
import org.session.libsession.messaging.sending_receiving.attachments.Attachment as SignalAttachment

class VisibleMessage : Message()  {
    /** In the case of a sync message, the public key of the person the message was targeted at.
     *
     * **Note:** `nil` if this isn't a sync message.
     */
    var syncTarget: String? = null
    var text: String? = null
    val attachmentIDs: MutableList<Long> = mutableListOf()
    var quote: Quote? = null
    var linkPreview: LinkPreview? = null
    var profile: Profile? = null

    override val isSelfSendValid: Boolean = true

    // region Validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        if (attachmentIDs.isNotEmpty()) return true
        val text = text?.trim() ?: return false
        if (text.isNotEmpty()) return true
        return false
    }
    // endregion

    // region Proto Conversion
    companion object {
        const val TAG = "VisibleMessage"

        fun fromProto(proto: SignalServiceProtos.Content): VisibleMessage? {
            val dataMessage = proto.dataMessage ?: return null
            val result = VisibleMessage()
            if (dataMessage.hasSyncTarget()) { result.syncTarget = dataMessage.syncTarget }
            result.text = dataMessage.body
            // Attachments are handled in MessageReceiver
            val quoteProto = if (dataMessage.hasQuote()) dataMessage.quote else null
            if (quoteProto != null) {
                val quote = Quote.fromProto(quoteProto)
                result.quote = quote
            }
            val linkPreviewProto = dataMessage.previewList.firstOrNull()
            if (linkPreviewProto != null) {
                val linkPreview = LinkPreview.fromProto(linkPreviewProto)
                result.linkPreview = linkPreview
            }
            // TODO: Contact
            val profile = Profile.fromProto(dataMessage)
            if (profile != null) { result.profile = profile }
            return  result
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val proto = SignalServiceProtos.Content.newBuilder()
        val dataMessage: SignalServiceProtos.DataMessage.Builder
        // Profile
        val profileProto = profile?.let { it.toProto() }
        if (profileProto != null) {
            dataMessage = profileProto.toBuilder()
        } else {
            dataMessage = SignalServiceProtos.DataMessage.newBuilder()
        }
        // Text
        if (text != null) { dataMessage.body = text }
        // Quote
        val quoteProto = quote?.let { it.toProto() }
        if (quoteProto != null) {
            dataMessage.quote = quoteProto
        }
        // Link preview
        val linkPreviewProto = linkPreview?.let { it.toProto() }
        if (linkPreviewProto != null) {
            dataMessage.addAllPreview(listOf(linkPreviewProto))
        }
        // Attachments
        val database = MessagingModuleConfiguration.shared.messageDataProvider
        val attachments = attachmentIDs.mapNotNull { database.getSignalAttachmentPointer(it) }
        if (attachments.any { it.url.isNullOrEmpty() }) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Sending a message before all associated attachments have been uploaded.")
            }
        }
        val pointers = attachments.mapNotNull { Attachment.createAttachmentPointer(it) }
        dataMessage.addAllAttachments(pointers)
        // TODO: Contact
        // Expiration timer
        // TODO: We * want * expiration timer updates to be explicit. But currently Android will disable the expiration timer for a conversation
        //       if it receives a message without the current expiration timer value attached to it...
        val storage = MessagingModuleConfiguration.shared.storage
        val context = MessagingModuleConfiguration.shared.context
        val expiration = if (storage.isClosedGroup(recipient!!)) {
            Recipient.from(context, Address.fromSerialized(GroupUtil.doubleEncodeGroupID(recipient!!)), false).expireMessages
        } else {
            Recipient.from(context, Address.fromSerialized(recipient!!), false).expireMessages
        }
        dataMessage.expireTimer = expiration
        // Group context
        if (storage.isClosedGroup(recipient!!)) {
            try {
                setGroupContext(dataMessage)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't construct visible message proto from: $this")
                return null
            }
        }
        // Sync target
        if (syncTarget != null) {
            dataMessage.syncTarget = syncTarget
        }
        // Build
        try {
            proto.dataMessage = dataMessage.build()
            return proto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct visible message proto from: $this")
            return null
        }
    }
    // endregion

    fun addSignalAttachments(signalAttachments: List<SignalAttachment>) {
        val attachmentIDs = signalAttachments.map {
            val databaseAttachment = it as DatabaseAttachment
            databaseAttachment.attachmentId.rowId
        }
        this.attachmentIDs.addAll(attachmentIDs)
    }

    fun isMediaMessage(): Boolean {
        return attachmentIDs.isNotEmpty() || quote != null || linkPreview != null
    }
}