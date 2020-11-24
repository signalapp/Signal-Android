package org.whispersystems.signalservice.loki.protocol.shelved.multidevice

import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.logging.Log
import org.whispersystems.signalservice.internal.util.Base64
import org.whispersystems.signalservice.internal.util.Hex
import org.whispersystems.signalservice.loki.utilities.removing05PrefixIfNeeded
import java.util.*

data class DeviceLink(val masterPublicKey: String, val slavePublicKey: String, val requestSignature: ByteArray?, val authorizationSignature: ByteArray?) {
    private val curve = Curve25519.getInstance(Curve25519.BEST)

    val type: Type
        get() = when (authorizationSignature) {
            null -> Type.REQUEST
            else -> Type.AUTHORIZATION
        }

    enum class Type(val rawValue: Int) { REQUEST(1), AUTHORIZATION(2) }

    constructor(masterPublicKey: String, slavePublicKey: String) : this(masterPublicKey, slavePublicKey, null, null)

    fun sign(type: Type, privateKey: ByteArray): DeviceLink? {
        val target = if (type == Type.REQUEST) masterPublicKey else slavePublicKey
        val data = Hex.fromStringCondensed(target) + ByteArray(1) { type.rawValue.toByte() }
        try {
            val signature = curve.calculateSignature(privateKey, data)
            return if (type == Type.REQUEST) copy(requestSignature = signature) else copy(authorizationSignature = signature)
        } catch (e: Exception) {
            return null
        }
    }

    fun verify(): Boolean {
        if (requestSignature == null && authorizationSignature == null) { return false }
        val signature = if (type == Type.REQUEST) requestSignature else authorizationSignature
        val issuer = if (type == Type.REQUEST) slavePublicKey else masterPublicKey
        val target = if (type == Type.REQUEST) masterPublicKey else slavePublicKey
        return try {
            val data = Hex.fromStringCondensed(target) + ByteArray(1) { type.rawValue.toByte() }
            val issuerPublicKey = Hex.fromStringCondensed(issuer.removing05PrefixIfNeeded())
            curve.verifySignature(issuerPublicKey, data, signature)
        } catch (e: Exception) {
            Log.w("LOKI", e.message)
            false
        }
    }

    fun toJSON(): Map<String, Any> {
        val result = mutableMapOf( "primaryDevicePubKey" to masterPublicKey, "secondaryDevicePubKey" to slavePublicKey )
        if (requestSignature != null) { result["requestSignature"] = Base64.encodeBytes(requestSignature) }
        if (authorizationSignature != null) { result["grantSignature"] = Base64.encodeBytes(authorizationSignature) }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is DeviceLink) {
            return (masterPublicKey == other.masterPublicKey && slavePublicKey == other.slavePublicKey
                && Arrays.equals(requestSignature, other.requestSignature) && Arrays.equals(authorizationSignature, other.authorizationSignature))
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        var hash = masterPublicKey.hashCode() xor slavePublicKey.hashCode()
        if (requestSignature != null) { hash = hash xor Arrays.hashCode(requestSignature) }
        if (authorizationSignature != null) { hash = hash xor Arrays.hashCode(authorizationSignature) }
        return hash
    }
}
