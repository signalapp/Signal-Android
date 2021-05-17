package org.session.libsession.messaging.messages.control

import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log

class DataExtractionNotification() : ControlMessage() {
    var kind: Kind? = null

    sealed class Kind {
        class Screenshot() : Kind()
        class MediaSaved(val timestamp: Long) : Kind()

        val description: String =
            when (this) {
                is Screenshot -> "screenshot"
                is MediaSaved -> "mediaSaved"
            }
    }

    companion object {
        const val TAG = "DataExtractionNotification"

        fun fromProto(proto: SignalServiceProtos.Content): DataExtractionNotification? {
            if (!proto.hasDataExtractionNotification()) return null
            val dataExtractionNotification = proto.dataExtractionNotification!!
            val kind: Kind = when(dataExtractionNotification.type) {
                SignalServiceProtos.DataExtractionNotification.Type.SCREENSHOT -> Kind.Screenshot()
                SignalServiceProtos.DataExtractionNotification.Type.MEDIA_SAVED -> {
                    val timestamp = if (dataExtractionNotification.hasTimestamp()) dataExtractionNotification.timestamp else return null
                    Kind.MediaSaved(timestamp)
                }
            }
            return DataExtractionNotification(kind)
        }
    }

    internal constructor(kind: Kind) : this() {
        this.kind = kind
    }

    override fun isValid(): Boolean {
        val kind = kind
        if (!super.isValid() || kind == null) return false
        return when(kind) {
            is Kind.Screenshot -> true
            is Kind.MediaSaved -> kind.timestamp > 0
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val kind = kind
        if (kind == null) {
            Log.w(TAG, "Couldn't construct data extraction notification proto from: $this")
            return null
        }
        try {
            val dataExtractionNotification = SignalServiceProtos.DataExtractionNotification.newBuilder()
            when(kind) {
                is Kind.Screenshot -> dataExtractionNotification.type = SignalServiceProtos.DataExtractionNotification.Type.SCREENSHOT
                is Kind.MediaSaved -> {
                    dataExtractionNotification.type = SignalServiceProtos.DataExtractionNotification.Type.MEDIA_SAVED
                    dataExtractionNotification.timestamp = kind.timestamp
                }
            }
            val contentProto = SignalServiceProtos.Content.newBuilder()
            contentProto.dataExtractionNotification = dataExtractionNotification.build()
            return contentProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct data extraction notification proto from: $this")
            return null
        }
    }
}
