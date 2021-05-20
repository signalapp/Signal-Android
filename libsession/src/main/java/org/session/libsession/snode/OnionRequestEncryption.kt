package org.session.libsession.snode

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.utilities.AESGCM
import org.session.libsession.utilities.AESGCM.EncryptionResult
import org.session.libsignal.utilities.toHexString
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.ThreadUtils
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OnionRequestEncryption {

    internal fun encode(ciphertext: ByteArray, json: Map<*, *>): ByteArray {
        // The encoding of V2 onion requests looks like: | 4 bytes: size N of ciphertext | N bytes: ciphertext | json as utf8 |
        val jsonAsData = JsonUtil.toJson(json).toByteArray()
        val ciphertextSize = ciphertext.size
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(ciphertextSize)
        val ciphertextSizeAsData = ByteArray(buffer.capacity())
        // Casting here avoids an issue where this gets compiled down to incorrect byte code. See
        // https://github.com/eclipse/jetty.project/issues/3244 for more info
        (buffer as Buffer).position(0)
        buffer.get(ciphertextSizeAsData)
        return ciphertextSizeAsData + ciphertext + jsonAsData
    }

    /**
     * Encrypts `payload` for `destination` and returns the result. Use this to build the core of an onion request.
     */
    internal fun encryptPayloadForDestination(payload: Map<*, *>, destination: OnionRequestAPI.Destination): Promise<EncryptionResult, Exception> {
        val deferred = deferred<EncryptionResult, Exception>()
        ThreadUtils.queue {
            try {
                // Wrapping isn't needed for file server or open group onion requests
                when (destination) {
                    is OnionRequestAPI.Destination.Snode -> {
                        val snodeX25519PublicKey = destination.snode.publicKeySet!!.x25519Key
                        val payloadAsData = JsonUtil.toJson(payload).toByteArray()
                        val plaintext = encode(payloadAsData, mapOf( "headers" to "" ))
                        val result = AESGCM.encrypt(plaintext, snodeX25519PublicKey)
                        deferred.resolve(result)
                    }
                    is OnionRequestAPI.Destination.Server -> {
                        val plaintext = JsonUtil.toJson(payload).toByteArray()
                        val result = AESGCM.encrypt(plaintext, destination.x25519PublicKey)
                        deferred.resolve(result)
                    }
                }
            } catch (exception: Exception) {
                deferred.reject(exception)
            }
        }
        return deferred.promise
    }

    /**
     * Encrypts the previous encryption result (i.e. that of the hop after this one) for this hop. Use this to build the layers of an onion request.
     */
    internal fun encryptHop(lhs: OnionRequestAPI.Destination, rhs: OnionRequestAPI.Destination, previousEncryptionResult: EncryptionResult): Promise<EncryptionResult, Exception> {
        val deferred = deferred<EncryptionResult, Exception>()
        ThreadUtils.queue {
            try {
                val payload: MutableMap<String, Any>
                when (rhs) {
                    is OnionRequestAPI.Destination.Snode -> {
                        payload = mutableMapOf( "destination" to rhs.snode.publicKeySet!!.ed25519Key )
                    }
                    is OnionRequestAPI.Destination.Server -> {
                        payload = mutableMapOf(
                            "host" to rhs.host,
                            "target" to rhs.target,
                            "method" to "POST",
                            "protocol" to rhs.scheme,
                            "port" to rhs.port
                        )
                    }
                }
                payload["ephemeral_key"] = previousEncryptionResult.ephemeralPublicKey.toHexString()
                val x25519PublicKey: String
                when (lhs) {
                    is OnionRequestAPI.Destination.Snode -> {
                        x25519PublicKey = lhs.snode.publicKeySet!!.x25519Key
                    }
                    is OnionRequestAPI.Destination.Server -> {
                        x25519PublicKey = lhs.x25519PublicKey
                    }
                }
                val plaintext = encode(previousEncryptionResult.ciphertext, payload)
                val result = AESGCM.encrypt(plaintext, x25519PublicKey)
                deferred.resolve(result)
            } catch (exception: Exception) {
                deferred.reject(exception)
            }
        }
        return deferred.promise
    }
}
