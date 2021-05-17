package org.session.libsession.messaging.messages.control

import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log

class TypingIndicator() : ControlMessage() {
    var kind: Kind? = null

    override val ttl: Long = 20 * 1000

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return kind != null
    }

    companion object {
        const val TAG = "TypingIndicator"

        fun fromProto(proto: SignalServiceProtos.Content): TypingIndicator? {
            val typingIndicatorProto = if (proto.hasTypingMessage()) proto.typingMessage else return null
            val kind = Kind.fromProto(typingIndicatorProto.action)
            return TypingIndicator(kind = kind)
        }
    }

    enum class Kind {
        STARTED, STOPPED;

        companion object {
            @JvmStatic
            fun fromProto(proto: SignalServiceProtos.TypingMessage.Action): Kind =
                when (proto) {
                    SignalServiceProtos.TypingMessage.Action.STARTED -> STARTED
                    SignalServiceProtos.TypingMessage.Action.STOPPED -> STOPPED
                }
        }

        fun toProto(): SignalServiceProtos.TypingMessage.Action {
            when (this) {
                STARTED -> return SignalServiceProtos.TypingMessage.Action.STARTED
                STOPPED -> return SignalServiceProtos.TypingMessage.Action.STOPPED
            }
        }
    }

    internal constructor(kind: Kind) : this() {
        this.kind = kind
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val timestamp = sentTimestamp
        val kind = kind
        if (timestamp == null || kind == null) {
            Log.w(TAG, "Couldn't construct typing indicator proto from: $this")
            return null
        }
        val typingIndicatorProto = SignalServiceProtos.TypingMessage.newBuilder()
        typingIndicatorProto.timestamp = timestamp
        typingIndicatorProto.action = kind.toProto()
        val contentProto = SignalServiceProtos.Content.newBuilder()
        try {
            contentProto.typingMessage = typingIndicatorProto.build()
            return contentProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct typing indicator proto from: $this")
            return null
        }
    }
}