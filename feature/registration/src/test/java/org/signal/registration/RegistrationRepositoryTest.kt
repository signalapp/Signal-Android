/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.content.Context
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.billing.OneTimePurchaseApi
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.network.api.RegistrationApiV2.SvrCredentials
import org.signal.registration.NetworkController.GetBackupInfoError
import org.signal.registration.NetworkController.GetBackupInfoResponse
import org.signal.registration.NetworkController.MasterKeyResponse
import org.signal.registration.NetworkController.ReserveBackupIdError
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.fakes.FakeNetworkController
import org.signal.registration.fakes.FakeStorageController
import org.signal.registration.fakes.SystemOutLogger
import org.signal.registration.proto.AccountData
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [RegistrationRepository] behavior that isn't just a passthrough to a controller.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationRepositoryTest {

  private lateinit var networkController: FakeNetworkController
  private lateinit var storageController: FakeStorageController
  private lateinit var repository: RegistrationRepository
  private lateinit var numberlessRepository: RegistrationRepository

  private val aep = AccountEntropyPool.generate()
  private val masterKey = aep.deriveMasterKey()
  private val svrCredentials = SvrCredentials(username = "user", password = "pass")
  private val backupInfo = GetBackupInfoResponse(cdn = 3, backupDir = "dir", mediaDir = "media", backupName = "backup", usedSpace = 1024L)

  @Before
  fun setup() {
    Log.initialize(SystemOutLogger())
    networkController = FakeNetworkController()
    storageController = FakeStorageController()
    repository = RegistrationRepository(
      context = mockk<Context>(relaxed = true),
      networkController = networkController,
      storageController = storageController,
      isLinkAndSyncAvailable = false,
      signalLoginPurchaseApi = OneTimePurchaseApi.Empty
    )
    numberlessRepository = RegistrationRepository(
      context = mockk<Context>(relaxed = true),
      networkController = networkController,
      storageController = storageController,
      isLinkAndSyncAvailable = false,
      isPhoneNumberlessRegistrationAvailable = true,
      signalLoginPurchaseApi = OneTimePurchaseApi.Empty
    )
  }

  // ==================== getAndMaybeHealRemoteBackupInfo ====================

  @Test
  fun `getAndMaybeHealRemoteBackupInfo does not re-commit the backup-id when the credential verifies`() = runTest {
    networkController.onGetRemoteBackupInfo = { RequestResult.Success(backupInfo) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    assertThat(networkController.reserveBackupIdCount).isEqualTo(0)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo does not re-commit the backup-id for unrelated errors`() = runTest {
    networkController.onGetRemoteBackupInfo = { RequestResult.NonSuccess(GetBackupInfoError.NoBackup) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat((result as RequestResult.NonSuccess).error).isEqualTo(GetBackupInfoError.NoBackup)
    assertThat(networkController.reserveBackupIdCount).isEqualTo(0)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo re-commits the backup-id and retries when the credential fails verification`() = runTest {
    var attempts = 0
    networkController.onGetRemoteBackupInfo = {
      attempts++
      if (attempts == 1) {
        RequestResult.NonSuccess(GetBackupInfoError.CredentialVerificationFailed)
      } else {
        RequestResult.Success(backupInfo)
      }
    }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat((result as RequestResult.Success).result).isEqualTo(backupInfo)
    assertThat(networkController.reserveBackupIdCount).isEqualTo(1)
    assertThat(attempts).isEqualTo(2)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo only re-commits the backup-id once when the retry fails too`() = runTest {
    networkController.onGetRemoteBackupInfo = { RequestResult.NonSuccess(GetBackupInfoError.CredentialVerificationFailed) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat((result as RequestResult.NonSuccess).error).isEqualTo(GetBackupInfoError.CredentialVerificationFailed)
    assertThat(networkController.reserveBackupIdCount).isEqualTo(1)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo reports the original error when the backup-id cannot be re-committed`() = runTest {
    networkController.onGetRemoteBackupInfo = { RequestResult.NonSuccess(GetBackupInfoError.CredentialVerificationFailed) }
    networkController.onReserveBackupId = { RequestResult.NonSuccess(ReserveBackupIdError.RateLimited(30.seconds)) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat((result as RequestResult.NonSuccess).error).isEqualTo(GetBackupInfoError.CredentialVerificationFailed)
    assertThat(networkController.reserveBackupIdCount).isEqualTo(1)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo reports a retryable error when re-committing the backup-id hits the network`() = runTest {
    networkController.onGetRemoteBackupInfo = { RequestResult.NonSuccess(GetBackupInfoError.CredentialVerificationFailed) }
    networkController.onReserveBackupId = { RequestResult.RetryableNetworkError(IOException("no network")) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat(result).isInstanceOf(RequestResult.RetryableNetworkError::class)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo propagates an application error from re-committing the backup-id rather than reporting a credential problem`() = runTest {
    val cause = IllegalStateException("ACI not available")
    networkController.onGetRemoteBackupInfo = { RequestResult.NonSuccess(GetBackupInfoError.CredentialVerificationFailed) }
    networkController.onReserveBackupId = { RequestResult.ApplicationError(cause) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat((result as RequestResult.ApplicationError).cause).isEqualTo(cause)
  }

  // ==================== restoreMasterKeyFromSvr ====================

  @Test
  fun `restoreMasterKeyFromSvr commits the restored data when already registered`() = runTest {
    networkController.onRestoreMasterKeyFromSvr = { RequestResult.Success(MasterKeyResponse(masterKey)) }

    val result = repository.restoreMasterKeyFromSvr(svrCredentials, pin = "1234", forRegistrationLock = false, isRegistered = true)

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    assertThat(storageController.committedData).isNotNull()
    assertThat(storageController.committedData!!.pin).isEqualTo("1234")
  }

  @Test
  fun `restoreMasterKeyFromSvr does not commit the restored data when not yet registered`() = runTest {
    networkController.onRestoreMasterKeyFromSvr = { RequestResult.Success(MasterKeyResponse(masterKey)) }

    val result = repository.restoreMasterKeyFromSvr(svrCredentials, pin = "1234", forRegistrationLock = false, isRegistered = false)

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    assertThat(storageController.committedData).isNull()
  }

  @Test
  fun `restoreMasterKeyFromSvr still records the in-progress data when not yet registered`() = runTest {
    networkController.onRestoreMasterKeyFromSvr = { RequestResult.Success(MasterKeyResponse(masterKey)) }

    repository.restoreMasterKeyFromSvr(svrCredentials, pin = "1234", forRegistrationLock = true, isRegistered = false)

    val inProgress = storageController.readInProgressRegistrationData()
    assertThat(inProgress.pin).isEqualTo("1234")
    assertThat(inProgress.masterKeyForInitialDataRestore?.toByteArray()?.toList()).isEqualTo(masterKey.serialize().toList())
    assertThat(inProgress.registrationLockEnabled).isTrue()
    assertThat(inProgress.svrCredentials.map { it.username }).isEqualTo(listOf("user"))
  }

  @Test
  fun `restoreMasterKeyFromSvr does not record or commit anything on failure`() = runTest {
    networkController.onRestoreMasterKeyFromSvr = { RequestResult.NonSuccess(RestoreMasterKeyError.WrongPin(triesRemaining = 5)) }

    val result = repository.restoreMasterKeyFromSvr(svrCredentials, pin = "1234", forRegistrationLock = false, isRegistered = true)

    assertThat(result).isInstanceOf(RequestResult.NonSuccess::class)
    assertThat(storageController.committedData).isNull()
    assertThat(storageController.readInProgressRegistrationData().pin).isEmpty()
  }

  // ==================== registerAccountWithoutPhoneNumber / reRegisterAccountWithoutPhoneNumber ====================

  @Test
  fun `reRegisterAccountWithoutPhoneNumber sends PNI key material when recovering an account by ACI`() = runTest {
    networkController.onRegisterAccount = { RequestResult.Success(networkController.registerAccountResponse(e164 = null)) }

    val result = numberlessRepository.reRegisterAccountWithoutPhoneNumber(
      aci = ACI.from(UUID.randomUUID()),
      recoveryPassword = masterKey.deriveRegistrationRecoveryPassword(),
      aep = aep
    )

    assertThat(result).isInstanceOf(RequestResult.Success::class)

    val pniPreKeys = networkController.lastRegisterAccountRequest?.pniPreKeys
    assertThat(pniPreKeys).isNotNull()
    assertThat(networkController.lastRegisterAccountRequest?.pniRegistrationId).isNotNull()
    assertThat(pniPreKeys!!.identityKey.publicKey.verifySignature(pniPreKeys.signedPreKey.keyPair.publicKey.serialize(), pniPreKeys.signedPreKey.signature)).isTrue()
    assertThat(pniPreKeys.identityKey.publicKey.verifySignature(pniPreKeys.lastResortKyberPreKey.keyPair.publicKey.serialize(), pniPreKeys.lastResortKyberPreKey.signature)).isTrue()
  }

  @Test
  fun `reRegisterAccountWithoutPhoneNumber does not keep the PNI key material it sends when recovering an account by ACI`() = runTest {
    networkController.onRegisterAccount = { RequestResult.Success(networkController.registerAccountResponse(e164 = null)) }

    numberlessRepository.reRegisterAccountWithoutPhoneNumber(
      aci = ACI.from(UUID.randomUUID()),
      recoveryPassword = masterKey.deriveRegistrationRecoveryPassword(),
      aep = aep
    )

    val accountData = storageController.committedData?.accountData
    assertThat(accountData).isNotNull()
    assertThat(accountData!!.pniIdentityKeyPair.size).isEqualTo(0)
    assertThat(accountData.pniRegistrationId).isEqualTo(0)
  }

  @Test
  fun `reRegisterAccountWithoutPhoneNumber keeps the PNI key material it sends when the reclaimed account has a phone number`() = runTest {
    networkController.onRegisterAccount = { RequestResult.Success(networkController.registerAccountResponse(e164 = "+15551234567")) }

    numberlessRepository.reRegisterAccountWithoutPhoneNumber(
      aci = ACI.from(UUID.randomUUID()),
      recoveryPassword = masterKey.deriveRegistrationRecoveryPassword(),
      aep = aep
    )

    val sentPniPreKeys = networkController.lastRegisterAccountRequest!!.pniPreKeys!!
    val accountData = storageController.committedData?.accountData
    assertThat(accountData).isNotNull()
    assertThat(IdentityKeyPair(accountData!!.pniIdentityKeyPair.toByteArray()).publicKey).isEqualTo(sentPniPreKeys.identityKey)
    assertThat(accountData.pniRegistrationId).isEqualTo(networkController.lastRegisterAccountRequest!!.pniRegistrationId)
  }

  @Test
  fun `reRegisterAccountWithoutPhoneNumber clears PNI key material left behind by an abandoned attempt`() = runTest {
    networkController.onRegisterAccount = { RequestResult.Success(networkController.registerAccountResponse(e164 = null)) }
    storageController.updateInProgressRegistrationData {
      accountData = AccountData(
        pniIdentityKeyPair = IdentityKeyPair.generate().serialize().toByteString(),
        pniSignedPreKey = "abandoned-signed-pre-key".toByteArray().toByteString(),
        pniLastResortKyberPreKey = "abandoned-kyber-pre-key".toByteArray().toByteString(),
        pniRegistrationId = 1234
      )
    }

    numberlessRepository.reRegisterAccountWithoutPhoneNumber(
      aci = ACI.from(UUID.randomUUID()),
      recoveryPassword = masterKey.deriveRegistrationRecoveryPassword(),
      aep = aep
    )

    val accountData = storageController.committedData?.accountData
    assertThat(accountData).isNotNull()
    assertThat(accountData!!.pniIdentityKeyPair.size).isEqualTo(0)
    assertThat(accountData.pniSignedPreKey.size).isEqualTo(0)
    assertThat(accountData.pniLastResortKyberPreKey.size).isEqualTo(0)
    assertThat(accountData.pniRegistrationId).isEqualTo(0)
  }

  @Test
  fun `registerAccountWithoutPhoneNumber does not send PNI key material when creating a brand new account`() = runTest {
    networkController.onRegisterAccount = { RequestResult.Success(networkController.registerAccountResponse(e164 = null)) }

    val result = numberlessRepository.registerAccountWithoutPhoneNumber(receiptCredentialPresentation = mockk(relaxed = true))

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    assertThat(networkController.lastRegisterAccountRequest?.pniPreKeys).isNull()
    assertThat(networkController.lastRegisterAccountRequest?.pniRegistrationId).isNull()
  }
}
