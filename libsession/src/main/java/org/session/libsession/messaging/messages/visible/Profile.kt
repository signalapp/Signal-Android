package org.session.libsession.messaging.messages.visible

import org.session.libsignal.service.internal.push.SignalServiceProtos

internal class Profile : VisibleMessage() {
    override fun fromProto(proto: SignalServiceProtos.Content): Profile? {
        TODO("Not yet implemented")
    }

    override fun toProto(): SignalServiceProtos.Content? {
        TODO("Not yet implemented")
    }
}