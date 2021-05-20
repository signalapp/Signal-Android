package org.session.libsession.messaging.open_groups

import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import java.util.*

data class OpenGroupV2(
    val server: String,
    val room: String,
    val id: String,
    val name: String,
    val publicKey: String
) {

    constructor(server: String, room: String, name: String, publicKey: String) : this(
        server = server,
        room = room,
        id = "$server.$room",
        name = name,
        publicKey = publicKey,
    )

    companion object {

        fun fromJSON(jsonAsString: String): OpenGroupV2? {
            return try {
                val json = JsonUtil.fromJson(jsonAsString)
                if (!json.has("room")) return null
                val room = json.get("room").asText().toLowerCase(Locale.US)
                val server = json.get("server").asText().toLowerCase(Locale.US)
                val displayName = json.get("displayName").asText()
                val publicKey = json.get("publicKey").asText()
                OpenGroupV2(server, room, displayName, publicKey)
            } catch (e: Exception) {
                Log.w("Loki", "Couldn't parse open group from JSON: $jsonAsString.", e);
                null
            }
        }

    }

    fun toJson(): Map<String,String> = mapOf(
        "room" to room,
        "server" to server,
        "displayName" to name,
        "publicKey" to publicKey,
    )

    val joinURL: String get() = "$server/$room?public_key=$publicKey"
}