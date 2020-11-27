package org.session.libsession.messaging.messages.control

import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos

class ExpirationTimerUpdate() : ControlMessage() {

    var duration: Int? = 0

    companion object {
        const val TAG = "ExpirationTimerUpdate"

        fun fromProto(proto: SignalServiceProtos.Content): ExpirationTimerUpdate? {
            val dataMessageProto = proto.dataMessage ?: return null
            val isExpirationTimerUpdate = (dataMessageProto.flags and SignalServiceProtos.DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE) != 0
            if (!isExpirationTimerUpdate) return null
            val duration = dataMessageProto.expireTimer
            return ExpirationTimerUpdate(duration)
        }
    }

    //constructor
    internal constructor(duration: Int) : this() {
        this.duration = duration
    }

    // validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return duration != null
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val duration = duration
        if (duration == null) {
            Log.w(TAG, "Couldn't construct expiration timer update proto from: $this")
            return null
        }
        val dataMessageProto = SignalServiceProtos.DataMessage.newBuilder()
        dataMessageProto.flags = SignalServiceProtos.DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE
        dataMessageProto.expireTimer = duration
        val contentProto = SignalServiceProtos.Content.newBuilder()
        try {
            contentProto.dataMessage = dataMessageProto.build()
            return contentProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct expiration timer update proto from: $this")
            return null
        }
    }
}