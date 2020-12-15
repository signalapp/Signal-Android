package org.session.libsession.messaging.messages.visible

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsignal.libsignal.logging.Log
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
            val attachmentProto = MessagingConfiguration.shared.messageDataProvider.getAttachment(attachmentID)
            attachmentProto?.let { linkPreviewProto.image = attachmentProto.toProto() }
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