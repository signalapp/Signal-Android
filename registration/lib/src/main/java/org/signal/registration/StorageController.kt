/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import org.signal.core.models.AccountEntropyPool
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ServiceId
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

  /**
   * Called after a successful registration to store new registration data.
   */
  suspend fun saveNewRegistrationData(newRegistrationData: NewRegistrationData)

  /**
   * Retrieves previously stored registration data for registered installs, if any.
   *
   * @return Data for the existing registration if registered, otherwise null.
   */
  suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData?
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

data class NewRegistrationData(
  val e164: String,
  val aci: ServiceId.Aci,
  val pni: ServiceId.Pni,
  val servicePassword: String,
  val aep: AccountEntropyPool
)

data class PreExistingRegistrationData(
  val e164: String,
  val aci: ServiceId.Aci,
  val pni: ServiceId.Pni,
  val servicePassword: String,
  val aep: AccountEntropyPool
)
