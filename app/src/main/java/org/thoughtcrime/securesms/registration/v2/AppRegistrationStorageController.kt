/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.util.logging.Log
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.StorageController
import org.signal.registration.proto.RegistrationData
import org.signal.registration.screens.localbackuprestore.LocalBackupInfo
import org.signal.registration.screens.restoreselection.ArchiveRestoreOption
import org.thoughtcrime.securesms.backup.FullBackupImporter
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.LocalRegistrationMetadata
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.pin.SvrRepository
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

/**
 * Implementation of [StorageController] that bridges to the app's existing storage infrastructure.
 */
class AppRegistrationStorageController(private val context: Context) : StorageController {

  companion object {
    private val TAG = Log.tag(AppRegistrationStorageController::class)
    private const val TEMP_PROTO_FILENAME = "registration-in-progress.proto"
    private val TEMP_PROTO_TIMEOUT = 15.minutes
    private val MODERN_BACKUP_PATTERN = Regex("^signal-backup-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})$")
    private val LEGACY_BACKUP_PATTERN = Regex("^signal-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})\\.backup$")
  }

  override suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? = withContext(Dispatchers.IO) {
    if (!SignalStore.account.isRegistered) {
      return@withContext null
    }

    val aci = SignalStore.account.aci ?: return@withContext null
    val pni = SignalStore.account.pni ?: return@withContext null
    val e164 = SignalStore.account.e164 ?: return@withContext null
    val servicePassword = SignalStore.account.servicePassword ?: return@withContext null
    val aep = SignalStore.account.accountEntropyPool ?: return@withContext null

    val aciIdentityKeyPair = SignalStore.account.aciIdentityKey
    val pniIdentityKeyPair = SignalStore.account.pniIdentityKey

    PreExistingRegistrationData(
      e164 = e164,
      aci = aci,
      pni = pni,
      servicePassword = servicePassword,
      aep = aep,
      registrationLockEnabled = SignalStore.svr.isRegistrationLockEnabled,
      aciIdentityKeyPair = aciIdentityKeyPair,
      pniIdentityKeyPair = pniIdentityKeyPair
    )
  }

  override suspend fun clearAllData() = withContext(Dispatchers.IO) {
    File(context.cacheDir, TEMP_PROTO_FILENAME).takeIf { it.exists() }?.delete()
    Unit
  }

