package org.session.libsession.messaging.open_groups

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsignal.crypto.PushTransportDetails
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Base64.decode
import org.session.libsignal.utilities.Log
import org.whispersystems.curve25519.Curve25519

data class OpenGroupMessageV2(
    val serverID: Long? = null,
    val sender: String?,
    val sentTimestamp: Long,
    /**
     * The serialized protobuf in base64 encoding.
     */
    val base64EncodedData: String,
    /**
     * When sending a message, the sender signs the serialized protobuf with their private key so that
     * a receiving user can verify that the message wasn't tampered with.
     */
    val base64EncodedSignature: String? = null
) {

    companion object {
        private val curve = Curve25519.getInstance(Curve25519.BEST)

        fun fromJSON(json: Map<String, Any>): OpenGroupMessageV2? {
            val base64EncodedData = json["data"] as? String ?: return null
            val sentTimestamp = json["timestamp"] as? Long ?: return null
            val serverID = json["server_id"] as? Int
            val sender = json["public_key"] as? String
            val base64EncodedSignature = json["signature"] as? String
            return OpenGroupMessageV2(
                serverID = serverID?.toLong(),
                sender = sender,
                sentTimestamp = sentTimestamp,
                base64EncodedData = base64EncodedData,
                base64EncodedSignature = base64EncodedSignature
            )
        }

    }

    fun sign(): OpenGroupMessageV2? {
        if (base64EncodedData.isEmpty()) return null
        val (publicKey, privateKey) = MessagingModuleConfiguration.shared.storage.getUserKeyPair() ?: return null
        if (sender != publicKey) return null
        val signature = try {
            curve.calculateSignature(privateKey, decode(base64EncodedData))
        } catch (e: Exception) {
            Log.w("Loki", "Couldn't sign open group message.", e)
            return null
        }
        return copy(base64EncodedSignature = Base64.encodeBytes(signature))
    }

    fun toJSON(): Map<String, Any> {
        val json = mutableMapOf( "data" to base64EncodedData, "timestamp" to sentTimestamp )
        serverID?.let { json["server_id"] = it }
        sender?.let { json["public_key"] = it }
        base64EncodedSignature?.let { json["signature"] = it }
        return json
    }

    fun toProto(): SignalServiceProtos.Content {
        val data = decode(base64EncodedData).let(PushTransportDetails::getStrippedPaddingMessageBody)
        return SignalServiceProtos.Content.parseFrom(data)
    }
}