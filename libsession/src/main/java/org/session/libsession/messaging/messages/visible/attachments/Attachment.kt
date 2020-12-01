package org.session.libsession.messaging.messages.visible.attachments

import org.session.libsession.messaging.messages.visible.BaseVisibleMessage
import org.session.libsignal.service.internal.push.SignalServiceProtos

internal class Attachment : BaseVisibleMessage() {

    companion object {
        fun fromProto(proto: SignalServiceProtos.Content): Attachment? {
            TODO("Not yet implemented")
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        TODO("Not yet implemented")
    }
}