package org.session.libsession.messaging.messages.visible

import org.session.libsignal.service.internal.push.SignalServiceProtos

class Quote() : VisibleMessage<SignalServiceProtos.DataMessage.Quote?>() {

    var timestamp: Long? = 0
    var publicKey: String? = null
    var text: String? = null
    var attachmentID: String? = null

    companion object {
        fun fromProto(proto: SignalServiceProtos.DataMessage.Quote): Quote? {
            TODO("Not yet implemented")
        }
    }

    override fun toProto(transaction: String): SignalServiceProtos.DataMessage.Quote? {
        return null
    }
}