package org.session.libsession.messaging.messages.control

import org.session.libsignal.service.internal.push.SignalServiceProtos

class TypingIndicator : ControlMessage() {
    override fun fromProto(proto: SignalServiceProtos.Content): TypingIndicator?{
        TODO("Not yet implemented")
    }

    override fun toProto(): SignalServiceProtos.Content? {
        TODO("Not yet implemented")
    }
}