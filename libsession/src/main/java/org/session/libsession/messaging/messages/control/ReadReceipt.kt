package org.session.libsession.messaging.messages.control

import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log

class ReadReceipt() : ControlMessage() {
    var timestamps: List<Long>? = null

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        val timestamps = timestamps ?: return false
        if (timestamps.isNotEmpty()) { return true }
        return false
    }

    companion object {
        const val TAG = "ReadReceipt"

        fun fromProto(proto: SignalServiceProtos.Content): ReadReceipt? {
            val receiptProto = if (proto.hasReceiptMessage()) proto.receiptMessage else return null
            if (receiptProto.type != SignalServiceProtos.ReceiptMessage.Type.READ) return null
            val timestamps = receiptProto.timestampList
            if (timestamps.isEmpty()) return null
            return ReadReceipt(timestamps = timestamps)
        }
    }

    internal constructor(timestamps: List<Long>?) : this() {
        this.timestamps = timestamps
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val timestamps = timestamps
        if (timestamps == null) {
            Log.w(TAG, "Couldn't construct read receipt proto from: $this")
            return null
        }
        val receiptProto = SignalServiceProtos.ReceiptMessage.newBuilder()
        receiptProto.type = SignalServiceProtos.ReceiptMessage.Type.READ
        receiptProto.addAllTimestamp(timestamps.asIterable())
        val contentProto = SignalServiceProtos.Content.newBuilder()
        try {
            contentProto.receiptMessage = receiptProto.build()
            return contentProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct read receipt proto from: $this")
            return null
        }
    }
}