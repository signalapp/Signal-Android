package org.whispersystems.signalservice.loki.api

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.whispersystems.libsignal.logging.Log
import org.whispersystems.signalservice.internal.util.Base64
import org.whispersystems.signalservice.loki.api.crypto.ProofOfWork
import org.whispersystems.signalservice.loki.protocol.meta.TTLUtilities
import org.whispersystems.signalservice.loki.utilities.prettifiedDescription

internal data class LokiMessage(
    /**
     * The hex encoded public key of the receiver.
     */
    internal val recipientPublicKey: String,
    /**
     * The content of the message.
     */
    internal val data: String,
    /**
     * The time to live for the message in milliseconds.
     */
    internal val ttl: Int,
    /**
     * Whether this message is a ping.
     */
    internal val isPing: Boolean,
    /**
     * When the proof of work was calculated, if applicable (P2P messages don't require proof of work).
     *
     * - Note: Expressed as milliseconds since 00:00:00 UTC on 1 January 1970.
     */
    internal var timestamp: Long? = null,
    /**
     * The base 64 encoded proof of work, if applicable (P2P messages don't require proof of work).
     */
    internal var nonce: String? = null
) {

    internal companion object {

        internal fun from(message: SignalMessageInfo): LokiMessage? {
            try {
                val wrappedMessage = MessageWrapper.wrap(message)
                val data = Base64.encodeBytes(wrappedMessage)
                val destination = message.recipientPublicKey
                var ttl = TTLUtilities.fallbackMessageTTL
                val messageTTL = message.ttl
                if (messageTTL != null && messageTTL != 0) { ttl = messageTTL }
                val isPing = message.isPing
                return LokiMessage(destination, data, ttl, isPing)
            } catch (e: Exception) {
                Log.d("Loki", "Failed to convert Signal message to Loki message: ${message.prettifiedDescription()}.")
                return null
            }
        }
    }

    @kotlin.ExperimentalUnsignedTypes
    internal fun calculatePoW(): Promise<LokiMessage, Exception> {
        val deferred = deferred<LokiMessage, Exception>()
        // Run PoW in a background thread
        Thread {
            val now = System.currentTimeMillis()
            val nonce = ProofOfWork.calculate(data, recipientPublicKey, now, ttl)
            if (nonce != null ) {
                deferred.resolve(copy(nonce = nonce, timestamp = now))
            } else {
                deferred.reject(SnodeAPI.Error.ProofOfWorkCalculationFailed)
            }
        }.start()
        return deferred.promise
    }

    internal fun toJSON(): Map<String, String> {
        val result = mutableMapOf( "pubKey" to recipientPublicKey, "data" to data, "ttl" to ttl.toString() )
        val timestamp = timestamp
        val nonce = nonce
        if (timestamp != null && nonce != null) {
            result["timestamp"] = timestamp.toString()
            result["nonce"] = nonce
        }
        return result
    }
}
