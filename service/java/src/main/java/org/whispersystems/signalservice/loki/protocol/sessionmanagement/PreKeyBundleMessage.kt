package org.whispersystems.signalservice.loki.protocol.sessionmanagement

import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.state.PreKeyBundle

data class PreKeyBundleMessage(
    val identityKey: ByteArray,
    val deviceID: Int,
    val preKeyID: Int,
    val signedPreKeyID: Int,
    val preKey: ByteArray,
    val signedPreKey: ByteArray,
    val signedPreKeySignature: ByteArray
) {

    constructor(preKeyBundle: PreKeyBundle) : this(preKeyBundle.identityKey.serialize(), preKeyBundle.deviceId, preKeyBundle.preKeyId,
        preKeyBundle.signedPreKeyId, preKeyBundle.preKey.serialize(), preKeyBundle.signedPreKey.serialize(), preKeyBundle.signedPreKeySignature)

    fun getPreKeyBundle(registrationID: Int): PreKeyBundle {
        return PreKeyBundle(registrationID, deviceID, preKeyID, Curve.decodePoint(preKey, 0), signedPreKeyID, Curve.decodePoint(signedPreKey, 0), signedPreKeySignature, IdentityKey(identityKey, 0))
    }
}
