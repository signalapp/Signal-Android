package org.session.libsession.messaging.open_groups

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.OpenGroupApi.Capability
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsignal.crypto.PushTransportDetails
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Base64.decode
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.removingIdPrefixIfNeeded
import org.session.libsignal.utilities.toHexString
import org.whispersystems.curve25519.Curve25519

data class OpenGroupMessage(
    val serverID: Long? = null,
    val sender: String?,
    val sentTimestamp: Long,
    /**
     * The serialized protobuf in base64 encoding.
     */
    val base64EncodedData: String?,
    /**
     * When sending a message, the sender signs the serialized protobuf with their private key so that
     * a receiving user can verify that the message wasn't tampered with.
     */
    val base64EncodedSignature: String? = null,
    val reactions: Map<String, OpenGroupApi.Reaction>? = null
) {

    companion object {
        private val curve = Curve25519.getInstance(Curve25519.BEST)

        fun fromJSON(json: Map<String, Any>): OpenGroupMessage? {
            val base64EncodedData = json["data"] as? String ?: return null
            val sentTimestamp = json["posted"] as? Double ?: return null
            val serverID = json["id"] as? Int
            val sender = json["session_id"] as? String
            val base64EncodedSignature = json["signature"] as? String
            return OpenGroupMessage(
                serverID = serverID?.toLong(),
                sender = sender,
                sentTimestamp = (sentTimestamp * 1000).toLong(),
                base64EncodedData = base64EncodedData,
                base64EncodedSignature = base64EncodedSignature
            )
        }
    }

    fun sign(room: String, server: String, fallbackSigningType: IdPrefix): OpenGroupMessage? {
        if (base64EncodedData.isNullOrEmpty()) return null
        val userEdKeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair() ?: return null
        val openGroup = MessagingModuleConfiguration.shared.storage.getOpenGroup(room, server) ?: return null
        val serverCapabilities = MessagingModuleConfiguration.shared.storage.getServerCapabilities(server)
        val signature = when {
            serverCapabilities.contains(Capability.BLIND.name.lowercase()) -> {
                val blindedKeyPair = SodiumUtilities.blindedKeyPair(openGroup.publicKey, userEdKeyPair) ?: return null
                SodiumUtilities.sogsSignature(
                    decode(base64EncodedData),
                    userEdKeyPair.secretKey.asBytes,
                    blindedKeyPair.secretKey.asBytes,
                    blindedKeyPair.publicKey.asBytes
                ) ?: return null
            }
            fallbackSigningType == IdPrefix.UN_BLINDED -> {
                curve.calculateSignature(userEdKeyPair.secretKey.asBytes, decode(base64EncodedData))
            }
            else -> {
                val (publicKey, privateKey) = MessagingModuleConfiguration.shared.storage.getUserX25519KeyPair().let { it.publicKey.serialize() to it.privateKey.serialize() }
                if (sender != publicKey.toHexString() && !userEdKeyPair.publicKey.asHexString.equals(sender?.removingIdPrefixIfNeeded(), true)) return null
                try {
                    curve.calculateSignature(privateKey, decode(base64EncodedData))
                } catch (e: Exception) {
                    Log.w("Loki", "Couldn't sign open group message.", e)
                    return null
                }
            }
        }
        return copy(base64EncodedSignature = Base64.encodeBytes(signature))
    }

    fun toJSON(): Map<String, Any?> {
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