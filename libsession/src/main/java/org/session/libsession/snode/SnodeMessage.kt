package org.session.libsession.snode

data class SnodeMessage(
    /**
     * The hex encoded public key of the recipient.
     */
    val recipient: String,
    /**
     * The base64 encoded content of the message.
     */
    val data: String,
    /**
     * The time to live for the message in milliseconds.
     */
    val ttl: Long,
    /**
     * When the proof of work was calculated.
     *
     * **Note:** Expressed as milliseconds since 00:00:00 UTC on 1 January 1970.
     */
    val timestamp: Long
) {

    internal fun toJSON(): Map<String, String> {
        return mapOf(
            "pubKey" to recipient,
            "data" to data,
            "ttl" to ttl.toString(),
            "timestamp" to timestamp.toString(),
        )
    }
}
