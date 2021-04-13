package org.session.libsession.messaging.opengroups

import org.session.libsignal.utilities.JsonUtil
import java.util.*

data class OpenGroupV2(
        val server: String,
        val room: String,
        val id: String,
        val name: String,
        val publicKey: String,
        val imageId: String?
) {

    constructor(server: String, room: String, name: String, publicKey: String, imageId: String?) : this(
            server = server,
            room = room,
            id = "$server.$room",
            name = name,
            publicKey = publicKey,
            imageId = imageId
    )

    companion object {

        fun fromJson(jsonAsString: String): OpenGroupV2? {
            return try {
                val json = JsonUtil.fromJson(jsonAsString)
                if (!json.has("room")) return null

                val room = json.get("room").asText().toLowerCase(Locale.getDefault())
                val server = json.get("server").asText().toLowerCase(Locale.getDefault())
                val displayName = json.get("displayName").asText()
                val publicKey = json.get("publicKey").asText()
                val imageId = json.get("imageId").asText().let { str -> if (str.isEmpty()) null else str }

                OpenGroupV2(server, room, displayName, publicKey, imageId)
            } catch (e: Exception) {
                null
            }
        }

    }

}