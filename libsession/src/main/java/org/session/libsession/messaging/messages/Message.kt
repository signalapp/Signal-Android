package org.session.libsession.messaging.messages

import org.session.libsignal.service.internal.push.SignalServiceProtos

abstract class Message<T: com.google.protobuf.MessageOrBuilder?> {

    var id: String? = null
    var threadID: String? = null
    var sentTimestamp: Long? = null
    var receivedTimestamp: Long? = null
    var recipient: String? = null
    var sender: String? = null
    var groupPublicKey: String? = null
    var openGroupServerMessageID: Long? = null
    open val ttl: Long = 2 * 24 * 60 * 60 * 1000

    // validation
    open fun isValid(): Boolean {
        sentTimestamp = if (sentTimestamp!! > 0) sentTimestamp else return false
        receivedTimestamp = if (receivedTimestamp!! > 0) receivedTimestamp else return false
        return sender != null && recipient != null
    }

    abstract fun toProto(): T

}