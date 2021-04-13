package org.session.libsession.messaging.opengroups

import org.session.libsession.messaging.opengroups.OpenGroupAPIV2.Error
import org.session.libsession.messaging.utilities.DotNetAPI
import java.util.*

class OpenGroupAPIV2: DotNetAPI() {

    enum class Error {
        GENERIC,
        PARSING_FAILED,
        DECRYPTION_FAILED,
        SIGNING_FAILED,
        INVALID_URL,
        NO_PUBLIC_KEY
    }

    companion object {
        private val moderators: HashMap<String, HashMap<String, Set<String>>> = hashMapOf() // Server URL to (channel ID to set of moderator IDs)
        const val DEFAULT_SERVER = "https://sessionopengroup.com"
        const val DEFAULT_SERVER_PUBLIC_KEY = "658d29b91892a2389505596b135e76a53db6e11d613a51dbd3d0816adffb231b"
    }



}

data class Info(val id: String, val name: String, val imageId: String?)

fun Error.errorDescription() = when (this) {
    Error.GENERIC -> "An error occurred."
    Error.PARSING_FAILED -> "Invalid response."
    Error.DECRYPTION_FAILED -> "Couldn't decrypt response."
    Error.SIGNING_FAILED -> "Couldn't sign message."
    Error.INVALID_URL -> "Invalid URL."
    Error.NO_PUBLIC_KEY -> "Couldn't find server public key."
}