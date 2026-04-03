/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.dependencies

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.registration.NetworkController
import org.signal.registration.NewRegistrationData
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.StorageController
import org.signal.registration.proto.ProvisioningData
import org.signal.registration.proto.RegistrationData
import org.signal.registration.sample.storage.RegistrationDatabase
import org.signal.registration.sample.storage.RegistrationPreferences
import org.signal.registration.screens.localbackuprestore.LocalBackupInfo
import org.signal.registration.screens.restoreselection.ArchiveRestoreOption
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
    private val MODERN_BACKUP_PATTERN = Regex("^signal-backup-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})$")
    private val LEGACY_BACKUP_PATTERN = Regex("^signal-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})\\.backup$")
  }

  private val db = RegistrationDatabase(context)

  override suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? = withContext(Dispatchers.IO) {
    RegistrationPreferences.getPreExistingRegistrationData()
  }

  override suspend fun clearAllData() = withContext(Dispatchers.IO) {
    File(context.filesDir, TEMP_PROTO_FILENAME).takeIf { it.exists() }?.delete()
    RegistrationPreferences.clearAll()
    RegistrationPreferences.clearRestoredSvr2Credentials()
    db.clearAllPreKeys()
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

    // Key material
    if (data.aciIdentityKeyPair.size > 0) {
      RegistrationPreferences.aciIdentityKeyPair = IdentityKeyPair(data.aciIdentityKeyPair.toByteArray())
    }
    if (data.pniIdentityKeyPair.size > 0) {
      RegistrationPreferences.pniIdentityKeyPair = IdentityKeyPair(data.pniIdentityKeyPair.toByteArray())
    }
    if (data.aciRegistrationId != 0) {
      RegistrationPreferences.aciRegistrationId = data.aciRegistrationId
    }
    if (data.pniRegistrationId != 0) {
      RegistrationPreferences.pniRegistrationId = data.pniRegistrationId
    }
    if (data.servicePassword.isNotEmpty()) {
      RegistrationPreferences.servicePassword = data.servicePassword
    }
    if (data.accountEntropyPool.isNotEmpty()) {
      RegistrationPreferences.aep = AccountEntropyPool(data.accountEntropyPool)
    }

    // Pre-keys
    if (data.aciSignedPreKey.size > 0) {
      db.signedPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_ACI, SignedPreKeyRecord(data.aciSignedPreKey.toByteArray()))
    }
    if (data.pniSignedPreKey.size > 0) {
      db.signedPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_PNI, SignedPreKeyRecord(data.pniSignedPreKey.toByteArray()))
    }
    if (data.aciLastResortKyberPreKey.size > 0) {
      db.kyberPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_ACI, KyberPreKeyRecord(data.aciLastResortKyberPreKey.toByteArray()))
    }
    if (data.pniLastResortKyberPreKey.size > 0) {
      db.kyberPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_PNI, KyberPreKeyRecord(data.pniLastResortKyberPreKey.toByteArray()))
    }

    // Account identity
    if (data.e164.isNotEmpty() && data.aci.isNotEmpty() && data.pni.isNotEmpty() && data.servicePassword.isNotEmpty() && data.accountEntropyPool.isNotEmpty()) {
      RegistrationPreferences.saveRegistrationData(
        NewRegistrationData(
          e164 = data.e164,
          aci = ACI.parseOrThrow(data.aci),
          pni = PNI.parseOrThrow(data.pni),
          servicePassword = data.servicePassword,
          aep = AccountEntropyPool(data.accountEntropyPool)
        )
      )
    }

    // PIN data
    if (data.pin.isNotEmpty()) {
      RegistrationPreferences.pin = data.pin
      RegistrationPreferences.pinAlphanumeric = data.pinIsAlphanumeric
    }
    if (data.temporaryMasterKey.size > 0) {
      RegistrationPreferences.temporaryMasterKey = MasterKey(data.temporaryMasterKey.toByteArray())
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
          e164 = data.e164,
          pin = data.pin.ifEmpty { null },
          aciIdentityKeyPair = IdentityKeyPair(data.aciIdentityKeyPair.toByteArray()),
          pniIdentityKeyPair = IdentityKeyPair(data.pniIdentityKeyPair.toByteArray()),
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

  override suspend fun getAvailableRestoreOptions(): Set<ArchiveRestoreOption> {
    return setOf(
      ArchiveRestoreOption.SignalSecureBackup,
      ArchiveRestoreOption.LocalBackup,
      ArchiveRestoreOption.DeviceTransfer
    )
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

  override fun restoreLocalBackupV1(uri: Uri, passphrase: String): Flow<LocalBackupRestoreProgress> = flow {
    Log.d(TAG, "Starting simulated V1 local backup restore from: $uri")

    require(DocumentFile.fromSingleUri(context, uri)?.exists() == true) { "Backup file does not exist: $uri" }

    emit(LocalBackupRestoreProgress.Preparing)
    delay(SIMULATED_STAGE_DELAY_MS)

    val totalBytes = 100L
    for (i in 1..4) {
      emit(LocalBackupRestoreProgress.InProgress(bytesRead = totalBytes * i / 4, totalBytes = totalBytes))
      delay(SIMULATED_STAGE_DELAY_MS)
    }

    emit(LocalBackupRestoreProgress.Complete)
    Log.d(TAG, "Simulated V1 restore complete.")
  }.flowOn(Dispatchers.IO)

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

    emit(LocalBackupRestoreProgress.Complete)
    Log.d(TAG, "Simulated V2 restore complete.")
  }.flowOn(Dispatchers.IO)

  private suspend fun writeRegistrationData(data: RegistrationData) = withContext(Dispatchers.IO) {
    val file = File(context.filesDir, TEMP_PROTO_FILENAME)
    file.writeBytes(RegistrationData.ADAPTER.encode(data))
  }
}
