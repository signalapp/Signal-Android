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
import org.signal.registration.screens.messagesync.LinkAndSyncProgress
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
   * Wipes **all** local app data and attempts to relaunch the app into a fresh state. Used when the primary
   * asks a freshly-linked device to re-link.
   */
  suspend fun clearLocalDataAndRestart()

  /**
   * Reads the persisted [RegistrationData] proto that is currently in the process of being worked on.
   * Returns a default empty [RegistrationData] if nothing has been written yet.
   */
  suspend fun readInProgressRegistrationData(): RegistrationData

  /**
   * Reads the persisted [RegistrationData] (that is currently in the process of being worked on),
   * applies the [updater] to its builder, and writes the result back to persistent storage.
   *
   * Note that [RegistrationData.accountData] must never be modified once [RegistrationData.accountDataCommitted] is
   * true -- it describes the account that was registered, and [commitRegistrationData] will not apply it again.
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
   *
   * The one-time [RegistrationData.accountData] is applied exactly once, on the first commit where it is complete;
   * it is frozen from then on (tracked via [RegistrationData.accountDataCommitted]). All other fields are mutable
   * state that is (re-)applied on every commit.
   */
  suspend fun commitRegistrationData()

  /**
   * Persists the terminal [RestoreDecision] the user reached during registration directly to permanent app state,
   * so the rest of the app knows whether we're a fresh account, skipped a restore, or successfully restored data.
   */
  suspend fun setRestoreDecision(decision: RestoreDecision)

  /**
   * Begins restoring from a V1 (.backup) file identified by the given [backupUri].
   *
   * @param rootUri The backup directory that contains the [backupUri] file. Persisted as the backup directory so
   *   local backups can be re-enabled after the restore.
   * @param backupUri The specific .backup file to restore from.
   * @return A [Flow] of [LocalBackupRestoreProgress] that reports the state of the restore operation
   *   from preparation through completion or error.
   */
  fun restoreLocalBackupV1(rootUri: Uri, backupUri: Uri, passphrase: String): Flow<LocalBackupRestoreProgress>

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
   * Verifies that [aep] can decrypt the V2 (folder-based) backup at [backupUri], without restoring anything.
   * Used to distinguish a mistyped recovery key from a key that belongs to a different account before
   * attempting recovery-password registration.
   */
  suspend fun verifyLocalBackupKey(backupUri: Uri, aep: AccountEntropyPool): Boolean

  /**
   * Begins restoring from a remote (server-hosted) backup.
   *
   * @param aep The Account Entropy Pool used to derive backup keys.
   * @return A [Flow] of [RemoteBackupRestoreProgress] that reports the state of the restore
   *   from download through import, completion, or error.
   */
  fun restoreRemoteBackup(aep: AccountEntropyPool): Flow<RemoteBackupRestoreProgress>

  /**
   * Downloads and imports the link-and-sync message backup from the given CDN location ([cdn]/[key]). The ephemeral
   * backup key needed to decrypt the backup is read from the locally persisted registration metadata committed
   * during registration.
   *
   * @return A [Flow] of [LinkAndSyncProgress] reporting progress through completion or error.
   */
  fun restoreLinkAndSyncBackup(cdn: Int, key: String): Flow<LinkAndSyncProgress>

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

  /**
   * Reads any profile data already on disk for the locally-registered account. May return data when
   * the user is re-registering (the previous profile name/avatar are still on the device) or after a
   * storage-service account record restore has populated them.
   *
   * Returned fields are individually populated — any subset may be empty/null. The caller decides
   * what to do with partial data (typically: pre-seed the create-profile form, or skip the screen
   * altogether if everything is already present).
   */
  suspend fun getStoredProfileData(): StoredProfileData
}

/**
 * Snapshot of profile data already present on the device — used to pre-seed (or auto-skip) the
 * create-profile screen during registration.
 *
 * [discoverableByPhoneNumber] is null when the device has no opinion yet (UNDECIDED on Android), in
 * which case callers should default to discoverable.
 */
data class StoredProfileData(
  val givenName: String = "",
  val familyName: String = "",
  val avatar: ByteArray? = null,
  val discoverableByPhoneNumber: Boolean? = null
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is StoredProfileData) return false
    if (givenName != other.givenName) return false
    if (familyName != other.familyName) return false
    if (avatar != null) {
      if (other.avatar == null) return false
      if (!avatar.contentEquals(other.avatar)) return false
    } else if (other.avatar != null) {
      return false
    }
    if (discoverableByPhoneNumber != other.discoverableByPhoneNumber) return false
    return true
  }

  override fun hashCode(): Int {
    var result = givenName.hashCode()
    result = 31 * result + familyName.hashCode()
    result = 31 * result + (avatar?.contentHashCode() ?: 0)
    result = 31 * result + (discoverableByPhoneNumber?.hashCode() ?: 0)
    return result
  }
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
  val unrestrictedUnidentifiedAccess: Boolean,
  val aciIdentityKeyPair: IdentityKeyPair,
  val pniIdentityKeyPair: IdentityKeyPair
) : Parcelable
