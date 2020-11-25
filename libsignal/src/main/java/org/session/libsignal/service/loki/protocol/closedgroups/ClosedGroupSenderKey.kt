package org.session.libsignal.service.loki.protocol.closedgroups

import com.google.protobuf.ByteString
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.libsignal.protocol.SignalProtos
import org.session.libsignal.libsignal.util.Hex
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.internal.util.JsonUtil
import org.session.libsignal.service.loki.utilities.toHexString

public class ClosedGroupSenderKey(public val chainKey: ByteArray, public val keyIndex: Int, public val publicKey: ByteArray) {

    companion object {

        public fun fromJSON(jsonAsString: String): ClosedGroupSenderKey? {
            try {
                val json = JsonUtil.fromJson(jsonAsString, Map::class.java)
                val chainKey = Hex.fromStringCondensed(json["chainKey"] as String)
                val keyIndex = json["keyIndex"] as Int
                val publicKey = Hex.fromStringCondensed(json["publicKey"] as String)
                return ClosedGroupSenderKey(chainKey, keyIndex, publicKey)
            } catch (exception: Exception) {
                Log.d("Loki", "Couldn't parse closed group sender key from: $jsonAsString.")
                return null
            }
        }
    }

    public fun toJSON(): String {
        val json = mapOf( "chainKey" to chainKey.toHexString(), "keyIndex" to keyIndex, "publicKey" to publicKey.toHexString() )
        return JsonUtil.toJson(json)
    }

    public fun toProto(): SignalServiceProtos.ClosedGroupUpdate.SenderKey {
        val builder = SignalServiceProtos.ClosedGroupUpdate.SenderKey.newBuilder()
        builder.chainKey = ByteString.copyFrom(chainKey)
        builder.keyIndex = keyIndex
        builder.publicKey = ByteString.copyFrom(publicKey)
        return builder.build()
    }

    override fun equals(other: Any?): Boolean {
        return if (other is ClosedGroupSenderKey) {
            chainKey.contentEquals(other.chainKey) && keyIndex == other.keyIndex && publicKey.contentEquals(other.publicKey)
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return chainKey.hashCode() xor keyIndex.hashCode() xor publicKey.hashCode()
    }

    override fun toString(): String {
        return "[ chainKey : ${chainKey.toHexString()}, keyIndex : $keyIndex, messageKeys : ${publicKey.toHexString()} ]"
    }
}
