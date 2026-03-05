/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.registration.util.ACIParceler
import org.signal.registration.util.AccountEntropyPoolParceler
import org.signal.registration.util.IdentityKeyPairParceler
import org.signal.registration.util.KyberPreKeyRecordParceler
import org.signal.registration.util.PNIParceler
import org.signal.registration.util.SignedPreKeyRecordParceler

interface StorageController {

  /**
   * Generates all key material required for account registration and stores it persistently.
   * This includes ACI identity key, PNI identity key, and their respective pre-keys.
   *
   * If optional parameters are provided (e.g. from a pre-existing registration), those values
   * will be re-used instead of generating new ones.
   *
   * @param existingAccountEntropyPool If non-null, re-use this AEP instead of generating a new one.
   * @param existingAciIdentityKeyPair If non-null, re-use this ACI identity key pair instead of generating a new one.
   * @param existingPniIdentityKeyPair If non-null, re-use this PNI identity key pair instead of generating a new one.
   * @return [KeyMaterial] containing all generated cryptographic material needed for registration.
   */
  suspend fun generateAndStoreKeyMaterial(
    existingAccountEntropyPool: AccountEntropyPool? = null,
    existingAciIdentityKeyPair: IdentityKeyPair? = null,
    existingPniIdentityKeyPair: IdentityKeyPair? = null
  ): KeyMaterial

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
   * Retrieves any SVR2 credentials that may have been restored via the OS-level backup/restore service. May be empty.
   */
  suspend fun getRestoredSvrCredentials(): List<NetworkController.SvrCredentials>

  // TODO [regV5] Can this just take a single item?
  /**
   * Appends known-working SVR credentials to the local store of credentials.
   * Implementations should limit the number of stored credentials to some reasonable maximum.
   */
  suspend fun appendSvrCredentials(credentials: List<NetworkController.SvrCredentials>)

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
@Parcelize
@TypeParceler<IdentityKeyPair, IdentityKeyPairParceler>
@TypeParceler<SignedPreKeyRecord, SignedPreKeyRecordParceler>
@TypeParceler<KyberPreKeyRecord, KyberPreKeyRecordParceler>
@TypeParceler<AccountEntropyPool, AccountEntropyPoolParceler>
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
) : Parcelable

data class NewRegistrationData(
  val e164: String,
  val aci: ACI,
  val pni: PNI,
  val servicePassword: String,
  val aep: AccountEntropyPool
)

@Parcelize
@TypeParceler<AccountEntropyPool, AccountEntropyPoolParceler>
@TypeParceler<ACI, ACIParceler>
@TypeParceler<PNI, PNIParceler>
@TypeParceler<IdentityKeyPair, IdentityKeyPairParceler>
data class PreExistingRegistrationData(
  val e164: String,
  val aci: ACI,
  val pni: PNI,
  val servicePassword: String,
  val aep: AccountEntropyPool,
  val registrationLockEnabled: Boolean,
  val aciIdentityKeyPair: IdentityKeyPair,
  val pniIdentityKeyPair: IdentityKeyPair
) : Parcelable
