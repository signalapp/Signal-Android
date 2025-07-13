
package org.thoughtcrime.securesms.crypto

import org.whispersystems.libsignal.DuplicateMessageException
import org.whispersystems.libsignal.InvalidMessageException
import org.whispersystems.libsignal.LegacyMessageException
import org.whispersystems.libsignal.NoSessionException
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.SessionCipher as LibSignalSessionCipher
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.UntrustedIdentityException

/**
 * FIPS-compliant SessionCipher wrapper that ensures all encryption/decryption
 * operations use validated FIPS cryptographic modules.
 */
class SessionCipher(
    private val store: SignalProtocolStore,
    private val remoteAddress: SignalProtocolAddress
) {
    private val libSignalSessionCipher = LibSignalSessionCipher(store, remoteAddress)

    @Throws(UntrustedIdentityException::class)
    fun encrypt(paddedMessage: ByteArray): CiphertextMessage {
        // Ensure FIPS mode is enabled
        if (!FipsBridge.isFipsMode()) {
            throw RuntimeException("FIPS mode required for encryption")
        }

        // Use the underlying Signal implementation with FIPS crypto bridge
        return libSignalSessionCipher.encrypt(paddedMessage)
    }

    @Throws(
        InvalidMessageException::class,
        DuplicateMessageException::class,
        LegacyMessageException::class,
        NoSessionException::class,
        UntrustedIdentityException::class
    )
    fun decrypt(ciphertext: SignalMessage): ByteArray {
        // Ensure FIPS mode is enabled
        if (!FipsBridge.isFipsMode()) {
            throw RuntimeException("FIPS mode required for decryption")
        }

        return libSignalSessionCipher.decrypt(ciphertext)
    }

    @Throws(
        InvalidMessageException::class,
        DuplicateMessageException::class,
        LegacyMessageException::class,
        UntrustedIdentityException::class
    )
    fun decrypt(ciphertext: PreKeySignalMessage): ByteArray {
        // Ensure FIPS mode is enabled
        if (!FipsBridge.isFipsMode()) {
            throw RuntimeException("FIPS mode required for decryption")
        }

        return libSignalSessionCipher.decrypt(ciphertext)
    }

    fun getRemoteRegistrationId(): Int {
        return libSignalSessionCipher.remoteRegistrationId
    }

    fun getSessionVersion(): Int {
        return libSignalSessionCipher.sessionVersion
    }
}
