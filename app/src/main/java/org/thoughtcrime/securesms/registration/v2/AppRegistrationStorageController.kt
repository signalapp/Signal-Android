/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.google.common.io.CountingInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.AppUtil
import org.signal.core.util.Result
import org.signal.core.util.StreamUtil
import org.signal.core.util.crypto.AttachmentSecretProvider
import org.signal.core.util.getLength
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.RestoreDecision
import org.signal.registration.StorageController
import org.signal.registration.StoredProfileData
import org.signal.registration.proto.AccountData
import org.signal.registration.proto.RegistrationData
import org.signal.registration.screens.localbackuprestore.LocalBackupInfo
import org.signal.registration.screens.messagesync.LinkAndSyncProgress
import org.signal.registration.screens.remotebackuprestore.RemoteBackupRestoreProgress
import org.thoughtcrime.securesms.backup.BackupEvent
import org.thoughtcrime.securesms.backup.BackupPassphrase
import org.thoughtcrime.securesms.backup.FullBackupImporter
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.RemoteRestoreResult
import org.thoughtcrime.securesms.backup.v2.RestoreV2Event
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.crypto.AppAttachmentSecretStore
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.crypto.SenderKeyUtil
import org.thoughtcrime.securesms.crypto.storage.PreKeyMetadataStore
import org.thoughtcrime.securesms.crypto.storage.SignalServiceAccountDataStoreImpl
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.runJobBlocking
import org.thoughtcrime.securesms.jobs.CheckKeyTransparencyJob
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob
import org.thoughtcrime.securesms.jobs.LocalBackupRestoreMediaJob
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob
import org.thoughtcrime.securesms.jobs.RotateCertificateJob
import org.thoughtcrime.securesms.keyvalue.Completed
import org.thoughtcrime.securesms.keyvalue.NewAccount
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.Skipped
import org.thoughtcrime.securesms.keyvalue.isDecisionPending
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.pin.SvrRepository
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.service.DirectoryRefreshListener
import org.thoughtcrime.securesms.service.LocalBackupListener
import org.thoughtcrime.securesms.service.RotateSignedPreKeyListener
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.link.TransferArchiveResponse
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of [StorageController] that bridges to the app's existing storage infrastructure.
 */
class AppRegistrationStorageController(private val context: Context) : StorageController {

  /**
   * Restarts the process-wide network stack after account data is applied. Overridable only so tests can avoid
   * touching the real, suite-shared network module; production must never replace it.
   */
  @VisibleForTesting
  internal var restartNetwork: () -> Unit = {
    AppDependencies.resetNetwork()
    AppDependencies.startNetwork()
  }

  companion object {
    private val TAG = Log.tag(AppRegistrationStorageController::class)
    private const val TEMP_PROTO_FILENAME = "registration-in-progress.proto"
    private val MODERN_BACKUP_PATTERN = Regex("^signal-backup-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})$")
    private val LEGACY_BACKUP_PATTERN = Regex("^signal-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})\\.backup$")
  }

