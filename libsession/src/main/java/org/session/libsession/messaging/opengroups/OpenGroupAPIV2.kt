package org.session.libsession.messaging.opengroups

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import okhttp3.HttpUrl
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.opengroups.OpenGroupAPIV2.Error
import org.session.libsignal.service.loki.api.utilities.HTTP
import java.util.*

object OpenGroupAPIV2 {

    private val moderators: HashMap<String, HashMap<String, Set<String>>> = hashMapOf() // Server URL to (channel ID to set of moderator IDs)
    const val DEFAULT_SERVER = "https://sessionopengroup.com"
    const val DEFAULT_SERVER_PUBLIC_KEY = "658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231b"

    sealed class Error : Exception() {
        object GENERIC : Error()
        object PARSING_FAILED : Error()
        object DECRYPTION_FAILED : Error()
        object SIGNING_FAILED : Error()
        object INVALID_URL : Error()
        object NO_PUBLIC_KEY : Error()
    }

    data class Info(
            val id: String,
            val name: String,
            val imageID: String
    )

    data class Request(
            val verb: HTTP.Verb,
            val room: String?,
            val server: String,
            val endpoint: String,
            val queryParameters: Map<String, String>,
            val parameters: Any,
            val headers: Map<String, String>,
            val isAuthRequired: Boolean,
            // Always `true` under normal circumstances. You might want to disable
            // this when running over Lokinet.
            val useOnionRouting: Boolean
    )

    private fun send(request: Request): Promise<Any, Exception> {
        val parsed = HttpUrl.parse(request.server) ?: return Promise.ofFail(Error.INVALID_URL)
        val urlBuilder = HttpUrl.Builder()
                .scheme(parsed.scheme())
                .host(parsed.host())
                .addPathSegment(request.endpoint)

        for ((key, value) in request.queryParameters) {
            urlBuilder.addQueryParameter(key, value)
        }

        fun execute(token: String?): Promise<Map<*, *>, Exception> {
        }
        return if (request.isAuthRequired) {
            getAuthToken(request.room!!, request.server).bind(::execute)
        } else {
            execute(null)
        }
    }

    fun getAuthToken(room: String, server: String): Promise<String, Exception> {
        val storage = MessagingConfiguration.shared.storage
        return storage.getAuthToken(room, server)?.let {
            Promise.of(it)
        } ?: run {
            requestNewAuthToken(room, server)
                    .bind { claimAuthToken(it, room, server) }
                    .success { authToken ->
                        storage.setAuthToken(room, server, authToken)
                    }
        }
    }

    fun requestNewAuthToken(room: String, server: String): Promise<String, Exception> {
        val (publicKey, _) = MessagingConfiguration.shared.storage.getUserKeyPair()
                ?: return Promise.ofFail(Error.GENERIC)
        val queryParameters = mutableMapOf("public_key" to publicKey)

    }

    fun claimAuthToken(authToken: String, room: String, server: String): Promise<String, Exception> {
        TODO("implement")
    }

    fun deleteAuthToken(room: String, server: String): Promise<Long, Exception> {
        TODO("implement")
    }

    fun upload(file: ByteArray, room: String, server: String): Promise<Long, Exception> {
        TODO("implement")
    }

    fun download(file: Long, room: String, server: String): Promise<ByteArray, Exception> {
        TODO("implement")
    }

    fun send(message: OpenGroupMessageV2, room: String, server: String): Promise<OpenGroupMessageV2, Exception> {
        TODO("implement")
    }

    fun getMessages(room: String, server: String): Promise<List<OpenGroupMessageV2>, Exception> {
        TODO("implement")
    }

    fun deleteMessage(serverID: Long, room: String, server: String): Promise<Unit, Exception> {
        TODO("implement")
    }

    fun getDeletedMessages(room: String, server: String): Promise<List<Long>, Exception> {
        TODO("implement")
    }

    fun getModerators(room: String, server: String): Promise<List<String>, Exception> {
        TODO("implement")
    }

    fun ban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        TODO("implement")
    }

    fun unban(publicKey: String, room: String, server: String): Promise<Unit, Exception> {
        TODO("implement")
    }

    fun isUserModerator(publicKey: String, room: String, server: String): Promise<Boolean, Exception> {
        TODO("implement")
    }

    fun getDefaultRoomsIfNeeded() {
        TODO("implement")
    }

    fun getInfo(room: String, server: String): Promise<Info, Exception> {
        TODO("implement")
    }

    fun getAllRooms(server: String): Promise<List<Info>, Exception> {
        TODO("implement")
    }

    fun getMemberCount(room: String, server: String): Promise<Long, Exception> {
        TODO("implement")
    }

}

fun Error.errorDescription() = when (this) {
    Error.GENERIC -> "An error occurred."
    Error.PARSING_FAILED -> "Invalid response."
    Error.DECRYPTION_FAILED -> "Couldn't decrypt response."
    Error.SIGNING_FAILED -> "Couldn't sign message."
    Error.INVALID_URL -> "Invalid URL."
    Error.NO_PUBLIC_KEY -> "Couldn't find server public key."
}