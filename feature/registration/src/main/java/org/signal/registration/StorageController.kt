/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
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

  /**
   * Saves a validated PIN, temporary master key, and registration lock status.
   *
   * Called after successfully verifying a PIN against SVR, either during
   * registration lock unlock or SVR restore flows.
   *
   * It's a "temporary master key" because at the end of the day, what we actually want is a master key derived from the AEP.
   * We may need this master key to perform the initial storage service restore, but after that's done, it will be discarded after generating a new AEP.
   *
   * @param pin The validated PIN that was successfully verified.
   * @param registrationLockEnabled Whether registration lock should be enabled for this account.
   */
  suspend fun saveValidatedPinAndTemporaryMasterKey(pin: String, isAlphanumeric: Boolean, masterKey: MasterKey, registrationLockEnabled: Boolean)

  /**
   * Saves a newly-created PIN for the account.
   */
  suspend fun saveNewlyCreatedPin(pin: String, isAlphanumeric: Boolean)

  /**
   * Clears all stored registration data, including key material and account information.
   */
  suspend fun clearAllData()
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
  val servicePassword: String,
  /** Account entropy pool for key derivation. */
  val accountEntropyPool: AccountEntropyPool
)

data class NewRegistrationData(
  val e164: String,
  val aci: ACI,
  val pni: PNI,
  val servicePassword: String,
  val aep: AccountEntropyPool
)

data class PreExistingRegistrationData(
  val e164: String,
  val aci: ACI,
  val pni: PNI,
  val servicePassword: String,
  val aep: AccountEntropyPool
)