  override suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? = withContext(Dispatchers.Default) {
    if (!SignalStore.account.isRegistered) {
      return@withContext null
    }

    val aci = SignalStore.account.aci ?: return@withContext null
    val pni = SignalStore.account.pni ?: return@withContext null
    val e164 = SignalStore.account.e164 ?: return@withContext null
    val servicePassword = SignalStore.account.servicePassword ?: return@withContext null
    val aep = SignalStore.account.accountEntropyPool

    val aciIdentityKeyPair = SignalStore.account.aciIdentityKey
    val pniIdentityKeyPair = SignalStore.account.pniIdentityKey

    PreExistingRegistrationData(
      e164 = e164,
      aci = aci,
      pni = pni,
      servicePassword = servicePassword,
      aep = aep,
      registrationLockEnabled = SignalStore.svr.isRegistrationLockEnabled,
      unrestrictedUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context),
      aciIdentityKeyPair = aciIdentityKeyPair,
      pniIdentityKeyPair = pniIdentityKeyPair
    )
  }

  override suspend fun clearAllData() = withContext(Dispatchers.IO) {
    SignalStore.registration.inProgressRegistrationDataBlobUri?.toUri()?.let { AppDependencies.blobs.delete(context, it) }
    SignalStore.registration.inProgressRegistrationDataBlobUri = null

    // Best-effort cleanup of the legacy plaintext file written by older builds.
    File(context.cacheDir, TEMP_PROTO_FILENAME).takeIf { it.exists() }?.delete()
    Unit
  }

  override suspend fun clearLocalDataAndRestart() = withContext(Dispatchers.Main) {
    Log.w(TAG, "[clearLocalDataAndRestart] Wiping all local app data and attempting to relaunch.")
    AppUtil.clearAllDataAndRestart(context)
  }

  override suspend fun getStoredProfileData(): StoredProfileData = withContext(Dispatchers.IO) {
    if (!SignalStore.account.isRegistered) {
      return@withContext StoredProfileData()
    }

    val self = Recipient.self()
    val profileName = self.profileName

    val avatar: ByteArray? = if (AvatarHelper.hasAvatar(context, self.id)) {
      try {
        AvatarHelper.getAvatar(context, self.id)?.use { StreamUtil.readFully(it) }
      } catch (e: IOException) {
        Log.w(TAG, "[getStoredProfileData] Failed to read self avatar.", e)
        null
      }
    } else {
      null
    }

    val discoverable: Boolean? = when (SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode) {
      PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode.DISCOVERABLE -> true
      PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE -> false
      PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode.UNDECIDED -> null
    }

    StoredProfileData(
      givenName = profileName.givenName,
      familyName = profileName.familyName,
      avatar = avatar,
      discoverableByPhoneNumber = discoverable
    )
  }

  override suspend fun readInProgressRegistrationData(): RegistrationData = withContext(Dispatchers.IO) {
    val uri = SignalStore.registration.inProgressRegistrationDataBlobUri?.toUri() ?: return@withContext RegistrationData()
    try {
      AppDependencies.blobs.getStream(context, uri).use { RegistrationData.ADAPTER.decode(it) }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to read/decode in-progress registration data, returning empty.", e)
      RegistrationData()
    }
  }

  override suspend fun updateInProgressRegistrationData(updater: RegistrationData.Builder.() -> Unit) = withContext(Dispatchers.IO) {
    val current = readInProgressRegistrationData()
    val updated = current.newBuilder().apply(updater).build()
    writeRegistrationData(updated)
  }

  override suspend fun commitRegistrationData() = withContext(Dispatchers.IO) {
    val data = readInProgressRegistrationData()
    val accountData = data.accountData

    val masterKey: MasterKey? = applyAccountEntropyPool(data)

    // We only want to apply account data a single time
    val svrStateUpdated = if (!data.accountDataCommitted && accountData != null && accountData.isComplete()) {
      applyAccountData(
        accountData = accountData,
        pin = data.pin,
        registrationLockEnabled = data.registrationLockEnabled,
        masterKey = masterKey
      )
      updateInProgressRegistrationData { accountDataCommitted = true }
      true
    } else {
      false
    }

    // Handle PIN/master key
    if (data.pin.isNotEmpty() && masterKey != null && accountData?.linkedDeviceData == null) {
      // We call this same function in applyAccountData, so just avoiding double-calls
      if (!svrStateUpdated) {
        SvrRepository.onRegistrationComplete(
          masterKey = masterKey,
          userPin = data.pin,
          hasPinToRestore = true,
          setRegistrationLockEnabled = data.registrationLockEnabled,
          restoredAEP = data.accountEntropyPool.isNotEmpty()
        )
      }
    } else if (data.pinOptedOut && accountData?.linkedDeviceData == null) {
      Log.i(TAG, "[commitRegistrationData] User opted out of creating a PIN. Applying opt-out.")
      SvrRepository.optOutOfPin(rotateAep = false)
    }

    // This must be set last, as SvrRepository.onRegistrationComplete will have  cleared the initial-restore key after recognizing the AEP-derived master key as our own.
    if (data.masterKeyForInitialDataRestore.size > 0) {
      SignalStore.svr.masterKeyForInitialDataRestore = MasterKey(data.masterKeyForInitialDataRestore.toByteArray())
    }

    RegistrationUtil.maybeMarkRegistrationComplete()
  }

  override suspend fun setRestoreDecision(decision: RestoreDecision) = withContext(Dispatchers.Default) {
    if (!SignalStore.registration.restoreDecisionState.isDecisionPending) {
      return@withContext
    }

    SignalStore.registration.restoreDecisionState = when (decision) {
      RestoreDecision.NEW_ACCOUNT -> RestoreDecisionState.NewAccount
      RestoreDecision.SKIPPED -> RestoreDecisionState.Skipped
      RestoreDecision.COMPLETED -> RestoreDecisionState.Completed
    }
  }

  override fun restoreLocalBackupV1(rootUri: Uri, backupUri: Uri, passphrase: String): Flow<LocalBackupRestoreProgress> = callbackFlow {
    Log.d(TAG, "Starting V1 local backup restore from: $backupUri")

    trySend(LocalBackupRestoreProgress.Preparing)

    // The importer only reports a running frame count with no total, so we track bytes read from the backup file against
    // its size to produce a real progress fraction, sampling the counting stream on each frame-progress event.
    val totalBytes = context.contentResolver.getLength(backupUri) ?: 0L
    var countingStream: CountingInputStream? = null

    val subscriber = object {
      @Subscribe(threadMode = ThreadMode.POSTING)
      fun onBackupEvent(event: BackupEvent) {
        if (event.type == BackupEvent.Type.PROGRESS) {
          trySend(LocalBackupRestoreProgress.InProgress(bytesRead = countingStream?.count ?: 0L, totalBytes = totalBytes))
        }
      }
    }

    EventBus.getDefault().register(subscriber)

    launch(Dispatchers.IO) {
      try {
        if (!FullBackupImporter.validatePassphrase(context, backupUri, passphrase)) {
          Log.w(TAG, "V1 restore failed: incorrect passphrase")
          trySend(LocalBackupRestoreProgress.IncorrectCredential)
          return@launch
        }

        // If this flow has already committed an account locally, keep its fresh key material out of the restore.
        val inProgressData = readInProgressRegistrationData()
        val excludeKeyTables = inProgressData.accountDataCommitted

        val database = SignalDatabase.backupDatabase
        val inputStream = context.contentResolver.openInputStream(backupUri) ?: throw IOException("Unable to open backup stream for $backupUri")
        CountingInputStream(inputStream).use { counting ->
          countingStream = counting
          FullBackupImporter.importFile(
            context,
            AttachmentSecretProvider.getInstance(context, AppAttachmentSecretStore).getOrCreateAttachmentSecret(),
            database,
            counting,
            passphrase,
            excludeKeyTables
          )
        }

        SignalDatabase.runPostBackupRestoreTasks(database)

        // The importer writes the restored key-value store straight to disk, bypassing the in-memory SignalStore cache.
        // Reset it so the state we read below reflects the restored values rather than stale pre-restore ones.
        SignalStore.onPostBackupRestore()

        // A post-registration restore clobbers parts of SignalStore.account with the backup's values -- V1 backups
        // carry the identity keys and AEP. Re-apply the frozen account data to heal the committed registration.
        val committedAccountData = inProgressData.accountData
        if (inProgressData.accountDataCommitted && committedAccountData != null) {
          Log.i(TAG, "V1 restore ran after an account was committed. Re-applying committed account data.")
          val masterKey = applyAccountEntropyPool(inProgressData)
          applyAccountData(committedAccountData, pin = inProgressData.pin, registrationLockEnabled = inProgressData.registrationLockEnabled, masterKey = masterKey)
        }

        reenableLegacyLocalBackups(rootUri, passphrase)

        trySend(readRestoredLocalBackupState(includePreRegistrationKeys = true))
        Log.d(TAG, "V1 restore complete.")
      } catch (e: FullBackupImporter.DatabaseDowngradeException) {
        Log.w(TAG, "V1 restore failed: database downgrade", e)
        trySend(LocalBackupRestoreProgress.Error(e))
      } catch (e: Exception) {
        Log.w(TAG, "V1 restore failed", e)
        trySend(LocalBackupRestoreProgress.Error(e))
      } finally {
        channel.close()
      }
    }

    awaitClose {
      EventBus.getDefault().unregister(subscriber)
    }
  }

  override suspend fun verifyLocalBackupKey(backupUri: Uri, aep: AccountEntropyPool): Boolean = withContext(Dispatchers.IO) {
    val backupDir = DocumentFile.fromTreeUri(context, backupUri)
    if (backupDir == null || !backupDir.canRead()) {
      Log.w(TAG, "[verifyLocalBackupKey] Could not open backup directory.")
      return@withContext false
    }

    LocalArchiver.canDecryptMainArchive(SnapshotFileSystem(context, backupDir), aep.deriveMessageBackupKey())
  }

  override fun restoreLocalBackupV2(rootUri: Uri, backupUri: Uri, aep: AccountEntropyPool): Flow<LocalBackupRestoreProgress> = callbackFlow {
    Log.d(TAG, "Starting V2 local backup restore from backup=$backupUri, root=$rootUri")

    trySend(LocalBackupRestoreProgress.Preparing)

    // The import posts RestoreV2Event frame-progress events as it reads through the main archive stream, tracking bytes
    // read against the stream length. Bridge those into InProgress so the UI can show a real percentage.
    val subscriber = object {
      @Subscribe(threadMode = ThreadMode.POSTING)
      fun onRestoreEvent(event: RestoreV2Event) {
        if (event.type == RestoreV2Event.Type.PROGRESS_RESTORE) {
          trySend(LocalBackupRestoreProgress.InProgress(bytesRead = event.count.inWholeBytes, totalBytes = event.estimatedTotalCount.inWholeBytes))
        }
      }
    }

    EventBus.getDefault().register(subscriber)

    launch(Dispatchers.IO) {
      try {
        val backupDir = DocumentFile.fromTreeUri(context, backupUri)
        if (backupDir == null || !backupDir.canRead()) {
          trySend(LocalBackupRestoreProgress.Error(IllegalStateException("Could not open backup directory")))
          return@launch
        }

        val selfAci = SignalStore.account.aci
        val selfPni = SignalStore.account.pni
        val selfE164 = SignalStore.account.e164

        if (selfAci == null || selfPni == null || selfE164 == null) {
          trySend(LocalBackupRestoreProgress.Error(IllegalStateException("Account not registered, cannot restore V2 backup")))
          return@launch
        }

        val selfData = BackupRepository.SelfData(selfAci, selfPni, selfE164, ProfileKeyUtil.getSelfProfileKey())
        val messageBackupKey = aep.deriveMessageBackupKey()
        val snapshotFileSystem = SnapshotFileSystem(context, backupDir)

        if (!LocalArchiver.canDecryptMainArchive(snapshotFileSystem, messageBackupKey)) {
          Log.w(TAG, "V2 restore failed: recovery key cannot decrypt backup")
          trySend(LocalBackupRestoreProgress.IncorrectCredential)
          return@launch
        }

        when (val result = LocalArchiver.import(snapshotFileSystem, selfData, messageBackupKey)) {
          is Result.Success -> {
            AppDependencies.jobManager.add(LocalBackupRestoreMediaJob.create(rootUri))

            // The entered recovery key decrypted the backup the user chose to restore, so it always becomes the
            // account's AEP -- even if the backup was made by a different account.
            SignalStore.account.restoreAccountEntropyPool(aep)
            updateInProgressRegistrationData { this.accountEntropyPool = aep.value }

            // Re-enable new-style local backups pointing at the restored location, so the user keeps getting backups.
            // Skip it if the folder is the SignalBackups directory itself, since it can't be reused as a destination.
            val archiveFileSystem = ArchiveFileSystem.openForRestore(context, rootUri)
            if (archiveFileSystem != null && !archiveFileSystem.isRootedAtSignalBackups) {
              SignalStore.backup.newLocalBackupsDirectory = rootUri.toString()
              SignalStore.backup.newLocalBackupsEnabled = true
              LocalBackupListener.setNextBackupTimeToIntervalFromNow(context)
              LocalBackupListener.schedule(context)
            } else {
              Log.w(TAG, "V2 local backup directory can't be reused as a destination; not re-enabling local backups.")
            }

            trySend(readRestoredLocalBackupState())
            Log.d(TAG, "V2 restore complete.")
          }
          is Result.Failure -> {
            Log.w(TAG, "V2 restore failed: ${result.failure}")
            trySend(LocalBackupRestoreProgress.Error(IOException("V2 restore failed: ${result.failure}")))
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "V2 restore failed", e)
        trySend(LocalBackupRestoreProgress.Error(e))
      } finally {
        channel.close()
      }
    }

    awaitClose {
      EventBus.getDefault().unregister(subscriber)
    }
  }

  override suspend fun scanLocalBackupFolder(folderUri: Uri): List<LocalBackupInfo> = withContext(Dispatchers.IO) {
    // Persist access immediately, while the picker's transient grant is still alive. Restore jobs read from this
    // folder long after the registration activity (and its grant) are gone.
    try {
      val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      context.contentResolver.takePersistableUriPermission(folderUri, takeFlags)
    } catch (e: SecurityException) {
      Log.w(TAG, "Unable to take persistable permission for backup folder", e)
    }

    val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
    val children = folder.listFiles()

    // If the selected folder contains a SignalBackups directory, use that instead
    val signalBackupsDir = children.firstOrNull { it.isDirectory && it.name == "SignalBackups" }
    val effectiveChildren = if (signalBackupsDir != null) {
      Log.d(TAG, "Found SignalBackups directory, using it as the effective folder")
      signalBackupsDir.listFiles()
    } else {
      children
    }

    val backups = mutableListOf<LocalBackupInfo>()

    // Check for modern backups: requires a 'files' directory and signal-backup-* directories
    val hasFilesDir = effectiveChildren.any { it.isDirectory && it.name == "files" }
    if (hasFilesDir) {
      for (child in effectiveChildren) {
        if (!child.isDirectory) continue
        val name = child.name ?: continue
        val match = MODERN_BACKUP_PATTERN.matchEntire(name) ?: continue
        val (year, month, day, hour, minute, second) = match.destructured
        try {
          // V2 snapshot folders are named in UTC (see ArchiveFileSystem.createSnapshot), so convert to the device zone for display.
          val date = LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
            .atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
          backups.add(
            LocalBackupInfo(
              type = LocalBackupInfo.BackupType.V2,
              date = date,
              name = name,
              uri = child.uri
            )
          )
        } catch (e: Exception) {
          Log.w(TAG, "Failed to parse date from modern backup name: $name", e)
        }
      }
    }

    // Check for legacy backups: signal-yyyy-MM-dd-HH-mm-ss.backup files
    for (child in effectiveChildren) {
      if (!child.isFile) continue
      val name = child.name ?: continue
      val match = LEGACY_BACKUP_PATTERN.matchEntire(name) ?: continue
      val (year, month, day, hour, minute, second) = match.destructured
      try {
        val date = LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
        backups.add(
          LocalBackupInfo(
            type = LocalBackupInfo.BackupType.V1,
            date = date,
            name = name,
            uri = child.uri,
            sizeBytes = child.length()
          )
        )
      } catch (e: Exception) {
        Log.w(TAG, "Failed to parse date from legacy backup name: $name", e)
      }
    }

    backups.sortedByDescending { it.date }
  }

  override fun restoreRemoteBackup(aep: AccountEntropyPool): Flow<RemoteBackupRestoreProgress> = callbackFlow {
    val subscriber = object {
      @Subscribe(threadMode = ThreadMode.POSTING)
      fun onRestoreEvent(event: RestoreV2Event) {
        val progress = when (event.type) {
          RestoreV2Event.Type.PROGRESS_DOWNLOAD -> RemoteBackupRestoreProgress.Downloading(event.count.inWholeBytes, event.estimatedTotalCount.inWholeBytes)
          RestoreV2Event.Type.PROGRESS_RESTORE -> RemoteBackupRestoreProgress.Restoring(event.count.inWholeBytes, event.estimatedTotalCount.inWholeBytes)
          RestoreV2Event.Type.PROGRESS_FINALIZING -> RemoteBackupRestoreProgress.Finalizing
        }
        trySend(progress)
      }
    }

    EventBus.getDefault().register(subscriber)

    launch(Dispatchers.IO) {
      try {
        when (val result = BackupRepository.restoreRemoteBackup()) {
          is RemoteRestoreResult.Success -> {
            send(
              RemoteBackupRestoreProgress.Complete(
                restoredSvrPin = SignalStore.svr.pin,
                restoredProfileKey = SignalDatabase.recipients.getRecord(result.selfRecipientId).profileKey?.let { ProfileKey(it) }
              )
            )
          }
          RemoteRestoreResult.NetworkError -> {
            send(RemoteBackupRestoreProgress.NetworkError())
          }
          RemoteRestoreResult.Canceled -> {
            send(RemoteBackupRestoreProgress.Canceled)
          }
          RemoteRestoreResult.Failure -> {
            if (SignalStore.backup.hasInvalidBackupVersion) {
              send(RemoteBackupRestoreProgress.InvalidBackupVersion)
            } else {
              send(RemoteBackupRestoreProgress.GenericError())
            }
          }
          RemoteRestoreResult.PermanentSvrBFailure -> {
            send(RemoteBackupRestoreProgress.PermanentSvrBFailure)
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Remote restore failed", e)
        send(RemoteBackupRestoreProgress.GenericError(e))
      } finally {
        channel.close()
      }
    }

    awaitClose {
      EventBus.getDefault().unregister(subscriber)
    }
  }

  override fun restoreLinkAndSyncBackup(cdn: Int, key: String): Flow<LinkAndSyncProgress> = callbackFlow {
    val ephemeralBackupKeyBytes = readInProgressRegistrationData().accountData?.linkedDeviceData?.ephemeralBackupKey?.toByteArray()

    if (ephemeralBackupKeyBytes == null) {
      Log.i(TAG, "[restoreLinkAndSyncBackup] No ephemeral backup key present; nothing to restore.")
      trySend(LinkAndSyncProgress.Complete)
      channel.close()
      return@callbackFlow
    }

    val subscriber = object {
      @Subscribe(threadMode = ThreadMode.POSTING)
      fun onRestoreEvent(event: RestoreV2Event) {
        val progress = when (event.type) {
          RestoreV2Event.Type.PROGRESS_DOWNLOAD -> LinkAndSyncProgress.Downloading(event.count, event.estimatedTotalCount)
          RestoreV2Event.Type.PROGRESS_RESTORE -> LinkAndSyncProgress.Restoring
          RestoreV2Event.Type.PROGRESS_FINALIZING -> LinkAndSyncProgress.Restoring
        }
        trySend(progress)
      }
    }

    EventBus.getDefault().register(subscriber)

    launch(Dispatchers.IO) {
      try {
        when (val result = BackupRepository.restoreLinkAndSyncBackup(TransferArchiveResponse(cdn = cdn, key = key), MessageBackupKey(ephemeralBackupKeyBytes))) {
          is RemoteRestoreResult.Success -> send(LinkAndSyncProgress.Complete)
          RemoteRestoreResult.Canceled -> Log.i(TAG, "[restoreLinkAndSyncBackup] Restore canceled.")
          else -> {
            Log.w(TAG, "[restoreLinkAndSyncBackup] Link-and-sync restore did not succeed: $result")
            send(LinkAndSyncProgress.Failed())
          }
        }
      } catch (e: CancellationException) {
        Log.d(TAG, "[restoreLinkAndSyncBackup] Restore cancelled, aborting.")
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "[restoreLinkAndSyncBackup] Link-and-sync restore failed.", e)
        send(LinkAndSyncProgress.Failed(e))
      } finally {
        channel.close()
      }
    }

    awaitClose {
      EventBus.getDefault().unregister(subscriber)
    }
  }

  private suspend fun writeRegistrationData(data: RegistrationData) = withContext(Dispatchers.IO) {
    val stamped = data.newBuilder().lastUpdatedMillis(System.currentTimeMillis()).build()

    val previousUri = SignalStore.registration.inProgressRegistrationDataBlobUri?.toUri()
    val newUri = AppDependencies.blobs
      .forData(RegistrationData.ADAPTER.encode(stamped))
      .createForMultipleSessionsOnDisk(context)

    SignalStore.registration.inProgressRegistrationDataBlobUri = newUri.toString()
    previousUri?.let { AppDependencies.blobs.delete(context, it) }
    Unit
  }

  /**
   * The account's master key is always the one derived from the AEP, which we expect to have by the time we commit.
   * Restore it up-front so any master-key-derived state we touch afterwards resolves against the correct value rather
   * than lazily generating a new AEP. Returns the AEP-derived master key, if an AEP is present.
   */
  private fun applyAccountEntropyPool(data: RegistrationData): MasterKey? {
    val accountEntropyPool = data.accountEntropyPool.takeIf { it.isNotEmpty() }?.let { AccountEntropyPool(it) } ?: return null

    if (data.accountData?.linkedDeviceData != null) {
      SignalStore.account.setAccountEntropyPoolFromPrimaryDevice(accountEntropyPool)
    } else {
      SignalStore.account.restoreAccountEntropyPool(accountEntropyPool)
    }

    return accountEntropyPool.deriveMasterKey()
  }

  private fun AccountData.isComplete(): Boolean {
    return e164.isNotEmpty() && aci.isNotEmpty() && pni.isNotEmpty() && servicePassword.isNotEmpty()
  }

  /**
   * Applies the one-time [AccountData] to permanent storage, registering the account locally. This runs once per
   * registration, guarded by [RegistrationData.accountDataCommitted] -- the only re-application is after a
   * post-registration V1 backup restore, which clobbers parts of [SignalStore.account] and re-applies this frozen
   * data to heal them.
   */
  private suspend fun applyAccountData(accountData: AccountData, pin: String, registrationLockEnabled: Boolean, masterKey: MasterKey?) {
    Log.i(TAG, "[applyAccountData] Registering account locally.")

    SignalStore.account.registrationId = accountData.aciRegistrationId
    SignalStore.account.pniRegistrationId = accountData.pniRegistrationId

    accountData.linkedDeviceData?.let {
      SignalStore.account.deviceId = it.deviceId
      SignalStore.account.deviceName = it.deviceName
    }

    val aciIdentityKeyPair = IdentityKeyPair(accountData.aciIdentityKeyPair.toByteArray())
    val pniIdentityKeyPair = IdentityKeyPair(accountData.pniIdentityKeyPair.toByteArray())
    SignalStore.account.restoreAciIdentityKeyFromBackup(aciIdentityKeyPair.publicKey.serialize(), aciIdentityKeyPair.privateKey.serialize())
    SignalStore.account.restorePniIdentityKeyFromBackup(pniIdentityKeyPair.publicKey.serialize(), pniIdentityKeyPair.privateKey.serialize())

    val aci = ACI.parseOrThrow(accountData.aci)
    val pni = PNI.parseOrThrow(accountData.pni)
    val isAciChanged = SignalStore.account.aci != aci

    SignalStore.account.setAci(aci)
    SignalStore.account.setPni(pni)

    AppDependencies.resetProtocolStores()

    AppDependencies.protocolStore.aci().sessions().archiveAllSessions()
    AppDependencies.protocolStore.pni().sessions().archiveAllSessions()
    SenderKeyUtil.clearAllState()

    val aciProtocolStore = AppDependencies.protocolStore.aci()
    val pniProtocolStore = AppDependencies.protocolStore.pni()

    storeSignedAndLastResortPreKeys(aciProtocolStore, SignalStore.account.aciPreKeys, SignedPreKeyRecord(accountData.aciSignedPreKey.toByteArray()), KyberPreKeyRecord(accountData.aciLastResortKyberPreKey.toByteArray()))
    storeSignedAndLastResortPreKeys(pniProtocolStore, SignalStore.account.pniPreKeys, SignedPreKeyRecord(accountData.pniSignedPreKey.toByteArray()), KyberPreKeyRecord(accountData.pniLastResortKyberPreKey.toByteArray()))

    val profileKey = getOrCreateProfileKey(accountData.e164)
    val recipientTable = SignalDatabase.recipients
    val selfId = recipientTable.getAndPossiblyMergePnpVerified(aci, pni, accountData.e164)

    recipientTable.setProfileSharing(selfId, true)
    recipientTable.markRegisteredOrThrow(selfId, aci)
    recipientTable.linkIdsForSelf(aci, pni, accountData.e164)
    recipientTable.setProfileKey(selfId, profileKey)

    AppDependencies.recipientCache.clearSelf()

    SignalStore.account.setE164(accountData.e164)

    val now = System.currentTimeMillis()
    saveOwnIdentityKey(selfId, aci, aciProtocolStore, now)
    saveOwnIdentityKey(selfId, pni, pniProtocolStore, now)

    accountData.linkedDeviceData?.mediaRootBackupKey?.let {
      SignalStore.backup.mediaRootBackupKey = MediaRootBackupKey(it.toByteArray())
    }

    SignalStore.account.setServicePassword(accountData.servicePassword)
    SignalStore.account.setRegistered(registered = true, isAciChanged = isAciChanged)
    TextSecurePreferences.setPromptedPushRegistration(context, true)
    TextSecurePreferences.setUnauthorizedReceived(context, false)
    NotificationManagerCompat.from(context).cancel(NotificationIds.UNREGISTERED_NOTIFICATION_ID)

    SvrRepository.onRegistrationComplete(
      masterKey = if (pin.isNotEmpty()) masterKey else null,
      userPin = pin.takeIf { it.isNotEmpty() },
      hasPinToRestore = pin.isNotEmpty(),
      setRegistrationLockEnabled = registrationLockEnabled,
      restoredAEP = SignalStore.account.restoredAccountEntropyPool
    )

    restartNetwork()
    PreKeysSyncJob.enqueue()

    recipientTable.clearSelfKeyTransparencyData()
    CheckKeyTransparencyJob.enqueueIfNecessary(addDelay = true)

    val jobManager = AppDependencies.jobManager

    if (accountData.linkedDeviceData == null) {
      jobManager.add(DirectoryRefreshJob(false))
      jobManager.add(RotateCertificateJob())

      DirectoryRefreshListener.schedule(context)
      RotateSignedPreKeyListener.schedule(context)
    } else {
      SignalStore.account.isMultiDevice = true
      jobManager.runJobBlocking(RefreshOwnProfileJob(), 30.seconds)

      jobManager.add(RotateCertificateJob())
      RotateSignedPreKeyListener.schedule(context)
    }

    accountData.linkedDeviceData?.readReceipts?.let { TextSecurePreferences.setReadReceiptsEnabled(context, it) }
  }

  private fun getOrCreateProfileKey(e164: String): ProfileKey {
    val existing = SignalDatabase.recipients.getByE164(e164).getOrNull()?.let { ProfileKeyUtil.profileKeyOrNull(SignalDatabase.recipients.getRecord(it).profileKey) }
    return existing ?: ProfileKeyUtil.createNew().also { Log.i(TAG, "[commitRegistrationData] No profile key found, created a new one") }
  }

  private fun saveOwnIdentityKey(selfId: RecipientId, serviceId: ServiceId, protocolStore: SignalServiceAccountDataStoreImpl, now: Long) {
    protocolStore.identities().saveIdentityWithoutSideEffects(
      selfId,
      serviceId,
      protocolStore.identityKeyPair.publicKey,
      IdentityTable.VerifiedStatus.VERIFIED,
      true,
      now,
      true
    )
  }

  private fun storeSignedAndLastResortPreKeys(protocolStore: SignalServiceAccountDataStoreImpl, metadataStore: PreKeyMetadataStore, signedPreKey: SignedPreKeyRecord, lastResortKyberPreKey: KyberPreKeyRecord) {
    PreKeyUtil.storeSignedPreKey(protocolStore, metadataStore, signedPreKey)
    metadataStore.isSignedPreKeyRegistered = true
    metadataStore.activeSignedPreKeyId = signedPreKey.id
    metadataStore.lastSignedPreKeyRotationTime = System.currentTimeMillis()

    PreKeyUtil.storeLastResortKyberPreKey(protocolStore, metadataStore, lastResortKyberPreKey)
    metadataStore.lastResortKyberPreKeyId = lastResortKyberPreKey.id
    metadataStore.lastResortKyberPreKeyRotationTime = System.currentTimeMillis()
  }

  /**
   * Persists the restored backup folder as the backup directory and re-enables scheduled local backups, so the user
   * keeps getting backups after restoring. Best-effort: a failure here must not fail the restore itself.
   */
  private fun reenableLegacyLocalBackups(rootUri: Uri, passphrase: String) {
    try {
      BackupPassphrase.set(context, passphrase)

      val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      context.contentResolver.takePersistableUriPermission(rootUri, takeFlags)
      SignalStore.settings.setSignalBackupDirectory(rootUri)

      if (BackupUtil.canUserAccessBackupDirectory(context)) {
        LocalBackupListener.setNextBackupTimeToIntervalFromNow(context)
        SignalStore.settings.isBackupEnabled = true
        LocalBackupListener.schedule(context)
      } else {
        Log.w(TAG, "Can't access restored backup directory; not re-enabling local backups.")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to re-enable local backups after V1 restore.", e)
    }
  }

  private fun readRestoredLocalBackupState(includePreRegistrationKeys: Boolean = false): LocalBackupRestoreProgress.Complete {
    val restoredPin = SignalStore.svr.pin?.takeIf { it.isNotBlank() }
    val restoredProfileKey = SignalStore.account.aci
      ?.let { SignalDatabase.recipients.getByAci(it).getOrNull() }
      ?.let { SignalDatabase.recipients.getRecord(it).profileKey }
      ?.let { ProfileKey(it) }

    val restoredAccountEntropyPool = if (includePreRegistrationKeys) SignalStore.account.accountEntropyPoolOrNull else null
    val restoredAciIdentityKey = if (includePreRegistrationKeys && SignalStore.account.hasAciIdentityKey()) SignalStore.account.aciIdentityKey else null
    val restoredPniIdentityKey = if (includePreRegistrationKeys && SignalStore.account.hasPniIdentityKey()) SignalStore.account.pniIdentityKey else null

    return LocalBackupRestoreProgress.Complete(
      restoredSvrPin = restoredPin,
      restoredProfileKey = restoredProfileKey,
      restoredAccountEntropyPool = restoredAccountEntropyPool,
      restoredAciIdentityKey = restoredAciIdentityKey,
      restoredPniIdentityKey = restoredPniIdentityKey
    )
  }
}
