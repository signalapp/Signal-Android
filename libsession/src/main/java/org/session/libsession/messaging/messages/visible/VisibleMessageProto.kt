package org.session.libsession.messaging.messages.visible

import org.session.libsession.database.MessageDataProvider
import org.session.libsession.messaging.messages.Message

abstract class VisibleMessageProto<T: com.google.protobuf.MessageOrBuilder?> : Message<T>() {

    abstract fun toProto(messageDataProvider: MessageDataProvider): T
}