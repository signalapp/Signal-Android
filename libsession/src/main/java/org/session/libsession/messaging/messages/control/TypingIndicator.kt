package org.session.libsession.messaging.messages.control

import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos

class TypingIndicator() : ControlMessage() {

    companion object {
        const val TAG = "TypingIndicator"

        //val ttl:  30 * 1000 //TODO

        fun fromProto(proto: SignalServiceProtos.Content): TypingIndicator? {
            val typingIndicatorProto = proto.typingMessage ?: return null
            val kind = Kind.fromProto(typingIndicatorProto.action)
            return TypingIndicator(kind = kind)
        }
    }

    // Kind enum
    enum class Kind {
        STARTED,
        STOPPED,
        ;

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

    var kind: Kind? = null

    //constructor
    internal constructor(kind: Kind) : this() {
        this.kind = kind
    }

    // validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return kind != null
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