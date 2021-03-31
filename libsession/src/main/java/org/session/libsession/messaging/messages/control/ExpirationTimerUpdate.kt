package org.session.libsession.messaging.messages.control

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos

class ExpirationTimerUpdate() : ControlMessage() {

    var syncTarget: String? = null
    var duration: Int? = 0

    override val isSelfSendValid: Boolean = true

    companion object {
        const val TAG = "ExpirationTimerUpdate"

        fun fromProto(proto: SignalServiceProtos.Content): ExpirationTimerUpdate? {
            val dataMessageProto = if (proto.hasDataMessage()) proto.dataMessage else return null
            val isExpirationTimerUpdate = dataMessageProto.flags.and(SignalServiceProtos.DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE) != 0
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
        syncTarget?.let { dataMessageProto.syncTarget = it }
        // Group context
        if (MessagingConfiguration.shared.storage.isClosedGroup(recipient!!)) {
            try {
                setGroupContext(dataMessageProto)
            } catch(e: Exception) {
                Log.w(VisibleMessage.TAG, "Couldn't construct visible message proto from: $this")
                return null
            }
        }
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