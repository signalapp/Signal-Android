/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import android.net.Uri
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.libsignal.net.RequestResult
import org.signal.registration.KeyMaterial
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute

class EnterAepForLocalBackupViewModelTest {

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var resultBus: ResultEventBus
  private lateinit var parentState: MutableStateFlow<RegistrationFlowState>
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<EnterAepState>
  private lateinit var stateEmitter: (EnterAepState) -> Unit

  @Before
  fun setup() {
    mockkStatic(Uri::class)
    every { Uri.parse(any()) } answers { mockk(relaxed = true) }

    mockRepository = mockk(relaxed = true)
    resultBus = ResultEventBus()
    parentState = MutableStateFlow(RegistrationFlowState(sessionE164 = E164))
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
  }

  @After
  fun tearDown() {
    unmockkStatic(Uri::class)
  }

  private fun createViewModel(isPreRegistration: Boolean = true): EnterAepForLocalBackupViewModel {
    return EnterAepForLocalBackupViewModel(
      isPreRegistration = isPreRegistration,
      backupUri = BACKUP_URI,
      repository = mockRepository,
      parentState = parentState,
      parentEventEmitter = parentEventEmitter,
      resultBus = resultBus,
      resultKey = RESULT_KEY
    )
  }

  private fun latestResult(): EnterAepForLocalBackupResult? {
    return resultBus.channelMap[RESULT_KEY]?.tryReceive()?.getOrNull() as EnterAepForLocalBackupResult?
  }

  // ==================== Already-registered mode ====================

