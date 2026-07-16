/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.dependencies

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.archive.stream.EncryptedBackupReader
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.AppUtil
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.network.NetworkResult
import org.signal.registration.NetworkController
import org.signal.registration.NewRegistrationData
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.RestoreDecision
import org.signal.registration.StorageController
import org.signal.registration.StoredProfileData
import org.signal.registration.proto.AccountData
import org.signal.registration.proto.ProvisioningData
import org.signal.registration.proto.RegistrationData
import org.signal.registration.sample.RegistrationApplication
import org.signal.registration.sample.storage.RegistrationDatabase
import org.signal.registration.sample.storage.RegistrationPreferences
import org.signal.registration.screens.localbackuprestore.LocalBackupInfo
import org.signal.registration.screens.messagesync.LinkAndSyncProgress
import org.signal.registration.screens.remotebackuprestore.RemoteBackupRestoreProgress
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider
import java.io.File
import java.time.LocalDateTime

/**
 * Implementation of [StorageController] that persists registration data using
 * SharedPreferences for simple key-value data and SQLite for prekeys.
 */
class DemoStorageController(private val context: Context) : StorageController {

  companion object {
    private val TAG = Log.tag(DemoStorageController::class)
    private const val TEMP_PROTO_FILENAME = "registration_data.pb"
    private const val SIMULATED_STAGE_DELAY_MS = 500L
    private const val USER_AGENT = "Signal-Android-Registration-Sample"
    private val MODERN_BACKUP_PATTERN = Regex("^signal-backup-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})$")
    private val LEGACY_BACKUP_PATTERN = Regex("^signal-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})\\.backup$")
  }

  private val db = RegistrationDatabase(context)

  override suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? = withContext(Dispatchers.IO) {
    RegistrationPreferences.getPreExistingRegistrationData()
  }

  override suspend fun getStoredProfileData(): StoredProfileData = withContext(Dispatchers.IO) {
    StoredProfileData(
      givenName = RegistrationPreferences.profileGivenName,
      familyName = RegistrationPreferences.profileFamilyName,
      avatar = RegistrationPreferences.profileAvatar,
      discoverableByPhoneNumber = RegistrationPreferences.profileDiscoverableByPhoneNumber
    )
  }

  override suspend fun clearAllData() = withContext(Dispatchers.IO) {
    File(context.filesDir, TEMP_PROTO_FILENAME).takeIf { it.exists() }?.delete()
    RegistrationPreferences.clearAll()
    RegistrationPreferences.clearRestoredSvr2Credentials()
    db.clearAllPreKeys()
  }

  override suspend fun clearLocalDataAndRestart() {
    Log.w(TAG, "[clearLocalDataAndRestart] Relink requested; clearing demo data and restarting.")
    clearAllData()
    withContext(Dispatchers.Main) {
      AppUtil.restart(context)
    }
  }

  override suspend fun readInProgressRegistrationData(): RegistrationData = withContext(Dispatchers.IO) {
    val file = File(context.filesDir, TEMP_PROTO_FILENAME)
    if (file.exists()) {
      try {
        RegistrationData.ADAPTER.decode(file.readBytes())
      } catch (e: Exception) {
        Log.w(TAG, "Failed to decode registration data, returning empty.", e)
        RegistrationData()
      }
    } else {
      RegistrationData()
    }
  }

  override suspend fun updateInProgressRegistrationData(updater: RegistrationData.Builder.() -> Unit) = withContext(Dispatchers.IO) {
    val current = readInProgressRegistrationData()
    val updated = current.newBuilder().apply(updater).build()
    writeRegistrationData(updated)
  }

