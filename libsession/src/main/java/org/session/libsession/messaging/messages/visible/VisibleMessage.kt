package org.session.libsession.messaging.messages.visible

import org.session.libsession.messaging.messages.Message
import org.session.libsignal.service.internal.push.SignalServiceProtos

abstract class VisibleMessage<out T: com.google.protobuf.MessageOrBuilder?> : Message() {

    abstract fun toProto(transaction: String): T

    final override fun toProto(): SignalServiceProtos.Content? {
        //we don't need to implement this method in subclasses
        //TODO it just needs an equivalent to swift: preconditionFailure("Use toProto(using:) instead.")
        TODO("Not yet implemented")
    }
}