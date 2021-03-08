package org.session.libsession.messaging.messages.visible

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.sending_receiving.linkpreview.LinkPreview as SignalLinkPreiview
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos

class LinkPreview() {

    var title: String? = null
    var url: String? = null
    var attachmentID: Long? = 0

    companion object {
        const val TAG = "LinkPreview"

        fun fromProto(proto: SignalServiceProtos.DataMessage.Preview): LinkPreview? {
            val title = proto.title
            val url = proto.url
            return LinkPreview(title, url, null)
        }

        fun from(signalLinkPreview: SignalLinkPreiview?): LinkPreview? {
            return if (signalLinkPreview == null) {
                null
            } else {
                LinkPreview(signalLinkPreview.title, signalLinkPreview.url, signalLinkPreview.attachmentId?.rowId)
            }
        }
    }

    //constructor
    internal constructor(title: String?, url: String, attachmentID: Long?) : this() {
        this.title = title
        this.url = url
        this.attachmentID = attachmentID
    }


    // validation
    fun isValid(): Boolean {
        return (title != null && url != null && attachmentID != null)
    }

    fun toProto(): SignalServiceProtos.DataMessage.Preview? {
        val url = url
        if (url == null) {
            Log.w(TAG, "Couldn't construct link preview proto from: $this")
            return null
        }
        val linkPreviewProto = SignalServiceProtos.DataMessage.Preview.newBuilder()
        linkPreviewProto.url = url
        title?.let { linkPreviewProto.title = title }
        val attachmentID = attachmentID
        attachmentID?.let {
            MessagingConfiguration.shared.messageDataProvider.getSignalAttachmentPointer(attachmentID)?.let {
                val attachmentProto = Attachment.createAttachmentPointer(it)
                linkPreviewProto.image = attachmentProto
            }
        }
        // Build
        try {
            return linkPreviewProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct link preview proto from: $this")
            return null
        }
    }
}