  override suspend fun readInProgressRegistrationData(): RegistrationData = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, TEMP_PROTO_FILENAME)
    if (file.exists()) {
      val age = System.currentTimeMillis() - file.lastModified()
      if (age > TEMP_PROTO_TIMEOUT.inWholeMilliseconds) {
        Log.w(TAG, "In-progress registration data is stale (${age}ms old), discarding.")
        file.delete()
        return@withContext RegistrationData()
      }

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
    val data = readInProgressRegistrationData()

    // Build LocalRegistrationMetadata if we have enough data for account setup
    if (data.e164.isNotEmpty() && data.aci.isNotEmpty() && data.pni.isNotEmpty() && data.servicePassword.isNotEmpty()) {
      val profileKey = RegistrationRepository.getProfileKey(data.e164)

      val metadata = LocalRegistrationMetadata.Builder().apply {
        if (data.aciIdentityKeyPair.size > 0) {
          aciIdentityKeyPair = data.aciIdentityKeyPair
        }
        if (data.pniIdentityKeyPair.size > 0) {
          pniIdentityKeyPair = data.pniIdentityKeyPair
        }
        if (data.aciSignedPreKey.size > 0) {
          aciSignedPreKey = data.aciSignedPreKey
        }
        if (data.pniSignedPreKey.size > 0) {
          pniSignedPreKey = data.pniSignedPreKey
        }
        if (data.aciLastResortKyberPreKey.size > 0) {
          aciLastRestoreKyberPreKey = data.aciLastResortKyberPreKey
        }
        if (data.pniLastResortKyberPreKey.size > 0) {
          pniLastRestoreKyberPreKey = data.pniLastResortKyberPreKey
        }

        aci = data.aci
        pni = data.pni
        e164 = data.e164
        this.servicePassword = data.servicePassword
        this.profileKey = profileKey.serialize().toByteString()
        hasPin = data.pin.isNotEmpty()
        if (data.pin.isNotEmpty()) {
          pin = data.pin
        }
        if (data.temporaryMasterKey.size > 0) {
          masterKey = data.temporaryMasterKey
        }
        fcmEnabled = SignalStore.account.fcmEnabled
        fcmToken = SignalStore.account.fcmToken ?: ""
        reglockEnabled = data.registrationLockEnabled
      }.build()

      // TODO [greyson] Should probably move this stuff into this file as we get closer to being done
      RegistrationRepository.registerAccountLocally(context, metadata)
      SignalStore.registration.localRegistrationMetadata = metadata

      if (data.accountEntropyPool.isNotEmpty()) {
        SignalStore.account.restoreAccountEntropyPool(AccountEntropyPool(data.accountEntropyPool))
      }
    }

    // Handle PIN/master key
    if (data.pin.isNotEmpty() && data.temporaryMasterKey.size > 0) {
      val masterKey = MasterKey(data.temporaryMasterKey.toByteArray())
      SvrRepository.onRegistrationComplete(
        masterKey,
        data.pin,
        true,
        data.registrationLockEnabled,
        data.accountEntropyPool.isNotEmpty()
      )
    }

    Unit
  }

  override suspend fun getAvailableRestoreOptions(): Set<ArchiveRestoreOption> = withContext(Dispatchers.IO) {
    // TODO [greyson] Real options
    val options = mutableSetOf<ArchiveRestoreOption>()

    options.add(ArchiveRestoreOption.LocalBackup)
    options.add(ArchiveRestoreOption.DeviceTransfer)

    options
  }

  override fun restoreLocalBackupV1(uri: Uri, passphrase: String): Flow<LocalBackupRestoreProgress> = flow {
    // TODO [greyson] better progress
    Log.d(TAG, "Starting V1 local backup restore from: $uri")

    emit(LocalBackupRestoreProgress.Preparing)

    try {
      if (!FullBackupImporter.validatePassphrase(context, uri, passphrase)) {
        emit(LocalBackupRestoreProgress.Error(IllegalArgumentException("Invalid passphrase")))
        return@flow
      }

      val database = SignalDatabase.backupDatabase
      FullBackupImporter.importFile(
        context,
        AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
        database,
        uri,
        passphrase,
        SignalStore.registration.localRegistrationMetadata != null
      )

      SignalDatabase.runPostBackupRestoreTasks(database)

      emit(LocalBackupRestoreProgress.Complete)
      Log.d(TAG, "V1 restore complete.")
    } catch (e: FullBackupImporter.DatabaseDowngradeException) {
      Log.w(TAG, "V1 restore failed: database downgrade", e)
      emit(LocalBackupRestoreProgress.Error(e))
    } catch (e: Exception) {
      Log.w(TAG, "V1 restore failed", e)
      emit(LocalBackupRestoreProgress.Error(e))
    }
  }.flowOn(Dispatchers.IO)

  override fun restoreLocalBackupV2(rootUri: Uri, backupUri: Uri, aep: AccountEntropyPool): Flow<LocalBackupRestoreProgress> = flow {
    // TODO [greyson] better progress
    Log.d(TAG, "Starting V2 local backup restore from backup=$backupUri, root=$rootUri")

    emit(LocalBackupRestoreProgress.Preparing)

    try {
      val backupDir = DocumentFile.fromTreeUri(context, backupUri)
      if (backupDir == null || !backupDir.canRead()) {
        emit(LocalBackupRestoreProgress.Error(IllegalStateException("Could not open backup directory")))
        return@flow
      }

      val selfAci = SignalStore.account.aci
      val selfPni = SignalStore.account.pni
      val selfE164 = SignalStore.account.e164

      if (selfAci == null || selfPni == null || selfE164 == null) {
        emit(LocalBackupRestoreProgress.Error(IllegalStateException("Account not registered, cannot restore V2 backup")))
        return@flow
      }

      val selfData = BackupRepository.SelfData(selfAci, selfPni, selfE164, ProfileKeyUtil.getSelfProfileKey())
      val messageBackupKey = aep.deriveMessageBackupKey()
      val snapshotFileSystem = SnapshotFileSystem(context, backupDir)

      when (val result = LocalArchiver.import(snapshotFileSystem, selfData, messageBackupKey)) {
        is org.signal.core.util.Result.Success -> {
          emit(LocalBackupRestoreProgress.Complete)
          Log.d(TAG, "V2 restore complete.")
        }
        is org.signal.core.util.Result.Failure -> {
          Log.w(TAG, "V2 restore failed: ${result.failure}")
          emit(LocalBackupRestoreProgress.Error(IOException("V2 restore failed: ${result.failure}")))
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "V2 restore failed", e)
      emit(LocalBackupRestoreProgress.Error(e))
    }
  }.flowOn(Dispatchers.IO)

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

  private suspend fun writeRegistrationData(data: RegistrationData) = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, TEMP_PROTO_FILENAME)
    file.writeBytes(RegistrationData.ADAPTER.encode(data))
  }
}
