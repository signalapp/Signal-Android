package org.session.libsession.messaging.messages.visible

import org.session.libsignal.service.internal.push.SignalServiceProtos

class Profile() : VisibleMessage<SignalServiceProtos.DataMessage?>() {

    companion object {
        fun fromProto(proto: SignalServiceProtos.DataMessage): Profile? {
            TODO("Not yet implemented")
        }
    }

    override fun toProto(transaction: String): SignalServiceProtos.DataMessage? {
        return null
    }
}