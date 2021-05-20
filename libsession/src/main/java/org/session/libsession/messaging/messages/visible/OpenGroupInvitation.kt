package org.session.libsession.messaging.messages.visible

import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log

class OpenGroupInvitation() {
    var url: String? = null
    var name: String? = null

    fun isValid(): Boolean {
        return (url != null && name != null)
    }

    companion object {
        const val TAG = "OpenGroupInvitation"

        fun fromProto(proto: SignalServiceProtos.DataMessage.OpenGroupInvitation): OpenGroupInvitation {
            return OpenGroupInvitation(proto.url, proto.name)
        }
    }

    constructor(url: String?, serverName: String?): this() {
        this.url = url
        this.name = serverName
    }

    fun toProto(): SignalServiceProtos.DataMessage.OpenGroupInvitation? {
        val openGroupInvitationProto = SignalServiceProtos.DataMessage.OpenGroupInvitation.newBuilder()
        openGroupInvitationProto.url = url
        openGroupInvitationProto.name = name
        return try {
            openGroupInvitationProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct open group invitation proto from: $this.")
            null
        }
    }
}