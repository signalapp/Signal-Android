package org.session.libsession.messaging.messages.control

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsignal.utilities.Log
import org.session.libsignal.protos.SignalServiceProtos

class ExpirationTimerUpdate() : ControlMessage() {
    /** In the case of a sync message, the public key of the person the message was targeted at.
     *
     * **Note:** `nil` if this isn't a sync message.
     */
    var syncTarget: String? = null
    var duration: Int? = 0

    override val isSelfSendValid: Boolean = true

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return duration != null
    }

    companion object {
        const val TAG = "ExpirationTimerUpdate"

        fun fromProto(proto: SignalServiceProtos.Content): ExpirationTimerUpdate? {
            val dataMessageProto = if (proto.hasDataMessage()) proto.dataMessage else return null
            val isExpirationTimerUpdate = dataMessageProto.flags.and(SignalServiceProtos.DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE) != 0
            if (!isExpirationTimerUpdate) return null
            val syncTarget = dataMessageProto.syncTarget
            val duration = dataMessageProto.expireTimer
            return ExpirationTimerUpdate(syncTarget, duration)
        }
    }

    internal constructor(duration: Int) : this() {
        this.syncTarget = null
        this.duration = duration
    }

    internal constructor(syncTarget: String, duration: Int) : this() {
        this.syncTarget = syncTarget
        this.duration = duration
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
        // Sync target
        if (syncTarget != null) {
            dataMessageProto.syncTarget = syncTarget
        }
        // Group context
        if (MessagingModuleConfiguration.shared.storage.isClosedGroup(recipient!!)) {
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