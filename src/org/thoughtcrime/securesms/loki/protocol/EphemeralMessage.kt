package org.thoughtcrime.securesms.loki.protocol

import org.whispersystems.signalservice.internal.util.JsonUtil

data class EphemeralMessage private constructor(val data: Map<*, *>) {

    companion object {

        @JvmStatic
        fun create(publicKey: String) = EphemeralMessage(mapOf( "recipient" to publicKey ))

        @JvmStatic
        fun createUnlinkingRequest(publicKey: String) = EphemeralMessage(mapOf( "recipient" to publicKey, "unpairingRequest" to true ))

        @JvmStatic
        fun createSessionRestorationRequest(publicKey: String) = EphemeralMessage(mapOf( "recipient" to publicKey, "friendRequest" to true, "sessionRestore" to true ))

        @JvmStatic
        fun createSessionRequest(publicKey: String) = EphemeralMessage(mapOf("recipient" to publicKey, "friendRequest" to true, "sessionRequest" to true))

        internal fun parse(serialized: String): EphemeralMessage {
            val data = JsonUtil.fromJson(serialized, Map::class.java) ?: throw IllegalArgumentException("Couldn't parse string to JSON")
            return EphemeralMessage(data)
        }
    }

    fun <T> get(key: String, defaultValue: T): T {
        return data[key] as? T ?: defaultValue
    }

    fun serialize(): String {
        return JsonUtil.toJson(data)
    }
}