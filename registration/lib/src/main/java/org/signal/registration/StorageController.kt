/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

interface StorageController {

  /**
   * Generates all key material required for account registration and stores it persistently.
   * This includes ACI identity key, PNI identity key, and their respective pre-keys.
   *
   * @return [KeyMaterial] containing all generated cryptographic material needed for registration.
   */
  suspend fun generateAndStoreKeyMaterial(): KeyMaterial
}

/**
 * Container for all cryptographic key material generated during registration.
 */
data class KeyMaterial(
  /** Identity key pair for the Account Identity (ACI). */
  val aciIdentityKeyPair: IdentityKeyPair,
  /** Signed pre-key for ACI. */
  val aciSignedPreKey: SignedPreKeyRecord,
  /** Last resort Kyber pre-key for ACI. */
  val aciLastResortKyberPreKey: KyberPreKeyRecord,
  /** Identity key pair for the Phone Number Identity (PNI). */
  val pniIdentityKeyPair: IdentityKeyPair,
  /** Signed pre-key for PNI. */
  val pniSignedPreKey: SignedPreKeyRecord,
  /** Last resort Kyber pre-key for PNI. */
  val pniLastResortKyberPreKey: KyberPreKeyRecord,
  /** Registration ID for the ACI. */
  val aciRegistrationId: Int,
  /** Registration ID for the PNI. */
  val pniRegistrationId: Int,
  /** Unidentified access key (derived from profile key) for sealed sender. */
  val unidentifiedAccessKey: ByteArray,
  /** Password for basic auth during registration (18 random bytes, base64 encoded). */
  val servicePassword: String
)
