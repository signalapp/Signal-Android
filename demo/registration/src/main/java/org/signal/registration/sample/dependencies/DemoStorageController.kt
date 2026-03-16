/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.dependencies

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
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
import java.io.File

/**
 * Implementation of [StorageController] that persists registration data using
 * SharedPreferences for simple key-value data and SQLite for prekeys.
 */
class DemoStorageController(private val context: Context) : StorageController {

  companion object {
    private const val TEMP_PROTO_FILENAME = "registration_data.pb"
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
      RegistrationData.ADAPTER.decode(file.readBytes())
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

  private suspend fun writeRegistrationData(data: RegistrationData) = withContext(Dispatchers.IO) {
    val file = File(context.filesDir, TEMP_PROTO_FILENAME)
    file.writeBytes(RegistrationData.ADAPTER.encode(data))
  }
}