  @Test
  fun `Submit without requiring registration hands the key back and navigates back`() = runTest {
    val viewModel = createViewModel(isPreRegistration = false)
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(latestResult()).isEqualTo(EnterAepForLocalBackupResult.RestoreReady(VALID_AEP))
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
    coVerify(exactly = 0) { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) }
  }

  // ==================== Register-first mode ====================

  @Test
  fun `Submit with a key that cannot decrypt the backup shows an inline error without registering`() = runTest {
    val viewModel = createViewModel()
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)

    coEvery { mockRepository.verifyLocalBackupKey(any(), any()) } returns false

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates.last().aepValidationError).isEqualTo(AepValidationError.Incorrect)
    assertThat(emittedStates.last().isRegistering).isEqualTo(false)
    coVerify(exactly = 0) { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `Submit with successful registration emits UserSuppliedAepSubmitted and Registered, then hands the key back`() = runTest {
    val viewModel = createViewModel()
    val aep = AccountEntropyPool(VALID_AEP)
    val mockKeyMaterial = mockk<KeyMaterial>(relaxed = true) {
      io.mockk.every { accountEntropyPool } returns aep
    }
    val mockResponse = mockk<NetworkController.RegisterAccountResponse>(relaxed = true)
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)

    coEvery { mockRepository.verifyLocalBackupKey(any(), any()) } returns true
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(mockResponse to mockKeyMaterial)

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedParentEvents).hasSize(3)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.UserSuppliedAepSubmitted>()
    assertThat(emittedParentEvents[1]).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedParentEvents[2]).isEqualTo(RegistrationFlowEvent.NavigateBack)
    assertThat(latestResult()).isEqualTo(EnterAepForLocalBackupResult.RestoreReady(VALID_AEP))
  }

  @Test
  fun `Submit with RegistrationRecoveryPasswordIncorrect shows the different account dialog`() = runTest {
    val viewModel = createViewModel()
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)

    coEvery { mockRepository.verifyLocalBackupKey(any(), any()) } returns true
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect("Incorrect")
      )

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates.last().showDifferentAccountDialog).isEqualTo(true)
    assertThat(emittedStates.last().isRegistering).isEqualTo(false)
    assertThat(latestResult()).isNull()
  }

  @Test
  fun `ConfirmDifferentAccountRestore forces the session path and hands control back for SMS verification`() = runTest {
    val viewModel = createViewModel()
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true, showDifferentAccountDialog = true)

    viewModel.applyEvent(initialState, EnterAepEvents.ConfirmDifferentAccountRestore, stateEmitter)

    assertThat(emittedStates.last().showDifferentAccountDialog).isEqualTo(false)
    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isEqualTo(RegistrationFlowEvent.RecoveryPasswordInvalid)
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.NavigateBack)
    assertThat(latestResult()).isEqualTo(EnterAepForLocalBackupResult.RegistrationDeferredToSms)
  }

  @Test
  fun `DismissDifferentAccountDialog clears the dialog`() = runTest {
    val viewModel = createViewModel()
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true, showDifferentAccountDialog = true)

    viewModel.applyEvent(initialState, EnterAepEvents.DismissDifferentAccountDialog, stateEmitter)

    assertThat(emittedStates.last().showDifferentAccountDialog).isEqualTo(false)
    assertThat(emittedParentEvents).hasSize(0)
  }

  @Test
  fun `Submit with RegistrationLock retries with the reglock token derived from the AEP`() = runTest {
    val viewModel = createViewModel()
    val aep = AccountEntropyPool(VALID_AEP)
    val mockKeyMaterial = mockk<KeyMaterial>(relaxed = true) {
      io.mockk.every { accountEntropyPool } returns aep
    }
    val mockResponse = mockk<NetworkController.RegisterAccountResponse>(relaxed = true)
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)
    val registrationLockData = NetworkController.RegistrationLockResponse(
      timeRemaining = 86400000L,
      svr2Credentials = NetworkController.SvrCredentials(username = "test-username", password = "test-password")
    )

    coEvery { mockRepository.verifyLocalBackupKey(any(), any()) } returns true
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), registrationLock = any<String>(), any(), any(), any()) } returns
      RequestResult.Success(mockResponse to mockKeyMaterial)
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), registrationLock = null, any(), any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.RegisterAccountError.RegistrationLock(registrationLockData)
      )

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    coVerify {
      mockRepository.registerAccountWithRecoveryPassword(any(), any(), registrationLock = aep.deriveMasterKey().deriveRegistrationLock(), any(), any(), any())
    }
    assertThat(latestResult()).isEqualTo(EnterAepForLocalBackupResult.RestoreReady(VALID_AEP))
  }

  @Test
  fun `Submit with RegistrationLock when already providing the reglock token navigates to PinEntryForRegistrationLock`() = runTest {
    val viewModel = createViewModel()
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)
    val registrationLockData = NetworkController.RegistrationLockResponse(
      timeRemaining = 86400000L,
      svr2Credentials = NetworkController.SvrCredentials(username = "test-username", password = "test-password")
    )

    coEvery { mockRepository.verifyLocalBackupKey(any(), any()) } returns true
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.RegisterAccountError.RegistrationLock(registrationLockData)
      )

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates.last().isRegistering).isEqualTo(false)
    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinEntryForRegistrationLock>()
  }

  @Test
  fun `Submit with RateLimited sets registrationError to RateLimited`() = runTest {
    val viewModel = createViewModel()
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)

    coEvery { mockRepository.verifyLocalBackupKey(any(), any()) } returns true
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.RegisterAccountError.RateLimited(kotlin.time.Duration.parse("1m"))
      )

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates.last().registrationError).isEqualTo(RegistrationError.RateLimited)
    assertThat(emittedStates.last().isRegistering).isEqualTo(false)
  }

  @Test
  fun `Submit with RetryableNetworkError sets registrationError to NetworkError`() = runTest {
    val viewModel = createViewModel()
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)

    coEvery { mockRepository.verifyLocalBackupKey(any(), any()) } returns true
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.RetryableNetworkError(java.io.IOException("Network error"))

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates.last().registrationError).isEqualTo(RegistrationError.NetworkError)
    assertThat(emittedStates.last().isRegistering).isEqualTo(false)
  }

  // ==================== Cancel Tests ====================

  @Test
  fun `Cancel emits NavigateBack`() = runTest {
    val viewModel = createViewModel()

    viewModel.applyEvent(EnterAepState(), EnterAepEvents.Cancel, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  // ==================== Constants ====================

  companion object {
    private const val VALID_AEP = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"
    private const val E164 = "+15551234567"
    private const val BACKUP_URI = "content://test/backups/signal-backup-2026-01-01-12-00-00"
    private const val RESULT_KEY = "test-result-key"
  }
}
