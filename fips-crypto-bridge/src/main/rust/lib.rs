// DEPRECATED — see proposals/fips-discovery-2026-04-17.md

//! FIPS Signal: JNI Bridge & Cryptographic Abstraction Layer (Client-Only Model)
//!
//! This module serves as the primary interface between the Signal-Android (Kotlin/Java)
//! application and the underlying Rust cryptographic logic in `libsignal`. It implements
//! the core "Cryptographic Abstraction Layer" (CAL) which intelligently routes
//! cryptographic operations based on the session's designated mode.
//!
//! This implementation is specifically designed for the "Client-Only" architecture,
//! supporting the "Opportunistic FIPS Handshake" protocol.

// JNI bindings for interacting with the Java Virtual Machine.
use jni::JNIEnv;
use jni::objects::{JClass, JObject};
use jni::sys::{jlong, jbyteArray, jboolean, jint};

// Standard library components.
use std::ffi::{CString, CStr};
use std::os::raw::c_char;

// Logging facade for Rust, which can be configured to output to Android's logcat.
use log::{info, warn, error, Level};

// --- FFI Declarations for the OpenSSL FIPS Provider Bridge ---
// These are the "chunky" C functions we will call into our C bridge, which in turn
// calls the OpenSSL FIPS provider.

#[link(name = "openssl_fips_bridge")]
extern "C" {
    /// Initializes the FIPS module and runs power-on self-tests.
    /// Returns 1 on success, 0 on failure.
    fn FIPS_BRIDGE_initialize() -> jint;

    /// Encrypts data using FIPS-validated AES-256-GCM.
    fn FIPS_BRIDGE_aes_gcm_encrypt(
        key: *const u8, key_len: usize,
        iv: *const u8, iv_len: usize,
        plaintext: *const u8, plaintext_len: usize,
        ciphertext: *mut u8, tag: *mut u8
    ) -> jint;
    
    /// Decrypts data using FIPS-validated AES-256-GCM.
    fn FIPS_BRIDGE_aes_gcm_decrypt(
        key: *const u8, key_len: usize,
        iv: *const u8, iv_len: usize,
        ciphertext: *const u8, ciphertext_len: usize,
        tag: *const u8, plaintext: *mut u8
    ) -> jint;

    /// Performs a key exchange using FIPS-validated ECDH on the P-256 curve.
    fn FIPS_BRIDGE_ecdh_p256(
        private_key: *const u8, public_key: *const u8,
        shared_secret: *mut u8
    ) -> jint;
}

// --- Core Data Structures for the Hybrid Model ---

/// Defines the cryptographic mode for a given Signal session.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SessionMode {
    /// Standard Signal session, using native Rust crypto (X25519, XChaCha20).
    Standard,
    /// A FIPS-compliant session has been successfully negotiated and established.
    Fips,
    /// The session is currently attempting to upgrade from Standard to FIPS.
    FipsNegotiating,
}

/// Represents the context for a cryptographic operation.
/// In a real implementation, this would be part of the SessionRecord or a similar struct.
pub struct CryptoContext {
    session_id: u64,
    mode: SessionMode,
}

/// The Cryptographic Abstraction Layer (CAL) implementation.
pub struct CryptoRouter;

impl CryptoRouter {
    pub fn new() -> Self {
        CryptoRouter
    }

    /// Creates the special "FIPS Invitation" message.
    /// This message contains the user's FIPS-specific public keys.
    pub fn create_fips_invitation(&self) -> Result<Vec<u8>, String> {
        info!("CAL: Creating FIPS Invitation message...");
        // STUB: In a real implementation, this would:
        // 1. Retrieve the user's FIPS P-256 identity and ephemeral public keys.
        // 2. Serialize them into a defined Protobuf structure.
        // 3. Prepend a "magic number" to identify it as a FIPS handshake message.
        let magic_number = b"FIPS_INV_01";
        let mut invitation = magic_number.to_vec();
        invitation.extend_from_slice(b"<-- FIPS P-256 Public Keys Placeholder -->");
        Ok(invitation)
    }

    /// Processes a potential FIPS handshake message.
    /// If it's an invitation, it generates a response.
    /// If it's a response, it finalizes the FIPS session.
    /// Returns an optional response payload or None if no response is needed.
    pub fn process_fips_handshake_message(&self, message: &[u8]) -> Option<Vec<u8>> {
        let magic_inv = b"FIPS_INV_01";
        let magic_res = b"FIPS_RES_01";

        if message.starts_with(magic_inv) {
            info!("CAL: Received a FIPS Invitation. Generating response...");
            // STUB: In a real implementation, this would:
            // 1. Parse the sender's FIPS public keys from the message.
            // 2. Perform the FIPS ECDH key exchange.
            // 3. Derive FIPS session keys.
            // 4. Return a FIPS Response message containing this user's FIPS public keys.
            let mut response = magic_res.to_vec();
            response.extend_from_slice(b"<-- Responder FIPS P-256 Public Keys -->");
            Some(response)
        } else if message.starts_with(magic_res) {
            info!("CAL: Received a FIPS Response. Finalizing FIPS session.");
            // STUB: In a real implementation, this would:
            // 1. Parse the responder's FIPS public keys.
            // 2. Perform the FIPS ECDH key exchange.
            // 3. Derive FIPS session keys and mark the session as fully FIPS.
            None // No further message needs to be sent.
        } else {
            None // Not a FIPS handshake message.
        }
    }

