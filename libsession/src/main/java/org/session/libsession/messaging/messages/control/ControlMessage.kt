package org.session.libsession.messaging.messages.control

import org.session.libsession.messaging.messages.Message
import org.session.libsignal.service.internal.push.SignalServiceProtos

abstract class ControlMessage : Message<SignalServiceProtos.Content?>() {
}