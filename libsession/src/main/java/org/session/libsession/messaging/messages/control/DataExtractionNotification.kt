package org.session.libsession.messaging.messages.control

import com.google.protobuf.ByteString
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.utilities.logging.Log
import java.lang.Exception

class DataExtractionNotification(): ControlMessage() {
    var kind: Kind? = null

    // Kind enum
    sealed class Kind {
        class Screenshot() : Kind()
        class MediaSaved(val timestamp: Long) : Kind()

        val description: String = run {
            when(this) {
                is Screenshot -> "screenshot"
                is MediaSaved -> "mediaSaved"
            }
        }
    }

    companion object {
        const val TAG = "DataExtractionNotification"

        fun fromProto(proto: SignalServiceProtos.Content): DataExtractionNotification? {
            val dataExtractionNotification = proto.dataExtractionNotification ?: return null
            val kind: Kind
            when(dataExtractionNotification.type) {
                SignalServiceProtos.DataExtractionNotification.Type.SCREENSHOT -> kind = Kind.Screenshot()
                SignalServiceProtos.DataExtractionNotification.Type.MEDIA_SAVED -> {
                    val timestamp = if (dataExtractionNotification.hasTimestamp()) dataExtractionNotification.timestamp else 0
                    kind = Kind.MediaSaved(timestamp)
                }
            }
            return DataExtractionNotification(kind)
        }
    }

    //constructor
    internal constructor(kind: Kind) : this() {
        this.kind = kind
    }

    // MARK: Validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        val kind = kind ?: return false
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