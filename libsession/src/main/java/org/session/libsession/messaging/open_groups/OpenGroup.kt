package org.session.libsession.messaging.open_groups

import okhttp3.HttpUrl
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import java.util.Locale

data class OpenGroup(
    val server: String,
    val room: String,
    val id: String,
    val name: String,
    val publicKey: String,
    val infoUpdates: Int,
) {

    constructor(server: String, room: String, name: String, infoUpdates: Int, publicKey: String) : this(
        server = server,
        room = room,
        id = "$server.$room",
        name = name,
        publicKey = publicKey,
        infoUpdates = infoUpdates,
    )

    companion object {

        fun fromJSON(jsonAsString: String): OpenGroup? {
            return try {
                val json = JsonUtil.fromJson(jsonAsString)
                if (!json.has("room")) return null
                val room = json.get("room").asText().toLowerCase(Locale.US)
                val server = json.get("server").asText().toLowerCase(Locale.US)
                val displayName = json.get("displayName").asText()
                val publicKey = json.get("publicKey").asText()
                val infoUpdates = json.get("infoUpdates")?.asText()?.toIntOrNull() ?: 0
                val capabilities = json.get("capabilities")?.asText()?.split(",") ?: emptyList()
                OpenGroup(server, room, displayName, infoUpdates, publicKey)
            } catch (e: Exception) {
                Log.w("Loki", "Couldn't parse open group from JSON: $jsonAsString.", e);
                null
            }
        }

        fun getServer(urlAsString: String): HttpUrl? {
            val url = HttpUrl.parse(urlAsString) ?: return null
            val builder = HttpUrl.Builder().scheme(url.scheme()).host(url.host())
            if (url.port() != 80 || url.port() != 443) {
                // Non-standard port; add to server
                builder.port(url.port())
            }
            return builder.build()
        }
    }

    fun toJson(): Map<String,String> = mapOf(
        "room" to room,
        "server" to server,
        "displayName" to name,
        "publicKey" to publicKey,
        "infoUpdates" to infoUpdates.toString(),
    )

    val joinURL: String get() = "$server/$room?public_key=$publicKey"

    val groupId: String get() = "$server.$room"
}