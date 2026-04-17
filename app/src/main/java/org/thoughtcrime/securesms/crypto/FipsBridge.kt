// DEPRECATED — see proposals/fips-discovery-2026-04-17.md

package org.thoughtcrime.securesms.crypto

import android.util.Log
import java.io.Closeable
import java.io.IOException

/**
 * Manages the lifecycle and interaction with the native Rust/C FIPS cryptographic bridge.
 *
 * This object acts as a singleton factory for creating [FipsRouter] instances, which provide
 * a safe, object-oriented wrapper around the native cryptographic functions. It is responsible
 * for loading the native library and initializing the FIPS module on application startup.
 * This implementation is tailored for the "Client-Only" architecture.
 */
object FipsBridge {

    private const val TAG = "FipsBridge"
    private var isInitialized = false

    init {
        try {
            // Load the combined Rust/C native library. The name must match the `module`
            // property in `build.gradle.kts`'s `cargoNdk` block.
            System.loadLibrary("fips_signal_bridge")
            Log.i(TAG, "Native library 'libfips_signal_bridge.so' loaded successfully.")

            // Initialize the FIPS provider and run its power-on self-tests.
            // This must be done once when the library is loaded.
            if (initialize()) {
                isInitialized = true
                Log.i(TAG, "FIPS provider initialized and self-tests passed.")
            } else {
                Log.e(TAG, "FATAL: FIPS provider failed to initialize or self-tests failed.")
                // In a real application, this might trigger a more drastic error state,
                // preventing the use of any FIPS functionality.
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native FIPS bridge library.", e)
            // This error is critical and means the FIPS functionality is unavailable.
        }
    }

    /**
     * Creates a new instance of the native cryptographic router.
     *
     * @return A [FipsRouter] instance which provides access to the cryptographic functions.
     * @throws IOException if the FIPS bridge was not initialized successfully.
     */
    fun createRouter(): FipsRouter {
        if (!isInitialized) {
            throw IOException("FIPS Bridge is not initialized. Cannot create router.")
        }
        val routerPtr = createRouterNative()
        if (routerPtr == 0L) {
            throw IOException("Failed to create native cryptographic router.")
        }
        Log.d(TAG, "Created native CryptoRouter instance with pointer: $routerPtr")
        return FipsRouter(routerPtr)
    }

    // --- Native Method Declarations ---
    // These methods are implemented in the Rust JNI bridge (`lib.rs`).

    @JvmStatic
    private external fun initialize(): Boolean

    @JvmStatic
    private external fun createRouterNative(): Long

    @JvmStatic
    internal external fun destroyRouter(routerPtr: Long)

    /**
     * Native call to create the special "FIPS Invitation" message payload.
     * This is the first step in the "Opportunistic FIPS Handshake".
     *
     * @param routerPtr The pointer to the native `CryptoRouter` instance.
     * @return The serialized invitation message as a byte array, or `null` on failure.
     */
    @JvmStatic
    internal external fun createFipsInvitation(routerPtr: Long): ByteArray?
}

/**
 * A safe, resource-managing wrapper around a native `CryptoRouter` instance.
 *
 * This class encapsulates the raw pointer (`jlong`) to the Rust object and ensures
 * that the native resources are freed when the object is no longer in use by
 * implementing [Closeable]. This allows it to be used with Kotlin's `use` block.
 *
 * @param routerPtr The raw pointer to the native Rust object.
 */
class FipsRouter internal constructor(private var routerPtr: Long) : Closeable {

    private val isDestroyed: Boolean
        get() = routerPtr == 0L

    /**
     * Creates the special "FIPS Invitation" message payload.
     *
     * @return The invitation payload as a byte array.
     * @throws IOException if the native call fails or the router has been destroyed.
     */
    fun createFipsInvitation(): ByteArray {
        if (isDestroyed) {
            throw IOException("FipsRouter has already been closed.")
        }
        return FipsBridge.createFipsInvitation(routerPtr)
            ?: throw IOException("Native call to createFipsInvitation failed.")
    }

    /**
     * Releases the native resources associated with this router instance.
     * This method is idempotent; calling it multiple times has no effect.
     */
    override fun close() {
        if (!isDestroyed) {
            Log.d("FipsBridge", "Closing FipsRouter and destroying native instance with pointer: $routerPtr")
            FipsBridge.destroyRouter(routerPtr)
            routerPtr = 0L // Mark as destroyed to prevent further use.
        }
    }
}
