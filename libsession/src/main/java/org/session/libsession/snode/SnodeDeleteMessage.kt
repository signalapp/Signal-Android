package org.session.libsession.snode

import org.session.libsignal.utilities.removing05PrefixIfNeeded

data class SnodeDeleteMessage(
    /**
     * The hex encoded public key of the user.
     */
    val pubKey: String,
    /**
     * The timestamp at which this request was initiated, in milliseconds since unix epoch.
     * Must be within Must be within ±60s of the current time.
     * (For clients it is recommended to retrieve a timestamp via `info` first, to avoid client time sync issues).
     */
    val timestamp: Long,
    /**
     * a Base64-encoded signature of ( "delete_all" || timestamp ), signed by the pubKey
     */
    val signature: String,
) {

    internal fun toJSON(): Map<String, Any> {
        return mapOf(
            "pubkey" to pubKey,
            "timestamp" to timestamp,
            "signature" to signature
        )
    }

}