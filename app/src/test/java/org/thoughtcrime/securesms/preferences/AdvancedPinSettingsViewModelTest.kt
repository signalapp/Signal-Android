/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.preferences

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.advancedpin.AdvancedPinSettingsAction
import org.signal.appsettings.advancedpin.AdvancedPinSettingsEvent
import org.signal.appsettings.advancedpin.AdvancedPinSettingsState.Dialog
import org.signal.appsettings.advancedpin.AdvancedPinSettingsState.Snackbar
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedPinSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  private var optedOut = false
  private var registrationLock = false
  private var needsRecoveryPhrase = false
  private var backupsEnabled = false

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(AdvancedPinSettingsRepository)

    every { AdvancedPinSettingsRepository.hasOptedOutOfPin() } answers { optedOut }
    every { AdvancedPinSettingsRepository.isRegistrationLockEnabled() } answers { registrationLock }
    every { AdvancedPinSettingsRepository.needsPaymentsRecoveryPhrase() } answers { needsRecoveryPhrase }
    every { AdvancedPinSettingsRepository.areBackupsEnabled() } answers { backupsEnabled }
    coEvery { AdvancedPinSettingsRepository.optOutOfPinAfterKeyRotation() } answers { optedOut = true }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(AdvancedPinSettingsRepository)
  }

  @Test
  fun `initial state reflects whether the PIN is off`() = runTest(testDispatcher) {
    optedOut = true

    assertThat(createViewModel().state.value.hasOptedOutOfPin).isTrue()
  }

  @Test
  fun `ScreenResumed re-reads whether the PIN is off`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    optedOut = true

    viewModel.onEvent(AdvancedPinSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.hasOptedOutOfPin).isTrue()
  }

  @Test
  fun `PinToggleClicked with the PIN off starts PIN creation`() = runTest(testDispatcher) {
    optedOut = true
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AdvancedPinSettingsEvent.PinToggleClicked)

    assertThat(actions).containsExactly(AdvancedPinSettingsAction.LaunchPinCreation)
  }

  @Test
  fun `PinToggleClicked explains that registration lock needs the PIN`() = runTest(testDispatcher) {
    registrationLock = true
    val viewModel = createViewModel()

    viewModel.onEvent(AdvancedPinSettingsEvent.PinToggleClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.RegistrationLockRequiresPin)
  }

  @Test
  fun `PinToggleClicked asks for the payments recovery phrase first`() = runTest(testDispatcher) {
    needsRecoveryPhrase = true
    val viewModel = createViewModel()

    viewModel.onEvent(AdvancedPinSettingsEvent.PinToggleClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.RecordPaymentsRecoveryPhrase)
  }

  @Test
  fun `PinToggleClicked with backups on asks to rotate the backup key`() = runTest(testDispatcher) {
    backupsEnabled = true
    val viewModel = createViewModel()

    viewModel.onEvent(AdvancedPinSettingsEvent.PinToggleClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.RotateBackupKey)
  }

  @Test
  fun `PinToggleClicked with nothing in the way asks to confirm the opt out`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AdvancedPinSettingsEvent.PinToggleClicked)

    assertThat(actions).containsExactly(AdvancedPinSettingsAction.ConfirmPinOptOut)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `RecordRecoveryPhraseConfirmed opens payments`() = runTest(testDispatcher) {
    needsRecoveryPhrase = true
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AdvancedPinSettingsEvent.PinToggleClicked)
    viewModel.onEvent(AdvancedPinSettingsEvent.RecordRecoveryPhraseConfirmed)

    assertThat(actions).containsExactly(AdvancedPinSettingsAction.LaunchRecoveryPhraseRecording)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `RotateBackupKeyConfirmed opens the backup key straight into rotation`() = runTest(testDispatcher) {
    backupsEnabled = true
    val viewModel = createViewModel()

    viewModel.onEvent(AdvancedPinSettingsEvent.PinToggleClicked)
    viewModel.onEvent(AdvancedPinSettingsEvent.RotateBackupKeyConfirmed)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.BackupsRoute.DisplayKey(startWithKeyRotation = true)))
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `BackupKeyRotated turns the PIN off and says so`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AdvancedPinSettingsEvent.BackupKeyRotated)

    coVerify { AdvancedPinSettingsRepository.optOutOfPinAfterKeyRotation() }
    assertThat(viewModel.state.value.hasOptedOutOfPin).isTrue()
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.PinDisabled)
  }

  @Test
  fun `PinOptedOut refreshes and says so`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    optedOut = true

    viewModel.onEvent(AdvancedPinSettingsEvent.PinOptedOut)

    assertThat(viewModel.state.value.hasOptedOutOfPin).isTrue()
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.PinDisabled)
  }

  @Test
  fun `PinCreated refreshes and says so`() = runTest(testDispatcher) {
    optedOut = true
    val viewModel = createViewModel()
    optedOut = false

    viewModel.onEvent(AdvancedPinSettingsEvent.PinCreated)

    assertThat(viewModel.state.value.hasOptedOutOfPin).isFalse()
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.PinCreated)
  }

  @Test
  fun `DialogDismissed and SnackbarDismissed clear them`() = runTest(testDispatcher) {
    backupsEnabled = true
    val viewModel = createViewModel()

    viewModel.onEvent(AdvancedPinSettingsEvent.PinToggleClicked)
    viewModel.onEvent(AdvancedPinSettingsEvent.DialogDismissed)
    viewModel.onEvent(AdvancedPinSettingsEvent.PinOptedOut)
    viewModel.onEvent(AdvancedPinSettingsEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.None)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AdvancedPinSettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
    assertThat(actions).isEmpty()
  }

  private fun createViewModel(): AdvancedPinSettingsViewModel = AdvancedPinSettingsViewModel(parentEventEmitter = { navigationEvents += it })

  private fun TestScope.collectActions(actions: Flow<AdvancedPinSettingsAction>): List<AdvancedPinSettingsAction> {
    val collected = mutableListOf<AdvancedPinSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
