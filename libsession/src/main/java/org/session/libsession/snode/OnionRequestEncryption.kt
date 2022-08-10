package org.session.libsession.snode

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.snode.OnionRequestAPI.Destination
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
    internal fun encryptPayloadForDestination(
        payload: ByteArray,
        destination: Destination,
        version: Version
    ): Promise<EncryptionResult, Exception> {
        val deferred = deferred<EncryptionResult, Exception>()
        ThreadUtils.queue {
            try {
                val plaintext = if (version == Version.V4) {
                    payload
                } else {
                    // Wrapping isn't needed for file server or open group onion requests
                    when (destination) {
                        is Destination.Snode -> encode(payload, mapOf("headers" to ""))
                        is Destination.Server -> payload
                    }
                }
                val x25519PublicKey = when (destination) {
                    is Destination.Snode -> destination.snode.publicKeySet!!.x25519Key
                    is Destination.Server -> destination.x25519PublicKey
                }
                val result = AESGCM.encrypt(plaintext, x25519PublicKey)
                deferred.resolve(result)
            } catch (exception: Exception) {
                deferred.reject(exception)
            }
        }
        return deferred.promise
    }

    /**
     * Encrypts the previous encryption result (i.e. that of the hop after this one) for this hop. Use this to build the layers of an onion request.
     */
    internal fun encryptHop(lhs: Destination, rhs: Destination, previousEncryptionResult: EncryptionResult): Promise<EncryptionResult, Exception> {
        val deferred = deferred<EncryptionResult, Exception>()
        ThreadUtils.queue {
            try {
                val payload: MutableMap<String, Any> = when (rhs) {
                    is Destination.Snode -> {
                        mutableMapOf( "destination" to rhs.snode.publicKeySet!!.ed25519Key )
                    }
                    is Destination.Server -> {
                        mutableMapOf(
                            "host" to rhs.host,
                            "target" to rhs.target,
                            "method" to "POST",
                            "protocol" to rhs.scheme,
                            "port" to rhs.port
                        )
                    }
                }
                payload["ephemeral_key"] = previousEncryptionResult.ephemeralPublicKey.toHexString()
                val x25519PublicKey = when (lhs) {
                    is Destination.Snode -> {
                        lhs.snode.publicKeySet!!.x25519Key
                    }
                    is Destination.Server -> {
                        lhs.x25519PublicKey
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
