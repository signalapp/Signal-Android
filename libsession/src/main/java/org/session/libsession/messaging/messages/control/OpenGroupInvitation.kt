package org.session.libsession.messaging.messages.control

import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.utilities.logging.Log

class OpenGroupInvitation() : ControlMessage() {

    var groupUrl: String? = null;
    var groupName: String? = null;

    companion object {
        const val TAG = "OpenGroupInvitation"

        fun fromProto(proto: SignalServiceProtos.Content): OpenGroupInvitation? {
            val openGroupInvitationProto = if (proto.hasOpenGroupInvitation()) proto.openGroupInvitation else return null
            val serverAddress = openGroupInvitationProto.groupUrl
            val serverName = openGroupInvitationProto.groupName
            return OpenGroupInvitation(serverAddress, serverName)
        }
    }

    constructor(url: String?, serverName: String?): this() {
        this.groupUrl = url
        this.groupName = serverName
    }

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return (groupUrl != null && groupName != null)
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val openGroupInvitationProto = SignalServiceProtos.OpenGroupInvitation.newBuilder()
        openGroupInvitationProto.groupUrl = groupUrl
        openGroupInvitationProto.groupName = groupName

        val proto = SignalServiceProtos.Content.newBuilder()
        return try {
            proto.openGroupInvitation = openGroupInvitationProto.build()
            proto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct open group invitation proto from: $this")
            null
        }
    }
}