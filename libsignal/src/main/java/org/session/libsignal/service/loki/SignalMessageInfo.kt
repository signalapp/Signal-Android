package org.session.libsignal.service.loki

import org.session.libsignal.service.internal.push.SignalServiceProtos

data class SignalMessageInfo(
    val type: SignalServiceProtos.Envelope.Type,
    val timestamp: Long,
    val senderPublicKey: String,
    val senderDeviceID: Int,
    val content: String,
    val recipientPublicKey: String,
    val ttl: Int?,
    val isPing: Boolean
)
