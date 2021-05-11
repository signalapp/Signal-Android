package org.session.libsession.messaging.messages.visible

import org.session.libsession.messaging.messages.control.ControlMessage
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.utilities.logging.Log

class OpenGroupInvitation() {

    var groupUrl: String? = null;
    var groupName: String? = null;

    companion object {
        const val TAG = "OpenGroupInvitation"

        fun fromProto(proto: SignalServiceProtos.DataMessage.OpenGroupInvitation): OpenGroupInvitation? {
            val groupUrl = proto.url
            val groupName = proto.name
            return OpenGroupInvitation(groupUrl, groupName)
        }
    }

    constructor(url: String?, serverName: String?): this() {
        this.groupUrl = url
        this.groupName = serverName
    }

    fun isValid(): Boolean {
        return (groupUrl != null && groupName != null)
    }

    fun toProto(): SignalServiceProtos.DataMessage.OpenGroupInvitation? {
        val openGroupInvitationProto = SignalServiceProtos.DataMessage.OpenGroupInvitation.newBuilder()
        openGroupInvitationProto.url = groupUrl
        openGroupInvitationProto.name = groupName

        return try {
            openGroupInvitationProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct open group invitation proto from: $this")
            null
        }
    }
}