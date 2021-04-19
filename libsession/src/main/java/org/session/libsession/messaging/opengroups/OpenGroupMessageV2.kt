package org.session.libsession.messaging.opengroups

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.logging.Log
import org.whispersystems.curve25519.Curve25519

data class OpenGroupMessageV2(
        val serverID: Long?,
        val sender: String?,
        val sentTimestamp: Long,
        // The serialized protobuf in base64 encoding
        val base64EncodedData: String,
        // When sending a message, the sender signs the serialized protobuf with their private key so that
        // a receiving user can verify that the message wasn't tampered with.
        val base64EncodedSignature: String?
) {

    companion object {
        private val curve = Curve25519.getInstance(Curve25519.BEST)

        fun fromJSON(json: Map<String, Any>): OpenGroupMessageV2? {
            val base64EncodedData = json["data"] as? String ?: return null
            val sentTimestamp = json["timestamp"] as? Long ?: return null
            val serverID = json["server_id"] as? Long
            val sender = json["public_key"] as? String
            val base64EncodedSignature = json["signature"] as? String
            return OpenGroupMessageV2(serverID = serverID,
                    sender = sender,
                    sentTimestamp = sentTimestamp,
                    base64EncodedData = base64EncodedData,
                    base64EncodedSignature = base64EncodedSignature
            )
        }

    }

    fun sign(): OpenGroupMessageV2? {
        if (base64EncodedData.isEmpty()) return null
        val (publicKey, privateKey) = MessagingConfiguration.shared.storage.getUserKeyPair() ?: return null

        if (sender != publicKey) return null // only sign our own messages?

        val signature = try {
            curve.calculateSignature(privateKey, Base64.decode(base64EncodedData))
        } catch (e: Exception) {
            Log.e("Loki", "Couldn't sign OpenGroupV2Message", e)
            return null
        }

        return copy(base64EncodedSignature = Base64.encodeBytes(signature))
    }

    fun toJSON(): Map<String, Any> {
        val jsonMap = mutableMapOf("data" to base64EncodedData, "timestamp" to sentTimestamp)
        serverID?.let { jsonMap["server_id"] = serverID }
        sender?.let { jsonMap["public_key"] = sender }
        base64EncodedSignature?.let { jsonMap["signature"] = base64EncodedSignature }
        return jsonMap
    }
}