  override suspend fun commitRegistrationData() = withContext(Dispatchers.IO) {
    val file = File(context.filesDir, TEMP_PROTO_FILENAME)
    val data = RegistrationData.ADAPTER.decode(file.readBytes())
    val accountData = data.accountData ?: AccountData()

    // Key material
    if (accountData.aciIdentityKeyPair.size > 0) {
      RegistrationPreferences.aciIdentityKeyPair = IdentityKeyPair(accountData.aciIdentityKeyPair.toByteArray())
    }
    if (accountData.pniIdentityKeyPair.size > 0) {
      RegistrationPreferences.pniIdentityKeyPair = IdentityKeyPair(accountData.pniIdentityKeyPair.toByteArray())
    }
    if (accountData.aciRegistrationId != 0) {
      RegistrationPreferences.aciRegistrationId = accountData.aciRegistrationId
    }
    if (accountData.pniRegistrationId != 0) {
      RegistrationPreferences.pniRegistrationId = accountData.pniRegistrationId
    }
    if (accountData.servicePassword.isNotEmpty()) {
      RegistrationPreferences.servicePassword = accountData.servicePassword
    }
    if (data.accountEntropyPool.isNotEmpty()) {
      RegistrationPreferences.aep = AccountEntropyPool(data.accountEntropyPool)
    }
    if (data.profileKey.size > 0) {
      RegistrationPreferences.profileKey = ProfileKey(data.profileKey.toByteArray())
    }
    RegistrationPreferences.fetchesMessages = accountData.fetchesMessages

    // Pre-keys
    if (accountData.aciSignedPreKey.size > 0) {
      db.signedPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_ACI, SignedPreKeyRecord(accountData.aciSignedPreKey.toByteArray()))
    }
    if (accountData.pniSignedPreKey.size > 0) {
      db.signedPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_PNI, SignedPreKeyRecord(accountData.pniSignedPreKey.toByteArray()))
    }
    if (accountData.aciLastResortKyberPreKey.size > 0) {
      db.kyberPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_ACI, KyberPreKeyRecord(accountData.aciLastResortKyberPreKey.toByteArray()))
    }
    if (accountData.pniLastResortKyberPreKey.size > 0) {
      db.kyberPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_PNI, KyberPreKeyRecord(accountData.pniLastResortKyberPreKey.toByteArray()))
    }

    // Account identity
    if (accountData.e164.isNotEmpty() && accountData.aci.isNotEmpty() && accountData.pni.isNotEmpty() && accountData.servicePassword.isNotEmpty() && data.accountEntropyPool.isNotEmpty()) {
      RegistrationPreferences.saveRegistrationData(
        NewRegistrationData(
          e164 = accountData.e164,
          aci = ACI.parseOrThrow(accountData.aci),
          pni = PNI.parseOrThrow(accountData.pni),
          servicePassword = accountData.servicePassword,
          aep = AccountEntropyPool(data.accountEntropyPool)
        )
      )
    }

    // Linked-device data (persisted so the link-and-sync step can authenticate as this device and the
    // home screen can show the linked account).
    accountData.linkedDeviceData?.let { linkData ->
      RegistrationPreferences.linkedDeviceId = linkData.deviceId
      RegistrationPreferences.ephemeralBackupKey = linkData.ephemeralBackupKey?.toByteArray()
    }

    // PIN data
    if (data.pin.isNotEmpty()) {
      RegistrationPreferences.pin = data.pin
      RegistrationPreferences.pinAlphanumeric = data.pin.any { !it.isDigit() }
    }
    if (data.masterKeyForInitialDataRestore.size > 0) {
      RegistrationPreferences.temporaryMasterKey = MasterKey(data.masterKeyForInitialDataRestore.toByteArray())
    }
    RegistrationPreferences.registrationLockEnabled = data.registrationLockEnabled

    // SVR credentials
    if (data.svrCredentials.isNotEmpty()) {
      RegistrationPreferences.restoredSvr2Credentials = data.svrCredentials.map {
        NetworkController.SvrCredentials(username = it.username, password = it.password)
      }
    }

    // Provisioning data
    data.provisioningData?.let { prov ->
      RegistrationPreferences.saveProvisioningData(
        NetworkController.ProvisioningMessage(
          accountEntropyPool = data.accountEntropyPool,
          e164 = accountData.e164,
          pin = data.pin.ifEmpty { null },
          aciIdentityKeyPair = IdentityKeyPair(accountData.aciIdentityKeyPair.toByteArray()),
          pniIdentityKeyPair = IdentityKeyPair(accountData.pniIdentityKeyPair.toByteArray()),
          platform = when (prov.platform) {
            ProvisioningData.Platform.ANDROID -> NetworkController.ProvisioningMessage.Platform.ANDROID
            ProvisioningData.Platform.IOS -> NetworkController.ProvisioningMessage.Platform.IOS
            else -> NetworkController.ProvisioningMessage.Platform.ANDROID
          },
          tier = when (prov.tier) {
            ProvisioningData.Tier.FREE -> NetworkController.ProvisioningMessage.Tier.FREE
            ProvisioningData.Tier.PAID -> NetworkController.ProvisioningMessage.Tier.PAID
            else -> null
          },
          backupTimestampMs = prov.backupTimestampMs,
          backupSizeBytes = prov.backupSizeBytes,
          restoreMethodToken = prov.restoreMethodToken,
          backupVersion = prov.backupVersion
        )
      )
    }

    Unit
  }

  override suspend fun setRestoreDecision(decision: RestoreDecision) = withContext(Dispatchers.IO) {
    Log.i(TAG, "[setRestoreDecision] Recording restore decision: $decision")
    RegistrationPreferences.restoreDecision = decision
  }

  override suspend fun scanLocalBackupFolder(folderUri: Uri): List<LocalBackupInfo> = withContext(Dispatchers.IO) {
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
          val date = LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
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

  override fun restoreLocalBackupV1(rootUri: Uri, backupUri: Uri, passphrase: String): Flow<LocalBackupRestoreProgress> = flow {
    Log.d(TAG, "Starting simulated V1 local backup restore from: $backupUri")

    require(DocumentFile.fromSingleUri(context, backupUri)?.exists() == true) { "Backup file does not exist: $backupUri" }

    emit(LocalBackupRestoreProgress.Preparing)
    delay(SIMULATED_STAGE_DELAY_MS)

    val totalBytes = 100L
    for (i in 1..4) {
      emit(LocalBackupRestoreProgress.InProgress(bytesRead = totalBytes * i / 4, totalBytes = totalBytes))
      delay(SIMULATED_STAGE_DELAY_MS)
    }

    emit(LocalBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null))
    Log.d(TAG, "Simulated V1 restore complete.")
  }.flowOn(Dispatchers.IO)

  override suspend fun verifyLocalBackupKey(backupUri: Uri, aep: AccountEntropyPool): Boolean = true

  override fun restoreLocalBackupV2(rootUri: Uri, backupUri: Uri, aep: AccountEntropyPool): Flow<LocalBackupRestoreProgress> = flow {
    Log.d(TAG, "Starting simulated V2 local backup restore from backup=$backupUri, root=$rootUri")

    require(DocumentFile.fromTreeUri(context, backupUri)?.exists() == true) { "Backup directory does not exist: $backupUri" }

    emit(LocalBackupRestoreProgress.Preparing)
    delay(SIMULATED_STAGE_DELAY_MS)

    val totalBytes = 100L
    for (i in 1..4) {
      emit(LocalBackupRestoreProgress.InProgress(bytesRead = totalBytes * i / 4, totalBytes = totalBytes))
      delay(SIMULATED_STAGE_DELAY_MS)
    }

    emit(LocalBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null))
    Log.d(TAG, "Simulated V2 restore complete.")
  }.flowOn(Dispatchers.IO)

  override fun restoreRemoteBackup(aep: AccountEntropyPool): Flow<RemoteBackupRestoreProgress> = flow {
    Log.d(TAG, "Starting simulated remote backup restore")

    val totalBytes = 10_000_000L

    for (i in 1..4) {
      emit(RemoteBackupRestoreProgress.Downloading(bytesDownloaded = totalBytes * i / 4, totalBytes = totalBytes))
      delay(250)
    }

    for (i in 1..4) {
      emit(RemoteBackupRestoreProgress.Restoring(bytesRead = totalBytes * i / 4, totalBytes = totalBytes))
      delay(250)
    }

    emit(RemoteBackupRestoreProgress.Finalizing)
    delay(250)

    emit(RemoteBackupRestoreProgress.Complete(restoredSvrPin = null, restoredProfileKey = null))
    Log.d(TAG, "Simulated remote restore complete.")
  }.flowOn(Dispatchers.IO)

  /**
   * Performs a real link-and-sync restore against the (staging) service. The demo
   * stops short of actually persisting any sync data but does read through the frames.
   */
  override fun restoreLinkAndSyncBackup(cdn: Int, key: String): Flow<LinkAndSyncProgress> = callbackFlow {
    val aci = RegistrationPreferences.aci
    val pni = RegistrationPreferences.pni
    val e164 = RegistrationPreferences.e164
    val password = RegistrationPreferences.servicePassword
    val deviceId = RegistrationPreferences.linkedDeviceId
    val ephemeralBackupKeyBytes = RegistrationPreferences.ephemeralBackupKey

    if (aci == null || e164 == null || password == null || deviceId <= 0 || ephemeralBackupKeyBytes == null) {
      Log.i(TAG, "[restoreLinkAndSyncBackup] No link-and-sync backup expected; nothing to restore.")
      trySend(LinkAndSyncProgress.Complete)
      close()
      return@callbackFlow
    }

    val job = launch(Dispatchers.IO) {
      val tempFile = File.createTempFile("link-and-sync", ".backup", context.cacheDir)
      try {
        val configuration = RegistrationApplication.serviceConfiguration
        val credentialsProvider = StaticCredentialsProvider(aci, pni, e164, deviceId, password)

        // Download the encrypted backup file from the CDN (cdn/key obtained from awaitLinkAndSyncArchive on the
        // network side), reporting progress.
        Log.i(TAG, "[restoreLinkAndSyncBackup] Downloading backup from CDN $cdn...")
        val messageReceiver = SignalServiceMessageReceiver(PushServiceSocket(configuration, credentialsProvider, USER_AGENT, true))
        val progressListener = object : SignalServiceAttachment.ProgressListener {
          override fun onAttachmentProgress(progress: AttachmentTransferProgress) {
            trySend(LinkAndSyncProgress.Downloading(progress.transmitted, progress.total))
          }

          override fun shouldCancel(): Boolean = !isActive
        }

        val download = messageReceiver.retrieveLinkAndSyncBackup(cdn, key, tempFile, progressListener)
        if (download !is NetworkResult.Success) {
          Log.w(TAG, "[restoreLinkAndSyncBackup] Failed to download backup file.")
          trySend(LinkAndSyncProgress.Failed())
          return@launch
        }

        // Decrypt + parse the backup proto to prove the round-trip worked. We intentionally do NOT
        // import the frames into a database -- that is the app's full backup-restore pipeline.
        trySend(LinkAndSyncProgress.Restoring)
        val downloadedBytes = tempFile.length()
        var frameCount = 0
        EncryptedBackupReader.createForLocalOrLinking(
          key = MessageBackupKey(ephemeralBackupKeyBytes),
          aci = aci,
          length = downloadedBytes,
          dataStream = { tempFile.inputStream() }
        ).use { reader ->
          val hasHeader = reader.getHeader() != null
          while (reader.hasNext()) {
            reader.next()
            frameCount++
          }
          Log.i(TAG, "[restoreLinkAndSyncBackup] Decrypted backup proto (header=$hasHeader, frames=$frameCount). Not importing to a database (out of scope for the demo).")
        }

        // Persist a summary so the demo's home screen can display the link-and-sync result.
        RegistrationPreferences.linkAndSyncFrameCount = frameCount
        RegistrationPreferences.linkAndSyncDownloadedBytes = downloadedBytes

        trySend(LinkAndSyncProgress.Complete)
      } catch (e: CancellationException) {
        Log.d(TAG, "[restoreLinkAndSyncBackup] Restore cancelled, aborting.")
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "[restoreLinkAndSyncBackup] Link-and-sync restore failed.", e)
        trySend(LinkAndSyncProgress.Failed(e))
      } finally {
        tempFile.delete()
        close()
      }
    }

    awaitClose { job.cancel() }
  }

  private suspend fun writeRegistrationData(data: RegistrationData) = withContext(Dispatchers.IO) {
    val stamped = data.newBuilder().lastUpdatedMillis(System.currentTimeMillis()).build()
    val file = File(context.filesDir, TEMP_PROTO_FILENAME)
    file.writeBytes(RegistrationData.ADAPTER.encode(stamped))
  }
}