    /// Encrypts a message payload based on the session's crypto context.
    pub fn encrypt(&self, context: &CryptoContext, key: &[u8], plaintext: &[u8]) -> Result<Vec<u8>, String> {
        info!("CAL: Encrypting for session {}, mode: {:?}", context.session_id, context.mode);
        match context.mode {
            SessionMode::Standard | SessionMode::FipsNegotiating => {
                // During negotiation, we still use the standard channel for transport.
                self.native_signal_encrypt(key, plaintext)
            }
            SessionMode::Fips => {
                // Once negotiated, we switch to the FIPS module.
                self.fips_module_encrypt(key, plaintext)
            }
        }
    }

    /// A placeholder for the native Signal encryption logic (XChaCha20-Poly1305).
    fn native_signal_encrypt(&self, _key: &[u8], plaintext: &[u8]) -> Result<Vec<u8>, String> {
        info!("Routing to NATIVE Signal encryption (XChaCha20-Poly1305 stub)");
        // STUB: Simply reverses the plaintext for demonstration.
        let mut ciphertext = plaintext.to_vec();
        ciphertext.reverse();
        Ok(ciphertext)
    }

    /// Calls the FIPS module to perform AES-256-GCM encryption.
    fn fips_module_encrypt(&self, key: &[u8], plaintext: &[u8]) -> Result<Vec<u8>, String> {
        info!("Routing to FIPS module encryption (AES-256-GCM)");
        // STUB: A real implementation would need to handle IV generation.
        let iv = [0u8; 12];
        let mut ciphertext = vec![0u8; plaintext.len()];
        let mut tag = vec![0u8; 16];

        let result = unsafe {
            FIPS_BRIDGE_aes_gcm_encrypt(
                key.as_ptr(), key.len(),
                iv.as_ptr(), iv.len(),
                plaintext.as_ptr(), plaintext.len(),
                ciphertext.as_mut_ptr(),
                tag.as_mut_ptr(),
            )
        };

        if result == 1 {
            ciphertext.extend_from_slice(&tag);
            Ok(ciphertext)
        } else {
            error!("FIPS_BRIDGE_aes_gcm_encrypt failed");
            Err("FIPS module encryption failed".to_string())
        }
    }
}

// --- JNI Entry Points ---
// These are the functions that the Kotlin/Java code will call.

#[no_mangle]
pub extern "system" fn Java_org_thoughtcrime_securesms_crypto_FipsBridge_initialize(
    mut _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    android_logger::init_once(android_logger::Config::default().with_min_level(Level::Info));
    info!("JNI: Initializing FIPS Bridge...");
    let result = unsafe { FIPS_BRIDGE_initialize() };
    (result == 1).into()
}

#[no_mangle]
pub extern "system" fn Java_org_thoughtcrime_securesms_crypto_FipsBridge_createRouter(
    mut _env: JNIEnv,
    _class: JClass,
) -> jlong {
    info!("JNI: Creating CryptoRouter instance.");
    Box::into_raw(Box::new(CryptoRouter::new())) as jlong
}

#[no_mangle]
pub extern "system" fn Java_org_thoughtcrime_securesms_crypto_FipsBridge_destroyRouter(
    mut _env: JNIEnv,
    _class: JClass,
    router_ptr: jlong,
) {
    if router_ptr != 0 {
        let _ = unsafe { Box::from_raw(router_ptr as *mut CryptoRouter) };
        info!("JNI: CryptoRouter instance destroyed.");
    }
}

#[no_mangle]
pub extern "system" fn Java_org_thoughtcrime_securesms_crypto_FipsBridge_createFipsInvitation(
    mut env: JNIEnv,
    _class: JClass,
    router_ptr: jlong,
) -> jbyteArray {
    let router = unsafe { &*(router_ptr as *const CryptoRouter) };
    match router.create_fips_invitation() {
        Ok(invitation) => env.byte_array_from_slice(&invitation).unwrap_or(JObject::null().into_inner()),
        Err(e) => {
            error!("Failed to create FIPS invitation: {}", e);
            JObject::null().into_inner()
        }
    }
}
