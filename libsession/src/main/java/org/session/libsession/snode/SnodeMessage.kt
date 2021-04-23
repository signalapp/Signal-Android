package org.session.libsession.snode

data class SnodeMessage(
    // The hex encoded public key of the recipient.
    val recipient: String,
    // The content of the message.
    val data: String,
    // The time to live for the message in milliseconds.
    val ttl: Long,
    // When the proof of work was calculated.
    val timestamp: Long
) {
    internal fun toJSON(): Map<String, String> {
        return mutableMapOf(
                "pubKey" to recipient,
                "data" to data,
                "ttl" to ttl.toString(),
                "timestamp" to timestamp.toString(),
                "nonce" to "")
    }
}
