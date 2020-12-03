package org.session.libsession.messaging.messages.control.unused

import com.google.protobuf.ByteString
import org.session.libsession.messaging.Configuration
import org.session.libsession.messaging.messages.control.ControlMessage
import org.session.libsignal.libsignal.IdentityKey
import org.session.libsignal.libsignal.ecc.DjbECPublicKey
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
            var registrationID: Int = 0
            registrationID = Configuration.shared.storage.getOrGenerateRegistrationID() //TODO no implementation for getOrGenerateRegistrationID yet
            //TODO just confirm if the above code does the equivalent to swift below:
            /*iOS code: Configuration.shared.storage.with { transaction in
                    registrationID = Configuration.shared.storage.getOrGenerateRegistrationID(using: transaction)
            }*/
            val preKeyBundle = PreKeyBundle(
                    registrationID,
                    1,
                    preKeyBundleProto.preKeyId,
                    DjbECPublicKey(preKeyBundleProto.preKey.toByteArray()),
                    preKeyBundleProto.signedKeyId,
                    DjbECPublicKey(preKeyBundleProto.signedKey.toByteArray()),
                    preKeyBundleProto.signature.toByteArray(),
                    IdentityKey(DjbECPublicKey(preKeyBundleProto.identityKey.toByteArray()))
            )
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
        preKeyBundleProto.identityKey = ByteString.copyFrom(preKeyBundle.identityKey.publicKey.serialize())
        preKeyBundleProto.deviceId = preKeyBundle.deviceId
        preKeyBundleProto.preKeyId = preKeyBundle.preKeyId
        preKeyBundleProto.preKey = ByteString.copyFrom(preKeyBundle.preKey.serialize())
        preKeyBundleProto.signedKeyId = preKeyBundle.signedPreKeyId
        preKeyBundleProto.signedKey = ByteString.copyFrom(preKeyBundle.signedPreKey.serialize())
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