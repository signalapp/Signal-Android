package org.session.libsession.messaging.messages.control.unused

import com.google.protobuf.ByteString
import org.session.libsession.messaging.messages.control.ControlMessage
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos
import java.security.SecureRandom

class NullMessage() : ControlMessage() {

    companion object {
        const val TAG = "NullMessage"

        fun fromProto(proto: SignalServiceProtos.Content): NullMessage? {
            if (proto.nullMessage == null) return null
            return NullMessage()
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val nullMessageProto = SignalServiceProtos.NullMessage.newBuilder()
        val sr = SecureRandom()
        val paddingSize = sr.nextInt(512)
        val padding = ByteArray(paddingSize)
        nullMessageProto.padding = ByteString.copyFrom(padding)
        val contentProto = SignalServiceProtos.Content.newBuilder()
        try {
            contentProto.nullMessage = nullMessageProto.build()
            return contentProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct null message proto from: $this")
            return null
        }
    }
}