
package org.thoughtcrime.securesms.crypto

import org.thoughtcrime.securesms.crypto.fips.FipsKeyStoreManager
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.InvalidKeyException
import org.whispersystems.libsignal.protocol.PreKeyBundle
import org.whispersystems.libsignal.SessionBuilder as LibSignalSessionBuilder
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.SignalProtocolStore

/**
 * FIPS-compliant SessionBuilder wrapper that ensures all cryptographic operations
 * use validated FIPS modules through the FipsBridge.
 */
class SessionBuilder(
    private val store: SignalProtocolStore,
    private val remoteAddress: SignalProtocolAddress
) {
    private val libSignalSessionBuilder = LibSignalSessionBuilder(store, remoteAddress)
    private val fipsKeyStore = FipsKeyStoreManager.getInstance()

    @Throws(InvalidKeyException::class)
    fun process(preKeyBundle: PreKeyBundle) {
        // Validate that we're in FIPS mode before processing
        if (!FipsBridge.isFipsMode()) {
            throw InvalidKeyException("FIPS mode required for session building")
        }

        // Verify the identity key through FIPS validation
        if (!fipsKeyStore.validateIdentityKey(preKeyBundle.identityKey)) {
            throw InvalidKeyException("Identity key failed FIPS validation")
        }

        // Process the bundle using the underlying Signal implementation
        // The actual crypto operations will be routed through our FIPS bridge
        libSignalSessionBuilder.process(preKeyBundle)
    }
}
