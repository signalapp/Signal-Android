package org.session.libsession.messaging.messages.visible.attachments

import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsignal.service.internal.push.SignalServiceProtos

internal class Attachment : VisibleMessage() {
    override fun fromProto(proto: SignalServiceProtos.Content): Attachment? {
        TODO("Not yet implemented")
    }

    override fun toProto(): SignalServiceProtos.Content? {
        TODO("Not yet implemented")
    }
}