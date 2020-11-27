package org.session.libsession.messaging.messages.visible

import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsignal.service.internal.push.SignalServiceProtos

internal class Quote : VisibleMessage() {

    companion object {
        fun fromProto(proto: SignalServiceProtos.Content): Quote? {
            TODO("Not yet implemented")
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        TODO("Not yet implemented")
    }
}