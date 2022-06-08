package org.session.libsession.messaging.messages.visible

import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.messages.Message
import org.session.libsignal.protos.SignalServiceProtos

data class ParsedMessage(
    val parameters: MessageReceiveParameters,
    val message: Message,
    val proto: SignalServiceProtos.Content
)