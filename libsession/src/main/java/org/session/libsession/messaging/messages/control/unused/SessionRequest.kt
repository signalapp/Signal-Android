package org.session.libsession.messaging.messages.control.unused

import com.google.protobuf.ByteString
import org.session.libsession.messaging.messages.control.ControlMessage
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.messages.control.TypingIndicator
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.libsignal.state.PreKeyBundle
import org.session.libsignal.service.internal.push.SignalServiceProtos
import java.security.SecureRandom

class SessionRequest() : ControlMessage() {

    var preKeyBundle: PreKeyBundle? = null

    companion object {
        const val TAG = "SessionRequest"

        fun fromProto(proto: SignalServiceProtos.Content): SessionRequest? {
            if (proto.nullMessage == null) return null
            val preKeyBundleProto = proto.preKeyBundleMessage ?: return null
            val registrationID: Int = 0
            //TODO looks like database stuff here
            /*iOS code: Configuration.shared.storage.with { transaction in
                    registrationID = Configuration.shared.storage.getOrGenerateRegistrationID(using: transaction)
            }*/
            val preKeyBundle = PreKeyBundle(
                    registrationID,
                    1,
                    preKeyBundleProto.preKeyId,
                    null, //TODO preKeyBundleProto.preKey,
                    0, //TODO preKeyBundleProto.signedKey,
                    null, //TODO preKeyBundleProto.signedKeyId,
                    preKeyBundleProto.signature.toByteArray(),
                    null, //TODO preKeyBundleProto.identityKey
            ) ?: return null
            return SessionRequest(preKeyBundle)
        }
    }

    //constructor
    internal constructor(preKeyBundle: PreKeyBundle) : this() {
        this.preKeyBundle = preKeyBundle
    }

    // validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return preKeyBundle != null
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val preKeyBundle = preKeyBundle
        if (preKeyBundle == null) {
            Log.w(TAG, "Couldn't construct session request proto from: $this")
            return null
        }
        val nullMessageProto = SignalServiceProtos.NullMessage.newBuilder()
        val sr = SecureRandom()
        val paddingSize = sr.nextInt(512)
        val padding = ByteArray(paddingSize)
        nullMessageProto.padding = ByteString.copyFrom(padding)
        val preKeyBundleProto = SignalServiceProtos.PreKeyBundleMessage.newBuilder()
        //TODO preKeyBundleProto.identityKey = preKeyBundle.identityKey
        preKeyBundleProto.deviceId = preKeyBundle.deviceId
        preKeyBundleProto.preKeyId = preKeyBundle.preKeyId
        //TODO preKeyBundleProto.preKey = preKeyBundle.preKeyPublic
        preKeyBundleProto.signedKeyId = preKeyBundle.signedPreKeyId
        //TODO preKeyBundleProto.signedKey = preKeyBundle.signedPreKeyPublic
        preKeyBundleProto.signature = ByteString.copyFrom(preKeyBundle.signedPreKeySignature)
        val contentProto = SignalServiceProtos.Content.newBuilder()
        try {
            contentProto.nullMessage = nullMessageProto.build()
            contentProto.preKeyBundleMessage = preKeyBundleProto.build()
            return contentProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct session request proto from: $this")
            return null
        }
    }
}