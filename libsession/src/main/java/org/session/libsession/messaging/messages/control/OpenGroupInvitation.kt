package org.session.libsession.messaging.messages.control

import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.utilities.logging.Log

class OpenGroupInvitation() : ControlMessage() {

    var serverAddress: String? = null;
    var channelId: Int? = 0;
    var serverName: String? = null;

    companion object {
        const val TAG = "OpenGroupInvitation"

        fun fromProto(proto: SignalServiceProtos.Content): OpenGroupInvitation? {
            val openGroupInvitationProto = if (proto.hasOpenGroupInvitation()) proto.openGroupInvitation else return null
            val serverAddress = openGroupInvitationProto.serverAddress
            val channelId = openGroupInvitationProto.channelId
            val serverName = openGroupInvitationProto.serverName
            return OpenGroupInvitation(serverAddress, channelId, serverName)
        }
    }

    constructor(serverAddress: String?, channelId: Int, serverName: String?): this() {
        this.serverAddress = serverAddress
        this.channelId = channelId
        this.serverName = serverName
    }

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        //TODO determine what's required
        return (serverAddress != null && channelId != null && serverName != null)
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val openGroupInvitationProto = SignalServiceProtos.OpenGroupInvitation.newBuilder()
        openGroupInvitationProto.serverAddress = serverAddress
        openGroupInvitationProto.channelId = channelId ?: 0
        openGroupInvitationProto.serverName = serverName

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