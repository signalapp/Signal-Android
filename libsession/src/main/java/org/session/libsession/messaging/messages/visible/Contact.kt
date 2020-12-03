package org.session.libsession.messaging.messages.visible

import org.session.libsignal.service.internal.push.SignalServiceProtos

class Contact : VisibleMessageProto<SignalServiceProtos.DataMessage.Contact?>() {

    companion object {
        fun fromProto(proto: SignalServiceProtos.Content): Contact? {
            TODO("Not yet implemented")
        }
    }

    override fun toProto(transaction: String): SignalServiceProtos.DataMessage.Contact? {
        TODO("Not yet implemented")
    }
}