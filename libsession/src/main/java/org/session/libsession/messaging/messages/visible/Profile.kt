package org.session.libsession.messaging.messages.visible

import com.google.protobuf.ByteString
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos

class Profile() {

    var displayName: String? = null
    var profileKey: ByteArray? = null
    var profilePictureURL: String? = null

    companion object {
        const val TAG = "Profile"

        fun fromProto(proto: SignalServiceProtos.DataMessage): Profile? {
            val profileProto = proto.profile ?: return null
            val displayName = profileProto.displayName ?: return null
            val profileKey = proto.profileKey
            val profilePictureURL = profileProto.profilePictureURL
            profileKey?.let {
                profilePictureURL?.let {
                    return Profile(displayName = displayName, profileKey = profileKey.toByteArray(), profilePictureURL = profilePictureURL)
                }
            }
            return Profile(displayName)
        }
    }

    //constructor
    internal constructor(displayName: String, profileKey: ByteArray? = null, profilePictureURL: String? = null) : this() {
        this.displayName = displayName
        this.profileKey = profileKey
        this.profilePictureURL = profilePictureURL
    }

    fun toProto(): SignalServiceProtos.DataMessage? {
        val displayName = displayName
        if (displayName == null) {
            Log.w(TAG, "Couldn't construct link preview proto from: $this")
            return null
        }
        val dataMessageProto = SignalServiceProtos.DataMessage.newBuilder()
        val profileProto = SignalServiceProtos.LokiUserProfile.newBuilder()
        profileProto.displayName = displayName
        val profileKey = profileKey
        profileKey?.let { dataMessageProto.profileKey = ByteString.copyFrom(profileKey) }
        val profilePictureURL = profilePictureURL
        profilePictureURL?.let { profileProto.profilePictureURL = profilePictureURL }
        // Build
        try {
            dataMessageProto.profile = profileProto.build()
            return dataMessageProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct profile proto from: $this")
            return null
        }
    }
}