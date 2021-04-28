package org.session.libsession.messaging.open_groups

import org.session.libsignal.utilities.JsonUtil

data class OpenGroup(
    val channel: Long,
    private val serverURL: String,
    val displayName: String,
    val isDeletable: Boolean
) {
    val server get() = serverURL.toLowerCase()
    val id get() = getId(channel, server)

    companion object {

        @JvmStatic fun getId(channel: Long, server: String): String {
            return "$server.$channel"
        }

        @JvmStatic fun fromJSON(jsonAsString: String): OpenGroup? {
            try {
                val json = JsonUtil.fromJson(jsonAsString)
                val channel = json.get("channel").asLong()
                val server = json.get("server").asText().toLowerCase()
                val displayName = json.get("displayName").asText()
                val isDeletable = json.get("isDeletable").asBoolean()
                return OpenGroup(channel, server, displayName, isDeletable)
            } catch (e: Exception) {
                return null
            }
        }
    }

    fun toJSON(): Map<String, Any> {
        return mapOf( "channel" to channel, "server" to server, "displayName" to displayName, "isDeletable" to isDeletable )
    }
}
