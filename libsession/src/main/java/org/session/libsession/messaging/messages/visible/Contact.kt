package org.session.libsession.messaging.messages.visible

import org.session.libsession.database.MessageDataProvider
import org.session.libsignal.service.internal.push.SignalServiceProtos

class Contact() {

    companion object {
        fun fromProto(proto: SignalServiceProtos.Content): Contact? {
            TODO("Not yet implemented")
        }
    }

    fun toProto(): SignalServiceProtos.DataMessage.Contact? {
        TODO("Not yet implemented")
    }
}