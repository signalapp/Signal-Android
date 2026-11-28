/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.RegistrationApiV2.RegisterAccountError
import org.signal.network.api.RegistrationApiV2.RegisterAccountResponse
import org.signal.network.api.RegistrationApiV2.RegistrationLockResponse
import org.signal.network.api.RegistrationApiV2.SvrCredentials
import org.signal.registration.KeyMaterial
import org.signal.registration.RegisteredAccountData
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import java.util.UUID
import kotlin.time.Duration

class EnterAepForRemoteBackupPreRegistrationViewModelTest {

  private val testAci = ACI.from(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))

  private lateinit var viewModel: EnterAepForRemoteBackupPreRegistrationViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var resultBus: ResultEventBus
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<EnterAepState>
  private lateinit var stateEmitter: (EnterAepState) -> Unit

  @Before
  fun setup() {
    mockRepository = mockk(relaxed = true)
    resultBus = ResultEventBus()
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
    viewModel = EnterAepForRemoteBackupPreRegistrationViewModel(
      e164 = E164,
      repository = mockRepository,
      parentEventEmitter = parentEventEmitter,
      resultBus = resultBus,
      resultKey = RESULT_KEY
    )
  }

  private fun latestResult(): EnterAepForRemoteBackupResult? {
    return resultBus.channelMap[RESULT_KEY]?.tryReceive()?.getOrNull() as EnterAepForRemoteBackupResult?
  }

  // ==================== BackupKeyChanged Tests ====================

  @Test
  fun `BackupKeyChanged updates state with new key`() = runTest {
    val initialState = EnterAepState()

    viewModel.applyEvent(initialState, EnterAepEvents.BackupKeyChanged(VALID_AEP), stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().recoveryKey.normalized).isEqualTo(VALID_AEP)
  }

  // ==================== Submit Success Tests ====================

  @Test
  fun `Submit with valid key and successful registration emits UserSuppliedAepSubmitted, Registered, and NavigateToScreen`() = runTest {
    val aep = AccountEntropyPool(VALID_AEP)
    val mockKeyMaterial = mockk<KeyMaterial>(relaxed = true) {
      io.mockk.every { accountEntropyPool } returns aep
    }
    val mockResponse = mockk<RegisterAccountResponse>(relaxed = true)
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP))

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(RegisteredAccountData(mockResponse, mockKeyMaterial, testAci))

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedParentEvents).hasSize(3)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.UserSuppliedAepSubmitted>()
    assertThat(emittedParentEvents[1]).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedParentEvents[2])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.RemoteRestore>()
  }

  @Test
  fun `Submit sets isRegistering true before registration call`() = runTest {
    val aep = AccountEntropyPool(VALID_AEP)
    val mockKeyMaterial = mockk<KeyMaterial>(relaxed = true) {
      io.mockk.every { accountEntropyPool } returns aep
    }
    val mockResponse = mockk<RegisterAccountResponse>(relaxed = true)
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP))

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(RegisteredAccountData(mockResponse, mockKeyMaterial, testAci))

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates).hasSize(2)
    assertThat(emittedStates[0].isRegistering).isEqualTo(true)
    assertThat(emittedStates[1].isRegistering).isEqualTo(false)
  }

  // ==================== Submit Error Tests ====================

  @Test
  fun `Submit with RegistrationRecoveryPasswordIncorrect sets registrationError and flags the recovery key`() = runTest {
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP))

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.RegistrationRecoveryPasswordIncorrect("Incorrect")
      )

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates.last().registrationError).isEqualTo(RegistrationError.IncorrectRecoveryPassword)
    assertThat(emittedStates.last().recoveryKey.error).isEqualTo(AepValidationError.Incorrect)
    assertThat(emittedStates.last().isRegistering).isEqualTo(false)
  }

  @Test
  fun `Submit with InvalidRequest sets registrationError to UnknownError`() = runTest {
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP))

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.InvalidRequest("Bad request")
      )

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates.last().registrationError).isEqualTo(RegistrationError.UnknownError)
    assertThat(emittedStates.last().isRegistering).isEqualTo(false)
  }

  @Test
  fun `Submit with RegistrationLock retries with the reglock token derived from the AEP`() = runTest {
    val aep = AccountEntropyPool(VALID_AEP)
    val mockKeyMaterial = mockk<KeyMaterial>(relaxed = true) {
      io.mockk.every { accountEntropyPool } returns aep
    }
    val mockResponse = mockk<RegisterAccountResponse>(relaxed = true)
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP))
    val registrationLockData = RegistrationLockResponse(
      timeRemaining = 86400000L,
      svr2Credentials = SvrCredentials(username = "test-username", password = "test-password")
    )

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), registrationLock = any<String>(), any(), any(), any()) } returns
      RequestResult.Success(RegisteredAccountData(mockResponse, mockKeyMaterial, testAci))
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), registrationLock = null, any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.RegistrationLock(registrationLockData)
      )

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    coVerify {
      mockRepository.registerAccountWithRecoveryPassword(any(), any(), registrationLock = aep.deriveMasterKey().deriveRegistrationLock(), any(), any(), any())
    }
    assertThat(emittedParentEvents).hasSize(3)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.UserSuppliedAepSubmitted>()
    assertThat(emittedParentEvents[1]).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedParentEvents[2])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.RemoteRestore>()
  }

  @Test
  fun `Submit with RegistrationLock when already providing the reglock token navigates to PinEntryForRegistrationLock`() = runTest {
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP))
    val svrCredentials = SvrCredentials(username = "test-username", password = "test-password")
    val registrationLockData = RegistrationLockResponse(
      timeRemaining = 86400000L,
      svr2Credentials = svrCredentials
    )

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.RegistrationLock(registrationLockData)
      )

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates.last().isRegistering).isEqualTo(false)
    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinEntryForRegistrationLock>()
  }

  @Test
  fun `Submit with RateLimited sets registrationError to RateLimited`() = runTest {
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP))

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.RateLimited(Duration.parse("1m"))
      )

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates.last().registrationError).isEqualTo(RegistrationError.RateLimited)
    assertThat(emittedStates.last().isRegistering).isEqualTo(false)
  }

  @Test(expected = IllegalStateException::class)
  fun `Submit with SessionNotFoundOrNotVerified throws IllegalStateException`() = runTest {
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP))

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.SessionNotFoundOrNotVerified("Not found")
      )

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)
  }

  @Test(expected = IllegalStateException::class)
  fun `Submit with DeviceTransferPossible throws IllegalStateException`() = runTest {
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP))

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.DeviceTransferPossible
      )

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)
  }

  @Test
  fun `Submit with RetryableNetworkError sets registrationError to NetworkError`() = runTest {
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP))

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.RetryableNetworkError(java.io.IOException("Network error"))

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates.last().registrationError).isEqualTo(RegistrationError.NetworkError)
    assertThat(emittedStates.last().isRegistering).isEqualTo(false)
  }

  @Test
  fun `Submit with ApplicationError sets registrationError to UnknownError`() = runTest {
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP))

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected"))

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates.last().registrationError).isEqualTo(RegistrationError.UnknownError)
    assertThat(emittedStates.last().isRegistering).isEqualTo(false)
  }

  // ==================== Cancel Tests ====================

  @Test
  fun `Cancel emits NavigateBack`() = runTest {
    val initialState = EnterAepState()

    viewModel.applyEvent(initialState, EnterAepEvents.Cancel, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  // ==================== DismissError Tests ====================

  @Test
  fun `DismissError clears registrationError`() = runTest {
    val initialState = EnterAepState(registrationError = RegistrationError.NetworkError)

    viewModel.applyEvent(initialState, EnterAepEvents.DismissError, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().registrationError).isNull()
  }

  // ==================== Try Another Way Tests ====================

  @Test
  fun `TryAnotherWay clears the incorrect key error and shows the verify with SMS dialog`() = runTest {
    val initialState = EnterAepState(
      recoveryKey = AepInput.from(VALID_AEP).copy(error = AepValidationError.Incorrect),
      registrationError = RegistrationError.IncorrectRecoveryPassword
    )

    viewModel.applyEvent(initialState, EnterAepEvents.TryAnotherWay, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().registrationError).isNull()
    assertThat(emittedStates.last().showVerifyWithSmsDialog).isTrue()
    assertThat(emittedParentEvents).isEmpty()
  }

  @Test
  fun `RecoveryKeyHelp emits the open help article action`() = runTest {
    val initialState = EnterAepState(registrationError = RegistrationError.IncorrectRecoveryPassword)

    viewModel.applyEvent(initialState, EnterAepEvents.RecoveryKeyHelp, stateEmitter)

    assertThat(viewModel.actions.first()).isEqualTo(EnterAepScreenActions.OpenRecoveryKeyHelpArticle)
    assertThat(emittedStates).isEmpty()
  }

  @Test
  fun `ConfirmVerifyWithSms forces the session path and hands control back to phone number entry`() = runTest {
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP), showVerifyWithSmsDialog = true)

    viewModel.applyEvent(initialState, EnterAepEvents.ConfirmVerifyWithSms, stateEmitter)

    assertThat(emittedStates.last().showVerifyWithSmsDialog).isFalse()
    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isEqualTo(RegistrationFlowEvent.RecoveryPasswordInvalid)
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.NavigateBack)
    assertThat(latestResult()).isEqualTo(EnterAepForRemoteBackupResult.VerifyWithSms)
  }

  @Test
  fun `DismissVerifyWithSmsDialog clears the dialog`() = runTest {
    val initialState = EnterAepState(recoveryKey = AepInput.from(VALID_AEP), showVerifyWithSmsDialog = true)

    viewModel.applyEvent(initialState, EnterAepEvents.DismissVerifyWithSmsDialog, stateEmitter)

    assertThat(emittedStates.last().showVerifyWithSmsDialog).isFalse()
    assertThat(emittedParentEvents).isEmpty()
    assertThat(latestResult()).isNull()
  }

  // ==================== Constants ====================

  companion object {
    private const val VALID_AEP = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"
    private const val E164 = "+15551234567"
    private const val RESULT_KEY = "test_result_key"
  }
}
