package org.session.libsession.messaging.messages.control

import org.session.libsignal.service.internal.push.SignalServiceProtos

class ExpirationTimerUpdate : ControlMessage() {
    override fun fromProto(proto: SignalServiceProtos.Content): ExpirationTimerUpdate? {
        TODO("Not yet implemented")
    }

    override fun toProto(): SignalServiceProtos.Content? {
        TODO("Not yet implemented")
    }
}