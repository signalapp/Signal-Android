/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreenEvents
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsState
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSignalLoginDetailsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private val repository = mockk<SignalLoginViewDetailsRepository>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.getAci() } returns null
    every { repository.getAccountEntropyPool() } returns null
    every { repository.isOptimizedStorageEnabled() } returns false
    every { repository.areBackupsEnabled() } returns true
    every { repository.turnOffOptimizedStorageAndDownloadMedia() } returns Unit
    coEvery { repository.canResetRecoveryKey() } returns true
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state maps the stored credentials into display form`() {
    val aep = AccountEntropyPool.generate()
    every { repository.getAci() } returns ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    every { repository.getAccountEntropyPool() } returns aep

    val viewModel = SettingsSignalLoginDetailsViewModel(repository)

    assertThat(viewModel.state.value.accountKey).isEqualTo("A6B28482-2E32-83D0-7F23-91360A4C2B91")
    assertThat(viewModel.state.value.recoveryKey).isEqualTo(aep.displayValue)
  }

  @Test
  fun `initial state is empty when there are no stored credentials`() {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)

    assertThat(viewModel.state.value.accountKey).isEqualTo("")
    assertThat(viewModel.state.value.recoveryKey).isEqualTo("")
  }

  @Test
  fun `BackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.BackClicked))

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.NavigateBack)
  }

  @Test
  fun `SaveToPasswordManagerClicked asks the user to confirm the save`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked))

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.showSaveToPasswordManagerConfirmation).isTrue()
  }

  @Test
  fun `SaveToPasswordManagerClicked without a password manager tells the user there isn't one`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository, isPasswordManagerAvailable = false)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked))

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.ShowNoPasswordManagerAvailable)
  }

  @Test
  fun `SaveToPasswordManagerConfirmed hides the confirmation and launches the save`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked))
    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerConfirmed))

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.LaunchSaveToPasswordManager)
    assertThat(viewModel.state.value.showSaveToPasswordManagerConfirmation).isFalse()
  }

  @Test
  fun `SaveToPasswordManagerConfirmationDismissed hides the confirmation without saving`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked))
    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerConfirmationDismissed))

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.showSaveToPasswordManagerConfirmation).isFalse()
  }

  @Test
  fun `SaveAsPdfClicked raises the do-not-share warning before saving`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked))

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.doNotShareWarningAction).isEqualTo(SignalLoginViewDetailsState.DoNotShareWarningAction.SAVE_AS_PDF)
  }

  @Test
  fun `acknowledging the warning after SaveAsPdfClicked launches the save as PDF flow`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked))
    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.DoNotShareWarningGotItClicked))

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.LaunchSaveAsPdf)
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `CopyAccountIdClicked raises the do-not-share warning before copying`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked))

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.doNotShareWarningAction).isEqualTo(SignalLoginViewDetailsState.DoNotShareWarningAction.COPY_ACCOUNT_ID)
  }

  @Test
  fun `acknowledging the warning after CopyAccountIdClicked copies the account key to the clipboard`() = runTest(testDispatcher) {
    every { repository.getAci() } returns ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked))
    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.DoNotShareWarningGotItClicked))

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.CopyTextToClipboard("A6B28482-2E32-83D0-7F23-91360A4C2B91"))
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `acknowledging the warning after CopyRecoveryKeyClicked copies the recovery key to the clipboard`() = runTest(testDispatcher) {
    val aep = AccountEntropyPool.generate()
    every { repository.getAccountEntropyPool() } returns aep
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked))
    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.DoNotShareWarningGotItClicked))

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.CopyTextToClipboard(aep.displayValue))
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `DoNotShareWarningLearnMoreClicked drops the warning and opens the help article without copying`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked))
    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.DoNotShareWarningLearnMoreClicked))

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.OpenDoNotShareLearnMoreArticle)
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `DoNotShareWarningDismissed drops the warning without copying`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked))
    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.DoNotShareWarningDismissed))

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.doNotShareWarningAction).isNull()
  }

  @Test
  fun `the reset button is hidden unless the caller asks for it`() {
    assertThat(SettingsSignalLoginDetailsViewModel(repository).state.value.showResetRecoveryKeyButton).isFalse()
    assertThat(SettingsSignalLoginDetailsViewModel(repository, showResetRecoveryKeyButton = true).state.value.showResetRecoveryKeyButton).isTrue()
  }

  @Test
  fun `ResetRecoveryKeyClicked shows the confirmation sheet`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository, showResetRecoveryKeyButton = true)

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.ResetRecoveryKeyClicked))

    assertThat(viewModel.resetRecoveryKeyState.value.dialog).isEqualTo(ResetRecoveryKeyState.Dialog.CONFIRMATION)
  }

  @Test
  fun `ResetRecoveryKeyClicked shows the limit dialog when there are no resets left`() = runTest(testDispatcher) {
    coEvery { repository.canResetRecoveryKey() } returns false
    val viewModel = SettingsSignalLoginDetailsViewModel(repository, showResetRecoveryKeyButton = true)

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.ResetRecoveryKeyClicked))

    assertThat(viewModel.resetRecoveryKeyState.value.dialog).isEqualTo(ResetRecoveryKeyState.Dialog.KEY_LIMIT_REACHED)
  }

  @Test
  fun `confirming the reset launches the reset flow`() = runTest(testDispatcher) {
    val viewModel = SettingsSignalLoginDetailsViewModel(repository, showResetRecoveryKeyButton = true)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.ResetRecoveryKeyConfirmed)

    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.LaunchRecoveryKeyReset)
    assertThat(viewModel.resetRecoveryKeyState.value.dialog).isEqualTo(ResetRecoveryKeyState.Dialog.NONE)
  }

  @Test
  fun `confirming the reset asks the user to download offloaded media first`() = runTest(testDispatcher) {
    every { repository.isOptimizedStorageEnabled() } returns true
    val viewModel = SettingsSignalLoginDetailsViewModel(repository, showResetRecoveryKeyButton = true)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.ResetRecoveryKeyConfirmed)

    assertThat(actions).isEmpty()
    assertThat(viewModel.resetRecoveryKeyState.value.dialog).isEqualTo(ResetRecoveryKeyState.Dialog.DOWNLOAD_MEDIA)
  }

  @Test
  fun `turning off optimized storage starts the download and leaves the screen`() = runTest(testDispatcher) {
    every { repository.isOptimizedStorageEnabled() } returns true
    val viewModel = SettingsSignalLoginDetailsViewModel(repository, showResetRecoveryKeyButton = true)
    val actions = mutableListOf<SignalLoginViewDetailsAction>()
    backgroundScope.launch { viewModel.actions.toList(actions) }

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.TurnOffOptimizedStorageClicked)

    verify { repository.turnOffOptimizedStorageAndDownloadMedia() }
    assertThat(actions).containsExactly(SignalLoginViewDetailsAction.NavigateBack)
    assertThat(viewModel.resetRecoveryKeyState.value.dialog).isEqualTo(ResetRecoveryKeyState.Dialog.NONE)
  }

  @Test
  fun `RecoveryKeyRotated picks up the key the reset generated`() = runTest(testDispatcher) {
    val original = AccountEntropyPool.generate()
    val replacement = AccountEntropyPool.generate()
    every { repository.getAccountEntropyPool() } returns original

    val viewModel = SettingsSignalLoginDetailsViewModel(repository, showResetRecoveryKeyButton = true)
    assertThat(viewModel.state.value.recoveryKey).isEqualTo(original.displayValue)

    every { repository.getAccountEntropyPool() } returns replacement
    viewModel.onEvent(SettingsSignalLoginDetailsEvent.RecoveryKeyRotated)

    assertThat(viewModel.state.value.recoveryKey).isEqualTo(replacement.displayValue)
  }

  @Test
  fun `a spinner stands in for the reset button until the reset limit is known`() = runTest(testDispatcher) {
    val limit = CompletableDeferred<Boolean>()
    coEvery { repository.canResetRecoveryKey() } coAnswers { limit.await() }

    val viewModel = SettingsSignalLoginDetailsViewModel(repository, showResetRecoveryKeyButton = true)

    assertThat(viewModel.state.value.resetRecoveryKeyButtonLoading).isTrue()

    limit.complete(true)

    assertThat(viewModel.state.value.resetRecoveryKeyButtonLoading).isFalse()
  }

  @Test
  fun `ResetRecoveryKeyClicked is ignored while the reset limit is unknown`() = runTest(testDispatcher) {
    coEvery { repository.canResetRecoveryKey() } coAnswers { CompletableDeferred<Boolean>().await() }
    val viewModel = SettingsSignalLoginDetailsViewModel(repository, showResetRecoveryKeyButton = true)

    viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(SignalLoginViewDetailsScreenEvents.ResetRecoveryKeyClicked))

    assertThat(viewModel.resetRecoveryKeyState.value.dialog).isEqualTo(ResetRecoveryKeyState.Dialog.NONE)
  }
}
