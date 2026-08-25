/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.content.Context
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController.GetBackupInfoError
import org.signal.registration.NetworkController.GetBackupInfoResponse
import org.signal.registration.NetworkController.ReserveBackupIdError
import org.signal.registration.fakes.FakeNetworkController
import org.signal.registration.fakes.FakeStorageController
import org.signal.registration.fakes.SystemOutLogger
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [RegistrationRepository] behavior that isn't just a passthrough to a controller.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationRepositoryTest {

  private lateinit var networkController: FakeNetworkController
  private lateinit var repository: RegistrationRepository

  private val aep = AccountEntropyPool.generate()
  private val backupInfo = GetBackupInfoResponse(cdn = 3, backupDir = "dir", mediaDir = "media", backupName = "backup", usedSpace = 1024L)

  @Before
  fun setup() {
    Log.initialize(SystemOutLogger())
    networkController = FakeNetworkController()
    repository = RegistrationRepository(
      context = mockk<Context>(relaxed = true),
      networkController = networkController,
      storageController = FakeStorageController(),
      isLinkAndSyncAvailable = false
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
}
