/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.net.Uri
import android.os.Parcelable
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.registration.proto.RegistrationData
import org.signal.registration.screens.localbackuprestore.LocalBackupInfo
import org.signal.registration.screens.remotebackuprestore.RemoteBackupRestoreProgress
import org.signal.registration.util.ACIParceler
import org.signal.registration.util.AccountEntropyPoolParceler
import org.signal.registration.util.IdentityKeyPairParceler
import org.signal.registration.util.KyberPreKeyRecordParceler
import org.signal.registration.util.PNIParceler
import org.signal.registration.util.SignedPreKeyRecordParceler

/**
 * The set of methods that the registration module needs to persist data to disk.
 *
 * Note that most data is stored via "in progress registration data", which gives the registration module
 * a lot of control over what data is saved, with the app just needing to persist the blob.
 *
 * It's referred to as "in progress" because it represents state that the registration module wants to persist
 * in case the process were to die, but it's not fully ready to be committed as permanent app state yet.
 *
 * For example, the module may create a bunch of keys, but until the user is registered and those keys are uploaded,
 * they should not be considered the actual keys for the current account.
 *
 * When the data *is* ready to be committed, it will be done via [commitRegistrationData].
 */
interface StorageController {

  /**
   * Retrieves previously stored registration data for registered installs, if any.
   *
   * @return Data for the existing registration if registered, otherwise null.
   */
  suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData?

  /**
   * Clears all stored registration data, including key material and account information.
   */
  suspend fun clearAllData()

  /**
   * Reads the persisted [RegistrationData] proto that is currently in the process of being worked on.
   * Returns a default empty [RegistrationData] if nothing has been written yet.
   */
  suspend fun readInProgressRegistrationData(): RegistrationData

  /**
   * Reads the persisted [RegistrationData] (that is currently in the process of being worked on),
   * applies the [updater] to its builder, and writes the result back to persistent storage.
   *
   * Example usage:
   * ```
   * storageController.updateRegistrationData {
   *   pin = "1234"
   *   pinIsAlphanumeric = false
   * }
   * ```
   */
  suspend fun updateInProgressRegistrationData(updater: RegistrationData.Builder.() -> Unit)

  /**
   * Commits in-progress [RegistrationData] to permanent storage. Any data in the blob should be considered actual data
   * for the currently-registered account. Commits can happen multiple times. For instance, we will commit data right after
   * successfully registering, but then there may be more operations we perform after registration that need to be
   * separately committed.
   */
  suspend fun commitRegistrationData()

  /**
   * Begins restoring from a V1 (.backup) file identified by the given [uri].
   *
   * Returns a [Flow] of [LocalBackupRestoreProgress] that reports the state of the restore operation
   * from preparation through completion or error.
   */
  fun restoreLocalBackupV1(uri: Uri, passphrase: String): Flow<LocalBackupRestoreProgress>

  /**
   * Begins restoring from a V2 (folder-based) backup.
   *
   * @param rootUri The root backup directory that contains shared files used across multiple backups.
   * @param backupUri The specific backup folder (e.g. signal-backup-yyyy-MM-dd-HH-mm-ss) to restore from.
   * @param aep The Account Entropy Pool used to decrypt the backup.
   * @return A [Flow] of [LocalBackupRestoreProgress] that reports the state of the restore operation
   *   from preparation through completion or error.
   */
  fun restoreLocalBackupV2(rootUri: Uri, backupUri: Uri, aep: AccountEntropyPool): Flow<LocalBackupRestoreProgress>

  /**
   * Begins restoring from a remote (server-hosted) backup.
   *
   * @param aep The Account Entropy Pool used to derive backup keys.
   * @return A [Flow] of [RemoteBackupRestoreProgress] that reports the state of the restore
   *   from download through import, completion, or error.
   */
  fun restoreRemoteBackup(aep: AccountEntropyPool): Flow<RemoteBackupRestoreProgress>

  /**
   * Scans the given folder URI for local backup files, checking for both modern
   * folder-based backups and legacy .backup files.
   *
   * If the folder contains a "SignalBackups" subdirectory, that directory is used
   * as the effective scan target.
   *
   * @return A list of [LocalBackupInfo] sorted by date descending (most recent first).
   */
  suspend fun scanLocalBackupFolder(folderUri: Uri): List<LocalBackupInfo>
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
  /** Profile key for sealed sender. */
  val profileKey: ByteArray,
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
