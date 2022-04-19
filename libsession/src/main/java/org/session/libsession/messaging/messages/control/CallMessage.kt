package org.session.libsession.messaging.messages.control

import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.CallMessage.Type.*
import org.session.libsignal.utilities.Log
import java.util.*

class CallMessage(): ControlMessage() {
    var type: SignalServiceProtos.CallMessage.Type? = null
    var sdps: List<String> = listOf()
    var sdpMLineIndexes: List<Int> = listOf()
    var sdpMids: List<String> = listOf()
    var callId: UUID? = null

    override val isSelfSendValid: Boolean get() = type in arrayOf(ANSWER, END_CALL)

    override val ttl: Long = 300000L // 5m

    override fun isValid(): Boolean = super.isValid() && type != null && callId != null
            && (!sdps.isNullOrEmpty() || type in listOf(END_CALL, PRE_OFFER))

    constructor(type: SignalServiceProtos.CallMessage.Type,
                sdps: List<String>,
                sdpMLineIndexes: List<Int>,
                sdpMids: List<String>,
                callId: UUID) : this() {
        this.type = type
        this.sdps = sdps
        this.sdpMLineIndexes = sdpMLineIndexes
        this.sdpMids = sdpMids
        this.callId = callId
    }

    companion object {
        const val TAG = "CallMessage"

        fun answer(sdp: String, callId: UUID) = CallMessage(ANSWER,
                listOf(sdp),
                listOf(),
                listOf(),
                callId
        )

        fun preOffer(callId: UUID) = CallMessage(PRE_OFFER,
                listOf(),
                listOf(),
                listOf(),
                callId
        )

        fun offer(sdp: String, callId: UUID) = CallMessage(OFFER,
                listOf(sdp),
                listOf(),
                listOf(),
                callId
        )

        fun endCall(callId: UUID) = CallMessage(END_CALL, emptyList(), emptyList(), emptyList(), callId)

        fun fromProto(proto: SignalServiceProtos.Content): CallMessage? {
            val callMessageProto = if (proto.hasCallMessage()) proto.callMessage else return null
            val type = callMessageProto.type
            val sdps = callMessageProto.sdpsList
            val sdpMLineIndexes = callMessageProto.sdpMLineIndexesList
            val sdpMids = callMessageProto.sdpMidsList
            val callId = UUID.fromString(callMessageProto.uuid)
            return CallMessage(type,sdps, sdpMLineIndexes, sdpMids, callId)
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val nonNullType = type ?: run {
            Log.w(TAG,"Couldn't construct call message request proto from: $this")
            return null
        }

        val callMessage = SignalServiceProtos.CallMessage.newBuilder()
            .setType(nonNullType)
            .addAllSdps(sdps)
            .addAllSdpMLineIndexes(sdpMLineIndexes)
            .addAllSdpMids(sdpMids)
            .setUuid(callId!!.toString())

        return SignalServiceProtos.Content.newBuilder()
            .setCallMessage(
                callMessage
            )
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CallMessage

        if (callId != other.callId) return false
        if (type != other.type) return false
        if (sdps != other.sdps) return false
        if (sdpMLineIndexes != other.sdpMLineIndexes) return false
        if (sdpMids != other.sdpMids) return false
        if (isSelfSendValid != other.isSelfSendValid) return false
        if (ttl != other.ttl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type?.hashCode() ?: 0
        result = 31 * result + sdps.hashCode()
        result = 31 * result + sdpMLineIndexes.hashCode()
        result = 31 * result + sdpMids.hashCode()
        result = 31 * result + isSelfSendValid.hashCode()
        result = 31 * result + ttl.hashCode()
        return result
    }


}