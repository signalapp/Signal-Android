package org.session.libsession.messaging.messages.control

import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log

class UnsendRequest(): ControlMessage() {
    var timestamp: Long? = null
    var author: String? = null

    override val isSelfSendValid: Boolean = true

    // region Validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return timestamp != null && author != null
    }
    // endregion

    companion object {
        const val TAG = "UnsendRequest"

        fun fromProto(proto: SignalServiceProtos.Content): UnsendRequest? {
            val unsendRequestProto = if (proto.hasUnsendRequest()) proto.unsendRequest else return null
            val timestamp = unsendRequestProto.timestamp
            val author = unsendRequestProto.author
            return UnsendRequest(timestamp, author)
        }
    }

    constructor(timestamp: Long, author: String) : this() {
        this.timestamp = timestamp
        this.author = author
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val timestamp = timestamp
        val author = author
        if (timestamp == null || author == null) {
            Log.w(TAG, "Couldn't construct unsend request proto from: $this")
            return null
        }
        val unsendRequestProto = SignalServiceProtos.UnsendRequest.newBuilder()
        unsendRequestProto.timestamp = timestamp
        unsendRequestProto.author = author
        val contentProto = SignalServiceProtos.Content.newBuilder()
        try {
            contentProto.unsendRequest = unsendRequestProto.build()
            return contentProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct unsend request proto from: $this")
            return null
        }
    }

}