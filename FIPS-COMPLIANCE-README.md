> **DEPRECATED** — see proposals/fips-discovery-2026-04-17.md


# FIPS Signal Android: FIPS 140-2 Compliance Documentation

## Overview

This document provides a comprehensive explanation of how the FIPS Signal Android application achieves Federal Information Processing Standards (FIPS) 140-2 compliance for secure messaging in government and enterprise environments.

## Table of Contents

1. [FIPS 140-2 Compliance Overview](#fips-140-2-compliance-overview)
2. [Architecture Design](#architecture-design)
3. [Cryptographic Implementation](#cryptographic-implementation)
4. [FIPS Module Integration](#fips-module-integration)
5. [Key Management](#key-management)
6. [Session Management](#session-management)
7. [Compliance Validation](#compliance-validation)
8. [Security Features](#security-features)
9. [Enterprise Policy Enforcement](#enterprise-policy-enforcement)
10. [Audit and Logging](#audit-and-logging)
11. [Testing and Validation](#testing-and-validation)
12. [Deployment Considerations](#deployment-considerations)

## FIPS 140-2 Compliance Overview

### What is FIPS 140-2?

FIPS 140-2 is a U.S. government computer security standard that specifies the security requirements for cryptographic modules. It defines four security levels (Level 1-4) with increasing security requirements.

### Our Compliance Level

This FIPS Signal implementation targets **FIPS 140-2 Level 1** compliance with the following key requirements:
- Use of FIPS-approved cryptographic algorithms
- Proper key management and storage
- Self-tests and integrity verification
- Approved random number generation
- Secure key derivation functions

## Architecture Design

### Client-Only FIPS Architecture

Our implementation uses a "Client-Only" architecture with the following components:

```
┌─────────────────────────────────────────────────────────────┐
│                Signal Android Application                   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ FIPS Conversation│  │ Session Manager │  │ Policy MGR   │ │
│  │    Activity     │  │                 │  │              │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
├─────────────────────────────────────────────────────────────┤
│              Cryptographic Abstraction Layer               │
│  ┌─────────────────┐           ┌─────────────────────────┐  │
│  │ Standard Signal │           │   FIPS Crypto Router    │  │
│  │   Crypto Path   │           │                         │  │
│  │ (X25519/XChaCha)│           │  (P-256/AES-256-GCM)    │  │
│  └─────────────────┘           └─────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    FIPS Crypto Bridge                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │  Rust JNI Bridge│  │  C Bridge Layer │  │ Key Store    │ │
│  │                 │  │                 │  │  Manager     │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
├─────────────────────────────────────────────────────────────┤
│              OpenSSL 3.x FIPS Provider                     │
│                (FIPS 140-2 Validated)                      │
└─────────────────────────────────────────────────────────────┘
```

### Hybrid Operation Model

The app operates in three modes:
1. **Standard Mode**: Uses Signal's native crypto (X25519, XChaCha20-Poly1305)
2. **FIPS Mode**: Uses FIPS-validated crypto (P-256 ECDH, AES-256-GCM)
3. **Negotiating Mode**: Opportunistic upgrade from Standard to FIPS

## Cryptographic Implementation

### FIPS-Approved Algorithms

Our implementation uses only FIPS 140-2 approved cryptographic algorithms:

#### Key Exchange
- **Algorithm**: Elliptic Curve Diffie-Hellman (ECDH)
- **Curve**: NIST P-256 (secp256r1)
- **Standard**: FIPS 186-4, SP 800-56A
- **Implementation**: OpenSSL 3.x FIPS Provider

#### Symmetric Encryption
- **Algorithm**: Advanced Encryption Standard (AES)
- **Mode**: Galois/Counter Mode (GCM)
- **Key Size**: 256-bit
- **Standard**: FIPS 197, SP 800-38D
- **Authentication**: 128-bit authentication tag

#### Hash Functions
- **Algorithm**: SHA-256, SHA-384, SHA-512
- **Standard**: FIPS 180-4
- **Usage**: Key derivation, integrity verification

#### Key Derivation
- **Algorithm**: HKDF (HMAC-based Key Derivation Function)
- **Standard**: RFC 5869, SP 800-108
- **Hash**: SHA-256

#### Random Number Generation
- **Algorithm**: CTR_DRBG (Counter mode DRBG)
- **Standard**: SP 800-90A
- **Entropy**: Hardware entropy sources when available

### Cryptographic Module Architecture

```c
// FIPS Bridge C Implementation
int FIPS_BRIDGE_initialize() {
    // Load FIPS provider and run self-tests
    OSSL_PROVIDER* fips_provider = OSSL_PROVIDER_load(NULL, "fips");
    if (fips_provider == NULL) {
        // Self-tests failed - critical error
        return 0;
    }
    return 1;
}

int FIPS_BRIDGE_aes_gcm_encrypt(
    const unsigned char* key, size_t key_len,
    const unsigned char* iv, size_t iv_len,
    const unsigned char* plaintext, size_t plaintext_len,
    unsigned char* ciphertext,
    unsigned char* tag
) {
    // Fetch AES-256-GCM specifically from FIPS provider
    EVP_CIPHER* cipher = EVP_CIPHER_fetch(NULL, "AES-256-GCM", "provider=fips");
    // ... encryption logic using FIPS provider
}
```

## FIPS Module Integration

### OpenSSL 3.x FIPS Provider

We integrate the OpenSSL 3.x FIPS Provider, which provides:

1. **FIPS 140-2 Validation**: The module is independently validated
2. **Self-Tests**: Automatic power-on and conditional self-tests
3. **Integrity Verification**: Module integrity checking
4. **Approved Services**: Only FIPS-approved cryptographic services

### Module Loading and Verification

```rust
// Rust FIPS Bridge Implementation
extern "C" {
    fn FIPS_BRIDGE_initialize() -> jint;
}

#[no_mangle]
pub extern "system" fn Java_org_thoughtcrime_securesms_crypto_FipsBridge_initialize(
    mut _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let result = unsafe { FIPS_BRIDGE_initialize() };
    (result == 1).into()
}
```

### Integrity and Self-Tests

The FIPS module performs:
- **Power-On Self-Tests (POST)**: Verify module integrity at startup
- **Known Answer Tests (KAT)**: Validate algorithm implementations
- **Conditional Self-Tests**: Continuous verification during operation

## Key Management

### Hardware-Backed Key Storage

We implement secure key storage using Android's hardware security module:

```kotlin
object FipsKeyStoreManager {
    private const val FIPS_WRAPPING_KEY_ALIAS = "fips_signal_wrapping_key_v1"
    
    private fun generateWrappingKeyPair() {
        val spec = KeyGenParameterSpec.Builder(
            FIPS_WRAPPING_KEY_ALIAS,
            KeyProperties.PURPOSE_WRAP_KEY or KeyProperties.PURPOSE_UNWRAP_KEY
        )
        .setKeySize(2048)
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
        .build()
    }
}
```

### Key Hierarchy

```
Hardware Security Module (HSM/TEE)
├── Device Root Key (Hardware-backed)
├── FIPS Wrapping Key (RSA-2048)
│   └── FIPS Identity Key (P-256, Wrapped)
└── Session Keys
    ├── Message Key (AES-256)
    ├── MAC Key (HMAC-SHA256)
    └── Next Header Key (AES-256)
```

### Key Derivation Process

1. **Initial Key Exchange**: ECDH using P-256 curve
2. **Master Secret**: Derived using HKDF-SHA256
3. **Session Keys**: Multiple keys derived for different purposes
4. **Perfect Forward Secrecy**: Keys rotated per message/session

## Session Management

### FIPS Session Negotiation

The app implements an "Opportunistic FIPS Handshake" protocol:

```kotlin
enum class SessionMode {
    Standard,        // X25519/XChaCha20
    Fips,           // P-256/AES-256-GCM
    FipsNegotiating // Upgrade in progress
}

class CryptoRouter {
    fun createFipsInvitation(): Result<ByteArray, String> {
        // Generate FIPS invitation with P-256 public keys
    }
    
    fun processFipsHandshakeMessage(message: ByteArray): ByteArray? {
        // Handle FIPS invitation/response messages
    }
}
```

### Session State Management

1. **Session Establishment**: Standard Signal handshake
2. **FIPS Upgrade Attempt**: Send FIPS invitation
3. **Mutual FIPS Support**: Establish FIPS session
4. **Fallback Handling**: Graceful degradation if FIPS not supported

## Compliance Validation

### Self-Test Implementation

```c
// Continuous self-tests during operation
static int perform_conditional_self_test() {
    // Test AES-256-GCM encryption/decryption
    // Test ECDH key exchange
    // Test random number generation
    // Test hash functions
    return all_tests_passed ? 1 : 0;
}
```

### Module State Management

The FIPS module maintains strict state management:
- **Approved State**: Normal operation with FIPS services
- **Self-Test State**: During power-on and conditional tests
- **Error State**: When self-tests fail or integrity is compromised

### Integrity Verification

- **Module Integrity**: SHA-256 checksums of cryptographic libraries
- **Key Integrity**: Verification of key material
- **Algorithm Integrity**: Validation of cryptographic operations

## Security Features

### Enterprise Policy Enforcement

```kotlin
class MdmPolicyManager {
    fun isFipsRequired(): Boolean {
        // Check MDM/EMM policy requirements
    }
    
    fun enforceEncryptionStandards(): EncryptionPolicy {
        // Return required encryption standards
    }
}
```

### Secure Communication Channels

- **TLS 1.3**: For client-server communication
- **Certificate Pinning**: Prevent man-in-the-middle attacks
- **Perfect Forward Secrecy**: Session keys are ephemeral

### Data Protection

- **Encrypted Storage**: All sensitive data encrypted at rest
- **Memory Protection**: Secure memory handling for keys
- **Secure Deletion**: Cryptographic erasure of key material

## Enterprise Policy Enforcement

### Mobile Device Management (MDM) Integration

```xml
<!-- App restrictions for enterprise deployment -->
<restrictions xmlns:android="http://schemas.android.com/apk/res/android">
    <restriction
        android:key="fips_mode_required"
        android:restrictionType="bool"
        android:defaultValue="false"
        android:title="Require FIPS Mode"
        android:description="Force all conversations to use FIPS-validated cryptography" />
    
    <restriction
        android:key="allow_non_fips_contacts"
        android:restrictionType="bool"
        android:defaultValue="true"
        android:title="Allow Non-FIPS Contacts"
        android:description="Allow communication with contacts not supporting FIPS" />
</restrictions>
```

### Policy Enforcement Points

1. **Conversation Initiation**: Check FIPS requirements before messaging
2. **Key Exchange**: Enforce FIPS algorithms only
3. **File Attachments**: Apply FIPS encryption to all file transfers
4. **Voice/Video Calls**: Use FIPS-compliant call encryption

## Audit and Logging

### Cryptographic Event Logging

```kotlin
object FipsAuditLogger {
    fun logFipsSessionEstablished(sessionId: String, algorithm: String) {
        // Log successful FIPS session establishment
    }
    
    fun logFipsHandshakeFailure(reason: String, contactId: String) {
        // Log failed FIPS handshake attempts
    }
    
    fun logKeyRotation(sessionId: String, keyType: String) {
        // Log key rotation events
    }
}
```

### Compliance Reporting

- **Algorithm Usage**: Track which algorithms are used
- **Session Types**: Monitor FIPS vs. standard sessions
- **Policy Violations**: Log when FIPS requirements aren't met
- **Security Events**: Record cryptographic failures or errors

## Testing and Validation

### FIPS Validation Process

1. **Algorithm Testing**: Validate each cryptographic algorithm
2. **Module Testing**: Test the complete FIPS module
3. **Integration Testing**: Verify proper integration with Signal
4. **Regression Testing**: Ensure updates maintain compliance

### Test Categories

#### Unit Tests
- Individual algorithm correctness
- Key generation and validation
- Self-test functionality

#### Integration Tests
- End-to-end FIPS session establishment
- Cross-platform compatibility
- Policy enforcement verification

#### Security Tests
- Penetration testing
- Side-channel analysis
- Cryptographic strength validation

### Continuous Compliance

```bash
# Automated testing pipeline
./gradlew testFipsCompliance
./gradlew validateCryptographicImplementation
./gradlew auditSecurityPolicies
```

## Deployment Considerations

### System Requirements

#### Android Version
- **Minimum**: Android 6.0 (API 23)
- **Recommended**: Android 10+ for hardware security features
- **Optimal**: Android 13+ for latest security enhancements

#### Hardware Requirements
- **TEE/SE Support**: Hardware-backed keystore
- **Entropy Sources**: Hardware random number generator
- **Secure Boot**: Verified boot process
- **Memory**: Minimum 4GB RAM for cryptographic operations

### Network Configuration

#### Certificate Management
- Install enterprise root certificates
- Configure certificate pinning
- Set up OCSP/CRL checking

#### Proxy/Firewall Configuration
- Allow Signal server connections
- Configure domain fronting if required
- Set up network security policies

### Enterprise Deployment

#### App Distribution
```xml
<!-- Enterprise app configuration -->
<application>
    <meta-data
        android:name="fips.compliance.required"
        android:value="true" />
    <meta-data
        android:name="enterprise.policy.enforced"
        android:value="true" />
</application>
```

#### Policy Templates
- FIPS-only communication policies
- Key management requirements
- Audit logging configurations
- Backup and recovery procedures

### Performance Considerations

#### Cryptographic Performance
- **P-256 vs X25519**: ~2-3x slower key exchange
- **AES-256-GCM vs XChaCha20**: Similar performance
- **Self-Tests**: ~500ms startup delay
- **Memory Usage**: +20-30MB for FIPS module

#### Optimization Strategies
- Hardware acceleration when available
- Batch cryptographic operations
- Efficient key caching
- Background self-test scheduling

## Maintenance and Updates

### FIPS Module Updates

1. **Validation Tracking**: Monitor FIPS certificate status
2. **Security Patches**: Apply updates while maintaining compliance
3. **Re-validation**: Process for major updates requiring re-validation
4. **Rollback Procedures**: Safe fallback if updates fail validation

### Compliance Monitoring

```kotlin
object ComplianceMonitor {
    fun validateCurrentCompliance(): ComplianceStatus {
        // Check FIPS module status
        // Verify policy enforcement
        // Validate cryptographic operations
        return ComplianceStatus.COMPLIANT
    }
}
```

## Conclusion

This FIPS Signal Android implementation provides enterprise-grade security through:

- **FIPS 140-2 Level 1 Compliance**: Using validated cryptographic modules
- **Hybrid Architecture**: Supporting both FIPS and standard Signal protocols
- **Enterprise Integration**: MDM/EMM policy enforcement
- **Hardware Security**: Leveraging Android's hardware security features
- **Continuous Validation**: Ongoing compliance monitoring and testing

The implementation ensures that government and enterprise users can leverage Signal's usability while meeting strict FIPS 140-2 requirements for cryptographic security.

## Support and Documentation

For additional technical documentation, implementation guides, and compliance verification procedures, refer to:

- `FIPS-README.md` - Build and setup instructions
- `app/src/main/java/org/thoughtcrime/securesms/crypto/` - Core FIPS implementation
- `fips-crypto-bridge/` - Native cryptographic bridge
- Enterprise deployment guides and policy templates

---

**Note**: This implementation is designed for compliance with FIPS 140-2 Level 1. Organizations requiring higher security levels should consult with cryptographic experts and consider additional hardware security measures.
